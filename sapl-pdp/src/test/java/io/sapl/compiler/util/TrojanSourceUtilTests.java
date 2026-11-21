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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TrojanSourceUtil")
class TrojanSourceUtilTests {

    private static final char LRE = '\u202A';
    private static final char RLE = '\u202B';
    private static final char PDF = '\u202C';
    private static final char LRO = '\u202D';
    private static final char RLO = '\u202E';
    private static final char LRI = '\u2066';
    private static final char RLI = '\u2067';
    private static final char FSI = '\u2068';
    private static final char PDI = '\u2069';

    private static final String TROJAN_SOURCE = "trojan source";
    private static final String VALID_POLICY  = "policy \"test\" permit";

    /**
     * The text of a policy before the place of a trojan source character.
     */
    private static final String BEFORE_TROJAN_CHARACTER = "policy \"te";

    /**
     * The index that stands for no trojan source character.
     */
    private static final int NOT_FOUND = -1;

    @Nested
    @DisplayName("indexOfTrojanSourceCharacter")
    class IndexOfTrojanSourceCharacterTests {

        @NullSource
        @ValueSource(strings = VALID_POLICY)
        @ParameterizedTest(name = "finds nothing in {0}")
        @DisplayName("finds nothing in null input and in a valid policy without trojan characters")
        void whenInputHoldsNoTrojanCharacterThenNothingIsFound(String harmless) {
            assertThat(TrojanSourceUtil.indexOfTrojanSourceCharacter(harmless)).isEqualTo(NOT_FOUND);
        }

        @ParameterizedTest(name = "finds {0}")
        @ValueSource(chars = { LRE, RLE, PDF, LRO, RLO, LRI, RLI, FSI, PDI })
        @DisplayName("finds the first trojan source character where it stands")
        void whenTrojanCharactersStandInInputThenTheFirstIsFound(char trojanChar) {
            val malicious = BEFORE_TROJAN_CHARACTER + trojanChar + "st\" permit " + RLO;

            assertThat(TrojanSourceUtil.indexOfTrojanSourceCharacter(malicious))
                    .isEqualTo(BEFORE_TROJAN_CHARACTER.length());
        }
    }

    @Nested
    @DisplayName("guardInputStream")
    class GuardInputStreamTests {

        @Test
        @DisplayName("passes through valid input unchanged")
        void whenValidInputThenPassesThrough() throws Exception {
            val stream = TrojanSourceUtil
                    .guardInputStream(new ByteArrayInputStream(VALID_POLICY.getBytes(StandardCharsets.UTF_8)));

            assertThat(IOUtils.toString(stream, StandardCharsets.UTF_8)).isEqualTo(VALID_POLICY);
        }

        @ParameterizedTest(name = "detects {0} in stream")
        @DisplayName("detects trojan source characters in stream")
        @ValueSource(chars = { LRE, RLE, PDF, LRO, RLO, LRI, RLI, FSI, PDI })
        void whenTrojanCharacterInStreamThenThrows(char trojanChar) {
            val malicious = "policy \"te" + trojanChar + "st\" permit";
            val stream    = TrojanSourceUtil
                    .guardInputStream(new ByteArrayInputStream(malicious.getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() -> IOUtils.toString(stream, StandardCharsets.UTF_8))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining(TROJAN_SOURCE);
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
