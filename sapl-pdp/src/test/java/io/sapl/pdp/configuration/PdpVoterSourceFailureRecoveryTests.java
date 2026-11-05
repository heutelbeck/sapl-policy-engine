/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.pdp.configuration;

import static com.github.valfirst.slf4jtest.TestLoggerFactory.getTestLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.pdp.ThreadLocalRandomIdFactory;
import io.sapl.pdp.configuration.PreparationResult.Accepted;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent.ConfigurationError;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent.ConfigurationExpired;
import io.sapl.pdp.plugins.MutablePluginsSource;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.PluginsSource;

import lombok.val;

@ExtendWith(MockitoExtension.class)
@DisplayName("Configuration failures preserve a usable status and explain recovery and cleanup")
class PdpVoterSourceFailureRecoveryTests {

    private static final String  PDP_ID = "orders";
    private static final Instant NOW    = Instant.parse("2026-10-31T12:00:00Z");

    @Mock
    private Clock               clock;
    @Mock
    private ExtensionsProcessor host;
    @Mock
    private AttributeBroker     attributeBroker;
    @Mock
    private PluginsSource       pluginSource;

    @AfterEach
    @BeforeEach
    void clearLogs() {
        getTestLogger(PdpVoterSource.class).clear();
        getTestLogger(ConfigurationUpdateQueue.class).clear();
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Previous configuration: {0}")
    @DisplayName("An unexpected publication failure records rejection and a later retry commits matching extensions")
    void whenLoadThrowsUnexpectedlyThenStatusAndLaterExtensionsMatchPolicies(boolean previous) {
        val events  = new MutablePluginsSource(plugins());
        val armed   = new AtomicBoolean();
        val failure = new IllegalStateException("Clock contract violation");
        when(clock.instant()).thenAnswer(ignored -> {
            if (armed.get()) {
                throw failure;
            }
            return NOW;
        });
        try (val source = source(events)) {
            val commits = new ArrayList<PDPConfiguration>();
            doAnswer(invocation -> {
                commits.add(invocation.getArgument(1, PDPConfiguration.class));
                return null;
            }).when(host).commit(eq(PDP_ID), any(PDPConfiguration.class));
            if (previous) {
                source.loadConfiguration(configuration("active", false));
            }
            val original    = source.getCurrentConfiguration(PDP_ID);
            val replacement = configuration("replacement", true);
            armed.set(true);

            assertThatThrownBy(() -> source.loadConfiguration(replacement)).isSameAs(failure);

            val failedStatus = source.getPdpStatus(PDP_ID);
            val failedVoter  = source.getCurrentConfiguration(PDP_ID);
            armed.set(false);

            events.publish(plugins());

            val recoveredStatus   = source.getPdpStatus(PDP_ID);
            val recoveredDecision = decision(source);
            assertSoftly(softly -> {
                softly.assertThat(failedStatus).hasValueSatisfying(status -> {
                    softly.assertThat(status.state()).isEqualTo(previous ? PdpState.STALE : PdpState.ERROR);
                    softly.assertThat(status.lastFailedLoad()).isNull();
                    softly.assertThat(status.lastError()).contains("replacement", PDP_ID, "IllegalStateException",
                            "Clock contract violation");
                });
                if (previous) {
                    softly.assertThat(failedVoter).containsSame(original.orElseThrow());
                }
                softly.assertThat(recoveredStatus).get().returns(PdpState.LOADED, PdpStatus::state)
                        .returns("replacement", PdpStatus::configurationId);
                softly.assertThat(recoveredDecision).isEqualTo(Decision.DENY);
                softly.assertThat(commits).last().isEqualTo(replacement);
            });
            assertOriginalCause(failure, "replacement", PDP_ID, "IllegalStateException");
        }
    }

    @Test
    @DisplayName("Unexpected migration failure fails closed and a later plugin update can recover")
    void whenMigrationThrowsUnexpectedlyThenRetiredVoterCannotKeepServing() {
        val events  = new MutablePluginsSource(plugins());
        val armed   = new AtomicBoolean();
        val failure = new IllegalStateException("Clock contract violation");
        when(clock.instant()).thenAnswer(ignored -> {
            if (armed.get()) {
                throw failure;
            }
            return NOW;
        });
        try (val source = source(events)) {
            source.loadConfiguration(configuration("active", false));
            armed.set(true);
            val replacement = plugins();

            assertThatCode(() -> events.publish(replacement)).doesNotThrowAnyException();

            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.ERROR, PdpStatus::state)
                    .satisfies(status -> assertThat(status.lastError()).contains("active", PDP_ID,
                            "IllegalStateException", "Clock contract violation"));
            assertThat(decision(source)).isEqualTo(Decision.INDETERMINATE);
            assertOriginalCause(failure, "active", PDP_ID, "IllegalStateException");
            armed.set(false);
            events.publish(plugins());
            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.LOADED, PdpStatus::state);
            assertThat(decision(source)).isEqualTo(Decision.PERMIT);
        }
    }

    @EnumSource(ClosedOperation.class)
    @ParameterizedTest(name = "Ignored operation: {0}")
    @DisplayName("An operation ignored after close identifies itself in a warning")
    void whenClosedSourceReceivesOperationThenWarningExplainsWhatWasIgnored(ClosedOperation operation) {
        when(clock.instant()).thenReturn(NOW);
        val events = new MutablePluginsSource(plugins());
        try (val source = source(events)) {
            source.loadConfiguration(configuration("active", false));
            source.close();
            getTestLogger(PdpVoterSource.class).clear();

            operation.run(source);

            assertThat(source.getCurrentConfiguration(PDP_ID)).isEmpty();
            assertThat(source.getPdpStatus(PDP_ID)).isEmpty();
            assertThat(getTestLogger(PdpVoterSource.class).getLoggingEvents()).singleElement().satisfies(
                    event -> assertThat(event.getFormattedMessage()).contains("closed", PDP_ID, operation.word));
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("Open decision subscriptions can close silently after their configuration source shuts down")
    void whenDecisionStreamsCloseAfterSourceThenUnsubscribingIsSilentAndIdempotent() throws InterruptedException {
        when(clock.instant()).thenReturn(NOW);
        val events = new MutablePluginsSource(plugins());
        try (val source = source(events)) {
            source.loadConfiguration(configuration("active", false));
            val pdp          = new BlockingPolicyDecisionPoint(source, attributeBroker,
                    new ThreadLocalRandomIdFactory());
            val subscription = AuthorizationSubscription.of("alice", "read", "archive");
            try (val first = pdp.decide(subscription, PDP_ID); val second = pdp.decide(subscription, PDP_ID)) {
                assertThat(first.awaitNext().decision()).isEqualTo(Decision.PERMIT);
                assertThat(second.awaitNext().decision()).isEqualTo(Decision.PERMIT);
                source.close();
                getTestLogger(PdpVoterSource.class).clear();

                first.close();
                second.close();
                source.unsubscribeFromUpdates(PDP_ID, ignored -> {});

                assertThat(getTestLogger(PdpVoterSource.class).getLoggingEvents()).isEmpty();
                assertThat(source.getAllPdpStatuses()).isEmpty();
            }
        }
    }

    @EnumSource(HostOperation.class)
    @ParameterizedTest(name = "Host operation: {0}")
    @DisplayName("Host lifecycle failures retain the original cause and identify the operation")
    void whenHostLifecycleThrowsThenDiagnosticIdentifiesOperationAndCause(HostOperation operation) {
        when(clock.instant()).thenReturn(NOW);
        val events  = new MutablePluginsSource(plugins());
        val failure = new IllegalStateException("Host lifecycle failure");
        try (val source = source(events)) {
            val active = configuration("active", false);
            if (operation == HostOperation.COMMIT) {
                doThrow(failure).when(host).commit(PDP_ID, active);
            }
            source.loadConfiguration(active);
            if (operation == HostOperation.REMOVE) {
                doThrow(failure).when(host).remove(PDP_ID);
                source.removeConfigurationForPdp(PDP_ID);
                assertThat(source.getPdpStatus(PDP_ID)).isEmpty();
            } else {
                assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.LOADED, PdpStatus::state);
            }

            assertOriginalCause(failure, operation.name().toLowerCase(), PDP_ID, "IllegalStateException");
        }
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Assertion failure: {0}")
    @DisplayName("A broken listener cannot prevent activation and its diagnostic retains the failure")
    void whenListenerThrowsThenOtherListenersAndActivationContinue(boolean assertion) {
        when(clock.instant()).thenReturn(NOW);
        val events  = new MutablePluginsSource(plugins());
        val failure = assertion ? new AssertionError("Listener failure")
                : new IllegalStateException("Listener failure");
        try (val source = source(events)) {
            source.subscribeToUpdates(PDP_ID, event -> {
                if (event instanceof PdpUpdateEvent.Voter) {
                    if (failure instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw (Error) failure;
                }
            });
            val received = new ArrayList<PdpUpdateEvent>();
            source.subscribeToUpdates(PDP_ID, received::add);
            received.clear();
            val active = configuration("active", false);

            assertThatCode(() -> source.loadConfiguration(active)).doesNotThrowAnyException();

            assertThat(received).singleElement().isInstanceOf(PdpUpdateEvent.Voter.class);
            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.LOADED, PdpStatus::state);
            assertOriginalCause(failure, "listener", "active", PDP_ID, failure.getClass().getSimpleName());
        }
    }

    @Test
    @DisplayName("Unsubscribe failure cannot prevent closing policies and host resources")
    void whenPluginUnsubscribeThrowsThenSourceStillCleansUp() {
        when(clock.instant()).thenReturn(NOW);
        when(host.prepare(anyString(), any(PDPConfiguration.class))).thenReturn(new Accepted());
        doAnswer(invocation -> {
            invocation.<Consumer<PluginsBundle>>getArgument(0).accept(plugins());
            return null;
        }).when(pluginSource).subscribe(any());
        val failure = new IllegalStateException("Plugin unsubscribe failure");
        doThrow(failure).when(pluginSource).unsubscribe(any());
        try (val source = new PdpVoterSource(pluginSource, clock, host)) {
            source.loadConfiguration(configuration("active", false));

            assertThatCode(source::close).doesNotThrowAnyException();

            assertThat(source.getCurrentConfiguration(PDP_ID)).isEmpty();
            assertThat(source.getAllPdpStatuses()).isEmpty();
            verify(host).remove(PDP_ID);
            assertOriginalCause(failure, "unsubscribe", "IllegalStateException");
        }
    }

    @Test
    @DisplayName("A fatal load abort reports discarded work, fails closed and releases the source")
    void whenLoadAbortsWithErrorThenQueuedWorkIsReportedAndDoesNotRun() {
        val events  = new MutablePluginsSource(plugins());
        val armed   = new AtomicBoolean();
        val failure = new AssertionError("Clock failed fatally");
        when(clock.instant()).thenAnswer(ignored -> {
            if (armed.get()) {
                throw failure;
            }
            return NOW;
        });
        try (val source = source(events)) {
            source.loadConfiguration(configuration("active", false));
            val fatal = configuration("fatal", true);
            doAnswer(ignored -> {
                source.loadConfiguration(configuration("discarded", true));
                armed.set(true);
                return new Accepted();
            }).when(host).prepare(PDP_ID, fatal);

            assertThatThrownBy(() -> source.loadConfiguration(fatal)).isSameAs(failure);

            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.ERROR, PdpStatus::state);
            assertThat(decision(source)).isEqualTo(Decision.INDETERMINATE);
            assertThat(getTestLogger(ConfigurationUpdateQueue.class).getLoggingEvents()).singleElement()
                    .satisfies(event -> {
                        assertThat(event.getFormattedMessage()).contains("fatal", "1").containsIgnoringCase("discard");
                        assertThat(event.getThrowable()).containsSame(failure);
                    });
            armed.set(false);
            source.loadConfiguration(configuration("recovered", false));
            assertThat(source.getPdpStatus(PDP_ID)).get().returns("recovered", PdpStatus::configurationId);
        }
    }

    private PdpVoterSource source(MutablePluginsSource events) {
        when(host.prepare(anyString(), any(PDPConfiguration.class))).thenReturn(new Accepted());
        return new PdpVoterSource(events, clock, host);
    }

    private Decision decision(PdpVoterSource source) {
        val pdp = new BlockingPolicyDecisionPoint(source, attributeBroker, new ThreadLocalRandomIdFactory());
        return pdp.decideOnce(AuthorizationSubscription.of("alice", "read", "archive"), PDP_ID).decision();
    }

    private static void assertOriginalCause(Throwable failure, String... details) {
        assertThat(getTestLogger(PdpVoterSource.class).getLoggingEvents())
                .filteredOn(event -> event.getThrowable().filter(cause -> cause == failure).isPresent()).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains(details));
    }

    private static PluginsBundle plugins() {
        return new PluginsBundle(new DefaultFunctionBroker());
    }

    private static PDPConfiguration configuration(String id, boolean deny) {
        return new PDPConfiguration(PDP_ID, id, CombiningAlgorithm.DEFAULT, List.of(deny ? """
                policy "access" deny""" : """
                policy "access" permit"""), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT))
                .withExtensions(Map.of("upstreams", Value.of(id)), Map.of());
    }

    private enum HostOperation {
        COMMIT,
        REMOVE
    }

    private enum ClosedOperation {
        LOAD("load"),
        REMOVE("remove"),
        EXPIRE("expire"),
        ERROR("error"),
        SUBSCRIBE("subscribe");

        private final String word;

        ClosedOperation(String word) {
            this.word = word;
        }

        private void run(PdpVoterSource source) {
            switch (this) {
            case LOAD      -> source.loadConfiguration(configuration("late", false));
            case REMOVE    -> source.removeConfigurationForPdp(PDP_ID);
            case EXPIRE    -> source.handle(new ConfigurationExpired(PDP_ID, "expired"));
            case ERROR     -> source.handle(new ConfigurationError(PDP_ID, "invalid"));
            case SUBSCRIBE -> source.subscribeToUpdates(PDP_ID, ignored -> {});
            }
        }
    }
}
