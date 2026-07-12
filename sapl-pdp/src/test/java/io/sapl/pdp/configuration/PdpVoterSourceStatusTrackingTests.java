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

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent;
import io.sapl.pdp.plugins.MutablePluginsSource;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.StaticPluginsSource;
import io.sapl.util.SaplTesting;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PdpVoterSource status tracking")
class PdpVoterSourceStatusTrackingTests {

    private static final Instant FIXED_TIME    = Instant.parse("2026-02-13T12:00:00Z");
    private static final Clock   FIXED_CLOCK   = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
    private static final String  VALID_POLICY  = """
            policy "test-policy" permit""";
    private static final String  BROKEN_POLICY = "this is not valid SAPL syntax!!!";
    private static final PdpData EMPTY_DATA    = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    private PdpVoterSource createSource() {
        return createSource(FIXED_CLOCK);
    }

    private PdpVoterSource createSource(Clock clock) {
        return new PdpVoterSource(new StaticPluginsSource(new PluginsBundle(SaplTesting.FUNCTION_BROKER)), clock);
    }

    private static PluginsBundle pluginsBundle() {
        return new PluginsBundle(SaplTesting.FUNCTION_BROKER);
    }

    private PDPConfiguration validConfig(String pdpId, String configId) {
        return new PDPConfiguration(pdpId, configId, CombiningAlgorithm.DEFAULT, List.of(VALID_POLICY), EMPTY_DATA);
    }

    private PDPConfiguration brokenConfig(String pdpId) {
        return new PDPConfiguration(pdpId, "broken-config", CombiningAlgorithm.DEFAULT, List.of(BROKEN_POLICY),
                EMPTY_DATA);
    }

    @Nested
    @DisplayName("when no configuration loaded")
    class WhenNoConfigurationLoaded {

        @Test
        @DisplayName("then status map is empty")
        void thenStatusMapIsEmpty() {
            val source = createSource();
            assertThat(source.getAllPdpStatuses()).isEmpty();
        }

        @Test
        @DisplayName("then getPdpStatus returns empty")
        void thenGetPdpStatusReturnsEmpty() {
            val source = createSource();
            assertThat(source.getPdpStatus("unknown")).isEmpty();
        }

    }

    @Nested
    @DisplayName("when configuration loaded successfully")
    class WhenConfigurationLoadedSuccessfully {

        @Test
        @DisplayName("then state is LOADED with metadata")
        void thenStateIsLoadedWithMetadata() {
            val source = createSource();
            source.loadConfiguration(validConfig("default", "config-1"));

            val status = source.getPdpStatus("default");
            assertThat(status).isPresent().hasValueSatisfying(s -> {
                assertThat(s.state()).isEqualTo(PdpState.LOADED);
                assertThat(s.configurationId()).isEqualTo("config-1");
                assertThat(s.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DEFAULT);
                assertThat(s.documentCount()).isEqualTo(1);
                assertThat(s.lastSuccessfulLoad()).isEqualTo(FIXED_TIME);
                assertThat(s.lastFailedLoad()).isNull();
                assertThat(s.lastError()).isNull();
            });
        }

        @Test
        @DisplayName("then status appears in all statuses map")
        void thenStatusAppearsInAllStatusesMap() {
            val source = createSource();
            source.loadConfiguration(validConfig("default", "config-1"));

            val statuses = source.getAllPdpStatuses();
            assertThat(statuses).containsKey("default").hasSize(1);
        }

    }

    @Nested
    @DisplayName("when a configuration fails to compile")
    class WhenLoadFails {

        @Test
        @DisplayName("then a broken first load serves an error voter in ERROR state")
        void thenBrokenFirstLoadServesErrorVoter() {
            val source = createSource();

            source.loadConfiguration(brokenConfig("default"));

            assertThat(source.getPdpStatus("default"))
                    .hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(PdpState.ERROR));
            assertThat(source.getCurrentConfiguration("default")).isPresent();
        }

