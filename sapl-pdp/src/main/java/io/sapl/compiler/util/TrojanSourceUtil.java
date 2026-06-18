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
import lombok.NoArgsConstructor;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility for detecting and preventing trojan source attacks.
 * <p>
 * Trojan source attacks use bidirectional unicode control characters to make
 * malicious code appear benign in editors while executing differently.
 *
 * @see <a href="https://trojansource.codes/">Trojan Source</a>
 */
@UtilityClass
public class TrojanSourceUtil {

    private static final String ERROR_TROJAN_SOURCE = "Illegal bidirectional unicode control characters detected. This is indicative of a potential trojan source attack.";

    private static final char EMBEDDING_RANGE_START = '\u202A';
    private static final char EMBEDDING_RANGE_END   = '\u202E';
    private static final char ISOLATE_RANGE_START   = '\u2066';
    private static final char ISOLATE_RANGE_END     = '\u2069';

    /**
     * Asserts that the given source string does not contain trojan source
     * characters.
     *
     * @param source the source string to validate
     * @throws SaplCompilerException if trojan source characters are detected
     */
    public static void assertNoTrojanSourceCharacters(String source) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if ((c >= EMBEDDING_RANGE_START && c <= EMBEDDING_RANGE_END)
                    || (c >= ISOLATE_RANGE_START && c <= ISOLATE_RANGE_END)) {
                throw new SaplCompilerException(ERROR_TROJAN_SOURCE);
            }
        }
    }

    /**
     * Wraps an input stream to detect trojan source characters during reading.
     *
     * @param source the input stream to wrap
     * @return a guarded input stream that throws on trojan source detection
     */
    public static InputStream guardInputStream(InputStream source) {
        return new TrojanSourceGuardInputStream(source);
    }

    private static class TrojanSourceGuardInputStream extends InputStream {

        private static final int EOF = -1;

        private final InputStream                  source;
        private final ByteSequenceValidationBuffer buffer = new ByteSequenceValidationBuffer();

        TrojanSourceGuardInputStream(InputStream source) {
            this.source = source;
        }

        @Override
        public int read() throws IOException {
            int b = source.read();
            checkByte(b);
            return b;
        }

        @Override
        public int read(byte @NonNull [] b, int off, int len) throws IOException {
            int numberOfBytesRead = source.read(b, off, len);
            if (numberOfBytesRead == EOF) {
                return EOF;
            }
            for (int i = off; i < off + numberOfBytesRead; i++) {
                checkByte(Byte.toUnsignedInt(b[i]));
            }
            return numberOfBytesRead;
        }

        private void checkByte(int b) {
            buffer.addByte(b);
            if (buffer.illegalCharacterDetected()) {
                throw new SaplCompilerException(ERROR_TROJAN_SOURCE);
            }
        }

        @NoArgsConstructor
        private static class ByteSequenceValidationBuffer {

            static final int BUFFER_SIZE = 3;

            private final int[] buffer       = new int[BUFFER_SIZE];
            private int         currentIndex = 0;

            void addByte(int b) {
                buffer[currentIndex] = b;
                currentIndex         = (currentIndex + 1) % BUFFER_SIZE;
            }

            // Embeddings/overrides U+202A..U+202E: 0xE2, 0x80, 0xAA..0xAE
            // Isolates U+2066..U+2069: 0xE2, 0x81, 0xA6..0xA9
            boolean illegalCharacterDetected() {
                if (buffer[currentIndex] != 0xE2) {
                    return false;
                }
                int second = buffer[(currentIndex + 1) % BUFFER_SIZE];
                int third  = buffer[(currentIndex + 2) % BUFFER_SIZE];
                if (second == 0x80) {
                    return third >= 0xAA && third <= 0xAE;
                }
                if (second == 0x81) {
                    return third >= 0xA6 && third <= 0xA9;
                }
                return false;
            }
        }
    }
}
