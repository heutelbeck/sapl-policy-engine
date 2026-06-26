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

import io.sapl.api.SaplVersion;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.Serial;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs regular-expression matching under a wall-clock budget so that a
 * catastrophically backtracking pattern on attacker-influenced input cannot
 * hang the evaluation thread (a ReDoS denial of service). The blacklist-style
 * pattern detectors used elsewhere are incomplete. A bounded matcher is the
 * general defence.
 * <p>
 * The Java regex engine reads the input character by character while
 * backtracking. Wrapping the input in a {@link CharSequence} whose reads check
 * a deadline therefore lets a runaway match abort with a
 * {@link RegexBudgetExceededException} a bounded time after the budget is
 * exhausted, without a watchdog thread. The clock is sampled only every
 * {@value #CHECK_INTERVAL} reads to keep the per-character cost negligible.
 * <p>
 * By default each match gets its own fresh budget. When many matches are driven
 * by a single attacker-influenced operation (for example one JSON-Schema
 * {@code schema.validate()} call over a large array or object, which matches a
 * {@code pattern} keyword once per element), the per-match budget alone lets
 * the
 * aggregate cost scale with the element count. To bound such an operation in
 * aggregate, wrap it in {@link #runWithSharedMatchBudget(Supplier)}: every
 * match
 * inside the wrapped operation then shares a single deadline.
 */
@UtilityClass
public class BoundedRegex {

    private static final long MATCH_BUDGET_NANOS = Duration.ofMillis(1000L).toNanos();
    private static final int  CHECK_INTERVAL     = 1024;

    /**
     * Holds the deadline shared by all matches inside a
     * {@link #runWithSharedMatchBudget(Supplier)} scope, or {@code null} when no
     * scope is active and each match uses its own fresh budget.
     */
    private static final ThreadLocal<Long> SHARED_DEADLINE_NANOS = new ThreadLocal<>();

    /**
     * Runs {@code operation} under a single match budget shared by every regex
     * match it triggers, so an operation that drives many matches over an
     * attacker-sized collection is bounded in aggregate rather than per match.
     * The budget starts when this method is entered. A nested call reuses the
     * outer deadline rather than extending it.
     *
     * @param <T> the operation result type
     * @param operation the operation to run under the shared budget
     * @return the operation result
     * @throws RegexBudgetExceededException if a match exceeds the shared budget
     */
    public static <T> T runWithSharedMatchBudget(Supplier<T> operation) {
        if (SHARED_DEADLINE_NANOS.get() != null) {
            return operation.get();
        }
        return runWithDeadline(System.nanoTime() + MATCH_BUDGET_NANOS, operation);
    }

    static <T> T runWithDeadline(long deadlineNanos, Supplier<T> operation) {
        SHARED_DEADLINE_NANOS.set(deadlineNanos);
        try {
            return operation.get();
        } finally {
            SHARED_DEADLINE_NANOS.remove();
        }
    }

    /**
     * Tests whether {@code pattern} matches the entire {@code input} under the
     * match budget.
     *
     * @param pattern the compiled pattern
     * @param input the input to match
     * @return true if the pattern matches the whole input
     * @throws RegexBudgetExceededException if matching exceeds the budget
     */
    public static boolean matches(Pattern pattern, String input) {
        return pattern.matcher(guard(input)).matches();
    }

    /**
     * Tests whether {@code pattern} matches any subsequence of {@code input}
     * under the match budget.
     *
     * @param pattern the compiled pattern
     * @param input the input to search
     * @return true if the pattern matches anywhere in the input
     * @throws RegexBudgetExceededException if matching exceeds the budget
     */
    public static boolean find(Pattern pattern, String input) {
        return pattern.matcher(guard(input)).find();
    }

    /**
     * Builds a matcher whose input reads are deadline-guarded, so that any
     * operation driven through it (find, replaceAll, group extraction) aborts
     * with a {@link RegexBudgetExceededException} once the match budget is
     * exhausted. The deadline starts now and spans the matcher's whole lifetime.
     *
     * @param pattern the compiled pattern
     * @param input the input to bind
     * @return a matcher over the deadline-guarded input
     */
    public static Matcher matcher(Pattern pattern, String input) {
        return pattern.matcher(guard(input));
    }

    /**
     * Wraps {@code input} in a deadline-guarded view for use with operations
     * that take a {@link CharSequence} directly, such as
     * {@link Pattern#split(CharSequence, int)}.
     *
     * @param input the input to guard
     * @return a deadline-guarded view of the input
     */
    public static CharSequence guarded(String input) {
        return guard(input);
    }

    private static CharSequence guard(String input) {
        val shared        = SHARED_DEADLINE_NANOS.get();
        val deadlineNanos = shared != null ? shared : System.nanoTime() + MATCH_BUDGET_NANOS;
        return new DeadlineCharSequence(input, deadlineNanos);
    }

    /**
     * Thrown when a regular-expression match exceeds its time budget.
     */
    public static final class RegexBudgetExceededException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = SaplVersion.VERSION_UID;

        RegexBudgetExceededException() {
            super("Regular expression evaluation exceeded its time budget.");
        }
    }

    private static final class DeadlineCharSequence implements CharSequence {

        private final CharSequence delegate;
        private final long         deadlineNanos;
        private int                reads;

        DeadlineCharSequence(CharSequence delegate, long deadlineNanos) {
            this.delegate      = delegate;
            this.deadlineNanos = deadlineNanos;
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public char charAt(int index) {
            if ((reads++ & (CHECK_INTERVAL - 1)) == 0 && System.nanoTime() > deadlineNanos) {
                throw new RegexBudgetExceededException();
            }
            return delegate.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new DeadlineCharSequence(delegate.subSequence(start, end), deadlineNanos);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
