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

import io.sapl.compiler.SaplCompilerException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputStreamUtilTests {

    @Test
    void testDetectAndConvertEncodingOfStream_utf8_withoutBOM() throws IOException {
        final var in                   = "Hello, UTF-8 without BOM!";
        final var inputStream          = new ByteArrayInputStream(in.getBytes(StandardCharsets.UTF_8));
        final var convertedInputStream = InputStreamUtil.detectAndConvertEncodingOfStream(inputStream);

        final var out = new String(convertedInputStream.readAllBytes(), StandardCharsets.UTF_8);

        assertThat(out).isEqualTo(in);
    }

    @Test
    void testDetectAndConvertEncodingOfStream_utf8_withBOM() throws IOException {
        final var in           = "Hello, UTF-8 with BOM!";
        final var outputStream = new ByteArrayOutputStream();
        outputStream.write(0xEF);
        outputStream.write(0xBB);
        outputStream.write(0xBF);
        outputStream.write(in.getBytes(StandardCharsets.UTF_8));
        final var utf16Bytes           = outputStream.toByteArray();
        final var inputStream          = new ByteArrayInputStream(utf16Bytes);
        final var convertedInputStream = InputStreamUtil.detectAndConvertEncodingOfStream(inputStream);

        final var out = new String(convertedInputStream.readAllBytes(), StandardCharsets.UTF_8);

        assertThat(out).isEqualTo(in);
    }

    @Test
    void testDetectAndConvertEncodingOfStream_utf16BE() throws IOException {
        final var in           = "Hello, UTF-16BE!";
        final var outputStream = new ByteArrayOutputStream();
        outputStream.write(0xFE);
        outputStream.write(0xFF);
        outputStream.write(in.getBytes(StandardCharsets.UTF_16BE));
        final var utf16BytesWithBOM    = outputStream.toByteArray();
        final var inputStream          = new ByteArrayInputStream(utf16BytesWithBOM);
        final var convertedInputStream = InputStreamUtil.detectAndConvertEncodingOfStream(inputStream);

        final var out = new String(convertedInputStream.readAllBytes(), StandardCharsets.UTF_8);

        assertThat(out).isEqualTo(in);
    }

    @Test
    void testDetectAndConvertEncodingOfStream_utf16LE() throws IOException {
        final var in           = "Hello, UTF-16LE!";
        final var outputStream = new ByteArrayOutputStream();
        outputStream.write(0xFF);
        outputStream.write(0xFE);
        outputStream.write(in.getBytes(StandardCharsets.UTF_16LE));
        final var utf16Bytes           = outputStream.toByteArray();
        final var inputStream          = new ByteArrayInputStream(utf16Bytes);
        final var convertedInputStream = InputStreamUtil.detectAndConvertEncodingOfStream(inputStream);

        final var out = new String(convertedInputStream.readAllBytes(), StandardCharsets.UTF_8);

        assertThat(out).isEqualTo(in);
    }

    @Test
    void testDetectAndConvertEncodingOfStream_utf32LE() throws IOException {
        final var in           = "Hello, UTF-32LE!";
        final var outputStream = new ByteArrayOutputStream();
        outputStream.write(0xFF);
        outputStream.write(0xFE);
        outputStream.write(0x00);
        outputStream.write(0x00);
        outputStream.write(in.getBytes("UTF-32LE"));
        final var utf32Bytes           = outputStream.toByteArray();
        final var inputStream          = new ByteArrayInputStream(utf32Bytes);
        final var convertedInputStream = InputStreamUtil.detectAndConvertEncodingOfStream(inputStream);

        String out = new String(convertedInputStream.readAllBytes(), StandardCharsets.UTF_8);

        assertThat(out).isEqualTo(in);
    }

    @Test
    void testDetectAndConvertEncodingOfStream_utf32BE() throws IOException {
        final var in           = "Hello, UTF-32BE!";
        final var outputStream = new ByteArrayOutputStream();
        outputStream.write(0x00);
        outputStream.write(0x00);
        outputStream.write(0xFE);
        outputStream.write(0xFF);
        outputStream.write(in.getBytes("UTF-32BE"));
        final var utf32Bytes           = outputStream.toByteArray();
        final var inputStream          = new ByteArrayInputStream(utf32Bytes);
        final var convertedInputStream = InputStreamUtil.detectAndConvertEncodingOfStream(inputStream);

        final var out = new String(convertedInputStream.readAllBytes(), StandardCharsets.UTF_8);

        assertThat(out).isEqualTo(in);
    }

    @Test
    void assertNoIllegalBidiUnicodeCharactersPresentInStreamCatchesIllegalCharacters() throws IOException {
        final var valid              = "policy \"te%sst\" permit";
        final var lri                = '\u2066';
        final var pdi                = '\u2069';
        final var rli                = '\u2067';
        final var rlo                = '\u202E';
        final var validStream        = InputStreamUtil
                .convertToTrojanSourceSecureStream(new ByteArrayInputStream(valid.getBytes(StandardCharsets.UTF_8)));
        final var invalidContainsLri = InputStreamUtil.convertToTrojanSourceSecureStream(
                new ByteArrayInputStream((String.format(valid, lri).getBytes(StandardCharsets.UTF_8))));
        final var invalidContainsPdi = InputStreamUtil.convertToTrojanSourceSecureStream(
                new ByteArrayInputStream(String.format(valid, pdi).getBytes(StandardCharsets.UTF_8)));
        final var invalidContainsRli = InputStreamUtil.convertToTrojanSourceSecureStream(
                new ByteArrayInputStream(String.format(valid, rli).getBytes(StandardCharsets.UTF_8)));
        final var invalidContainsRlo = InputStreamUtil.convertToTrojanSourceSecureStream(
                new ByteArrayInputStream(String.format(valid, rlo).getBytes(StandardCharsets.UTF_8)));

        final var utf8 = StandardCharsets.UTF_8.name();
        assertThat(IOUtils.toString(validStream, utf8)).isEqualTo(valid);
        assertThrows(SaplCompilerException.class, () -> IOUtils.toString(invalidContainsLri, utf8));
        assertThrows(SaplCompilerException.class, () -> IOUtils.toString(invalidContainsPdi, utf8));
        assertThrows(SaplCompilerException.class, () -> IOUtils.toString(invalidContainsRli, utf8));
        assertThrows(SaplCompilerException.class, () -> IOUtils.toString(invalidContainsRlo, utf8));
    }

    @Test
    @Timeout(10)
    void checkSize() {
        final var utf8     = StandardCharsets.UTF_8.name();
        final var testCase = "*".repeat(10000000);
        assertDoesNotThrow(() -> IOUtils.toString(InputStreamUtil.convertToTrojanSourceSecureStream(
                new ByteArrayInputStream(testCase.getBytes(StandardCharsets.UTF_8))), utf8));
    }
}
