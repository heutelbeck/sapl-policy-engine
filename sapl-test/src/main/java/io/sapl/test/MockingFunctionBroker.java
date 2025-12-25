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
import io.sapl.api.model.Value;
import io.sapl.test.verification.FunctionInvocationRecord;
import io.sapl.test.verification.MockVerificationError;
import io.sapl.test.verification.Times;
import lombok.NonNull;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A FunctionBroker decorator that intercepts function calls and returns mocked
 * values when configured.
 * <p>
 * Implements a spy pattern: unmocked functions delegate to the underlying
 * broker, while mocked functions return test-defined values.
 * <p>
 * Mock matching uses most-specific-first semantics:
 * <ol>
 * <li>Exact value matchers have highest priority (specificity: 2)</li>
 * <li>Predicate matchers have medium priority (specificity: 1)</li>
 * <li>Any matchers have lowest priority (specificity: 0)</li>
 * </ol>
 * <p>
 * Argument count must match exactly - a mock with 2 argument matchers will
 * never match an invocation with 1 or 3 arguments.
 */
public final class MockingFunctionBroker implements FunctionBroker {

    private FunctionBroker                        delegate;
    private final Map<String, List<FunctionMock>> mocks           = new HashMap<>();
    private final List<FunctionInvocationRecord>  invocations     = new CopyOnWriteArrayList<>();
    private final AtomicLong                      sequenceCounter = new AtomicLong(0);

    /**
     * Creates a mocking broker with no delegate.
     * A delegate must be set via {@link #setDelegate(FunctionBroker)} before
     * unmocked functions can be evaluated.
     */
    public MockingFunctionBroker() {
        this.delegate = null;
    }

    /**
     * Creates a mocking broker wrapping the given delegate.
     *
     * @param delegate the broker to delegate unmocked calls to
     * @throws NullPointerException if delegate is null
     */
    public MockingFunctionBroker(@NonNull FunctionBroker delegate) {
        this.delegate = delegate;
    }

    /**
     * Sets or replaces the delegate broker for unmocked function calls.
     *
     * @param delegate the broker to delegate unmocked calls to
     * @throws NullPointerException if delegate is null
     */
    public void setDelegate(@NonNull FunctionBroker delegate) {
        this.delegate = delegate;
    }

    @Override
    public Value evaluateFunction(FunctionInvocation invocation) {
        recordInvocation(invocation);
        return findMostSpecificMatch(invocation).map(FunctionMock::getReturnValue).orElseGet(() -> {
            if (delegate == null) {
                throw new IllegalStateException("No mock matched function '%s' and no delegate broker configured."
                        .formatted(invocation.functionName()));
            }
            return delegate.evaluateFunction(invocation);
        });
    }

    private void recordInvocation(FunctionInvocation invocation) {
        var invocationRecord = new FunctionInvocationRecord(invocation.functionName(), invocation.arguments(),
                sequenceCounter.getAndIncrement());
        invocations.add(invocationRecord);
    }

    @Override
    public List<Class<?>> getRegisteredLibraries() {
        if (delegate == null) {
            return List.of();
        }
        return delegate.getRegisteredLibraries();
    }

