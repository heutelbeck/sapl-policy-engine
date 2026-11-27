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
package io.sapl.api.binary;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import lombok.experimental.UtilityClass;

/**
 * Strict UTF-8 (RFC 3629): refuses overlong forms, encoded surrogates, code
 * points beyond U+10FFFF, and truncated or stray continuation bytes.
 * {@code new String(bytes, UTF_8)} alone replaces malformed input silently, so
 * the text region of a payload is validated here once and its texts are then
 * decoded by the JDK.
 */
@UtilityClass
class Utf8 {

    private static final VarHandle LONGS = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    /** The high bit of each of eight bytes: none is set in eight ASCII bytes. */
    private static final long HIGH_BITS = 0x8080808080808080L;

    /**
     * Whether {@code length} bytes from {@code offset} are UTF-8. The range must
     * lie within the array.
     */
    static boolean isValid(byte[] bytes, int offset, int length) {
        var       i   = offset;
        final int end = offset + length;
        while (i < end) {
            while (i + 8 <= end && ((long) LONGS.get(bytes, i) & HIGH_BITS) == 0) {
                i += 8;
            }
            if (i >= end) {
                return true;
            }
            final int lead = bytes[i] & 0xFF;
            if (lead < 0x80) {
                i++;
            } else if (lead < 0xC2) {
                return false;
            } else if (lead < 0xE0) {
                if (i + 1 >= end || !isContinuation(bytes[i + 1])) {
                    return false;
                }
                i += 2;
            } else if (lead < 0xF0) {
                if (i + 2 >= end || !isValidSecondOfThree(lead, bytes[i + 1] & 0xFF) || !isContinuation(bytes[i + 2])) {
                    return false;
                }
                i += 3;
            } else if (lead < 0xF5) {
                if (i + 3 >= end || !isValidSecondOfFour(lead, bytes[i + 1] & 0xFF) || !isContinuation(bytes[i + 2])
                        || !isContinuation(bytes[i + 3])) {
                    return false;
                }
                i += 4;
            } else {
                return false;
            }
        }
        return true;
    }

    /** The second byte of three: no overlong form after E0, no surrogate after ED. */
    private static boolean isValidSecondOfThree(int lead, int second) {
        return (second & 0xC0) == 0x80 && !(lead == 0xE0 && second < 0xA0) && !(lead == 0xED && second > 0x9F);
    }

    /** The second byte of four: no overlong form after F0, nothing beyond U+10FFFF after F4. */
    private static boolean isValidSecondOfFour(int lead, int second) {
        return (second & 0xC0) == 0x80 && !(lead == 0xF0 && second < 0x90) && !(lead == 0xF4 && second > 0x8F);
    }

    static boolean isContinuation(byte b) {
        return (b & 0xC0) == 0x80;
    }

    /** Decodes bytes that were validated before. */
    static String decodeValid(byte[] bytes, int offset, int length) {
        return length == 0 ? "" : new String(bytes, offset, length, StandardCharsets.UTF_8);
    }

    /**
     * Writes the UTF-8 bytes of a text from {@code position}. The target must
     * have room for three bytes per char.
     *
     * @return the position after the text, or -1 if the text holds an unpaired
     * surrogate, which Java strings may and UTF-8 may not
     */
    static int encode(String text, byte[] target, int position) {
        final int length = text.length();
        var       p      = position;
        for (var i = 0; i < length; i++) {
            final char c = text.charAt(i);
            if (c < 0x80) {
                target[p++] = (byte) c;
            } else if (c < 0x800) {
                target[p++] = (byte) (0xC0 | (c >> 6));
                target[p++] = (byte) (0x80 | (c & 0x3F));
            } else if (Character.isHighSurrogate(c)) {
                if (i + 1 >= length || !Character.isLowSurrogate(text.charAt(i + 1))) {
                    return -1;
                }
                final int codePoint = Character.toCodePoint(c, text.charAt(++i));
                target[p++] = (byte) (0xF0 | (codePoint >> 18));
                target[p++] = (byte) (0x80 | ((codePoint >> 12) & 0x3F));
                target[p++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
                target[p++] = (byte) (0x80 | (codePoint & 0x3F));
            } else if (Character.isLowSurrogate(c)) {
                return -1;
            } else {
                target[p++] = (byte) (0xE0 | (c >> 12));
                target[p++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                target[p++] = (byte) (0x80 | (c & 0x3F));
            }
        }
        return p;
    }
}