        @Test
        @DisplayName("then LOADED becomes STALE preserving old metadata")
        void thenLoadedBecomesStalPreservingOldMetadata() {
            val loadTime = Instant.parse("2026-02-13T11:00:00Z");
            val source   = createSource(Clock.fixed(loadTime, ZoneOffset.UTC));

            source.loadConfiguration(validConfig("default", "config-1"));
            source.loadConfiguration(brokenConfig("default"));

            val status = source.getPdpStatus("default");
            assertThat(status).isPresent().hasValueSatisfying(s -> {
                assertThat(s.state()).isEqualTo(PdpState.STALE);
                assertThat(s.configurationId()).isEqualTo("config-1");
                assertThat(s.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DEFAULT);
                assertThat(s.documentCount()).isEqualTo(1);
                assertThat(s.lastSuccessfulLoad()).isEqualTo(loadTime);
                assertThat(s.lastFailedLoad()).isEqualTo(loadTime);
                assertThat(s.lastError()).isNotBlank();
            });
        }

        @Test
        @DisplayName("then ERROR stays ERROR when no previous valid config")
        void thenErrorStaysErrorWhenNoPreviousConfig() {
            val source = createSource();
            source.loadConfiguration(brokenConfig("default"));

            val status = source.getPdpStatus("default");
            assertThat(status).isPresent().hasValueSatisfying(s -> {
                assertThat(s.state()).isEqualTo(PdpState.ERROR);
                assertThat(s.configurationId()).isNull();
                assertThat(s.documentCount()).isZero();
                assertThat(s.lastFailedLoad()).isEqualTo(FIXED_TIME);
                assertThat(s.lastError()).isNotBlank();
            });
        }

        @Test
        @DisplayName("then STALE stays STALE on repeated failures")
        void thenStaleStaysStaleOnRepeatedFailures() {
            val source = createSource();
            source.loadConfiguration(validConfig("default", "config-1"));
            source.loadConfiguration(brokenConfig("default"));

            assertThat(source.getPdpStatus("default"))
                    .hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(PdpState.STALE));

            source.loadConfiguration(brokenConfig("default"));

