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
package io.sapl.attributes.libraries;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.Base64;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("JWTEncodingDecodingUtils key decoding")
class JWTEncodingDecodingUtilsTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    // Bytes whose standard Base64 encoding uses '+' and '/', the two characters
    // that differ from the url-safe alphabet (0xFB's leading six bits are
    // 111110 = 62 = '+'). Operators paste keys in both alphabets, so both must
    // decode to the same key bytes.
    private static final byte[] SECRET = { (byte) 0xFB, (byte) 0xF0, 0x00, (byte) 0xFB, (byte) 0xF0, 0x00 };

    static Stream<Arguments> base64Alphabets() {
        return Stream.of(arguments("standard", Base64.getEncoder().encodeToString(SECRET)),
                arguments("url-safe", Base64.getUrlEncoder().encodeToString(SECRET)));
    }

    @ParameterizedTest(name = "a {0} Base64 key decodes to the original key bytes")
    @MethodSource("base64Alphabets")
    @DisplayName("a key in either Base64 alphabet decodes to the original key bytes")
    void whenKeyIsInEitherBase64AlphabetThenDecodesToOriginalBytes(String alphabet, String encodedKey) {
        val key = JWTEncodingDecodingUtils.jsonNodeToKey(JSON.stringNode(encodedKey));

        assertThat(key).hasValueSatisfying(decoded -> assertThat(decoded.getEncoded()).isEqualTo(SECRET));
    }
}
