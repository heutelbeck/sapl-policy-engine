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
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.api.pdp.PdpData;
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
        return new PdpVoterSource(SaplTesting.FUNCTION_BROKER, SaplTesting.ATTRIBUTE_BROKER, clock);
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
            source.loadConfiguration(validConfig("default", "config-1"), false);

            val status = source.getPdpStatus("default");
            assertThat(status).isPresent().hasValueSatisfying(s -> {
                assertThat(s.state()).isEqualTo(PdpState.LOADED);
                assertThat(s.configurationId()).isEqualTo("config-1");
                assertThat(s.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DEFAULT.toCanonicalString());
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
            source.loadConfiguration(validConfig("default", "config-1"), false);

            val statuses = source.getAllPdpStatuses();
            assertThat(statuses).containsKey("default").hasSize(1);
        }

    }

    @Nested
    @DisplayName("when load fails without keepOldConfigOnError")
    class WhenLoadFailsWithoutKeepOld {

        @Test
        @DisplayName("then state is ERROR with error info")
        void thenStateIsErrorWithErrorInfo() {
            val source = createSource();
            source.loadConfiguration(brokenConfig("default"), false);

            val status = source.getPdpStatus("default");
            assertThat(status).isPresent().hasValueSatisfying(s -> {
                assertThat(s.state()).isEqualTo(PdpState.ERROR);
                assertThat(s.configurationId()).isNull();
                assertThat(s.combiningAlgorithm()).isNull();
                assertThat(s.documentCount()).isZero();
                assertThat(s.lastSuccessfulLoad()).isNull();
                assertThat(s.lastFailedLoad()).isEqualTo(FIXED_TIME);
                assertThat(s.lastError()).isNotBlank();
            });
        }

        @Test
        @DisplayName("then error voter is stored in config cache")
        void thenErrorVoterIsStoredInConfigCache() {
            val source = createSource();
            source.loadConfiguration(brokenConfig("default"), false);

            assertThat(source.getCurrentConfiguration("default")).isPresent();
        }

    }

    @Nested
    @DisplayName("when load fails with keepOldConfigOnError")
    class WhenLoadFailsWithKeepOld {

        @Test
        @DisplayName("then LOADED becomes STALE preserving old metadata")
        void thenLoadedBecomesStalPreservingOldMetadata() {
            val loadTime = Instant.parse("2026-02-13T11:00:00Z");
            val source   = createSource(Clock.fixed(loadTime, ZoneOffset.UTC));

            source.loadConfiguration(validConfig("default", "config-1"), false);
            source.loadConfiguration(brokenConfig("default"), true);

            val status = source.getPdpStatus("default");
            assertThat(status).isPresent().hasValueSatisfying(s -> {
                assertThat(s.state()).isEqualTo(PdpState.STALE);
                assertThat(s.configurationId()).isEqualTo("config-1");
                assertThat(s.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DEFAULT.toCanonicalString());
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
            source.loadConfiguration(brokenConfig("default"), true);

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
            source.loadConfiguration(validConfig("default", "config-1"), false);
            source.loadConfiguration(brokenConfig("default"), true);

            assertThat(source.getPdpStatus("default"))
                    .hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(PdpState.STALE));

            source.loadConfiguration(brokenConfig("default"), true);

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
            source.loadConfiguration(validConfig("default", "config-1"), false);
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
            source.loadConfiguration(validConfig("pdp-1", "config-1"), false);
            source.loadConfiguration(validConfig("pdp-2", "config-2"), false);

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
            source.loadConfiguration(validConfig("default", "config-1"), false);
            source.loadConfiguration(brokenConfig("default"), true);

            assertThat(source.getPdpStatus("default"))
                    .hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(PdpState.STALE));

            source.loadConfiguration(validConfig("default", "config-2"), false);

            val status = source.getPdpStatus("default");
            assertThat(status).isPresent().hasValueSatisfying(s -> {
                assertThat(s.state()).isEqualTo(PdpState.LOADED);
                assertThat(s.configurationId()).isEqualTo("config-2");
                assertThat(s.lastFailedLoad()).isNull();
                assertThat(s.lastError()).isNull();
            });
        }

    }

}
