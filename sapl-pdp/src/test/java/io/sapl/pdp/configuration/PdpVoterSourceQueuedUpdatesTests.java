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

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.pdp.configuration.PreparationResult.Accepted;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent.ConfigurationError;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent.ConfigurationExpired;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent.ConfigurationRemoved;
import io.sapl.pdp.plugins.MutablePluginsSource;
import io.sapl.pdp.plugins.PluginsBundle;

import lombok.val;

@ExtendWith(MockitoExtension.class)
@DisplayName("Configuration changes requested by callbacks run after the current transition")
class PdpVoterSourceQueuedUpdatesTests {

    private static final String  PDP_ID = "orders";
    private static final String  REASON = "Configuration is no longer usable.";
    private static final Instant NOW    = Instant.parse("2026-10-31T12:00:00Z");
    private static final Clock   CLOCK  = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private ExtensionsProcessor host;
    @Mock
    private Clock               clock;

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Initial subscription delivery: {0}")
    @DisplayName("A nested load returns before activation and callbacks observe a complete active status")
    void whenListenerRequestsLoadThenActivationWaitsForCallbackReturn(boolean initialDelivery) {
        val events = new MutablePluginsSource(plugins());
        try (val source = source(events)) {
            val original     = configuration("original");
            val replacement  = configuration("replacement");
            val observations = new ArrayList<String>();
            if (initialDelivery) {
                source.loadConfiguration(original);
            }
            source.subscribeToUpdates(PDP_ID, onceForOriginal(() -> {
                observations.add(view(source));
                source.loadConfiguration(replacement);
                observations.add(view(source));
            }));
            if (!initialDelivery) {
                source.loadConfiguration(original);
            }

            assertThat(observations).containsExactly("original/LOADED", "original/LOADED");
            assertThat(view(source)).isEqualTo("replacement/LOADED");
        }
    }

