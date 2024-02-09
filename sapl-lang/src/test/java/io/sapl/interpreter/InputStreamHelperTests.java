/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class InputStreamHelperTests {

    @Test
    void testDetectAndConvertEncodingOfStream_utf8_withoutBOM() throws IOException {
        String      in                   = "Hello, UTF-8 without BOM!";
        InputStream inputStream          = new ByteArrayInputStream(in.getBytes(StandardCharsets.UTF_8));
        InputStream convertedInputStream = InputStreamHelper.detectAndConvertEncodingOfStream(inputStream);

        String out = new String(convertedInputStream.readAllBytes(), StandardCharsets.UTF_8);

        assertThat(out).isEqualTo(in);
    }

    @Test
    void testDetectAndConvertEncodingOfStream_utf8_withBOM() throws IOException {
        String in           = "Hello, UTF-8 with BOM!";
        var    outputStream = new ByteArrayOutputStream();
        outputStream.write(0xEF);
        outputStream.write(0xBB);
        outputStream.write(0xBF);
        outputStream.write(in.getBytes(StandardCharsets.UTF_8));
        byte[] utf16Bytes           = outputStream.toByteArray();
        var    inputStream          = new ByteArrayInputStream(utf16Bytes);
        var    convertedInputStream = InputStreamHelper.detectAndConvertEncodingOfStream(inputStream);

        var out = new String(convertedInputStream.readAllBytes(), StandardCharsets.UTF_8);

        assertThat(out).isEqualTo(in);
    }

    @Test
    void testDetectAndConvertEncodingOfStream_utf16BE() throws IOException {
        var in           = "Hello, UTF-16BE!";
        var outputStream = new ByteArrayOutputStream();
        outputStream.write(0xFE);
        outputStream.write(0xFF);
        outputStream.write(in.getBytes(StandardCharsets.UTF_16BE));
        byte[] utf16BytesWithBOM    = outputStream.toByteArray();
        var    inputStream          = new ByteArrayInputStream(utf16BytesWithBOM);
        var    convertedInputStream = InputStreamHelper.detectAndConvertEncodingOfStream(inputStream);

        var out = new String(convertedInputStream.readAllBytes(), StandardCharsets.UTF_8);

        assertThat(out).isEqualTo(in);
    }

    @Test
    void testDetectAndConvertEncodingOfStream_utf16LE() throws IOException {
        var in           = "Hello, UTF-16LE!";
        var outputStream = new ByteArrayOutputStream();
        outputStream.write(0xFF);
        outputStream.write(0xFE);
        outputStream.write(in.getBytes(StandardCharsets.UTF_16LE));
        byte[] utf16Bytes           = outputStream.toByteArray();
        var    inputStream          = new ByteArrayInputStream(utf16Bytes);
        var    convertedInputStream = InputStreamHelper.detectAndConvertEncodingOfStream(inputStream);

        var out = new String(convertedInputStream.readAllBytes(), StandardCharsets.UTF_8);

        assertThat(out).isEqualTo(in);
    }

    @Test
    void testDetectAndConvertEncodingOfStream_utf32LE() throws IOException {
        String                in           = "Hello, UTF-32LE!";
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(0xFF);
        outputStream.write(0xFE);
        outputStream.write(0x00);
        outputStream.write(0x00);
        outputStream.write(in.getBytes("UTF-32LE"));
        byte[]      utf32Bytes           = outputStream.toByteArray();
        InputStream inputStream          = new ByteArrayInputStream(utf32Bytes);
        InputStream convertedInputStream = InputStreamHelper.detectAndConvertEncodingOfStream(inputStream);

        String out = new String(convertedInputStream.readAllBytes(), StandardCharsets.UTF_8);

        assertThat(out).isEqualTo(in);
    }

    @Test
    void testDetectAndConvertEncodingOfStream_utf32BE() throws IOException {
        String                in           = "Hello, UTF-32BE!";
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(0x00);
        outputStream.write(0x00);
        outputStream.write(0xFE);
        outputStream.write(0xFF);
        outputStream.write(in.getBytes("UTF-32BE"));
        byte[]      utf32Bytes           = outputStream.toByteArray();
        InputStream inputStream          = new ByteArrayInputStream(utf32Bytes);
        InputStream convertedInputStream = InputStreamHelper.detectAndConvertEncodingOfStream(inputStream);

        String out = new String(convertedInputStream.readAllBytes(), StandardCharsets.UTF_8);

        assertThat(out).isEqualTo(in);
    }
}
