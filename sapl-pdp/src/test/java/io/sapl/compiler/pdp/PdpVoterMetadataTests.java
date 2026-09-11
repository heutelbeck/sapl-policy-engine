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
package io.sapl.compiler.pdp;

import io.sapl.ast.Outcome;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PdpVoterMetadata")
class PdpVoterMetadataTests {

    private static PdpVoterMetadata metadataWithConfigurationId(String configurationId) {
        return new PdpVoterMetadata("pdp voter", "default", configurationId, null, Outcome.PERMIT_OR_DENY, false);
    }

    @Test
    @DisplayName("metadata with a non-blank configurationId is constructed")
    void whenConfigurationIdPresentThenMetadataIsConstructed() {
        val metadata = metadataWithConfigurationId("release-77");

        assertThat(metadata.configurationId()).isEqualTo("release-77");
    }

    @Test
    @DisplayName("a null configurationId is rejected at construction")
    void whenConfigurationIdNullThenConstructionRejected() {
        assertThatThrownBy(() -> metadataWithConfigurationId(null)).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("configurationId");
    }

    @ParameterizedTest(name = "blank configurationId \"{0}\" is rejected")
    @ValueSource(strings = { "", " ", "\t", "  \n" })
    void whenConfigurationIdBlankThenConstructionRejected(String blankConfigurationId) {
        assertThatThrownBy(() -> metadataWithConfigurationId(blankConfigurationId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("non-blank configurationId");
    }
}
