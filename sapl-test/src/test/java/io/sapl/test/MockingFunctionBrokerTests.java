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

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.test.verification.MockVerificationException;
import io.sapl.test.verification.Times;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.test.Matchers.*;
import static io.sapl.test.Matchers.any;
import static io.sapl.test.Matchers.eq;
import static io.sapl.test.verification.Times.*;
import static io.sapl.test.verification.Times.atLeast;
import static io.sapl.test.verification.Times.atMost;
import static io.sapl.test.verification.Times.times;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.never;

class MockingFunctionBrokerTests {

    private static final String FUNCTION_NAME  = "test.function";
    private static final String OTHER_FUNCTION = "other.function";

    private FunctionBroker        delegate;
    private MockingFunctionBroker broker;

    @BeforeEach
    void setUp() {
        delegate = mock(FunctionBroker.class);
        broker   = new MockingFunctionBroker(delegate);
    }

    // ========== Single Return Value Tests ==========

    @Test
    void whenMockWithSingleValue_thenAlwaysReturnsThatValue() {
        var expected = Value.of("mocked");
        broker.mock(FUNCTION_NAME, args(), expected);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(expected);
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(expected);
        verify(delegate, never()).evaluateFunction(ArgumentMatchers.any());
    }

    @Test
    void whenMockZeroArgs_thenMatchesZeroArgInvocation() {
        broker.mock(FUNCTION_NAME, args(), Value.of("zero"));

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(Value.of("zero"));
    }

