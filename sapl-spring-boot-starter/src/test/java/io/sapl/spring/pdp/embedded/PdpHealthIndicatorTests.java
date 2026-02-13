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
package io.sapl.spring.pdp.embedded;

import io.sapl.pdp.configuration.PdpState;
import io.sapl.pdp.configuration.PdpStatus;
import io.sapl.pdp.configuration.PdpVoterSource;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PdpHealthIndicator")
class PdpHealthIndicatorTests {

    @Mock
    PdpVoterSource pdpVoterSource;

    @Nested
    @DisplayName("when all PDPs are LOADED")
    class WhenAllLoaded {

        @Test
        @DisplayName("then health is UP")
        void thenHealthIsUp() {
            when(pdpVoterSource.getAllPdpStatuses()).thenReturn(Map.of("default", new PdpStatus(PdpState.LOADED,
                    "config-1", "PRIORITY_DENY:DENY:PROPAGATE", 3, Instant.parse("2026-02-13T12:00:00Z"), null, null)));

            val indicator = new PdpHealthIndicator(pdpVoterSource);
            val health    = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsKey("pdps").doesNotContainKey("warning");
        }

    }

    @Nested
    @DisplayName("when any PDP is ERROR")
    class WhenAnyError {

        @Test
        @DisplayName("then health is DOWN")
        void thenHealthIsDown() {
            when(pdpVoterSource.getAllPdpStatuses()).thenReturn(Map.of("default", new PdpStatus(PdpState.ERROR, null,
                    null, 0, null, Instant.parse("2026-02-13T12:05:00Z"), "parse error")));

            val indicator = new PdpHealthIndicator(pdpVoterSource);
            val health    = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

    }

    @Nested
    @DisplayName("when any PDP is STALE")
    class WhenAnyStale {

        @Test
        @DisplayName("then health is UP with warning")
        void thenHealthIsUpWithWarning() {
            when(pdpVoterSource.getAllPdpStatuses()).thenReturn(Map.of("tenant1",
                    new PdpStatus(PdpState.STALE, "old-config", "PRIORITY_DENY:DENY:PROPAGATE", 2,
                            Instant.parse("2026-02-13T11:00:00Z"), Instant.parse("2026-02-13T12:05:00Z"),
                            "parse error")));

            val indicator = new PdpHealthIndicator(pdpVoterSource);
            val health    = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsKey("warning");
        }

    }

    @Nested
    @DisplayName("when no PDPs are registered")
    class WhenNoPdps {

        @Test
        @DisplayName("then health is DOWN")
        void thenHealthIsDown() {
            when(pdpVoterSource.getAllPdpStatuses()).thenReturn(Map.of());

            val indicator = new PdpHealthIndicator(pdpVoterSource);
            val health    = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

    }

    @Nested
    @DisplayName("when mixed LOADED and STALE")
    class WhenMixedLoadedAndStale {

        @Test
        @DisplayName("then health is UP with warning")
        void thenHealthIsUpWithWarning() {
            when(pdpVoterSource.getAllPdpStatuses()).thenReturn(Map.of("default",
                    new PdpStatus(PdpState.LOADED, "config-1", "PRIORITY_DENY:DENY:PROPAGATE", 3,
                            Instant.parse("2026-02-13T12:00:00Z"), null, null),
                    "tenant1", new PdpStatus(PdpState.STALE, "old-config", "PRIORITY_DENY:DENY:PROPAGATE", 2,
                            Instant.parse("2026-02-13T11:00:00Z"), Instant.parse("2026-02-13T12:05:00Z"), "error")));

            val indicator = new PdpHealthIndicator(pdpVoterSource);
            val health    = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsKey("warning");
        }

    }

    @Nested
    @DisplayName("detail map structure")
    class DetailMapStructure {

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("then LOADED PDP contains expected fields")
        void thenLoadedPdpContainsExpectedFields() {
            when(pdpVoterSource.getAllPdpStatuses()).thenReturn(Map.of("default", new PdpStatus(PdpState.LOADED,
                    "config-1", "PRIORITY_DENY:DENY:PROPAGATE", 3, Instant.parse("2026-02-13T12:00:00Z"), null, null)));

            val indicator = new PdpHealthIndicator(pdpVoterSource);
            val health    = indicator.health();
            val pdps      = (Map<String, Map<String, Object>>) health.getDetails().get("pdps");
            val detail    = pdps.get("default");

            assertThat(detail).containsEntry("state", "LOADED").containsEntry("configurationId", "config-1")
                    .containsEntry("combiningAlgorithm", "PRIORITY_DENY:DENY:PROPAGATE")
                    .containsEntry("documentCount", 3).containsEntry("lastSuccessfulLoad", "2026-02-13T12:00:00Z")
                    .doesNotContainKey("lastFailedLoad").doesNotContainKey("lastError");
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("then STALE PDP contains error fields")
        void thenStalePdpContainsErrorFields() {
            when(pdpVoterSource.getAllPdpStatuses()).thenReturn(Map.of("tenant1",
                    new PdpStatus(PdpState.STALE, "old-config", "PRIORITY_DENY:DENY:PROPAGATE", 2,
                            Instant.parse("2026-02-13T11:00:00Z"), Instant.parse("2026-02-13T12:05:00Z"),
                            "parse error at line 5")));

            val indicator = new PdpHealthIndicator(pdpVoterSource);
            val health    = indicator.health();
            val pdps      = (Map<String, Map<String, Object>>) health.getDetails().get("pdps");
            val detail    = pdps.get("tenant1");

            assertThat(detail).containsEntry("state", "STALE").containsEntry("configurationId", "old-config")
                    .containsEntry("lastFailedLoad", "2026-02-13T12:05:00Z")
                    .containsEntry("lastError", "parse error at line 5");
        }

    }

}
