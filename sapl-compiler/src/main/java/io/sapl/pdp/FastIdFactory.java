/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp;

import java.util.concurrent.ThreadLocalRandom;

/**
 * High-performance ID factory using ThreadLocalRandom for subscription IDs.
 * <p>
 * Unlike {@link UUIDFactory} which uses {@link java.util.UUID#randomUUID()}
 * (backed by SecureRandom with internal synchronization), this implementation
 * uses {@link ThreadLocalRandom} which is lock-free and scales linearly with
 * thread count.
 * <p>
 * The generated IDs are:
 * <ul>
 * <li>Unique within a JVM instance (statistically)</li>
 * <li>Not cryptographically secure (not suitable for security tokens)</li>
 * <li>Suitable for tracing and correlation purposes</li>
 * </ul>
 * <p>
 * Performance characteristics:
 * <ul>
 * <li>UUID.randomUUID(): ~250K ops/sec, plateaus at 5-6 threads</li>
 * <li>FastIdFactory: ~300K ops/sec single-threaded, scales to millions</li>
 * </ul>
 */
public class FastIdFactory implements IdFactory {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    @Override
    public String newRandom() {
        var random = ThreadLocalRandom.current();
        var high   = random.nextLong();
        var low    = random.nextLong();

        // Format as UUID-like string: 8-4-4-4-12 hex digits
        var chars = new char[36];
        formatHex(chars, 0, high >>> 32, 8);
        chars[8] = '-';
        formatHex(chars, 9, high >>> 16, 4);
        chars[13] = '-';
        formatHex(chars, 14, high, 4);
        chars[18] = '-';
        formatHex(chars, 19, low >>> 48, 4);
        chars[23] = '-';
        formatHex(chars, 24, low, 12);

        return new String(chars);
    }

    private static void formatHex(char[] buffer, int offset, long value, int digits) {
        for (int i = digits - 1; i >= 0; i--) {
            buffer[offset + i]    = HEX_CHARS[(int) (value & 0xF)];
            value              >>>= 4;
        }
    }
}