    @Test
    void whenMockZeroArgs_thenDoesNotMatchOneArgInvocation() {
        var delegateResult = Value.of("delegate");
        broker.mock(FUNCTION_NAME, args(), Value.of("zero"));
        when(delegate.evaluateFunction(ArgumentMatchers.any())).thenReturn(delegateResult);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("arg")))).isEqualTo(delegateResult);
    }

    @Test
    void whenMockOneArg_thenMatchesOneArgInvocation() {
        broker.mock(FUNCTION_NAME, args(any()), Value.of("one"));

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("anything")))).isEqualTo(Value.of("one"));
    }

    @Test
    void whenMockTwoArgs_thenMatchesTwoArgInvocation() {
        broker.mock(FUNCTION_NAME, args(any(), any()), Value.of("two"));

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("a"), Value.of("b"))))
                .isEqualTo(Value.of("two"));
    }

    // ========== Sequence Tests ==========

    @Test
    void whenMockWithMultipleValues_thenReturnsInSequence() {
        var first  = Value.of("first");
        var second = Value.of("second");
        var third  = Value.of("third");
        broker.mock(FUNCTION_NAME, args(), first, second, third);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(first);
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(second);
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(third);
    }

    @Test
    void whenSequenceExhausted_thenSticksOnLastValue() {
        var first = Value.of("first");
        var last  = Value.of("last");
        broker.mock(FUNCTION_NAME, args(), first, last);

        broker.evaluateFunction(invocation(FUNCTION_NAME)); // first
        broker.evaluateFunction(invocation(FUNCTION_NAME)); // last

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(last);
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(last);
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(last);
    }

    @Test
    void whenSequenceWithOneArg_thenArityMustMatch() {
        broker.mock(FUNCTION_NAME, args(any()), Value.of(1), Value.of(2), Value.of(3));

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("a")))).isEqualTo(Value.of(1));
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("b")))).isEqualTo(Value.of(2));
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("c")))).isEqualTo(Value.of(3));
    }

    @Test
    void whenMockWithNoReturnValues_thenThrowsException() {
        var argsParam = args();
        assertThatThrownBy(() -> broker.mock(FUNCTION_NAME, argsParam)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one return value");
    }

    // ========== Argument Matching Tests ==========

    @Test
    void whenExactMatch_thenReturnsValueOnMatch() {
        var argument = Value.of("specific");
        var expected = Value.of("result");
        broker.mock(FUNCTION_NAME, args(eq(argument)), expected);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, argument))).isEqualTo(expected);
    }

    @Test
    void whenExactMatch_thenDelegatesOnMismatch() {
        var argument       = Value.of("specific");
        var delegateResult = Value.of("delegate");
        broker.mock(FUNCTION_NAME, args(eq(argument)), Value.of("mocked"));
        when(delegate.evaluateFunction(ArgumentMatchers.any())).thenReturn(delegateResult);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("different")))).isEqualTo(delegateResult);
    }

    @Test
    void whenPredicateMatch_thenMatchesPredicateSatisfyingValues() {
        var expected = Value.of("number result");
        broker.mock(FUNCTION_NAME, args(anyNumber()), expected);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of(42)))).isEqualTo(expected);
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of(0)))).isEqualTo(expected);
    }

    @Test
    void whenPredicateMatch_thenDelegatesOnPredicateFailure() {
        var delegateResult = Value.of("delegate");
        broker.mock(FUNCTION_NAME, args(anyNumber()), Value.of("number result"));
        when(delegate.evaluateFunction(ArgumentMatchers.any())).thenReturn(delegateResult);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("text")))).isEqualTo(delegateResult);
    }

    @Test
    void whenMultipleMatchers_thenAllMustMatch() {
        var expected = Value.of("result");
        broker.mock(FUNCTION_NAME, args(eq(Value.of("a")), eq(Value.of("b"))), expected);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("a"), Value.of("b"))))
                .isEqualTo(expected);
    }

    @Test
    void whenMultipleMatchers_thenDelegatesOnPartialMatch() {
        var delegateResult = Value.of("delegate");
        broker.mock(FUNCTION_NAME, args(eq(Value.of("a")), eq(Value.of("b"))), Value.of("result"));
        when(delegate.evaluateFunction(ArgumentMatchers.any())).thenReturn(delegateResult);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("a"), Value.of("wrong"))))
                .isEqualTo(delegateResult);
    }

    // ========== Arity Mismatch Tests ==========

    @ParameterizedTest
    @MethodSource("arityMismatchCases")
    void whenArityMismatch_thenDelegates(int mockArity, int invocationArity) {
        var delegateResult = Value.of("delegate");
        var matchers       = new MockingFunctionBroker.ArgumentMatcher[mockArity];
        for (int i = 0; i < mockArity; i++) {
            matchers[i] = any();
        }
        broker.mock(FUNCTION_NAME, args(matchers), Value.of("mocked"));
        when(delegate.evaluateFunction(ArgumentMatchers.any())).thenReturn(delegateResult);

        var arguments = new Value[invocationArity];
        for (int i = 0; i < invocationArity; i++) {
            arguments[i] = Value.of(i);
        }

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, arguments))).isEqualTo(delegateResult);
    }

    static Stream<Arguments> arityMismatchCases() {
        return Stream.of(arguments(0, 1), arguments(0, 2), arguments(1, 0), arguments(1, 2), arguments(2, 0),
                arguments(2, 1), arguments(2, 3));
    }

    // ========== Specificity Tests ==========

    @Test
    void whenMultipleMocksMatch_thenMostSpecificWins() {
        var anyResult   = Value.of("any");
        var exactResult = Value.of("exact");
        var specificArg = Value.of("specific");

        broker.mock(FUNCTION_NAME, args(any()), anyResult);
        broker.mock(FUNCTION_NAME, args(eq(specificArg)), exactResult);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, specificArg))).isEqualTo(exactResult);
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("other")))).isEqualTo(anyResult);
    }

    @Test
    void whenMultipleMocksMatch_thenRegistrationOrderDoesNotMatter() {
        var anyResult   = Value.of("any");
        var exactResult = Value.of("exact");
        var specificArg = Value.of("specific");

        // Register exact BEFORE any - should still work
        broker.mock(FUNCTION_NAME, args(eq(specificArg)), exactResult);
        broker.mock(FUNCTION_NAME, args(any()), anyResult);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, specificArg))).isEqualTo(exactResult);
    }

    @Test
    void whenExactAndPredicateBothMatch_thenExactWins() {
        var predicateResult = Value.of("predicate");
        var exactResult     = Value.of("exact");
        var argument        = Value.of("text");

        broker.mock(FUNCTION_NAME, args(anyText()), predicateResult);
        broker.mock(FUNCTION_NAME, args(eq(argument)), exactResult);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, argument))).isEqualTo(exactResult);
    }

    @Test
    void whenPredicateAndAnyBothMatch_thenPredicateWins() {
        var anyResult       = Value.of("any");
        var predicateResult = Value.of("predicate");

        broker.mock(FUNCTION_NAME, args(any()), anyResult);
        broker.mock(FUNCTION_NAME, args(anyText()), predicateResult);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("text")))).isEqualTo(predicateResult);
    }

    @Test
    void whenMultipleArgumentsWithMixedSpecificity_thenTotalSpecificityDeterminesWinner() {
        var lowestResult  = Value.of("lowest");   // (any, any) = 0+0 = 0
        var mediumResult  = Value.of("medium");   // (exact, any) = 2+0 = 2
        var highestResult = Value.of("highest"); // (exact, exact) = 2+2 = 4

        var argA = Value.of("A");
        var argB = Value.of("B");

        broker.mock(FUNCTION_NAME, args(any(), any()), lowestResult);
        broker.mock(FUNCTION_NAME, args(eq(argA), any()), mediumResult);
        broker.mock(FUNCTION_NAME, args(eq(argA), eq(argB)), highestResult);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, argA, argB))).isEqualTo(highestResult);
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, argA, Value.of("X")))).isEqualTo(mediumResult);
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("X"), Value.of("Y"))))
                .isEqualTo(lowestResult);
    }

    // ========== Different Arities Same Function Tests ==========

    @Test
    void whenSameFunctionDifferentArities_thenEachArityIndependent() {
        broker.mock(FUNCTION_NAME, args(), Value.of("zero"));
        broker.mock(FUNCTION_NAME, args(any()), Value.of("one"));
        broker.mock(FUNCTION_NAME, args(any(), any()), Value.of("two"));

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(Value.of("zero"));
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("a")))).isEqualTo(Value.of("one"));
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("a"), Value.of("b"))))
                .isEqualTo(Value.of("two"));
    }

    @Test
    void whenSameFunctionDifferentArities_thenSequencesAreIndependent() {
        broker.mock(FUNCTION_NAME, args(), Value.of("z1"), Value.of("z2"));
        broker.mock(FUNCTION_NAME, args(any()), Value.of("o1"), Value.of("o2"));

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(Value.of("z1"));
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("x")))).isEqualTo(Value.of("o1"));
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(Value.of("z2"));
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("y")))).isEqualTo(Value.of("o2"));
    }

    // ========== Delegation Tests ==========

    @Test
    void whenNoMockRegistered_thenDelegatesToUnderlyingBroker() {
        var delegateResult = Value.of("delegate");
        when(delegate.evaluateFunction(ArgumentMatchers.any())).thenReturn(delegateResult);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("arg")))).isEqualTo(delegateResult);
        verify(delegate).evaluateFunction(ArgumentMatchers.any());
    }

    @Test
    void whenMockForDifferentFunction_thenDelegates() {
        var delegateResult = Value.of("delegate");
        broker.mock(OTHER_FUNCTION, args(), Value.of("other"));
        when(delegate.evaluateFunction(ArgumentMatchers.any())).thenReturn(delegateResult);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(delegateResult);
    }

    @Test
    void whenGetRegisteredLibraries_thenDelegatesToUnderlyingBroker() {
        var libraries = List.<Class<?>>of(String.class, Integer.class);
        when(delegate.getRegisteredLibraries()).thenReturn(libraries);

        assertThat(broker.getRegisteredLibraries()).isEqualTo(libraries);
    }

    // ========== Utility Method Tests ==========

    @Test
    void whenHasMockAfterRegistration_thenReturnsTrue() {
        broker.mock(FUNCTION_NAME, args(), Value.of("value"));

        assertThat(broker.hasMock(FUNCTION_NAME)).isTrue();
    }

    @Test
    void whenHasMockWithoutRegistration_thenReturnsFalse() {
        assertThat(broker.hasMock(FUNCTION_NAME)).isFalse();
    }

    @Test
    void whenClearAllMocks_thenRemovesAllMocks() {
        broker.mock(FUNCTION_NAME, args(), Value.of("value1"));
        broker.mock(OTHER_FUNCTION, args(), Value.of("value2"));

        broker.clearAllMocks();

        assertThat(broker.hasMock(FUNCTION_NAME)).isFalse();
        assertThat(broker.hasMock(OTHER_FUNCTION)).isFalse();
    }

    @Test
    void whenClearAllMocks_thenSubsequentCallsDelegateToUnderlyingBroker() {
        var delegateResult = Value.of("delegate");
        broker.mock(FUNCTION_NAME, args(), Value.of("mocked"));
        when(delegate.evaluateFunction(ArgumentMatchers.any())).thenReturn(delegateResult);

        broker.clearAllMocks();

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(delegateResult);
    }

    // ========== Invalid Parameters Tests ==========

    @Test
    void whenMockWithInvalidParameters_thenThrowsException() {
        var invalidParameters = new SaplTestFixture.Parameters() {};
        var value             = Value.of("value");

        assertThatThrownBy(() -> broker.mock(FUNCTION_NAME, invalidParameters, value))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("args()");
    }

    // ========== ArgumentMatcher Tests ==========

    @Test
    void whenAnyMatcher_thenMatchesAllValues() {
        var matcher = any();

        assertThat(matcher.matches(Value.of("text"))).isTrue();
        assertThat(matcher.matches(Value.of(42))).isTrue();
        assertThat(matcher.matches(Value.TRUE)).isTrue();
        assertThat(matcher.matches(Value.NULL)).isTrue();
    }

    @Test
    void whenExactMatcher_thenMatchesOnlyEqualValues() {
        var expected = Value.of("expected");
        var matcher  = eq(expected);

        assertThat(matcher.matches(Value.of("expected"))).isTrue();
        assertThat(matcher.matches(Value.of("different"))).isFalse();
        assertThat(matcher.matches(Value.of(42))).isFalse();
    }

    @Test
    void whenPredicateMatcher_thenMatchesPredicateSatisfyingValues() {
        var matcher = matching(v -> v instanceof TextValue text && text.value().startsWith("prefix"));

        assertThat(matcher.matches(Value.of("prefix-value"))).isTrue();
        assertThat(matcher.matches(Value.of("prefixed"))).isTrue();
        assertThat(matcher.matches(Value.of("no-prefix"))).isFalse();
        assertThat(matcher.matches(Value.of(42))).isFalse();
    }

    @Test
    void whenMatchersHaveCorrectSpecificity() {
        assertThat(any().specificity()).isZero();
        assertThat(eq(Value.of("x")).specificity()).isEqualTo(2);
        assertThat(matching(v -> true).specificity()).isEqualTo(1);
        assertThat(anyText().specificity()).isEqualTo(1);
        assertThat(anyNumber().specificity()).isEqualTo(1);
    }

    // ========== Complex Scenarios ==========

    @Test
    void whenMockingTimeFunction_thenWorksWithRealisticScenario() {
        var monday  = Value.of("2025-01-06T10:00:00Z");
        var tuesday = Value.of("2025-01-07T10:00:00Z");

        broker.mock("time.dayOfWeek", args(eq(monday)), Value.of("MONDAY"));
        broker.mock("time.dayOfWeek", args(eq(tuesday)), Value.of("TUESDAY"));
        broker.mock("time.dayOfWeek", args(any()), Value.of("UNKNOWN"));

        assertThat(broker.evaluateFunction(invocation("time.dayOfWeek", monday))).isEqualTo(Value.of("MONDAY"));
        assertThat(broker.evaluateFunction(invocation("time.dayOfWeek", tuesday))).isEqualTo(Value.of("TUESDAY"));
        assertThat(broker.evaluateFunction(invocation("time.dayOfWeek", Value.of("2025-01-08"))))
                .isEqualTo(Value.of("UNKNOWN"));
    }

    @Test
    void whenMockingWithMultipleFunctions_thenEachFunctionHasIndependentMocks() {
        broker.mock("fn.alpha", args(), Value.of("alpha"));
        broker.mock("fn.beta", args(), Value.of("beta"));
        broker.mock("fn.counter", args(), Value.of(1), Value.of(2));

        assertThat(broker.evaluateFunction(invocation("fn.alpha"))).isEqualTo(Value.of("alpha"));
        assertThat(broker.evaluateFunction(invocation("fn.beta"))).isEqualTo(Value.of("beta"));
        assertThat(broker.evaluateFunction(invocation("fn.counter"))).isEqualTo(Value.of(1));
        assertThat(broker.evaluateFunction(invocation("fn.counter"))).isEqualTo(Value.of(2));
        assertThat(broker.evaluateFunction(invocation("fn.alpha"))).isEqualTo(Value.of("alpha"));
    }

    // ========== Undefined Value Tests ==========

    @Test
    void whenMockWithUndefinedValue_thenReturnsUndefined() {
        broker.mock(FUNCTION_NAME, args(), Value.UNDEFINED);

        var result = broker.evaluateFunction(invocation(FUNCTION_NAME));

        assertThat(result).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void whenMockWithUndefinedValueAndArgs_thenReturnsUndefined() {
        broker.mock(FUNCTION_NAME, args(any()), Value.UNDEFINED);

        var result = broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("test")));

        assertThat(result).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void whenMockWithSequenceIncludingUndefined_thenReturnsSequenceCorrectly() {
        var first     = Value.of("defined");
        var undefined = Value.UNDEFINED;
        var last      = Value.of("also defined");
        broker.mock(FUNCTION_NAME, args(), first, undefined, last);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(first);
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(Value.UNDEFINED);
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(last);
    }

    @Test
    void whenMockWithUndefinedAndExactMatcher_thenReturnsUndefinedOnMatch() {
        var specificArg  = Value.of("missing_field");
        var successValue = Value.of("found");

        broker.mock(FUNCTION_NAME, args(eq(specificArg)), Value.UNDEFINED);
        broker.mock(FUNCTION_NAME, args(any()), successValue);

        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, specificArg))).isEqualTo(Value.UNDEFINED);
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("valid_field")))).isEqualTo(successValue);
    }

    // ========== Invocation Recording Tests ==========

    @Test
    void whenFunctionInvoked_thenRecordsInvocation() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));

        assertThat(broker.getInvocations()).hasSize(1);
        assertThat(broker.getInvocations().getFirst().functionName()).isEqualTo(FUNCTION_NAME);
    }

    @Test
    void whenMultipleInvocations_thenRecordsAll() {
        broker.mock(FUNCTION_NAME, args(any()), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("a")));
        broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("b")));
        broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("c")));

        assertThat(broker.getInvocations()).hasSize(3);
    }

    @Test
    void whenInvocationRecorded_thenContainsArguments() {
        broker.mock(FUNCTION_NAME, args(any(), any()), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("arg1"), Value.of("arg2")));

        var recorded = broker.getInvocations().getFirst();
        assertThat(recorded.arguments()).containsExactly(Value.of("arg1"), Value.of("arg2"));
    }

    @Test
    void whenInvocationRecorded_thenHasSequenceNumber() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));
        broker.mock(OTHER_FUNCTION, args(), Value.of("other"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));
        broker.evaluateFunction(invocation(OTHER_FUNCTION));
        broker.evaluateFunction(invocation(FUNCTION_NAME));

        var invocations = broker.getInvocations();
        assertThat(invocations.get(0).sequenceNumber()).isZero();
        assertThat(invocations.get(1).sequenceNumber()).isEqualTo(1);
        assertThat(invocations.get(2).sequenceNumber()).isEqualTo(2);
    }

    @Test
    void whenGetInvocationsForFunction_thenFiltersCorrectly() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));
        broker.mock(OTHER_FUNCTION, args(), Value.of("other"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));
        broker.evaluateFunction(invocation(OTHER_FUNCTION));
        broker.evaluateFunction(invocation(FUNCTION_NAME));

        assertThat(broker.getInvocations(FUNCTION_NAME)).hasSize(2);
        assertThat(broker.getInvocations(OTHER_FUNCTION)).hasSize(1);
    }

    @Test
    void whenClearInvocations_thenKeepsMocksButClearsRecords() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));
        assertThat(broker.getInvocations()).hasSize(1);

        broker.clearInvocations();

        assertThat(broker.getInvocations()).isEmpty();
        assertThat(broker.hasMock(FUNCTION_NAME)).isTrue();
        assertThat(broker.evaluateFunction(invocation(FUNCTION_NAME))).isEqualTo(Value.of("result"));
    }

    @Test
    void whenClearAllMocks_thenClearsInvocationsToo() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));

        broker.clearAllMocks();

        assertThat(broker.getInvocations()).isEmpty();
    }

    // ========== Verification Tests ==========

    @Test
    void whenVerifyOnce_thenPassesIfCalledOnce() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));

        broker.verify(FUNCTION_NAME, args(), once());
    }

    @Test
    void whenVerifyOnce_thenFailsIfNeverCalled() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));
        var argsParam  = args();
        var onceVerify = once();

        assertThatThrownBy(() -> broker.verify(FUNCTION_NAME, argsParam, onceVerify))
                .isInstanceOf(MockVerificationException.class).hasMessageContaining("exactly once")
                .hasMessageContaining("invoked 0 time(s)");
    }

    @Test
    void whenVerifyOnce_thenFailsIfCalledMultipleTimes() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));
        broker.evaluateFunction(invocation(FUNCTION_NAME));
        var argsParam  = args();
        var onceVerify = once();

        assertThatThrownBy(() -> broker.verify(FUNCTION_NAME, argsParam, onceVerify))
                .isInstanceOf(MockVerificationException.class).hasMessageContaining("exactly once")
                .hasMessageContaining("invoked 2 time(s)");
    }

    @Test
    void whenVerifyNever_thenPassesIfNeverCalled() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));

        broker.verify(FUNCTION_NAME, args(), Times.never());
    }

    @Test
    void whenVerifyNever_thenFailsIfCalled() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));
        var argsParam   = args();
        var neverVerify = Times.never();

        assertThatThrownBy(() -> broker.verify(FUNCTION_NAME, argsParam, neverVerify))
                .isInstanceOf(MockVerificationException.class).hasMessageContaining("never")
                .hasMessageContaining("invoked 1 time(s)");
    }

    @Test
    void whenVerifyTimes_thenPassesIfCountMatches() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));
        broker.evaluateFunction(invocation(FUNCTION_NAME));
        broker.evaluateFunction(invocation(FUNCTION_NAME));

        broker.verify(FUNCTION_NAME, args(), times(3));
    }

    @Test
    void whenVerifyAtLeast_thenPassesIfCountSufficient() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));
        broker.evaluateFunction(invocation(FUNCTION_NAME));
        broker.evaluateFunction(invocation(FUNCTION_NAME));

        broker.verify(FUNCTION_NAME, args(), atLeast(2));
        broker.verify(FUNCTION_NAME, args(), atLeast(3));
    }

    @Test
    void whenVerifyAtLeast_thenFailsIfCountInsufficient() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));
        var argsParam     = args();
        var atLeastVerify = atLeast(2);

        assertThatThrownBy(() -> broker.verify(FUNCTION_NAME, argsParam, atLeastVerify))
                .isInstanceOf(MockVerificationException.class);
    }

    @Test
    void whenVerifyAtMost_thenPassesIfCountWithinLimit() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));
        broker.evaluateFunction(invocation(FUNCTION_NAME));

        broker.verify(FUNCTION_NAME, args(), atMost(3));
        broker.verify(FUNCTION_NAME, args(), atMost(2));
    }

    @Test
    void whenVerifyAtMost_thenFailsIfCountExceedsLimit() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));
        broker.evaluateFunction(invocation(FUNCTION_NAME));
        broker.evaluateFunction(invocation(FUNCTION_NAME));
        var argsParam    = args();
        var atMostVerify = atMost(2);

        assertThatThrownBy(() -> broker.verify(FUNCTION_NAME, argsParam, atMostVerify))
                .isInstanceOf(MockVerificationException.class);
    }

    @Test
    void whenVerifyWithArgumentMatchers_thenMatchesCorrectly() {
        broker.mock(FUNCTION_NAME, args(any()), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("a")));
        broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("b")));
        broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("a")));

        broker.verify(FUNCTION_NAME, args(eq(Value.of("a"))), times(2));
        broker.verify(FUNCTION_NAME, args(eq(Value.of("b"))), once());
        broker.verify(FUNCTION_NAME, args(any()), times(3));
    }

    @Test
    void whenVerifyWithDifferentArities_thenMatchesCorrectly() {
        broker.mock(FUNCTION_NAME, args(), Value.of("zero"));
        broker.mock(FUNCTION_NAME, args(any()), Value.of("one"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));
        broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("x")));
        broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("y")));

        broker.verify(FUNCTION_NAME, args(), once());
        broker.verify(FUNCTION_NAME, args(any()), times(2));
    }

    @Test
    void whenVerifyCalled_thenIsConvenienceForAtLeastOnce() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME));
        broker.evaluateFunction(invocation(FUNCTION_NAME));

        broker.verifyCalled(FUNCTION_NAME, args());
    }

    @Test
    void whenVerifyNeverCalled_thenIsConvenienceForNever() {
        broker.mock(FUNCTION_NAME, args(), Value.of("result"));

        broker.verifyNeverCalled(FUNCTION_NAME, args());
    }

    @Test
    void whenVerificationFails_thenMessageShowsRecordedInvocations() {
        broker.mock(FUNCTION_NAME, args(any()), Value.of("result"));

        broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("arg1")));
        broker.evaluateFunction(invocation(FUNCTION_NAME, Value.of("arg2")));
        var otherValue = Value.of("other");
        var argsParam  = args(eq(otherValue));
        var onceVerify = once();

        assertThatThrownBy(() -> broker.verify(FUNCTION_NAME, argsParam, onceVerify))
                .isInstanceOf(MockVerificationException.class).hasMessageContaining("Recorded invocations")
                .hasMessageContaining(FUNCTION_NAME);
    }

    @Test
    void whenVerificationFailsForUnknownFunction_thenMessageIndicatesNoInvocations() {
        var argsParam  = args();
        var onceVerify = once();
        assertThatThrownBy(() -> broker.verify("unknown.function", argsParam, onceVerify))
                .isInstanceOf(MockVerificationException.class)
                .hasMessageContaining("No invocations of 'unknown.function' were recorded");
    }

    @Test
    void whenVerifyWithInvalidParameters_thenThrows() {
        var invalidParams = new SaplTestFixture.Parameters() {};
        var onceVerify    = once();

        assertThatThrownBy(() -> broker.verify(FUNCTION_NAME, invalidParams, onceVerify))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("args()");
    }

    // ========== Helper Methods ==========

    private static FunctionInvocation invocation(String functionName, Value... arguments) {
        return new FunctionInvocation(functionName, List.of(arguments));
    }
}