    /**
     * Registers a mock for a function with specified argument matchers and return
     * values.
     * <p>
     * This is the unified mocking method that covers all use cases:
     * <ul>
     * <li>Single return value: always returns that value</li>
     * <li>Multiple return values: returns them in sequence, sticking on the
     * last</li>
     * </ul>
     * <p>
     * Arity is determined by the number of matchers in the arguments parameter.
     * Use {@code args()} for zero-argument functions, {@code args(any())} for
     * single-argument functions that accept any value, etc.
     * <p>
     * Examples:
     *
     * <pre>{@code
     * // Zero-argument function returning fixed value
     * mock("time.now", args(), Value.of("2025-01-06T10:00:00Z"));
     *
     * // Single-argument function accepting any value
     * mock("time.dayOfWeek", args(any()), Value.of("MONDAY"));
     *
     * // Single-argument with exact match
     * mock("time.dayOfWeek", args(eq(Value.of("2025-01-06"))), Value.of("MONDAY"));
     *
     * // Sequence of return values (sticks on last)
     * mock("counter.next", args(), Value.of(1), Value.of(2), Value.of(3));
     *
     * // Two-argument function
     * mock("math.add", args(any(), any()), Value.of(42));
     * }</pre>
     *
     * @param functionName fully qualified name (e.g., "time.dayOfWeek")
     * @param arguments argument matchers defining arity and matching rules
     * @param returnValues one or more values to return (sequence if multiple)
     * @throws NullPointerException if functionName, arguments, or any return value
     * is null
     * @throws IllegalArgumentException if returnValues is empty or arguments is not
     * an ArgumentMatchers instance
     */
    public void mock(@NonNull String functionName, @NonNull SaplTestFixture.Parameters arguments,
            Value... returnValues) {
        if (returnValues.length == 0) {
            throw new IllegalArgumentException("At least one return value must be specified.");
        }
        for (int i = 0; i < returnValues.length; i++) {
            Objects.requireNonNull(returnValues[i], "Return value at index %d must not be null.".formatted(i));
        }
        if (!(arguments instanceof ArgumentMatchers(List<ArgumentMatcher> matchers))) {
            throw new IllegalArgumentException("Arguments must be created via args().");
        }

        Supplier<Value> valueSupplier;
        if (returnValues.length == 1) {
            var singleValue = returnValues[0];
            valueSupplier = () -> singleValue;
        } else {
            var index = new AtomicInteger(0);
            valueSupplier = () -> {
                int currentIndex = index.getAndUpdate(i -> Math.min(i + 1, returnValues.length - 1));
                return returnValues[currentIndex];
            };
        }

        addMock(functionName, new FunctionMock(matchers, valueSupplier));
    }

    /**
     * Checks if any mock is registered for the given function.
     *
     * @param functionName fully qualified name
     * @return true if at least one mock exists
     */
    public boolean hasMock(String functionName) {
        return mocks.containsKey(functionName) && !mocks.get(functionName).isEmpty();
    }

    /**
     * Clears all registered mocks and recorded invocations.
     */
    public void clearAllMocks() {
        mocks.clear();
        clearInvocations();
    }

    /**
     * Clears recorded invocations without clearing mocks.
     * <p>
     * Useful when you want to verify invocations for a specific phase of testing
     * while keeping mocks in place.
     */
    public void clearInvocations() {
        invocations.clear();
        sequenceCounter.set(0);
    }

    /**
     * Returns all recorded invocations.
     *
     * @return unmodifiable list of invocation records
     */
    public List<FunctionInvocationRecord> getInvocations() {
        return List.copyOf(invocations);
    }

    /**
     * Returns recorded invocations for a specific function.
     *
     * @param functionName fully qualified function name
     * @return unmodifiable list of invocation records for this function
     */
    public List<FunctionInvocationRecord> getInvocations(String functionName) {
        return invocations.stream().filter(r -> r.functionName().equals(functionName)).toList();
    }

    /**
     * Verifies that a function was invoked the expected number of times with
     * arguments matching the given matchers.
     * <p>
     * Example:
     *
     * <pre>{@code
     * broker.verify("time.dayOfWeek", args(any()), Times.once());
     * broker.verify("counter.next", args(), Times.times(3));
     * broker.verify("unused.function", args(), Times.never());
     * }</pre>
     *
     * @param functionName fully qualified function name
     * @param arguments argument matchers (use args() for arity matching)
     * @param times expected invocation count
     * @throws MockVerificationError if verification fails
     * @throws IllegalArgumentException if arguments is not an ArgumentMatchers
     * instance
     */
    public void verify(@NonNull String functionName, @NonNull SaplTestFixture.Parameters arguments,
            @NonNull Times times) {
        if (!(arguments instanceof ArgumentMatchers(List<ArgumentMatcher> matchers))) {
            throw new IllegalArgumentException("Arguments must be created via args().");
        }

        var matchingCount = countMatchingInvocations(functionName, matchers);
        if (!times.verify(matchingCount)) {
            throw new MockVerificationError(buildVerificationMessage(functionName, matchers, times, matchingCount));
        }
    }

    /**
     * Verifies that a function was invoked at least once with arguments matching
     * the given matchers. Equivalent to
     * {@code verify(functionName, arguments, Times.atLeast(1))}.
     *
     * @param functionName fully qualified function name
     * @param arguments argument matchers
     * @throws MockVerificationError if function was never invoked with matching
     * arguments
     */
    public void verifyCalled(@NonNull String functionName, @NonNull SaplTestFixture.Parameters arguments) {
        verify(functionName, arguments, Times.atLeast(1));
    }