    @EnumSource(HostCallback.class)
    @ParameterizedTest(name = "Host callback: {0}")
    @DisplayName("A host callback completes before another configuration is prepared or committed")
    void whenHostRequestsLoadThenCurrentHostOperationFinishesFirst(HostCallback callback) {
        val events = new MutablePluginsSource(plugins());
        try (val source = source(events)) {
            val      original    = configuration("original");
            val      replacement = configuration("replacement");
            val      deployments = new ArrayList<String>();
            val      observed    = new AtomicReference<String>();
            Runnable request     = () -> {
                                     source.loadConfiguration(replacement);
                                     observed.set(activeId(source));
                                 };
            doAnswer(invocation -> {
                val configuration = invocation.getArgument(1, PDPConfiguration.class);
                if (callback == HostCallback.COMMIT && "original".equals(configuration.configurationId())) {
                    request.run();
                }
                deployments.add(configuration.configurationId());
                return null;
            }).when(host).commit(eq(PDP_ID), any(PDPConfiguration.class));
            if (callback == HostCallback.PREPARE) {
                doAnswer(ignored -> {
                    request.run();
                    return new Accepted();
                }).when(host).prepare(PDP_ID, original);
            }

            source.loadConfiguration(original);

            assertSoftly(softly -> {
                softly.assertThat(observed.get()).isEqualTo(callback == HostCallback.PREPARE ? "absent" : "original");
                softly.assertThat(deployments).containsExactly("original", "replacement");
                softly.assertThat(view(source)).isEqualTo("replacement/LOADED");
            });
        }
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Requested from host commit: {0}")
    @DisplayName("A nested plugin change cannot change the runtime during the current transition")
    void whenCallbackPublishesPluginsThenRuntimeChangesAfterTransition(boolean fromHost) {
        val initial     = plugins();
        val replacement = plugins();
        val events      = new MutablePluginsSource(initial);
        try (val source = source(events)) {
            val      observed  = new AtomicReference<PluginsBundle>();
            val      requested = new AtomicBoolean();
            Runnable request   = () -> {
                                   if (requested.compareAndSet(false, true)) {
                                       events.publish(replacement);
                                       observed.set(source.getPlugins());
                                   }
                               };
            if (fromHost) {
                doAnswer(ignored -> {
                    request.run();
                    return null;
                }).when(host).commit(eq(PDP_ID), any(PDPConfiguration.class));
            } else {
                source.subscribeToUpdates(PDP_ID, onceForOriginal(request));
            }

            source.loadConfiguration(configuration("original"));

            assertSoftly(softly -> {
                softly.assertThat(observed.get()).isSameAs(initial);
                softly.assertThat(source.getPlugins()).isSameAs(replacement);
                softly.assertThat(source.getCurrentConfiguration(PDP_ID)).get().returns(replacement,
                        compiled -> compiled.plugins());
            });
        }
    }

    @EnumSource(SourceEvent.class)
    @ParameterizedTest(name = "Requested event: {0}")
    @DisplayName("Removal, expiration and rejection requested by a listener wait for the current commit")
    void whenListenerRequestsSourceEventThenEventRunsAfterActivation(SourceEvent event) {
        val events = new MutablePluginsSource(plugins());
        try (val source = source(events)) {
            val observed = new AtomicReference<String>();
            source.subscribeToUpdates(PDP_ID, onceForOriginal(() -> {
                source.handle(event.event());
                observed.set(view(source));
            }));

            source.loadConfiguration(configuration("original"));

            assertThat(observed.get()).isEqualTo("original/LOADED");
            switch (event) {
            case REMOVED -> {
                assertThat(source.getCurrentConfiguration(PDP_ID)).isEmpty();
                assertThat(source.getPdpStatus(PDP_ID)).isEmpty();
            }
            case EXPIRED -> {
                assertThat(source.getCurrentConfiguration(PDP_ID)).isEmpty();
                assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.ERROR, PdpStatus::state).returns(REASON,
                        PdpStatus::lastError);
            }
            case ERROR   -> assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.STALE, PdpStatus::state)
                    .returns("original", PdpStatus::configurationId).returns(REASON, PdpStatus::lastError);
            }
        }
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Configuration expired: {0}")
    @DisplayName("A load requested by a removal listener runs after the old extensions are removed")
    void whenRemovalListenerRequestsLoadThenReplacementSurvivesCleanup(boolean expired) {
        val events = new MutablePluginsSource(plugins());
        try (val source = source(events)) {
            source.loadConfiguration(configuration("original"));
            val operations = new ArrayList<String>();
            recordCommits(operations);
            doAnswer(ignored -> {
                operations.add("remove");
                return null;
            }).when(host).remove(PDP_ID);
            val observed = new AtomicReference<String>();
            source.subscribeToUpdates(PDP_ID, event -> {
                if (event instanceof PdpUpdateEvent.Removed) {
                    source.loadConfiguration(configuration("replacement"));
                    observed.set(activeId(source));
                }
            });

            source.handle(expired ? new ConfigurationExpired(PDP_ID, REASON) : new ConfigurationRemoved(PDP_ID));

            assertSoftly(softly -> {
                softly.assertThat(observed.get()).isEqualTo("absent");
                softly.assertThat(view(source)).isEqualTo("replacement/LOADED");
                softly.assertThat(operations).containsExactly("remove", "commit replacement");
            });
        }
    }

    @Test
    @DisplayName("A load requested during host removal cannot be torn down by the still running removal")
    void whenHostRemovalRequestsLoadThenRemovalFinishesBeforeReplacementCommit() {
        val events = new MutablePluginsSource(plugins());
        try (val source = source(events)) {
            source.loadConfiguration(configuration("original"));
            val operations = new ArrayList<String>();
            recordCommits(operations);
            val observed = new AtomicReference<String>();
            doAnswer(ignored -> {
                source.loadConfiguration(configuration("replacement"));
                observed.set(activeId(source));
                operations.add("remove");
                return null;
            }).when(host).remove(PDP_ID);

            source.removeConfigurationForPdp(PDP_ID);

            assertSoftly(softly -> {
                softly.assertThat(observed.get()).isEqualTo("absent");
                softly.assertThat(view(source)).isEqualTo("replacement/LOADED");
                softly.assertThat(operations).containsExactly("remove", "commit replacement");
            });
        }
    }

    @Test
    @DisplayName("A queued close completes after the current commit and prevents subsequent updates")
    void whenListenerClosesSourceThenQueuedAndLaterLoadsCannotRestoreIt() {
        val initial = plugins();
        val events  = new MutablePluginsSource(initial);
        try (val source = source(events)) {
            val operations = new ArrayList<String>();
            recordCommits(operations);
            doAnswer(ignored -> {
                operations.add("remove");
                return null;
            }).when(host).remove(PDP_ID);
            val observed = new AtomicReference<String>();
            source.subscribeToUpdates(PDP_ID, onceForOriginal(() -> {
                source.close();
                source.loadConfiguration(configuration("queued"));
                observed.set(activeId(source));
            }));

            source.loadConfiguration(configuration("original"));
            source.loadConfiguration(configuration("late"));
            events.publish(plugins());

            assertSoftly(softly -> {
                softly.assertThat(observed.get()).isEqualTo("original");
                softly.assertThat(source.getCurrentConfiguration(PDP_ID)).isEmpty();
                softly.assertThat(source.getPdpStatus(PDP_ID)).isEmpty();
                softly.assertThat(source.getPlugins()).isSameAs(initial);
                softly.assertThat(operations).containsExactly("commit original", "remove");
            });
        }
    }

