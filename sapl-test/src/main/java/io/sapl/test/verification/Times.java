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
package io.sapl.test.verification;

/**
 * Verification mode for asserting invocation counts.
 * <p>
 * Use the static factory methods to create verification instances:
 * <ul>
 * <li>{@link #once()} - exactly one invocation</li>
 * <li>{@link #never()} - no invocations</li>
 * <li>{@link #times(int)} - exact number of invocations</li>
 * <li>{@link #atLeast(int)} - minimum number of invocations</li>
 * <li>{@link #atMost(int)} - maximum number of invocations</li>
 * <li>{@link #between(int, int)} - invocations within range (inclusive)</li>
 * </ul>
 */
public sealed interface Times {

    /**
     * Verifies the actual count matches this expectation.
     *
     * @param actualCount the actual number of invocations
     * @return true if the count satisfies this verification
     */
    boolean verify(int actualCount);

    /**
     * Returns a human-readable description of the expectation.
     *
     * @return description string
     */
    String describe();

    /**
     * Formats a verification failure message.
     *
     * @param actualCount the actual count that failed verification
     * @return error message describing the mismatch
     */
    default String failureMessage(int actualCount) {
        return "Expected %s but was invoked %d time(s).".formatted(describe(), actualCount);
    }

    /**
     * Verifies exactly one invocation.
     */
    static Times once() {
        return new Exactly(1);
    }

    /**
     * Verifies no invocations occurred.
     */
    static Times never() {
        return new Exactly(0);
    }

    /**
     * Verifies exactly n invocations.
     *
     * @param n expected count (must be >= 0)
     * @throws IllegalArgumentException if n is negative
     */
    static Times times(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Times count must be non-negative, was: " + n);
        }
        return new Exactly(n);
    }

    /**
     * Verifies at least n invocations.
     *
     * @param n minimum count (must be >= 0)
     * @throws IllegalArgumentException if n is negative
     */
    static Times atLeast(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("AtLeast count must be non-negative, was: " + n);
        }
        return new AtLeast(n);
    }

    /**
     * Verifies at most n invocations.
     *
     * @param n maximum count (must be >= 0)
     * @throws IllegalArgumentException if n is negative
     */
    static Times atMost(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("AtMost count must be non-negative, was: " + n);
        }
        return new AtMost(n);
    }

    /**
     * Verifies invocations within range [min, max] inclusive.
     *
     * @param min minimum count (must be >= 0)
     * @param max maximum count (must be >= min)
     * @throws IllegalArgumentException if min is negative or max &lt; min
     */
    static Times between(int min, int max) {
        if (min < 0) {
            throw new IllegalArgumentException("Between min must be non-negative, was: " + min);
        }
        if (max < min) {
            throw new IllegalArgumentException("Between max must be >= min, was: max=%d, min=%d".formatted(max, min));
        }
        return new Between(min, max);
    }

    /**
     * Exactly n invocations.
     */
    record Exactly(int expected) implements Times {
        @Override
        public boolean verify(int actualCount) {
            return actualCount == expected;
        }

        @Override
        public String describe() {
            return switch (expected) {
            case 0  -> "never";
            case 1  -> "exactly once";
            default -> "exactly %d times".formatted(expected);
            };
        }
    }

    /**
     * At least n invocations.
     */
    record AtLeast(int minimum) implements Times {
        @Override
        public boolean verify(int actualCount) {
            return actualCount >= minimum;
        }

        @Override
        public String describe() {
            return switch (minimum) {
            case 1  -> "at least once";
            default -> "at least %d times".formatted(minimum);
            };
        }
    }

    /**
     * At most n invocations.
     */
    record AtMost(int maximum) implements Times {
        @Override
        public boolean verify(int actualCount) {
            return actualCount <= maximum;
        }

        @Override
        public String describe() {
            return switch (maximum) {
            case 1  -> "at most once";
            default -> "at most %d times".formatted(maximum);
            };
        }
    }

    /**
     * Between min and max invocations (inclusive).
     */
    record Between(int minimum, int maximum) implements Times {
        @Override
        public boolean verify(int actualCount) {
            return actualCount >= minimum && actualCount <= maximum;
        }

        @Override
        public String describe() {
            return "between %d and %d times".formatted(minimum, maximum);
        }
    }
}
