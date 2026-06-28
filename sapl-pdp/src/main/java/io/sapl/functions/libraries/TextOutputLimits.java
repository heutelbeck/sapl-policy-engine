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
package io.sapl.functions.libraries;

import lombok.val;

import java.io.IOException;
import java.io.Writer;

/**
 * Shared write-boundary limit for text-format serializers.
 */
final class TextOutputLimits {

    static final int    MAX_OUTPUT_CHARS       = 10_000_000;
    static final String ERROR_OUTPUT_TOO_LARGE = "Output exceeds the maximum length of %d characters.";

    private TextOutputLimits() {
    }

    static String write(WriterAction action) throws IOException {
        val writer = new BoundedStringWriter(MAX_OUTPUT_CHARS);
        action.write(writer);
        return writer.toString();
    }

    @FunctionalInterface
    interface WriterAction {
        void write(Writer writer) throws IOException;
    }

    static final class OutputLimitExceededException extends IOException {

        private static final long serialVersionUID = 1L;

        OutputLimitExceededException(int maxOutputChars) {
            super(ERROR_OUTPUT_TOO_LARGE.formatted(maxOutputChars));
        }
    }

    private static final class BoundedStringWriter extends Writer {

        private final int           maxOutputChars;
        private final StringBuilder builder = new StringBuilder();

        private BoundedStringWriter(int maxOutputChars) {
            this.maxOutputChars = maxOutputChars;
        }

        @Override
        public void write(char[] chars, int offset, int length) throws IOException {
            ensureCapacity(length);
            builder.append(chars, offset, length);
        }

        @Override
        public void write(String text, int offset, int length) throws IOException {
            ensureCapacity(length);
            builder.append(text, offset, offset + length);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return builder.toString();
        }

        private void ensureCapacity(int length) throws OutputLimitExceededException {
            if (builder.length() + length > maxOutputChars) {
                throw new OutputLimitExceededException(maxOutputChars);
            }
        }
    }
}