    @Test
    @DisplayName("An unexpected failure in a queued load cannot strand the next queued configuration")
    void whenQueuedLoadFailsUnexpectedlyThenLaterQueuedLoadStillCompletes() {
        val events       = new MutablePluginsSource(plugins());
        val failureArmed = new AtomicBoolean();
        when(host.prepare(anyString(), any(PDPConfiguration.class))).thenAnswer(invocation -> {
            failureArmed.set("failed".equals(invocation.getArgument(1, PDPConfiguration.class).configurationId()));
            return new Accepted();
        });
        val failureReached = new AtomicBoolean();
        when(clock.instant()).thenAnswer(ignored -> {
            if (failureArmed.get()) {
                failureReached.set(true);
                throw new IllegalStateException("Clock contract violation");
            }
            return NOW;
        });
        try (val source = new PdpVoterSource(events, clock, host)) {
            source.subscribeToUpdates(PDP_ID, onceForOriginal(() -> {
                source.loadConfiguration(configuration("failed"));
                source.loadConfiguration(configuration("replacement"));
            }));
            val original = configuration("original");

            assertThatCode(() -> source.loadConfiguration(original)).doesNotThrowAnyException();

            assertThat(failureReached).isTrue();
            assertThat(view(source)).isEqualTo("replacement/LOADED");
        }
    }

    @Test
    @DisplayName("An unexpected load failure reaches its caller unchanged and does not block a later load")
    void whenTopLevelLoadFailsThenOriginalExceptionReachesCallerAndLaterLoadSucceeds() {
        val events       = new MutablePluginsSource(plugins());
        val failureArmed = new AtomicBoolean();
        when(host.prepare(anyString(), any(PDPConfiguration.class))).thenAnswer(invocation -> {
            failureArmed.set("original".equals(invocation.getArgument(1, PDPConfiguration.class).configurationId()));
            return new Accepted();
        });
        val failure = new IllegalStateException("Clock contract violation");
        when(clock.instant()).thenAnswer(ignored -> {
            if (failureArmed.get()) {
                throw failure;
            }
            return NOW;
        });
        try (val source = new PdpVoterSource(events, clock, host)) {
            val original = configuration("original");

            assertThatThrownBy(() -> source.loadConfiguration(original)).isSameAs(failure);

            source.loadConfiguration(configuration("replacement"));
            assertThat(view(source)).isEqualTo("replacement/LOADED");
        }
    }

