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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.test.Matchers.*;
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

    // ========== isNull() Tests ==========

    @Test
    void whenIsNull_thenMatchesNullValue() {
        assertThat(isNull().matches(Value.NULL)).isTrue();
    }

    @Test
    void whenIsNull_thenDoesNotMatchNonNullValues() {
        assertThat(isNull().matches(Value.of("text"))).isFalse();
        assertThat(isNull().matches(Value.of(0))).isFalse();
        assertThat(isNull().matches(Value.TRUE)).isFalse();
    }

    // ========== Combinator Matchers Tests ==========

    @Test
    void whenNot_thenNegatesMatcher() {
        var notText = not(anyText());

        assertThat(notText.matches(Value.of("text"))).isFalse();
        assertThat(notText.matches(Value.of(42))).isTrue();
        assertThat(notText.matches(Value.TRUE)).isTrue();
    }

    @Test
    void whenAllOf_thenRequiresAllMatchersToMatch() {
        var matcher = allOf(anyNumber(), greaterThan(5), lessThan(10));

        assertThat(matcher.matches(Value.of(7))).isTrue();
        assertThat(matcher.matches(Value.of(5))).isFalse();
        assertThat(matcher.matches(Value.of(10))).isFalse();
        assertThat(matcher.matches(Value.of("7"))).isFalse();
    }

    @Test
    void whenAnyOf_thenRequiresAtLeastOneMatcherToMatch() {
        var matcher = anyOf(eq(Value.of("a")), eq(Value.of("b")), eq(Value.of("c")));

        assertThat(matcher.matches(Value.of("a"))).isTrue();
        assertThat(matcher.matches(Value.of("b"))).isTrue();
        assertThat(matcher.matches(Value.of("c"))).isTrue();
        assertThat(matcher.matches(Value.of("d"))).isFalse();
    }

    @Test
    void whenAllOfWithEmptyMatchers_thenMatchesEverything() {
        var matcher = allOf();
        assertThat(matcher.matches(Value.of("anything"))).isTrue();
    }

    @Test
    void whenAnyOfWithEmptyMatchers_thenMatchesNothing() {
        var matcher = anyOf();
        assertThat(matcher.matches(Value.of("anything"))).isFalse();
    }

    // ========== Additional Text Matchers Tests ==========

    @Test
    void whenTextContainingIgnoreCase_thenMatchesCaseInsensitive() {
        var matcher = textContainingIgnoreCase("admin");

        assertThat(matcher.matches(Value.of("admin"))).isTrue();
        assertThat(matcher.matches(Value.of("ADMIN"))).isTrue();
        assertThat(matcher.matches(Value.of("Super-Admin-User"))).isTrue();
        assertThat(matcher.matches(Value.of("user"))).isFalse();
    }

    @Test
    void whenTextStartingWith_thenMatchesPrefix() {
        var matcher = textStartingWith("Hello");

        assertThat(matcher.matches(Value.of("Hello World"))).isTrue();
        assertThat(matcher.matches(Value.of("Hello"))).isTrue();
        assertThat(matcher.matches(Value.of("hello World"))).isFalse();
        assertThat(matcher.matches(Value.of("Say Hello"))).isFalse();
    }

    @Test
    void whenTextStartingWithIgnoreCase_thenMatchesCaseInsensitive() {
        var matcher = textStartingWithIgnoreCase("hello");

        assertThat(matcher.matches(Value.of("Hello World"))).isTrue();
        assertThat(matcher.matches(Value.of("HELLO"))).isTrue();
        assertThat(matcher.matches(Value.of("hello"))).isTrue();
        assertThat(matcher.matches(Value.of("Say Hello"))).isFalse();
    }

    @Test
    void whenTextEndingWith_thenMatchesSuffix() {
        var matcher = textEndingWith(".txt");

        assertThat(matcher.matches(Value.of("file.txt"))).isTrue();
        assertThat(matcher.matches(Value.of(".txt"))).isTrue();
        assertThat(matcher.matches(Value.of("file.TXT"))).isFalse();
        assertThat(matcher.matches(Value.of("file.txt.bak"))).isFalse();
    }

    @Test
    void whenTextEndingWithIgnoreCase_thenMatchesCaseInsensitive() {
        var matcher = textEndingWithIgnoreCase(".txt");

        assertThat(matcher.matches(Value.of("file.txt"))).isTrue();
        assertThat(matcher.matches(Value.of("file.TXT"))).isTrue();
        assertThat(matcher.matches(Value.of("FILE.Txt"))).isTrue();
    }

    @Test
    void whenTextEqualsIgnoreCase_thenMatchesCaseInsensitive() {
        var matcher = textEqualsIgnoreCase("Hello");

        assertThat(matcher.matches(Value.of("Hello"))).isTrue();
        assertThat(matcher.matches(Value.of("hello"))).isTrue();
        assertThat(matcher.matches(Value.of("HELLO"))).isTrue();
        assertThat(matcher.matches(Value.of("Hello "))).isFalse();
    }

    @Test
    void whenTextIsEmpty_thenMatchesEmptyString() {
        assertThat(textIsEmpty().matches(Value.of(""))).isTrue();
        assertThat(textIsEmpty().matches(Value.of(" "))).isFalse();
        assertThat(textIsEmpty().matches(Value.of("text"))).isFalse();
    }

    @Test
    void whenTextIsBlank_thenMatchesBlankStrings() {
        assertThat(textIsBlank().matches(Value.of(""))).isTrue();
        assertThat(textIsBlank().matches(Value.of(" "))).isTrue();
        assertThat(textIsBlank().matches(Value.of("  \t\n  "))).isTrue();
        assertThat(textIsBlank().matches(Value.of("text"))).isFalse();
    }

    @Test
    void whenTextHasLength_thenMatchesExactLength() {
        var matcher = textHasLength(5);

        assertThat(matcher.matches(Value.of("hello"))).isTrue();
        assertThat(matcher.matches(Value.of("12345"))).isTrue();
        assertThat(matcher.matches(Value.of("hi"))).isFalse();
        assertThat(matcher.matches(Value.of("toolong"))).isFalse();
    }

    @Test
    void whenTextContainsInOrder_thenMatchesSubstringsInOrder() {
        var matcher = textContainsInOrder("Hello", "World", "!");

        assertThat(matcher.matches(Value.of("Hello World!"))).isTrue();
        assertThat(matcher.matches(Value.of("Hello beautiful World!"))).isTrue();
        assertThat(matcher.matches(Value.of("World Hello!"))).isFalse();
        assertThat(matcher.matches(Value.of("Hello World"))).isFalse();
    }

    @Test
    void whenTextEqualsCompressingWhitespace_thenNormalizesWhitespace() {
        var matcher = textEqualsCompressingWhitespace("Hello World");

        assertThat(matcher.matches(Value.of("Hello World"))).isTrue();
        assertThat(matcher.matches(Value.of("  Hello   World  "))).isTrue();
        assertThat(matcher.matches(Value.of("Hello\t\nWorld"))).isTrue();
        assertThat(matcher.matches(Value.of("HelloWorld"))).isFalse();
    }

    // ========== Number Matchers Tests ==========

    @Test
    void whenNumberEqualToLong_thenMatchesExactValue() {
        assertThat(numberEqualTo(42L).matches(Value.of(42))).isTrue();
        assertThat(numberEqualTo(42L).matches(Value.of(43))).isFalse();
    }

    @Test
    void whenNumberEqualToDouble_thenMatchesExactValue() {
        assertThat(numberEqualTo(3.14).matches(Value.of(3.14))).isTrue();
        assertThat(numberEqualTo(3.14).matches(Value.of(3.15))).isFalse();
    }

    @Test
    void whenNumberEqualToBigDecimal_thenMatchesExactValue() {
        assertThat(numberEqualTo(new BigDecimal("123.456")).matches(Value.of(123.456))).isTrue();
    }

    @Test
    void whenGreaterThanDouble_thenMatchesLargerNumbers() {
        assertThat(greaterThan(3.14).matches(Value.of(3.15))).isTrue();
        assertThat(greaterThan(3.14).matches(Value.of(3.14))).isFalse();
        assertThat(greaterThan(3.14).matches(Value.of(3.13))).isFalse();
    }

    @Test
    void whenGreaterThanOrEqualTo_thenMatchesEqualOrLarger() {
        assertThat(greaterThanOrEqualTo(10L).matches(Value.of(10))).isTrue();
        assertThat(greaterThanOrEqualTo(10L).matches(Value.of(11))).isTrue();
        assertThat(greaterThanOrEqualTo(10L).matches(Value.of(9))).isFalse();
    }

    @Test
    void whenLessThanDouble_thenMatchesSmallerNumbers() {
        assertThat(lessThan(3.14).matches(Value.of(3.13))).isTrue();
        assertThat(lessThan(3.14).matches(Value.of(3.14))).isFalse();
        assertThat(lessThan(3.14).matches(Value.of(3.15))).isFalse();
    }

    @Test
    void whenLessThanOrEqualTo_thenMatchesEqualOrSmaller() {
        assertThat(lessThanOrEqualTo(10L).matches(Value.of(10))).isTrue();
        assertThat(lessThanOrEqualTo(10L).matches(Value.of(9))).isTrue();
        assertThat(lessThanOrEqualTo(10L).matches(Value.of(11))).isFalse();
    }

    @Test
    void whenBetweenLong_thenMatchesValuesInRange() {
        var matcher = between(5L, 10L);

        assertThat(matcher.matches(Value.of(5))).isTrue();
        assertThat(matcher.matches(Value.of(7))).isTrue();
        assertThat(matcher.matches(Value.of(10))).isTrue();
        assertThat(matcher.matches(Value.of(4))).isFalse();
        assertThat(matcher.matches(Value.of(11))).isFalse();
    }

    @Test
    void whenBetweenDouble_thenMatchesValuesInRange() {
        var matcher = between(0.0, 1.0);

        assertThat(matcher.matches(Value.of(0.0))).isTrue();
        assertThat(matcher.matches(Value.of(0.5))).isTrue();
        assertThat(matcher.matches(Value.of(1.0))).isTrue();
        assertThat(matcher.matches(Value.of(-0.1))).isFalse();
        assertThat(matcher.matches(Value.of(1.1))).isFalse();
    }

    @Test
    void whenCloseTo_thenMatchesWithinDelta() {
        var matcher = closeTo(10.0, 0.1);

        assertThat(matcher.matches(Value.of(10.0))).isTrue();
        assertThat(matcher.matches(Value.of(10.05))).isTrue();
        assertThat(matcher.matches(Value.of(9.95))).isTrue();
        assertThat(matcher.matches(Value.of(10.2))).isFalse();
        assertThat(matcher.matches(Value.of(9.8))).isFalse();
    }

    // ========== Boolean Matchers Tests ==========

    @Test
    void whenBooleanEqualTo_thenMatchesExactValue() {
        assertThat(booleanEqualTo(true).matches(Value.TRUE)).isTrue();
        assertThat(booleanEqualTo(true).matches(Value.FALSE)).isFalse();
        assertThat(booleanEqualTo(false).matches(Value.FALSE)).isTrue();
        assertThat(booleanEqualTo(false).matches(Value.TRUE)).isFalse();
    }

    @Test
    void whenBooleanEqualTo_thenDoesNotMatchNonBooleans() {
        assertThat(booleanEqualTo(true).matches(Value.of("true"))).isFalse();
        assertThat(booleanEqualTo(true).matches(Value.of(1))).isFalse();
    }

    // ========== Object Matchers Tests ==========

    @Test
    void whenObjectContainingKey_thenMatchesObjectsWithKey() {
        var obj = new ObjectValue(Map.of("name", Value.of("Alice"), "age", Value.of(30)), ValueMetadata.EMPTY);

        assertThat(objectContainingKey("name").matches(obj)).isTrue();
        assertThat(objectContainingKey("age").matches(obj)).isTrue();
        assertThat(objectContainingKey("email").matches(obj)).isFalse();
    }

    @Test
    void whenObjectContainingKey_thenDoesNotMatchNonObjects() {
        assertThat(objectContainingKey("key").matches(Value.of("text"))).isFalse();
        assertThat(objectContainingKey("key").matches(Value.of(42))).isFalse();
    }

    @Test
    void whenObjectContainingKeyWithMatcher_thenMatchesKeyAndValue() {
        var obj = new ObjectValue(Map.of("name", Value.of("Alice"), "age", Value.of(30)), ValueMetadata.EMPTY);

        assertThat(objectContainingKey("name", eq(Value.of("Alice"))).matches(obj)).isTrue();
        assertThat(objectContainingKey("name", eq(Value.of("Bob"))).matches(obj)).isFalse();
        assertThat(objectContainingKey("age", greaterThan(25L)).matches(obj)).isTrue();
        assertThat(objectContainingKey("age", greaterThan(35L)).matches(obj)).isFalse();
    }

    @Test
    void whenObjectContainingKeys_thenMatchesObjectsWithAllKeys() {
        var obj = new ObjectValue(Map.of("a", Value.of(1), "b", Value.of(2), "c", Value.of(3)), ValueMetadata.EMPTY);

        assertThat(objectContainingKeys("a", "b").matches(obj)).isTrue();
        assertThat(objectContainingKeys("a", "b", "c").matches(obj)).isTrue();
        assertThat(objectContainingKeys("a", "d").matches(obj)).isFalse();
    }

    @Test
    void whenObjectIsEmpty_thenMatchesEmptyObjects() {
        var emptyObj = new ObjectValue(Map.of(), ValueMetadata.EMPTY);
        var nonEmpty = new ObjectValue(Map.of("key", Value.of("value")), ValueMetadata.EMPTY);

        assertThat(objectIsEmpty().matches(emptyObj)).isTrue();
        assertThat(objectIsEmpty().matches(nonEmpty)).isFalse();
    }

    // ========== Array Matchers Tests ==========

    @Test
    void whenArrayContaining_thenMatchesArraysWithMatchingElement() {
        var arr = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).add(Value.of(3)).build();

        assertThat(arrayContaining(eq(Value.of(2))).matches(arr)).isTrue();
        assertThat(arrayContaining(greaterThan(2L)).matches(arr)).isTrue();
        assertThat(arrayContaining(eq(Value.of(5))).matches(arr)).isFalse();
    }

    @Test
    void whenArrayContaining_thenDoesNotMatchNonArrays() {
        assertThat(arrayContaining(any()).matches(Value.of("text"))).isFalse();
        assertThat(arrayContaining(any()).matches(Value.of(42))).isFalse();
    }

    @Test
    void whenArrayContainingAll_thenRequiresAllMatchersToFindDistinctElements() {
        var arr = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).add(Value.of(3)).build();

        assertThat(arrayContainingAll(eq(Value.of(1)), eq(Value.of(3))).matches(arr)).isTrue();
        assertThat(arrayContainingAll(eq(Value.of(1)), eq(Value.of(1))).matches(arr)).isFalse(); // Only one "1"
        assertThat(arrayContainingAll(eq(Value.of(1)), eq(Value.of(5))).matches(arr)).isFalse();
    }

    @Test
    void whenArrayEveryItem_thenRequiresAllElementsToMatch() {
        var numbers = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).add(Value.of(3)).build();
        var mixed   = ArrayValue.builder().add(Value.of(1)).add(Value.of("two")).build();

        assertThat(arrayEveryItem(anyNumber()).matches(numbers)).isTrue();
        assertThat(arrayEveryItem(anyNumber()).matches(mixed)).isFalse();
        assertThat(arrayEveryItem(greaterThan(0L)).matches(numbers)).isTrue();
    }

    @Test
    void whenArrayContainsExactly_thenMatchesExactOrderAndContent() {
        var arr = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).add(Value.of(3)).build();

        assertThat(arrayContainsExactly(Value.of(1), Value.of(2), Value.of(3)).matches(arr)).isTrue();
        assertThat(arrayContainsExactly(Value.of(3), Value.of(2), Value.of(1)).matches(arr)).isFalse();
        assertThat(arrayContainsExactly(Value.of(1), Value.of(2)).matches(arr)).isFalse();
    }

    @Test
    void whenArrayContainsExactlyInAnyOrder_thenMatchesContentRegardlessOfOrder() {
        var arr = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).add(Value.of(3)).build();

        assertThat(arrayContainsExactlyInAnyOrder(Value.of(1), Value.of(2), Value.of(3)).matches(arr)).isTrue();
        assertThat(arrayContainsExactlyInAnyOrder(Value.of(3), Value.of(1), Value.of(2)).matches(arr)).isTrue();
        assertThat(arrayContainsExactlyInAnyOrder(Value.of(1), Value.of(2)).matches(arr)).isFalse();
        assertThat(arrayContainsExactlyInAnyOrder(Value.of(1), Value.of(2), Value.of(4)).matches(arr)).isFalse();
    }

    @Test
    void whenArrayIsEmpty_thenMatchesEmptyArrays() {
        var emptyArr = ArrayValue.builder().build();
        var nonEmpty = ArrayValue.builder().add(Value.of(1)).build();

        assertThat(arrayIsEmpty().matches(emptyArr)).isTrue();
        assertThat(arrayIsEmpty().matches(nonEmpty)).isFalse();
    }

    @Test
    void whenArrayHasSize_thenMatchesExactSize() {
        var arr = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).add(Value.of(3)).build();

        assertThat(arrayHasSize(3).matches(arr)).isTrue();
        assertThat(arrayHasSize(2).matches(arr)).isFalse();
        assertThat(arrayHasSize(4).matches(arr)).isFalse();
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
