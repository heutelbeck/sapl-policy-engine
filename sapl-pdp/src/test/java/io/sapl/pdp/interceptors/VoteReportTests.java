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
package io.sapl.pdp.interceptors;

import io.sapl.pdp.configuration.ConfigurationIds;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("VoteReport")
class VoteReportTests {

    private static final Instant DUMMY_TIMESTAMP = Instant.parse("2026-07-15T10:00:00Z");

    private static final AuthorizationSubscription DUMMY_SUBSCRIPTION = AuthorizationSubscription.of(Value.of("alice"),
            Value.of("read"), Value.of("archive"), Value.NULL);

    private static VoteReport reportWithConfigurationId(String configurationId) {
        return new VoteReport(DUMMY_TIMESTAMP, "sub-1", DUMMY_SUBSCRIPTION, Decision.PERMIT, Value.EMPTY_ARRAY,
                Value.EMPTY_ARRAY, null, "pdp voter", "default", configurationId, null, List.of(), List.of());
    }

    @Test
    @DisplayName("a report with a non-blank configurationId is constructed")
    void whenConfigurationIdPresentThenReportIsConstructed() {
        val report = reportWithConfigurationId("release-77");

        assertThat(report.configurationId()).isEqualTo("release-77");
    }

    @Test
    @DisplayName("a null configurationId renders as the missing sentinel")
    void whenConfigurationIdNullThenSentinelIsRendered() {
        assertThat(reportWithConfigurationId(null).configurationId()).isEqualTo(VoteReport.MISSING_CONFIGURATION_ID);
    }

    @ParameterizedTest(name = "blank configurationId \"{0}\" renders as the missing sentinel")
    @ValueSource(strings = { "", " ", "\t" })
    void whenConfigurationIdBlankThenSentinelIsRendered(String blankConfigurationId) {
        assertThat(reportWithConfigurationId(blankConfigurationId).configurationId())
                .isEqualTo(VoteReport.MISSING_CONFIGURATION_ID);
    }

    @Test
    @DisplayName("the sentinel can never collide with a valid configuration id")
    void whenSentinelValidatedAsConfigurationIdThenItIsInvalid() {
        assertThat(ConfigurationIds.isValid(VoteReport.MISSING_CONFIGURATION_ID)).isFalse();
    }
}
