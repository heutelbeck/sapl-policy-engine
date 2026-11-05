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
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
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
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent.ConfigurationError;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent.ConfigurationExpired;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.StaticPluginsSource;

import lombok.val;

@ExtendWith(MockitoExtension.class)
@DisplayName("Status timestamp failures cannot prevent configuration transitions")
class PdpVoterSourceTimestampTests {

    private static final Instant NOW           = Instant.parse("2026-10-31T12:00:00Z");
    private static final String  PDP_ID        = "orders";
    private static final String  PERMIT_POLICY = """
            policy "access" permit""";
    private static final String  DENY_POLICY   = """
            policy "access" deny""";

    @Mock
    private Clock           clock;
    @Mock
    private AttributeBroker attributeBroker;

    @AfterEach
    @BeforeEach
    void clearTimestampLog() {
        getTestLogger(PdpVoterSource.class).clear();
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Previous configuration: {0}")
    @DisplayName("A valid configuration becomes active without an available status timestamp")
    void whenSuccessTimestampIsUnavailableThenConfigurationIsLoaded(boolean previous) {
        try (val source = source(previous)) {
            val failure     = failClock();
            val replacement = configuration("replacement", DENY_POLICY);

            assertThatCode(() -> source.loadConfiguration(replacement)).doesNotThrowAnyException();

            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.LOADED, PdpStatus::state)
                    .returns("replacement", PdpStatus::configurationId).returns(null, PdpStatus::lastSuccessfulLoad)
                    .returns(null, PdpStatus::lastFailedLoad).returns(null, PdpStatus::lastError);
            assertThat(source.getCurrentConfiguration(PDP_ID)).get()
                    .satisfies(compiled -> assertThat(compiled.metadata().configurationId()).isEqualTo("replacement"));
            assertDecision(source, Decision.DENY);
            assertTimestampWarning(failure);
        }
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Previous configuration: {0}")
    @DisplayName("A compilation failure retains its cause when the failure timestamp is unavailable")
    void whenCompilationFailureTimestampIsUnavailableThenFailureIsRecorded(boolean previous) {
        try (val source = source(previous)) {
            val original = source.getCurrentConfiguration(PDP_ID);
            val failure  = failClock();
            val rejected = configuration("rejected", "not a SAPL document");

            assertThatCode(() -> source.loadConfiguration(rejected)).doesNotThrowAnyException();

            assertFailureStatus(source, previous);
            assertThat(source.getPdpStatus(PDP_ID)).get().satisfies(status -> assertThat(status.lastError())
                    .contains("Configuration 'rejected'", PDP_ID).doesNotContain(failure.getMessage()));
            if (previous) {
                assertThat(source.getCurrentConfiguration(PDP_ID)).containsSame(original.orElseThrow());
            } else {
                assertThat(source.getCurrentConfiguration(PDP_ID)).isPresent();
            }
            assertDecision(source, previous ? Decision.PERMIT : Decision.INDETERMINATE);
            assertTimestampWarning(failure);
        }
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Previous configuration: {0}")
    @DisplayName("A source rejection retains its cause when the failure timestamp is unavailable")
    void whenSourceRejectionTimestampIsUnavailableThenFailureIsRecorded(boolean previous) {
        try (val source = source(previous)) {
            val original = source.getCurrentConfiguration(PDP_ID);
            val failure  = failClock();
            val reason   = "Invalid configuration signature.";
            val event    = new ConfigurationError(PDP_ID, reason);

            assertThatCode(() -> source.handle(event)).doesNotThrowAnyException();

            assertFailureStatus(source, previous);
            assertThat(source.getPdpStatus(PDP_ID)).get().returns(reason, PdpStatus::lastError);
            assertThat(source.getCurrentConfiguration(PDP_ID)).isEqualTo(original);
            assertDecision(source, previous ? Decision.PERMIT : Decision.INDETERMINATE);
            assertTimestampWarning(failure);
        }
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Previous configuration: {0}")
    @DisplayName("An expired configuration stops serving even when its failure timestamp is unavailable")
    void whenExpirationTimestampIsUnavailableThenPdpFailsClosed(boolean previous) {
        try (val source = source(previous)) {
            val updates = new ArrayList<PdpUpdateEvent>();
            source.subscribeToUpdates(PDP_ID, updates::add);
            updates.clear();
            val failure = failClock();
            val reason  = "Configuration freshness limit exceeded.";
            val event   = new ConfigurationExpired(PDP_ID, reason);

            assertThatCode(() -> source.handle(event)).doesNotThrowAnyException();

            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.ERROR, PdpStatus::state)
                    .returns(null, PdpStatus::configurationId)
                    .returns(previous ? NOW : null, PdpStatus::lastSuccessfulLoad)
                    .returns(null, PdpStatus::lastFailedLoad).returns(reason, PdpStatus::lastError);
            assertThat(source.getCurrentConfiguration(PDP_ID)).isEmpty();
            assertThat(updates).containsExactly(new PdpUpdateEvent.Removed(PDP_ID));
            assertDecision(source, Decision.INDETERMINATE);
            assertTimestampWarning(failure);
        }
    }

    private PdpVoterSource source(boolean previous) {
        val plugins = new StaticPluginsSource(new PluginsBundle(new DefaultFunctionBroker()));
        val source  = new PdpVoterSource(plugins, clock);
        if (previous) {
            when(clock.instant()).thenReturn(NOW);
            source.loadConfiguration(configuration("active", PERMIT_POLICY));
        }
        return source;
    }

    private DateTimeException failClock() {
        val failure = new DateTimeException("Clock unavailable while recording configuration status");
        when(clock.instant()).thenThrow(failure);
        return failure;
    }

    private void assertFailureStatus(PdpVoterSource source, boolean previous) {
        assertThat(source.getPdpStatus(PDP_ID)).get()
                .returns(previous ? PdpState.STALE : PdpState.ERROR, PdpStatus::state)
                .returns(previous ? "active" : null, PdpStatus::configurationId)
                .returns(previous ? NOW : null, PdpStatus::lastSuccessfulLoad).returns(null, PdpStatus::lastFailedLoad);
    }

    private void assertDecision(PdpVoterSource source, Decision expected) {
        val pdp          = new BlockingPolicyDecisionPoint(source, attributeBroker, new ThreadLocalRandomIdFactory());
        val subscription = AuthorizationSubscription.of("alice", "read", "archive");
        assertThat(pdp.decideOnce(subscription, PDP_ID).decision()).isEqualTo(expected);
    }

    private static void assertTimestampWarning(DateTimeException failure) {
        assertThat(getTestLogger(PdpVoterSource.class).getLoggingEvents())
                .filteredOn(event -> event.getThrowable().isPresent()).singleElement().satisfies(event -> {
                    assertThat(event.getThrowable()).containsSame(failure);
                    assertThat(event.getFormattedMessage()).contains(PDP_ID, "without a timestamp");
                });
    }

    private static PDPConfiguration configuration(String id, String policy) {
        return new PDPConfiguration(PDP_ID, id, CombiningAlgorithm.DEFAULT, List.of(policy),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }
}
