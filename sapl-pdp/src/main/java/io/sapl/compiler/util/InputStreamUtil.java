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

import lombok.experimental.UtilityClass;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.io.input.ReaderInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Utility for handling input stream encoding detection and conversion.
 */
@UtilityClass
public class InputStreamUtil {

    /**
     * Detects the encoding of an input stream via BOM and converts to UTF-8.
     *
     * @param policyInputStream the input stream to process
     * @return a UTF-8 encoded input stream
     * @throws IOException if reading fails
     */
    public static InputStream detectAndConvertEncodingOfStream(InputStream policyInputStream) throws IOException {
        @SuppressWarnings("deprecation") // new versions break MQTT ITs
        BOMInputStream bomIn = new BOMInputStream(policyInputStream, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE,
                ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_8);

        if (!bomIn.hasBOM()) {
            return getUtf8InputStream(new InputStreamReader(bomIn, StandardCharsets.UTF_8));
        }

        if (bomIn.hasBOM(ByteOrderMark.UTF_8)) {
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

    @SuppressWarnings("deprecation") // New versions break MQTT ITs
    private static ReaderInputStream getUtf8InputStream(Reader origin) {
        return new ReaderInputStream(origin, StandardCharsets.UTF_8);
    }
}