    @Timeout(10)
    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Plugin update: {0}")
    @DisplayName("An update from another thread waits for its own commit or completed plugin migration")
    void whenAnotherThreadRequestsUpdateThenItsCallRemainsSynchronous(boolean pluginUpdate) throws Exception {
        val initial            = plugins();
        val replacementPlugins = plugins();
        val events             = new MutablePluginsSource(initial);
        val entered            = new CountDownLatch(1);
        val release            = new CountDownLatch(1);
        val replacementEntered = new CountDownLatch(1);
        val replacementRelease = new CountDownLatch(1);
        try (val source = source(events); val executor = newVirtualThreadPerTaskExecutor()) {
            doAnswer(invocation -> {
                val configuration = invocation.getArgument(1, PDPConfiguration.class);
                if ("original".equals(configuration.configurationId())) {
                    entered.countDown();
                    assertThat(release.await(5, SECONDS)).isTrue();
                } else {
                    replacementEntered.countDown();
                    assertThat(replacementRelease.await(5, SECONDS)).isTrue();
                }
                return null;
            }).when(host).commit(eq(PDP_ID), any(PDPConfiguration.class));
            if (pluginUpdate) {
                source.subscribeToUpdates(PDP_ID, event -> {
                    if (event instanceof PdpUpdateEvent.Voter(var ignoredId, var voter)
                            && voter.plugins() == replacementPlugins) {
                        replacementEntered.countDown();
                        try {
                            assertThat(replacementRelease.await(5, SECONDS)).isTrue();
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(failure);
                        }
                    }
                });
            }
            val original = executor.submit(() -> source.loadConfiguration(configuration("original")));
            try {
                assertThat(entered.await(5, SECONDS)).isTrue();
                val started     = new CountDownLatch(1);
                val replacement = executor.submit(() -> {
                                    started.countDown();
                                    if (pluginUpdate) {
                                        events.publish(replacementPlugins);
                                    } else {
                                        source.loadConfiguration(configuration("replacement"));
                                    }
                                    return source.getCurrentConfiguration(PDP_ID).orElseThrow();
                                });
                assertThat(started.await(5, SECONDS)).isTrue();
                release.countDown();
                assertThat(replacementEntered.await(5, SECONDS)).isTrue();

                assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> replacement.get(100, MILLISECONDS));

                replacementRelease.countDown();
                val served = replacement.get(5, SECONDS);
                if (pluginUpdate) {
                    assertThat(served.plugins()).isSameAs(replacementPlugins);
                } else {
                    assertThat(served.metadata().configurationId()).isEqualTo("replacement");
                }

                original.get(5, SECONDS);
            } finally {
                release.countDown();
                replacementRelease.countDown();
            }
        }
    }

    @Test
    @DisplayName("Queued configuration updates run between individual PDP migrations")
    void whenMigrationListenerRequestsLoadThenItDoesNotWaitForAllOtherPdps() {
        val events = new MutablePluginsSource();
        try (val source = source(events)) {
            val observations = new ArrayList<String>();
            val requested    = new AtomicBoolean();
            for (val pdpId : List.of("one", "two", "three")) {
                source.loadConfiguration(configuration(pdpId, "original"));
                source.subscribeToUpdates(pdpId, event -> {
                    if (event instanceof PdpUpdateEvent.Voter) {
                        observations.add(pdpId);
                        if (requested.compareAndSet(false, true)) {
                            source.loadConfiguration(configuration("urgent", "urgent"));
                        }
                    }
                });
            }
            source.subscribeToUpdates("urgent", event -> {
                if (event instanceof PdpUpdateEvent.Voter) {
                    observations.add("urgent");
                }
            });
            val replacement = plugins();

            events.publish(replacement);

            assertThat(observations).hasSize(4).containsExactlyInAnyOrder("one", "two", "three", "urgent")
                    .satisfies(order -> assertThat(order.get(1)).isEqualTo("urgent"));
            for (val pdpId : observations) {
                assertThat(source.getCurrentConfiguration(pdpId)).get().returns(replacement,
                        compiled -> compiled.plugins());
            }
        }
    }

    private PdpVoterSource source(MutablePluginsSource events) {
        when(host.prepare(anyString(), any(PDPConfiguration.class))).thenReturn(new Accepted());
        return new PdpVoterSource(events, CLOCK, host);
    }

    private void recordCommits(List<String> operations) {
        doAnswer(invocation -> {
            operations.add("commit " + invocation.getArgument(1, PDPConfiguration.class).configurationId());
            return null;
        }).when(host).commit(anyString(), any(PDPConfiguration.class));
    }

    private static Consumer<PdpUpdateEvent> onceForOriginal(Runnable action) {
        val invoked = new AtomicBoolean();
        return event -> {
            if (event instanceof PdpUpdateEvent.Voter(var ignoredId, var voter)
                    && "original".equals(voter.metadata().configurationId()) && invoked.compareAndSet(false, true)) {
                action.run();
            }
        };
    }

    private static String view(PdpVoterSource source) {
        return activeId(source) + "/"
                + source.getPdpStatus(PDP_ID).map(status -> status.state().name()).orElse("UNKNOWN");
    }

    private static String activeId(PdpVoterSource source) {
        return source.getCurrentConfiguration(PDP_ID).map(compiled -> compiled.metadata().configurationId())
                .orElse("absent");
    }

    private static PluginsBundle plugins() {
        return new PluginsBundle(new DefaultFunctionBroker());
    }

    private static PDPConfiguration configuration(String id) {
        return configuration(PDP_ID, id);
    }

    private static PDPConfiguration configuration(String pdpId, String id) {
        return new PDPConfiguration(pdpId, id, CombiningAlgorithm.DEFAULT, List.of("""
                policy "access" permit"""), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }

    private enum HostCallback {
        PREPARE,
        COMMIT
    }

    private enum SourceEvent {
        REMOVED,
        EXPIRED,
        ERROR;

        private ConfigurationEvent event() {
            return switch (this) {
            case REMOVED -> new ConfigurationRemoved(PDP_ID);
            case EXPIRED -> new ConfigurationExpired(PDP_ID, REASON);
            case ERROR   -> new ConfigurationError(PDP_ID, REASON);
            };
        }
    }
}
