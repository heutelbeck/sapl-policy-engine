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
package io.sapl.test.next;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.test.next.MockingFunctionBroker.ArgumentMatcher;
import io.sapl.test.next.MockingFunctionBroker.ArgumentMatchers;
import lombok.experimental.UtilityClass;

import java.util.function.Predicate;

/**
 * Static factory methods for creating argument matchers.
 * <p>
 * Use these methods with static imports for a fluent mocking API:
 *
 * <pre>{@code
 * import static io.sapl.test.next.Matchers.*;
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

    // ========== Single Argument Matchers ==========

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

    // ========== Type Matchers ==========

    /**
     * Matches any text value.
     */
    public static ArgumentMatcher anyText() {
        return matching(v -> v instanceof TextValue);
    }

    /**
     * Matches any number value.
     */
    public static ArgumentMatcher anyNumber() {
        return matching(v -> v instanceof NumberValue);
    }

    /**
     * Matches any boolean value.
     */
    public static ArgumentMatcher anyBoolean() {
        return matching(v -> v instanceof BooleanValue);
    }

    /**
     * Matches any object value.
     */
    public static ArgumentMatcher anyObject() {
        return matching(v -> v instanceof ObjectValue);
    }

    /**
     * Matches any array value.
     */
    public static ArgumentMatcher anyArray() {
        return matching(v -> v instanceof ArrayValue);
    }

    // ========== Text Matchers ==========

    /**
     * Matches text values containing the specified substring.
     *
     * @param substring the substring to search for
     */
    public static ArgumentMatcher textContaining(String substring) {
        return matching(v -> v instanceof TextValue text && text.value().contains(substring));
    }

    /**
     * Matches text values matching a regex pattern.
     *
     * @param regex the regex pattern
     */
    public static ArgumentMatcher textMatching(String regex) {
        return matching(v -> v instanceof TextValue text && text.value().matches(regex));
    }

    // ========== Number Matchers ==========

    /**
     * Matches number values greater than the specified value.
     *
     * @param threshold the threshold value
     */
    public static ArgumentMatcher greaterThan(long threshold) {
        return matching(v -> v instanceof NumberValue number && number.value().longValue() > threshold);
    }

    /**
     * Matches number values less than the specified value.
     *
     * @param threshold the threshold value
     */
    public static ArgumentMatcher lessThan(long threshold) {
        return matching(v -> v instanceof NumberValue number && number.value().longValue() < threshold);
    }
}
