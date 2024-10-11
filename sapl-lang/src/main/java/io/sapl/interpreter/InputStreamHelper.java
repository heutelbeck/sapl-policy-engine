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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.io.input.ReaderInputStream;

import io.sapl.api.interpreter.PolicyEvaluationException;
import lombok.NoArgsConstructor;
import lombok.experimental.UtilityClass;

@UtilityClass
public class InputStreamHelper {

    private static final String PARSING_ERRORS = "Parsing errors: %s";

    public static InputStream detectAndConvertEncodingOfStream(InputStream policyInputStream) throws IOException {

        BOMInputStream bomIn = new BOMInputStream(policyInputStream, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE,
                ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_8);

        if (!bomIn.hasBOM()) {
            // InputStream without BOM is treated as UTF-8
            return getUtf8InputStream(new InputStreamReader(bomIn, StandardCharsets.UTF_8));
        }

        if (bomIn.hasBOM(ByteOrderMark.UTF_8)) {
            // conversion not necessary
            return bomIn;
        }

        if (bomIn.hasBOM(ByteOrderMark.UTF_16LE)) {
            return getUtf8InputStream(new InputStreamReader(bomIn, StandardCharsets.UTF_16LE));
        }

        if (bomIn.hasBOM(ByteOrderMark.UTF_16BE)) {
            return getUtf8InputStream(new InputStreamReader(bomIn, StandardCharsets.UTF_16BE));
        }

        if (bomIn.hasBOM(ByteOrderMark.UTF_32LE)) {
            return getUtf8InputStream(new InputStreamReader(bomIn, "UTF-32LE"));
        }

        if (bomIn.hasBOM(ByteOrderMark.UTF_32BE)) {
            return getUtf8InputStream(new InputStreamReader(bomIn, "UTF-32BE"));
        }

        return getUtf8InputStream(new InputStreamReader(bomIn, StandardCharsets.UTF_8));
    }

    private static ReaderInputStream getUtf8InputStream(Reader origin) {
        return new ReaderInputStream(origin, StandardCharsets.UTF_8);
    }

    public static InputStream convertToTrojanSourceSecureStream(InputStream source) {
        return new TrojanSourceGuardInputStream(source);
    }

    private static class TrojanSourceGuardInputStream extends InputStream {
        private static final int  EOF = -1;
        private final InputStream source;

        ByteSequenceValidationBuffer buffer = new ByteSequenceValidationBuffer();

        public TrojanSourceGuardInputStream(InputStream source) {
            super();
            this.source = source;
        }

        @Override
        public int read() throws IOException {
            final var b = source.read();
            checkByte(b);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            final var numberOfBytesRead = source.read(b, off, len);
            if (numberOfBytesRead == EOF) {
                return EOF;
            }
            for (var i = off; i < off + numberOfBytesRead; i++) {
                checkByte(Byte.toUnsignedInt(b[i]));
            }
            return numberOfBytesRead;
        }

        private void checkByte(int b) {
            buffer.addByte(b);
            if (buffer.illegalCharacterDetected()) {
                final String message = "Illegal bidirectional unicode control characters were recognised in the input stream. This is indicative of a potential trojan source attack.";
                throw new PolicyEvaluationException(PARSING_ERRORS, message);
            }
        }

        @NoArgsConstructor
        private static class ByteSequenceValidationBuffer {
            static final int BUFFER_SIZE = 3;

            private int[] buffer       = new int[BUFFER_SIZE];
            private int   currentIndex = 0;

            public void addByte(int b) {
                buffer[currentIndex] = b;
                currentIndex         = (currentIndex + 1) % BUFFER_SIZE;
            }

            // LRI: 0xE2, 0x81, 0xA6
            // RLI: 0xE2, 0x81, 0xA7
            // PDI: 0xE2, 0x81, 0xA9
            // RLO: 0xE2, 0x80, 0xAE
            public boolean illegalCharacterDetected() {
                if (buffer[currentIndex] != 0xE2)
                    return false;

                final var second = buffer[(currentIndex + 1) % BUFFER_SIZE];
                if (second != 0x81 && second != 0x80)
                    return false;

                final var third = buffer[(currentIndex + 2) % BUFFER_SIZE];
                return third == 0xA6 || third == 0xA7 || third == 0xA9 || third == 0xAE;
            }
        }
    }
}
