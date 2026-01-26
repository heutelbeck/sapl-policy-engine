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

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InputStreamUtil")
class InputStreamUtilTests {

    private static final String TEST_CONTENT = "Hello, encoding test!";

    @Nested
    @DisplayName("detectAndConvertEncodingOfStream")
    class DetectAndConvertEncodingOfStreamTests {

        @Test
        @DisplayName("detects UTF-8 without BOM")
        void whenUtf8WithoutBomThenConvertsCorrectly() throws IOException {
            val inputStream          = new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
            val convertedInputStream = InputStreamUtil.detectAndConvertEncodingOfStream(inputStream);
            val out                  = new String(convertedInputStream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(out).isEqualTo(TEST_CONTENT);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("encodingsWithBom")
        @DisplayName("detects and converts encoding with BOM")
        void whenEncodingWithBomThenConvertsToUtf8(String name, int[] bom, String charsetName) throws IOException {
            val charset              = Charset.forName(charsetName);
            val inputStream          = createStreamWithBom(TEST_CONTENT, bom, charset);
            val convertedInputStream = InputStreamUtil.detectAndConvertEncodingOfStream(inputStream);
            val out                  = new String(convertedInputStream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(out).isEqualTo(TEST_CONTENT);
        }

        static Stream<Arguments> encodingsWithBom() {
            return Stream.of(arguments("UTF-8 with BOM", new int[] { 0xEF, 0xBB, 0xBF }, "UTF-8"),
                    arguments("UTF-16BE", new int[] { 0xFE, 0xFF }, "UTF-16BE"),
                    arguments("UTF-16LE", new int[] { 0xFF, 0xFE }, "UTF-16LE"),
                    arguments("UTF-32BE", new int[] { 0x00, 0x00, 0xFE, 0xFF }, "UTF-32BE"),
                    arguments("UTF-32LE", new int[] { 0xFF, 0xFE, 0x00, 0x00 }, "UTF-32LE"));
        }

        private static ByteArrayInputStream createStreamWithBom(String content, int[] bom, Charset charset)
                throws IOException {
            val outputStream = new ByteArrayOutputStream();
            for (int b : bom) {
                outputStream.write(b);
            }
            outputStream.write(content.getBytes(charset));
            return new ByteArrayInputStream(outputStream.toByteArray());
        }
    }
}
