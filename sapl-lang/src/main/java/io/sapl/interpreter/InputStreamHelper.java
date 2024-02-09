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

import lombok.experimental.UtilityClass;

@UtilityClass
public class InputStreamHelper {

    public static InputStream detectAndConvertEncodingOfStream(InputStream policyInputStream) throws IOException {

        BOMInputStream bomIn = BOMInputStream
                .builder().setInputStream(policyInputStream).setByteOrderMarks(ByteOrderMark.UTF_16LE,
                        ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_8)
                .get();

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

    private static ReaderInputStream getUtf8InputStream(Reader origin) throws IOException {
        var builder = ReaderInputStream.builder();
        builder.setCharset(StandardCharsets.UTF_8);
        builder.setReader(origin);
        return builder.get();
    }
}
