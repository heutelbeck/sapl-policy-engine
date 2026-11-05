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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.pdp.configuration.PreparationResult.Rejected;

import lombok.val;

@DisplayName("Extension preparation rejections carry an actionable reason")
class PreparationResultTests {

    @ParameterizedTest(name = "The rejection preserves reason {0}")
    @DisplayName("The host rejection reason is preserved verbatim")
    @ValueSource(strings = { "Unsupported required extension 'limits'.",
            "extensions.billing.transport.endpoint: expected an HTTPS URL", "  Missing endpoint.  " })
    void whenHostRejectsPreparationThenReasonIsPreserved(String reason) {
        val rejection = new Rejected(reason);

        assertThat(rejection.reason()).isEqualTo(reason);
    }

    @NullSource
    @ValueSource(strings = { "", " ", "\t\n" })
    @DisplayName("Rejecting without an explanation violates the preparation contract")
    @ParameterizedTest(name = "A rejection cannot have an absent or blank reason: {0}")
    void whenRejectionHasNoExplanationThenConstructionFails(String reason) {
        assertThatThrownBy(() -> new Rejected(reason)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }
}