    /**
     * Verifies that a function was never invoked with arguments matching the given
     * matchers.
     * Equivalent to {@code verify(functionName, arguments, Times.never())}.
     *
     * @param functionName fully qualified function name
     * @param arguments argument matchers
     * @throws MockVerificationError if function was invoked with matching
     * arguments
     */
    public void verifyNeverCalled(@NonNull String functionName, @NonNull SaplTestFixture.Parameters arguments) {
        verify(functionName, arguments, Times.never());
    }

    private int countMatchingInvocations(String functionName, List<ArgumentMatcher> matchers) {
        return (int) invocations.stream().filter(r -> r.functionName().equals(functionName))
                .filter(r -> matchesArguments(r.arguments(), matchers)).count();
    }

    private boolean matchesArguments(List<Value> arguments, List<ArgumentMatcher> matchers) {
        if (arguments.size() != matchers.size()) {
            return false;
        }
        for (int i = 0; i < arguments.size(); i++) {
            if (!matchers.get(i).matches(arguments.get(i))) {
                return false;
            }
        }
        return true;
    }

    private String buildVerificationMessage(String functionName, List<ArgumentMatcher> matchers, Times times,
            int actualCount) {
        var sb = new StringBuilder();
        sb.append("Function verification failed for '%s'.%n".formatted(functionName));
        sb.append(times.failureMessage(actualCount));

        var functionInvocations = getInvocations(functionName);
        if (functionInvocations.isEmpty()) {
            sb.append("%nNo invocations of '%s' were recorded.".formatted(functionName));
        } else {
            sb.append("%nRecorded invocations of '%s':".formatted(functionName));
            for (var inv : functionInvocations) {
                sb.append("\n  - ").append(inv);
            }
        }
        return sb.toString();
    }

    private void addMock(String functionName, FunctionMock mock) {
        mocks.computeIfAbsent(functionName, k -> new ArrayList<>()).add(mock);
    }

    private Optional<FunctionMock> findMostSpecificMatch(FunctionInvocation invocation) {
        var functionMocks = mocks.get(invocation.functionName());
        if (functionMocks == null || functionMocks.isEmpty()) {
            return Optional.empty();
        }

        return functionMocks.stream().filter(mock -> mock.matches(invocation.arguments()))
                .max(Comparator.comparingInt(FunctionMock::specificity));
    }

    /**
     * A registered function mock with argument matchers and return value.
     */
    private record FunctionMock(List<ArgumentMatcher> argumentMatchers, Supplier<Value> returnValueSupplier) {

        Value getReturnValue() {
            return returnValueSupplier.get();
        }

        boolean matches(List<Value> arguments) {
            if (arguments.size() != argumentMatchers.size()) {
                return false;
            }
            for (int i = 0; i < arguments.size(); i++) {
                if (!argumentMatchers.get(i).matches(arguments.get(i))) {
                    return false;
                }
            }
            return true;
        }

        int specificity() {
            return argumentMatchers.stream().mapToInt(ArgumentMatcher::specificity).sum();
        }
    }

    /**
     * Matcher for a single argument value.
     */
    public sealed interface ArgumentMatcher {

        boolean matches(Value value);

        int specificity();

        /**
         * Matches any value.
         */
        record Any() implements ArgumentMatcher {
            @Override
            public boolean matches(Value value) {
                return true;
            }

            @Override
            public int specificity() {
                return 0;
            }
        }

        /**
         * Matches a specific exact value using Value.equals().
         */
        record Exact(Value expected) implements ArgumentMatcher {
            @Override
            public boolean matches(Value value) {
                return expected.equals(value);
            }

            @Override
            public int specificity() {
                return 2;
            }
        }

        /**
         * Matches using a custom predicate.
         */
        record Predicated(Predicate<Value> predicate) implements ArgumentMatcher {
            @Override
            public boolean matches(Value value) {
                return predicate.test(value);
            }

            @Override
            public int specificity() {
                return 1;
            }
        }
    }

    /**
     * Container for argument matchers, implementing the Parameters interface.
     */
    public record ArgumentMatchers(List<ArgumentMatcher> matchers) implements SaplTestFixture.Parameters {

        public ArgumentMatchers {
            matchers = List.copyOf(matchers);
        }

        /**
         * Creates argument matchers from a list of matchers.
         */
        public static ArgumentMatchers of(ArgumentMatcher... matchers) {
            return new ArgumentMatchers(List.of(matchers));
        }

        /**
         * Creates argument matchers from a list.
         */
        public static ArgumentMatchers of(List<ArgumentMatcher> matchers) {
            return new ArgumentMatchers(matchers);
        }
    }
}
