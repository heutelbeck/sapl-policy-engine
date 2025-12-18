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
package io.sapl.test;

import io.sapl.api.model.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static io.sapl.test.Matchers.any;
import static io.sapl.test.Matchers.anyArray;
import static io.sapl.test.Matchers.anyBoolean;
import static io.sapl.test.Matchers.anyNumber;
import static io.sapl.test.Matchers.anyObject;
import static io.sapl.test.Matchers.anyText;
import static io.sapl.test.Matchers.args;
import static io.sapl.test.Matchers.eq;
import static io.sapl.test.Matchers.greaterThan;
import static io.sapl.test.Matchers.lessThan;
import static io.sapl.test.Matchers.matching;
import static io.sapl.test.Matchers.textContaining;
import static io.sapl.test.Matchers.textMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class MatchersTests {

    // ========== args() Tests ==========

    @Test
    void whenArgsWithNoMatchers_thenCreatesEmptyParameters() {
        var parameters = args();

        assertThat(parameters).isInstanceOf(MockingFunctionBroker.ArgumentMatchers.class);
        assertThat(((MockingFunctionBroker.ArgumentMatchers) parameters).matchers()).isEmpty();
    }

    @Test
    void whenArgsWithMultipleMatchers_thenCreatesParametersWithAllMatchers() {
        var parameters = args(any(), eq(Value.of("x")), anyText());

        assertThat(parameters).isInstanceOf(MockingFunctionBroker.ArgumentMatchers.class);
        var matchers = ((MockingFunctionBroker.ArgumentMatchers) parameters).matchers();
        assertThat(matchers).hasSize(3);
        assertThat(matchers.get(0)).isInstanceOf(MockingFunctionBroker.ArgumentMatcher.Any.class);
        assertThat(matchers.get(1)).isInstanceOf(MockingFunctionBroker.ArgumentMatcher.Exact.class);
        assertThat(matchers.get(2)).isInstanceOf(MockingFunctionBroker.ArgumentMatcher.Predicated.class);
    }

    // ========== any() Tests ==========

    @ParameterizedTest
    @MethodSource("allValueTypes")
    void whenAny_thenMatchesAllValueTypes(Value value) {
        assertThat(any().matches(value)).isTrue();
    }

    @Test
    void whenAny_thenHasSpecificityZero() {
        assertThat(any().specificity()).isZero();
    }

    // ========== eq() Tests ==========

    @Test
    void whenEqWithMatchingValue_thenReturnsTrue() {
        var value = Value.of("test");
        assertThat(eq(value).matches(Value.of("test"))).isTrue();
    }

    @Test
    void whenEqWithDifferentValue_thenReturnsFalse() {
        var value = Value.of("test");
        assertThat(eq(value).matches(Value.of("different"))).isFalse();
    }

    @Test
    void whenEqWithDifferentType_thenReturnsFalse() {
        var textValue   = Value.of("42");
        var numberValue = Value.of(42);
        assertThat(eq(textValue).matches(numberValue)).isFalse();
    }

    @Test
    void whenEq_thenHasSpecificityTwo() {
        assertThat(eq(Value.of("x")).specificity()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("equalValuePairs")
    void whenEqWithEqualValues_thenMatches(Value expected, Value actual) {
        assertThat(eq(expected).matches(actual)).isTrue();
    }

    // ========== matching() Tests ==========

    @Test
    void whenMatchingWithSatisfiedPredicate_thenReturnsTrue() {
        var matcher = matching(v -> v.equals(Value.of("target")));
        assertThat(matcher.matches(Value.of("target"))).isTrue();
    }

    @Test
    void whenMatchingWithUnsatisfiedPredicate_thenReturnsFalse() {
        var matcher = matching(v -> v.equals(Value.of("target")));
        assertThat(matcher.matches(Value.of("other"))).isFalse();
    }

    @Test
    void whenMatching_thenHasSpecificityOne() {
        assertThat(matching(v -> true).specificity()).isEqualTo(1);
    }

    // ========== Type Matchers Tests ==========

    @Test
    void whenAnyText_thenMatchesOnlyTextValues() {
        assertThat(anyText().matches(Value.of("hello"))).isTrue();
        assertThat(anyText().matches(Value.of(""))).isTrue();
        assertThat(anyText().matches(Value.of(42))).isFalse();
        assertThat(anyText().matches(Value.TRUE)).isFalse();
    }

    @Test
    void whenAnyNumber_thenMatchesOnlyNumberValues() {
        assertThat(anyNumber().matches(Value.of(42))).isTrue();
        assertThat(anyNumber().matches(Value.of(0))).isTrue();
        assertThat(anyNumber().matches(Value.of(-100))).isTrue();
        assertThat(anyNumber().matches(Value.of("42"))).isFalse();
        assertThat(anyNumber().matches(Value.TRUE)).isFalse();
    }

    @Test
    void whenAnyBoolean_thenMatchesOnlyBooleanValues() {
        assertThat(anyBoolean().matches(Value.TRUE)).isTrue();
        assertThat(anyBoolean().matches(Value.FALSE)).isTrue();
        assertThat(anyBoolean().matches(Value.of("true"))).isFalse();
        assertThat(anyBoolean().matches(Value.of(1))).isFalse();
    }

    @Test
    void whenAnyObject_thenMatchesOnlyObjectValues() {
        assertThat(anyObject().matches(Value.of("{\"key\":\"value\"}"))).isFalse(); // This is text
        assertThat(anyObject().matches(Value.of("text"))).isFalse();
        assertThat(anyObject().matches(Value.of(42))).isFalse();
    }

    @Test
    void whenAnyArray_thenMatchesOnlyArrayValues() {
        assertThat(anyArray().matches(Value.of("[1,2,3]"))).isFalse(); // This is text
        assertThat(anyArray().matches(Value.of("text"))).isFalse();
        assertThat(anyArray().matches(Value.of(42))).isFalse();
    }

    @ParameterizedTest
    @MethodSource("typeMatcherSpecificities")
    void whenTypeMatcher_thenHasSpecificityOne(MockingFunctionBroker.ArgumentMatcher matcher) {
        assertThat(matcher.specificity()).isEqualTo(1);
    }

    // ========== Text Matchers Tests ==========

    @Test
    void whenTextContaining_thenMatchesTextWithSubstring() {
        var matcher = textContaining("admin");

        assertThat(matcher.matches(Value.of("admin"))).isTrue();
        assertThat(matcher.matches(Value.of("super-admin-user"))).isTrue();
        assertThat(matcher.matches(Value.of("ADMIN"))).isFalse(); // Case sensitive
        assertThat(matcher.matches(Value.of("user"))).isFalse();
        assertThat(matcher.matches(Value.of(42))).isFalse();
    }

    @Test
    void whenTextContainingEmptyString_thenMatchesAllText() {
        var matcher = textContaining("");

        assertThat(matcher.matches(Value.of("anything"))).isTrue();
        assertThat(matcher.matches(Value.of(""))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "user@example.com", "admin@test.org", "x@y.z" })
    void whenTextMatchingEmailPattern_thenMatchesEmails(String email) {
        var emailPattern = ".*@.*\\..*";
        assertThat(textMatching(emailPattern).matches(Value.of(email))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "not-an-email", "missing@domain", "@invalid.com" })
    void whenTextMatchingEmailPattern_thenRejectsNonEmails(String notEmail) {
        var emailPattern = ".+@.+\\..+";
        assertThat(textMatching(emailPattern).matches(Value.of(notEmail))).isFalse();
    }

    @Test
    void whenTextMatching_thenDoesNotMatchNonTextValues() {
        var matcher = textMatching(".*");
        assertThat(matcher.matches(Value.of(42))).isFalse();
        assertThat(matcher.matches(Value.TRUE)).isFalse();
    }

    // ========== Number Matchers Tests ==========

    @Test
    void whenGreaterThan_thenMatchesLargerNumbers() {
        var matcher = greaterThan(10);

        assertThat(matcher.matches(Value.of(11))).isTrue();
        assertThat(matcher.matches(Value.of(100))).isTrue();
        assertThat(matcher.matches(Value.of(10))).isFalse();
        assertThat(matcher.matches(Value.of(9))).isFalse();
        assertThat(matcher.matches(Value.of(-5))).isFalse();
    }

    @Test
    void whenGreaterThanWithNegativeThreshold_thenWorksCorrectly() {
        var matcher = greaterThan(-5);

        assertThat(matcher.matches(Value.of(-4))).isTrue();
        assertThat(matcher.matches(Value.of(0))).isTrue();
        assertThat(matcher.matches(Value.of(-5))).isFalse();
        assertThat(matcher.matches(Value.of(-10))).isFalse();
    }

    @Test
    void whenGreaterThan_thenDoesNotMatchNonNumbers() {
        var matcher = greaterThan(0);
        assertThat(matcher.matches(Value.of("100"))).isFalse();
        assertThat(matcher.matches(Value.TRUE)).isFalse();
    }

    @Test
    void whenLessThan_thenMatchesSmallerNumbers() {
        var matcher = lessThan(10);

        assertThat(matcher.matches(Value.of(9))).isTrue();
        assertThat(matcher.matches(Value.of(0))).isTrue();
        assertThat(matcher.matches(Value.of(-100))).isTrue();
        assertThat(matcher.matches(Value.of(10))).isFalse();
        assertThat(matcher.matches(Value.of(11))).isFalse();
    }

    @Test
    void whenLessThan_thenDoesNotMatchNonNumbers() {
        var matcher = lessThan(100);
        assertThat(matcher.matches(Value.of("5"))).isFalse();
        assertThat(matcher.matches(Value.FALSE)).isFalse();
    }

    // ========== Specificity Ordering Tests ==========

    @Test
    void whenComparingSpecificities_thenExactIsHigherThanPredicate() {
        assertThat(eq(Value.of("x")).specificity()).isGreaterThan(anyText().specificity());
    }

    @Test
    void whenComparingSpecificities_thenPredicateIsHigherThanAny() {
        assertThat(anyText().specificity()).isGreaterThan(any().specificity());
    }

    @Test
    void whenComparingSpecificities_thenOrderIsExactPredicateAny() {
        var exactSpecificity     = eq(Value.of("x")).specificity();
        var predicateSpecificity = matching(v -> true).specificity();
        var anySpecificity       = any().specificity();

        assertThat(exactSpecificity).isGreaterThan(predicateSpecificity);
        assertThat(predicateSpecificity).isGreaterThan(anySpecificity);
    }

    // ========== ArgumentMatchers Record Tests ==========

    @Test
    void whenArgumentMatchersCreatedWithOf_thenContainsMatchers() {
        var matchers = MockingFunctionBroker.ArgumentMatchers.of(any(), eq(Value.of("x")));

        assertThat(matchers.matchers()).hasSize(2);
    }

    @Test
    void whenArgumentMatchersCreatedWithList_thenContainsMatchers() {
        var matcherList = java.util.List.of(any(), eq(Value.of("x")));
        var matchers    = MockingFunctionBroker.ArgumentMatchers.of(matcherList);

        assertThat(matchers.matchers()).hasSize(2);
    }

    @Test
    void whenArgumentMatchersMatchersList_thenIsImmutable() {
        var matchers = MockingFunctionBroker.ArgumentMatchers.of(any());
        var list     = matchers.matchers();

        assertThat(list).isUnmodifiable();
    }

    // ========== Test Data Providers ==========

    static Stream<Value> allValueTypes() {
        return Stream.of(Value.of("text"), Value.of(42), Value.of(3.14), Value.TRUE, Value.FALSE, Value.NULL,
                Value.UNDEFINED);
    }

    static Stream<Arguments> equalValuePairs() {
        return Stream.of(arguments(Value.of("hello"), Value.of("hello")), arguments(Value.of(42), Value.of(42)),
                arguments(Value.of(3.14), Value.of(3.14)), arguments(Value.TRUE, Value.TRUE),
                arguments(Value.FALSE, Value.FALSE), arguments(Value.NULL, Value.NULL));
    }

    static Stream<MockingFunctionBroker.ArgumentMatcher> typeMatcherSpecificities() {
        return Stream.of(anyText(), anyNumber(), anyBoolean(), anyObject(), anyArray(), textContaining("x"),
                textMatching(".*"), greaterThan(0), lessThan(0));
    }
}
