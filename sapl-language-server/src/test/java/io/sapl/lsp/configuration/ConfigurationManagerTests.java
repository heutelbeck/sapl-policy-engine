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
package io.sapl.lsp.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigurationManagerTests {

    @Nested
    @DisplayName("extractConfigurationIdFromUri")
    class ExtractConfigurationId {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("when URI is null or empty then default is returned")
        void whenUriNullOrEmptyThenDefault(String documentUri) {
            assertThat(ConfigurationManager.extractConfigurationIdFromUri(documentUri)).isEqualTo("default");
        }

        @ParameterizedTest
        @ValueSource(strings = { "file:///policy.sapl", "file:///policy.sapl?other=value" })
        @DisplayName("when URI has no configurationId then default is returned")
        void whenNoConfigurationIdThenDefault(String documentUri) {
            assertThat(ConfigurationManager.extractConfigurationIdFromUri(documentUri)).isEqualTo("default");
        }

        @Test
        @DisplayName("when configurationId is plain then it is returned verbatim")
        void whenPlainConfigurationIdThenReturned() {
            assertThat(ConfigurationManager
                    .extractConfigurationIdFromUri("file:///policy.sapl?configurationId=production"))
                    .isEqualTo("production");
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("io.sapl.lsp.configuration.ConfigurationManagerTests#encodedConfigurationIds")
        @DisplayName("when configurationId is percent-encoded then it is decoded exactly once")
        void whenEncodedConfigurationIdThenDecodedOnce(String documentUri, String expectedId) {
            assertThat(ConfigurationManager.extractConfigurationIdFromUri(documentUri)).isEqualTo(expectedId);
        }
    }

    static Stream<Arguments> encodedConfigurationIds() {
        return Stream.of(
                // A config ID literally named "a%20b" is transported as %2520b and must
                // decode exactly once back to "a%20b", not twice down to "a b".
                arguments("file:///policy.sapl?configurationId=a%2520b", "a%20b"),
                // A literal plus in the query is not form-encoding; it must stay a plus.
                arguments("file:///policy.sapl?configurationId=team+a", "team+a"),
                // An encoded ampersand inside the value must not split the parameter.
                arguments("file:///policy.sapl?configurationId=team%26a", "team&a"),
                // An encoded equals inside the value must not split the key/value.
                arguments("file:///policy.sapl?configurationId=team%3Da", "team=a"),
                // A genuinely encoded space decodes once to a space.
                arguments("file:///policy.sapl?configurationId=team%20a", "team a"));
    }
}
