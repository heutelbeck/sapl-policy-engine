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
package io.sapl.compiler.util;

import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TrojanSourceUtil")
class TrojanSourceUtilTests {

    private static final char LRI = '\u2066';
    private static final char RLI = '\u2067';
    private static final char PDI = '\u2069';
    private static final char RLO = '\u202E';

    @Nested
    @DisplayName("assertNoTrojanSourceCharacters")
    class AssertNoTrojanSourceCharactersTests {

        @Test
        @DisplayName("accepts null input")
        void whenNullThenNoException() {
            assertThatCode(() -> TrojanSourceUtil.assertNoTrojanSourceCharacters(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts valid policy without trojan characters")
        void whenValidPolicyThenNoException() {
            assertThatCode(() -> TrojanSourceUtil.assertNoTrojanSourceCharacters("policy \"test\" permit"))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "rejects input containing {0}")
        @ValueSource(chars = { LRI, RLI, PDI, RLO })
        @DisplayName("rejects input containing trojan source characters")
        void whenTrojanCharacterThenThrows(char trojanChar) {
            val malicious = "policy \"te" + trojanChar + "st\" permit";
            assertThatThrownBy(() -> TrojanSourceUtil.assertNoTrojanSourceCharacters(malicious))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining("trojan source");
        }
    }

    @Nested
    @DisplayName("guardInputStream")
    class GuardInputStreamTests {

        @Test
        @DisplayName("passes through valid input unchanged")
        void whenValidInputThenPassesThrough() throws Exception {
            val valid  = "policy \"test\" permit";
            val stream = TrojanSourceUtil
                    .guardInputStream(new ByteArrayInputStream(valid.getBytes(StandardCharsets.UTF_8)));

            assertThat(IOUtils.toString(stream, StandardCharsets.UTF_8)).isEqualTo(valid);
        }

        @ParameterizedTest(name = "detects {0} in stream")
        @ValueSource(chars = { LRI, RLI, PDI, RLO })
        @DisplayName("detects trojan source characters in stream")
        void whenTrojanCharacterInStreamThenThrows(char trojanChar) {
            val malicious = "policy \"te" + trojanChar + "st\" permit";
            val stream    = TrojanSourceUtil
                    .guardInputStream(new ByteArrayInputStream(malicious.getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() -> IOUtils.toString(stream, StandardCharsets.UTF_8))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining("trojan source");
        }

        @Test
        @Timeout(10)
        @DisplayName("handles large input efficiently")
        void whenLargeInputThenCompletesInTime() {
            val large  = "*".repeat(10_000_000);
            val stream = TrojanSourceUtil
                    .guardInputStream(new ByteArrayInputStream(large.getBytes(StandardCharsets.UTF_8)));

            assertThatCode(() -> IOUtils.toString(stream, StandardCharsets.UTF_8)).doesNotThrowAnyException();
        }
    }
}