            assertThat(source.getPdpStatus("default"))
                    .hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(PdpState.STALE));
        }

    }

    @Nested
    @DisplayName("when configuration removed")
    class WhenConfigurationRemoved {

        @Test
        @DisplayName("then status entry is removed")
        void thenStatusEntryIsRemoved() {
            val source = createSource();
            source.loadConfiguration(validConfig("default", "config-1"));
            source.removeConfigurationForPdp("default");

            assertThat(source.getPdpStatus("default")).isEmpty();
            assertThat(source.getAllPdpStatuses()).isEmpty();
        }

    }

    @Nested
    @DisplayName("when multiple PDPs configured")
    class WhenMultiplePdpsConfigured {

        @Test
        @DisplayName("then each PDP has independent status")
        void thenEachPdpHasIndependentStatus() {
            val source = createSource();
            source.loadConfiguration(validConfig("pdp-1", "config-1"));
            source.loadConfiguration(validConfig("pdp-2", "config-2"));

            val statuses = source.getAllPdpStatuses();
            assertThat(statuses).hasSize(2).containsKey("pdp-1").containsKey("pdp-2");

            assertThat(statuses.get("pdp-1").configurationId()).isEqualTo("config-1");
            assertThat(statuses.get("pdp-2").configurationId()).isEqualTo("config-2");
        }

    }

    @Nested
    @DisplayName("when STALE configuration reloaded successfully")
    class WhenStaleConfigReloadedSuccessfully {

        @Test
        @DisplayName("then state returns to LOADED")
        void thenStateReturnsToLoaded() {
            val source = createSource();
            source.loadConfiguration(validConfig("default", "config-1"));
            source.loadConfiguration(brokenConfig("default"));

            assertThat(source.getPdpStatus("default"))
                    .hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(PdpState.STALE));

            source.loadConfiguration(validConfig("default", "config-2"));

            val status = source.getPdpStatus("default");
            assertThat(status).isPresent().hasValueSatisfying(s -> {
                assertThat(s.state()).isEqualTo(PdpState.LOADED);
                assertThat(s.configurationId()).isEqualTo("config-2");
                assertThat(s.lastFailedLoad()).isNull();
                assertThat(s.lastError()).isNull();
            });
        }

    }

    @Nested
    @DisplayName("when ERROR configuration reloaded successfully")
    class WhenErrorConfigReloadedSuccessfully {

        @Test
        @DisplayName("then state returns to LOADED")
        void thenStateReturnsToLoaded() {
            val source = createSource();
            source.loadConfiguration(brokenConfig("default"));
            assertThat(source.getPdpStatus("default"))
                    .hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(PdpState.ERROR));

            source.loadConfiguration(validConfig("default", "config-1"));

            assertThat(source.getPdpStatus("default")).isPresent().hasValueSatisfying(s -> {
                assertThat(s.state()).isEqualTo(PdpState.LOADED);
                assertThat(s.configurationId()).isEqualTo("config-1");
                assertThat(s.lastSuccessfulLoad()).isEqualTo(FIXED_TIME);
            });
        }

    }

    @Nested
    @DisplayName("when a configuration expires (fails closed on staleness)")
    class WhenConfigurationExpired {

        @Test
        @DisplayName("then the served voter is dropped and state is ERROR while staying visible")
        void thenServedVoterDroppedAndErrorVisible() {
            val source = createSource();
            source.loadConfiguration(validConfig("default", "config-1"));
            assertThat(source.getCurrentConfiguration("default")).isPresent();

            source.handle(new ConfigurationEvent.ConfigurationExpired("default", "no contact"));

            assertThat(source.getCurrentConfiguration("default")).isEmpty();
            assertThat(source.getPdpStatus("default")).isPresent().hasValueSatisfying(s -> {
                assertThat(s.state()).isEqualTo(PdpState.ERROR);
                assertThat(s.configurationId()).isNull();
                assertThat(s.lastError()).isNotBlank();
            });
            assertThat(source.getAllPdpStatuses()).containsKey("default");
        }

        @Test
        @DisplayName("then a later valid configuration returns to LOADED")
        void thenLaterValidConfigurationReturnsToLoaded() {
            val source = createSource();
            source.loadConfiguration(validConfig("default", "config-1"));
            source.handle(new ConfigurationEvent.ConfigurationExpired("default", "no contact"));
            assertThat(source.getPdpStatus("default"))
                    .hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(PdpState.ERROR));

            source.loadConfiguration(validConfig("default", "config-2"));

            assertThat(source.getCurrentConfiguration("default")).isPresent();
            assertThat(source.getPdpStatus("default")).isPresent().hasValueSatisfying(s -> {
                assertThat(s.state()).isEqualTo(PdpState.LOADED);
                assertThat(s.configurationId()).isEqualTo("config-2");
            });
        }

        @Test
        @DisplayName("then a later plugins snapshot does not resurrect the expired configuration")
        void thenLaterPluginsSnapshotDoesNotResurrectExpiredConfiguration() {
            val pluginsSource = new MutablePluginsSource();
            try (val source = new PdpVoterSource(pluginsSource, FIXED_CLOCK)) {
                pluginsSource.publish(pluginsBundle());
                source.loadConfiguration(validConfig("default", "config-1"));
                source.handle(new ConfigurationEvent.ConfigurationExpired("default", "no contact"));

                pluginsSource.publish(pluginsBundle());

                assertThat(source.getCurrentConfiguration("default")).isEmpty();
                assertThat(source.getPdpStatus("default"))
                        .hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(PdpState.ERROR));
            }
        }

    }

    @Nested
    @DisplayName("when plugins source has not delivered initial snapshot")
    class WhenPluginsSourceHasNotDeliveredSnapshot {

        @Test
        @DisplayName("then state is AWAITING_PLUGINS exposing retained metadata")
        void thenStateIsAwaitingPluginsExposingRetainedMetadata() {
            val pluginsSource = new MutablePluginsSource();
            try (val source = new PdpVoterSource(pluginsSource, FIXED_CLOCK)) {
                source.loadConfiguration(validConfig("default", "config-1"));

                assertThat(source.getCurrentConfiguration("default")).isEmpty();
                assertThat(source.getPdpStatus("default")).isPresent().hasValueSatisfying(s -> {
                    assertThat(s.state()).isEqualTo(PdpState.AWAITING_PLUGINS);
                    assertThat(s.configurationId()).isEqualTo("config-1");
                    assertThat(s.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DEFAULT);
                    assertThat(s.documentCount()).isEqualTo(1);
                    assertThat(s.lastSuccessfulLoad()).isNull();
                    assertThat(s.lastFailedLoad()).isNull();
                    assertThat(s.lastError()).isNull();
                });
            }
        }

        @Test
        @DisplayName("then deferred status appears in all statuses map")
        void thenDeferredStatusAppearsInAllStatusesMap() {
            val pluginsSource = new MutablePluginsSource();
            try (val source = new PdpVoterSource(pluginsSource, FIXED_CLOCK)) {
                source.loadConfiguration(validConfig("default", "config-1"));

                assertThat(source.getAllPdpStatuses()).hasSize(1).containsKey("default").extractingByKey("default")
                        .satisfies(s -> assertThat(s.state()).isEqualTo(PdpState.AWAITING_PLUGINS));
            }
        }

        @Test
        @DisplayName("then second load replaces retained configuration metadata")
        void thenSecondLoadReplacesRetainedConfigurationMetadata() {
            val pluginsSource = new MutablePluginsSource();
            try (val source = new PdpVoterSource(pluginsSource, FIXED_CLOCK)) {
                source.loadConfiguration(validConfig("default", "config-1"));
                source.loadConfiguration(validConfig("default", "config-2"));

                assertThat(source.getPdpStatus("default")).hasValueSatisfying(s -> {
                    assertThat(s.state()).isEqualTo(PdpState.AWAITING_PLUGINS);
                    assertThat(s.configurationId()).isEqualTo("config-2");
                });

                pluginsSource.publish(pluginsBundle());

                assertThat(source.getPdpStatus("default")).hasValueSatisfying(s -> {
                    assertThat(s.state()).isEqualTo(PdpState.LOADED);
                    assertThat(s.configurationId()).isEqualTo("config-2");
                });
            }
        }

        @Test
        @DisplayName("then remove drops the deferred status entry")
        void thenRemoveDropsTheDeferredStatusEntry() {
            val pluginsSource = new MutablePluginsSource();
            try (val source = new PdpVoterSource(pluginsSource, FIXED_CLOCK)) {
                source.loadConfiguration(validConfig("default", "config-1"));
                source.removeConfigurationForPdp("default");

                assertThat(source.getPdpStatus("default")).isEmpty();
                assertThat(source.getAllPdpStatuses()).isEmpty();

                pluginsSource.publish(pluginsBundle());

                assertThat(source.getCurrentConfiguration("default")).isEmpty();
                assertThat(source.getAllPdpStatuses()).isEmpty();
            }
        }

        @Test
        @DisplayName("then broken policy on plugins arrival transitions to ERROR")
        void thenBrokenPolicyOnPluginsArrivalTransitionsToError() {
            val pluginsSource = new MutablePluginsSource();
            try (val source = new PdpVoterSource(pluginsSource, FIXED_CLOCK)) {
                source.loadConfiguration(brokenConfig("default"));
                assertThat(source.getPdpStatus("default"))
                        .hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(PdpState.AWAITING_PLUGINS));

                pluginsSource.publish(pluginsBundle());

                assertThat(source.getPdpStatus("default")).hasValueSatisfying(s -> {
                    assertThat(s.state()).isEqualTo(PdpState.ERROR);
                    assertThat(s.lastFailedLoad()).isEqualTo(FIXED_TIME);
                    assertThat(s.lastError()).isNotBlank();
                });
            }
        }

        @Test
        @DisplayName("then multiple deferred PDPs all compile when plugins arrive")
        void thenMultipleDeferredPdpsAllCompileWhenPluginsArrive() {
            val pluginsSource = new MutablePluginsSource();
            try (val source = new PdpVoterSource(pluginsSource, FIXED_CLOCK)) {
                source.loadConfiguration(validConfig("pdp-1", "config-1"));
                source.loadConfiguration(validConfig("pdp-2", "config-2"));

                assertThat(source.getAllPdpStatuses()).hasSize(2)
                        .allSatisfy((id, s) -> assertThat(s.state()).isEqualTo(PdpState.AWAITING_PLUGINS));

                pluginsSource.publish(pluginsBundle());

                assertThat(source.getAllPdpStatuses()).hasSize(2)
                        .allSatisfy((id, s) -> assertThat(s.state()).isEqualTo(PdpState.LOADED));
                assertThat(source.getCurrentConfiguration("pdp-1")).isPresent();
                assertThat(source.getCurrentConfiguration("pdp-2")).isPresent();
            }
        }

    }

}
