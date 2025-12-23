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
package io.sapl.test;

import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.test.MockingFunctionBroker.ArgumentMatcher;
import io.sapl.test.MockingFunctionBroker.ArgumentMatchers;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Static factory methods for creating argument matchers.
 * <p>
 * Use these methods with static imports for a fluent mocking API:
 *
 * <pre>{@code
 * import static io.sapl.test.Matchers.*;
 *
 * test.mockFunction("time.dayOfWeek", args(eq(Value.of("2025-01-06"))), Value.of("MONDAY"));
 * test.mockFunction("time.now", args(any()), Value.of("2025-01-06T10:00:00Z"));
 * }</pre>
 */
@UtilityClass
public class Matchers {

    // ========== Argument List Builders ==========

    /**
     * Creates argument matchers for a function mock.
     *
     * @param matchers the individual argument matchers
     * @return Parameters instance for use with mockFunction
     */
    public static SaplTestFixture.Parameters args(ArgumentMatcher... matchers) {
        return ArgumentMatchers.of(matchers);
    }

    // ========== Core Matchers ==========

    /**
     * Matches any value.
     */
    public static ArgumentMatcher any() {
        return new ArgumentMatcher.Any();
    }

    /**
     * Matches a specific exact value.
     *
     * @param expected the expected value
     */
    public static ArgumentMatcher eq(Value expected) {
        return new ArgumentMatcher.Exact(expected);
    }

    /**
     * Matches values satisfying a predicate.
     *
     * @param predicate the predicate to test
     */
    public static ArgumentMatcher matching(Predicate<Value> predicate) {
        return new ArgumentMatcher.Predicated(predicate);
    }

    /**
     * Matches null values (NullValue).
     */
    public static ArgumentMatcher isNull() {
        return matching(NullValue.class::isInstance);
    }

    // ========== Combinator Matchers ==========

    /**
     * Negates a matcher. Matches when the wrapped matcher does not match.
     *
     * @param matcher the matcher to negate
     */
    public static ArgumentMatcher not(@NonNull ArgumentMatcher matcher) {
        return matching(v -> !matcher.matches(v));
    }

    /**
     * Matches when all provided matchers match.
     *
     * @param matchers the matchers that must all match
     */
    public static ArgumentMatcher allOf(@NonNull ArgumentMatcher... matchers) {
        return matching(v -> {
            for (var matcher : matchers) {
                if (!matcher.matches(v)) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Matches when at least one of the provided matchers matches.
     *
     * @param matchers the matchers where at least one must match
     */
    public static ArgumentMatcher anyOf(@NonNull ArgumentMatcher... matchers) {
        return matching(v -> {
            for (var matcher : matchers) {
                if (matcher.matches(v)) {
                    return true;
                }
            }
            return false;
        });
    }

    // ========== Type Matchers ==========

    /**
     * Matches any text value.
     */
    public static ArgumentMatcher anyText() {
        return matching(TextValue.class::isInstance);
    }

    /**
     * Matches any number value.
     */
    public static ArgumentMatcher anyNumber() {
        return matching(NumberValue.class::isInstance);
    }

    /**
     * Matches any boolean value.
     */
    public static ArgumentMatcher anyBoolean() {
        return matching(BooleanValue.class::isInstance);
    }

    /**
     * Matches any object value.
     */
    public static ArgumentMatcher anyObject() {
        return matching(ObjectValue.class::isInstance);
    }

    /**
     * Matches any array value.
     */
    public static ArgumentMatcher anyArray() {
        return matching(ArrayValue.class::isInstance);
    }

    // ========== Text Matchers ==========

    /**
     * Matches text values containing the specified substring (case-sensitive).
     *
     * @param substring the substring to search for
     */
    public static ArgumentMatcher textContaining(@NonNull String substring) {
        return matching(v -> v instanceof TextValue text && text.value().contains(substring));
    }

    /**
     * Matches text values containing the specified substring (case-insensitive).
     *
     * @param substring the substring to search for
     */
    public static ArgumentMatcher textContainingIgnoreCase(@NonNull String substring) {
        var lowerSubstring = substring.toLowerCase();
        return matching(v -> v instanceof TextValue text && text.value().toLowerCase().contains(lowerSubstring));
    }

    /**
     * Matches text values starting with the specified prefix (case-sensitive).
     *
     * @param prefix the prefix
     */
    public static ArgumentMatcher textStartingWith(@NonNull String prefix) {
        return matching(v -> v instanceof TextValue text && text.value().startsWith(prefix));
    }

    /**
     * Matches text values starting with the specified prefix (case-insensitive).
     *
     * @param prefix the prefix
     */
    public static ArgumentMatcher textStartingWithIgnoreCase(@NonNull String prefix) {
        var lowerPrefix = prefix.toLowerCase();
        return matching(v -> v instanceof TextValue text && text.value().toLowerCase().startsWith(lowerPrefix));
    }

    /**
     * Matches text values ending with the specified suffix (case-sensitive).
     *
     * @param suffix the suffix
     */
    public static ArgumentMatcher textEndingWith(@NonNull String suffix) {
        return matching(v -> v instanceof TextValue text && text.value().endsWith(suffix));
    }

    /**
     * Matches text values ending with the specified suffix (case-insensitive).
     *
     * @param suffix the suffix
     */
    public static ArgumentMatcher textEndingWithIgnoreCase(@NonNull String suffix) {
        var lowerSuffix = suffix.toLowerCase();
        return matching(v -> v instanceof TextValue text && text.value().toLowerCase().endsWith(lowerSuffix));
    }

    /**
     * Matches text values equal to the expected text (case-insensitive).
     *
     * @param expected the expected text
     */
    public static ArgumentMatcher textEqualsIgnoreCase(@NonNull String expected) {
        return matching(v -> v instanceof TextValue text && text.value().equalsIgnoreCase(expected));
    }

    /**
     * Matches text values matching a regex pattern.
     *
     * @param regex the regex pattern
     */
    public static ArgumentMatcher textMatching(@NonNull String regex) {
        return matching(v -> v instanceof TextValue text && text.value().matches(regex));
    }

    /**
     * Matches empty text values (length 0).
     */
    public static ArgumentMatcher textIsEmpty() {
        return matching(v -> v instanceof TextValue text && text.value().isEmpty());
    }

    /**
     * Matches blank text values (empty or only whitespace).
     */
    public static ArgumentMatcher textIsBlank() {
        return matching(v -> v instanceof TextValue text && text.value().isBlank());
    }

    /**
     * Matches text values with the specified length.
     *
     * @param length the expected length
     */
    public static ArgumentMatcher textHasLength(int length) {
        return matching(v -> v instanceof TextValue text && text.value().length() == length);
    }

    /**
     * Matches text values containing all substrings in the specified order.
     * <p>
     * Example: {@code textContainsInOrder("Hello", "World")} matches "Hello
     * beautiful World"
     * but not "World says Hello".
     *
     * @param substrings the substrings that must appear in order
     */
    public static ArgumentMatcher textContainsInOrder(@NonNull String... substrings) {
        return matching(v -> {
            if (!(v instanceof TextValue text)) {
                return false;
            }
            var s         = text.value();
            int lastIndex = 0;
            for (var substring : substrings) {
                int index = s.indexOf(substring, lastIndex);
                if (index < 0) {
                    return false;
                }
                lastIndex = index + substring.length();
            }
            return true;
        });
    }

    /**
     * Matches text values equal to the expected text with whitespace compressed.
     * <p>
     * All sequences of whitespace are treated as equivalent to a single space,
     * and leading/trailing whitespace is ignored.
     *
     * @param expected the expected text (whitespace will also be compressed)
     */
    public static ArgumentMatcher textEqualsCompressingWhitespace(@NonNull String expected) {
        var normalizedExpected = compressWhitespace(expected);
        return matching(
                v -> v instanceof TextValue text && compressWhitespace(text.value()).equals(normalizedExpected));
    }

    private static String compressWhitespace(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    // ========== Number Matchers ==========

    /**
     * Matches number values equal to the specified value.
     *
     * @param expected the expected number
     */
    public static ArgumentMatcher numberEqualTo(long expected) {
        return matching(v -> v instanceof NumberValue n && n.value().longValue() == expected);
    }

    /**
     * Matches number values equal to the specified value.
     *
     * @param expected the expected number
     */
    public static ArgumentMatcher numberEqualTo(double expected) {
        return matching(v -> v instanceof NumberValue n && n.value().doubleValue() == expected);
    }

    /**
     * Matches number values equal to the specified BigDecimal value.
     *
     * @param expected the expected number
     */
    public static ArgumentMatcher numberEqualTo(@NonNull BigDecimal expected) {
        return matching(
                v -> v instanceof NumberValue n && expected.compareTo(new BigDecimal(n.value().toString())) == 0);
    }

    /**
     * Matches number values greater than the specified value.
     *
     * @param threshold the threshold value
     */
    public static ArgumentMatcher greaterThan(long threshold) {
        return matching(v -> v instanceof NumberValue n && n.value().longValue() > threshold);
    }

    /**
     * Matches number values greater than the specified value.
     *
     * @param threshold the threshold value
     */
    public static ArgumentMatcher greaterThan(double threshold) {
        return matching(v -> v instanceof NumberValue n && n.value().doubleValue() > threshold);
    }

    /**
     * Matches number values greater than or equal to the specified value.
     *
     * @param threshold the threshold value
     */
    public static ArgumentMatcher greaterThanOrEqualTo(long threshold) {
        return matching(v -> v instanceof NumberValue n && n.value().longValue() >= threshold);
    }

    /**
     * Matches number values greater than or equal to the specified value.
     *
     * @param threshold the threshold value
     */
    public static ArgumentMatcher greaterThanOrEqualTo(double threshold) {
        return matching(v -> v instanceof NumberValue n && n.value().doubleValue() >= threshold);
    }

    /**
     * Matches number values less than the specified value.
     *
     * @param threshold the threshold value
     */
    public static ArgumentMatcher lessThan(long threshold) {
        return matching(v -> v instanceof NumberValue n && n.value().longValue() < threshold);
    }

    /**
     * Matches number values less than the specified value.
     *
     * @param threshold the threshold value
     */
    public static ArgumentMatcher lessThan(double threshold) {
        return matching(v -> v instanceof NumberValue n && n.value().doubleValue() < threshold);
    }

    /**
     * Matches number values less than or equal to the specified value.
     *
     * @param threshold the threshold value
     */
    public static ArgumentMatcher lessThanOrEqualTo(long threshold) {
        return matching(v -> v instanceof NumberValue n && n.value().longValue() <= threshold);
    }

    /**
     * Matches number values less than or equal to the specified value.
     *
     * @param threshold the threshold value
     */
    public static ArgumentMatcher lessThanOrEqualTo(double threshold) {
        return matching(v -> v instanceof NumberValue n && n.value().doubleValue() <= threshold);
    }

    /**
     * Matches number values within the specified range (inclusive).
     *
     * @param min the minimum value (inclusive)
     * @param max the maximum value (inclusive)
     */
    public static ArgumentMatcher between(long min, long max) {
        return matching(v -> {
            if (!(v instanceof NumberValue n)) {
                return false;
            }
            long val = n.value().longValue();
            return val >= min && val <= max;
        });
    }

    /**
     * Matches number values within the specified range (inclusive).
     *
     * @param min the minimum value (inclusive)
     * @param max the maximum value (inclusive)
     */
    public static ArgumentMatcher between(double min, double max) {
        return matching(v -> {
            if (!(v instanceof NumberValue n)) {
                return false;
            }
            double val = n.value().doubleValue();
            return val >= min && val <= max;
        });
    }

    /**
     * Matches number values close to the expected value within the given delta.
     *
     * @param expected the expected value
     * @param delta the maximum allowed difference
     */
    public static ArgumentMatcher closeTo(double expected, double delta) {
        return matching(v -> v instanceof NumberValue n && Math.abs(n.value().doubleValue() - expected) <= delta);
    }

    // ========== Boolean Matchers ==========

    /**
     * Matches boolean values equal to the specified value.
     *
     * @param expected the expected boolean
     */
    public static ArgumentMatcher booleanEqualTo(boolean expected) {
        return matching(v -> v instanceof BooleanValue b && b.value() == expected);
    }

    // ========== Object Matchers ==========

    /**
     * Matches object values containing the specified key.
     *
     * @param key the key that must be present
     */
    public static ArgumentMatcher objectContainingKey(@NonNull String key) {
        return matching(v -> v instanceof ObjectValue obj && obj.containsKey(key));
    }

    /**
     * Matches object values containing the specified key with a value matching the
     * given matcher.
     *
     * @param key the key that must be present
     * @param valueMatcher the matcher for the value at that key
     */
    public static ArgumentMatcher objectContainingKey(@NonNull String key, @NonNull ArgumentMatcher valueMatcher) {
        return matching(v -> {
            if (!(v instanceof ObjectValue obj)) {
                return false;
            }
            if (!obj.containsKey(key)) {
                return false;
            }
            var value = obj.get(key);
            return value != null && valueMatcher.matches(value);
        });
    }

    /**
     * Matches object values containing all specified keys.
     *
     * @param keys the keys that must all be present
     */
    public static ArgumentMatcher objectContainingKeys(@NonNull String... keys) {
        return matching(v -> {
            if (!(v instanceof ObjectValue obj)) {
                return false;
            }
            for (var key : keys) {
                if (!obj.containsKey(key)) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Matches empty object values.
     */
    public static ArgumentMatcher objectIsEmpty() {
        return matching(v -> v instanceof ObjectValue obj && obj.isEmpty());
    }

    // ========== Array Matchers ==========

    /**
     * Matches array values containing an element that matches the given matcher.
     *
     * @param elementMatcher the matcher for the element
     */
    public static ArgumentMatcher arrayContaining(@NonNull ArgumentMatcher elementMatcher) {
        return matching(v -> {
            if (!(v instanceof ArrayValue arr)) {
                return false;
            }
            for (var element : arr) {
                if (elementMatcher.matches(element)) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Matches array values containing all elements that match the corresponding
     * matchers (in any order).
     * <p>
     * Each matcher must match at least one distinct element.
     *
     * @param elementMatchers the matchers for elements
     */
    public static ArgumentMatcher arrayContainingAll(@NonNull ArgumentMatcher... elementMatchers) {
        return matching(v -> {
            if (!(v instanceof ArrayValue arr)) {
                return false;
            }
            var remaining = new java.util.ArrayList<>(arr);
            for (var matcher : elementMatchers) {
                boolean found = false;
                for (int i = 0; i < remaining.size(); i++) {
                    if (matcher.matches(remaining.get(i))) {
                        remaining.remove(i);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Matches array values where every element matches the given matcher.
     *
     * @param elementMatcher the matcher that all elements must satisfy
     */
    public static ArgumentMatcher arrayEveryItem(@NonNull ArgumentMatcher elementMatcher) {
        return matching(v -> {
            if (!(v instanceof ArrayValue arr)) {
                return false;
            }
            for (var element : arr) {
                if (!elementMatcher.matches(element)) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Matches array values containing exactly the specified elements in order.
     *
     * @param expected the expected values in order
     */
    public static ArgumentMatcher arrayContainsExactly(@NonNull Value... expected) {
        return matching(v -> {
            if (!(v instanceof ArrayValue arr)) {
                return false;
            }
            if (arr.size() != expected.length) {
                return false;
            }
            for (int i = 0; i < expected.length; i++) {
                if (!expected[i].equals(arr.get(i))) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Matches array values containing exactly the specified elements in any order.
     *
     * @param expected the expected values (order doesn't matter)
     */
    public static ArgumentMatcher arrayContainsExactlyInAnyOrder(@NonNull Value... expected) {
        return matching(v -> {
            if (!(v instanceof ArrayValue arr)) {
                return false;
            }
            if (arr.size() != expected.length) {
                return false;
            }
            var remaining = new java.util.ArrayList<>(Arrays.asList(expected));
            for (var element : arr) {
                if (!remaining.remove(element)) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Matches empty array values.
     */
    public static ArgumentMatcher arrayIsEmpty() {
        return matching(v -> v instanceof ArrayValue arr && arr.isEmpty());
    }

    /**
     * Matches array values with the specified size.
     *
     * @param size the expected size
     */
    public static ArgumentMatcher arrayHasSize(int size) {
        return matching(v -> v instanceof ArrayValue arr && arr.size() == size);
    }

    // ========== Decision Matchers ==========

    /**
     * Creates a matcher that accepts any decision.
     * <p>
     * Useful when you only care about obligations, advice, or resource
     * without caring about the decision type itself.
     *
     * @return a predicate that matches any non-null decision
     */
    public static Predicate<AuthorizationDecision> anyDecision() {
        return Objects::nonNull;
    }

    /**
     * Creates a matcher for PERMIT decisions.
     *
     * @return a matcher expecting Decision.PERMIT
     */
    public static DecisionMatcher isPermit() {
        return new DecisionMatcher(Decision.PERMIT);
    }

    /**
     * Creates a matcher for DENY decisions.
     *
     * @return a matcher expecting Decision.DENY
     */
    public static DecisionMatcher isDeny() {
        return new DecisionMatcher(Decision.DENY);
    }

    /**
     * Creates a matcher for INDETERMINATE decisions.
     *
     * @return a matcher expecting Decision.INDETERMINATE
     */
    public static DecisionMatcher isIndeterminate() {
        return new DecisionMatcher(Decision.INDETERMINATE);
    }

    /**
     * Creates a matcher for NOT_APPLICABLE decisions.
     *
     * @return a matcher expecting Decision.NOT_APPLICABLE
     */
    public static DecisionMatcher isNotApplicable() {
        return new DecisionMatcher(Decision.NOT_APPLICABLE);
    }

    /**
     * Creates a matcher for a specific decision type.
     *
     * @param decision the expected decision type
     * @return a matcher expecting the specified decision
     */
    public static DecisionMatcher isDecision(Decision decision) {
        return new DecisionMatcher(decision);
    }
}
