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
package io.sapl.test.mocking.function;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.Value;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.function.models.FunctionParameters;
import lombok.val;
import org.hamcrest.Matcher;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * A FunctionBroker implementation that allows mocking of function calls for
 * testing purposes.
 * <p>
 * This broker wraps a delegate FunctionBroker and intercepts function calls.
 * If a mock is registered for a function, the mock value is returned.
 * Otherwise, the call is delegated to the original broker.
 * <p>
 * This implements a spy-like pattern where unmocked functions work normally
 * while mocked functions return test-defined values.
 */
public class MockingFunctionBroker implements FunctionBroker {

    private static final String ERROR_MOCK_INVALID_FULL_NAME           = "Invalid function reference containing more than one '.' delimiter: '%s'.";
    private static final String ERROR_MOCK_COLLISION                   = "A mock for function '%s' is already registered with a different mock type.";
    private static final String ERROR_MOCK_ALREADY_REGISTERED          = "A mock for function '%s' is already registered.";
    private static final String ERROR_SEQUENCE_EXHAUSTED               = "Mock sequence for function '%s' exhausted. No more values to return.";
    private static final String ERROR_NO_MATCHING_PARAMETER_MOCK       = "No mock found for function '%s' matching the given parameters.";
    private static final String NAME_DELIMITER                         = ".";
    private static final int    EXPECTED_PARTS_IN_FULLY_QUALIFIED_NAME = 2;

    private final FunctionBroker                      delegate;
    private final Map<String, FunctionMockDefinition> registeredMocks = new HashMap<>();

    /**
     * Creates a MockingFunctionBroker with the given delegate.
     *
     * @param delegate the FunctionBroker to delegate unmocked function calls to
     */
    public MockingFunctionBroker(FunctionBroker delegate) {
        this.delegate = delegate;
    }

    @Override
    public Value evaluateFunction(FunctionInvocation invocation) {
        val functionName = invocation.functionName();
        val mock         = registeredMocks.get(functionName);

        if (mock != null) {
            return mock.evaluate(invocation);
        }

        return delegate.evaluateFunction(invocation);
    }

    @Override
    public List<Class<?>> getRegisteredLibraries() {
        return delegate.getRegisteredLibraries();
    }

    /**
     * Registers a mock that always returns the same value for the given function.
     *
     * @param functionName the fully qualified function name (e.g.,
     * "filter.blacken")
     * @param returnValue the value to return when the function is called
     */
    public void mockFunctionAlwaysReturns(String functionName, Value returnValue) {
        validateFunctionName(functionName);
        ensureNoExistingMock(functionName);
        registeredMocks.put(functionName, new FixedValueMock(functionName, returnValue));
    }

    /**
     * Registers a mock that returns values from a sequence in order.
     * Each call consumes the next value in the sequence.
     *
     * @param functionName the fully qualified function name
     * @param values the sequence of values to return
     */
    public void mockFunctionReturnsSequence(String functionName, Value... values) {
        validateFunctionName(functionName);
        val existingMock = registeredMocks.get(functionName);

        if (existingMock != null) {
            if (existingMock instanceof SequenceMock sequenceMock) {
                sequenceMock.addValues(values);
            } else {
                throw new SaplTestException(ERROR_MOCK_COLLISION.formatted(functionName));
            }
        } else {
            registeredMocks.put(functionName, new SequenceMock(functionName, values));
        }
    }

    /**
     * Registers a mock that computes the return value based on the function
     * parameters.
     *
     * @param functionName the fully qualified function name
     * @param computation a function that takes the invocation and returns the mock
     * value
     */
    public void mockFunctionComputed(String functionName, Function<FunctionInvocation, Value> computation) {
        validateFunctionName(functionName);
        ensureNoExistingMock(functionName);
        registeredMocks.put(functionName, new ComputedMock(functionName, computation));
    }

    /**
     * Registers a parameter-specific mock that returns a value when the function
     * is called with matching parameters.
     *
     * @param functionName the fully qualified function name
     * @param expectedArguments the arguments that must match
     * @param returnValue the value to return when arguments match
     */
    public void mockFunctionForParameters(String functionName, List<Value> expectedArguments, Value returnValue) {
        validateFunctionName(functionName);
        val existingMock = registeredMocks.get(functionName);

        if (existingMock != null) {
            if (existingMock instanceof ParameterMatcherMock matcherMock) {
                matcherMock.addMapping(expectedArguments.stream().map(v -> org.hamcrest.CoreMatchers.is(v)).toList(),
                        returnValue);
            } else {
                throw new SaplTestException(ERROR_MOCK_COLLISION.formatted(functionName));
            }
        } else {
            val matcherMock = new ParameterMatcherMock(functionName);
            matcherMock.addMapping(expectedArguments.stream().map(v -> org.hamcrest.CoreMatchers.is(v)).toList(),
                    returnValue);
            registeredMocks.put(functionName, matcherMock);
        }
    }

    /**
     * Registers a parameter-specific mock using Hamcrest matchers.
     *
     * @param functionName the fully qualified function name
     * @param parameters the FunctionParameters containing matchers
     * @param returnValue the value to return when matchers match
     */
    public void mockFunctionForParameterMatchers(String functionName, FunctionParameters parameters,
            Value returnValue) {
        validateFunctionName(functionName);
        val existingMock = registeredMocks.get(functionName);

        if (existingMock != null) {
            if (existingMock instanceof ParameterMatcherMock matcherMock) {
                matcherMock.addMapping(parameters.getParameterMatchers(), returnValue);
            } else {
                throw new SaplTestException(ERROR_MOCK_COLLISION.formatted(functionName));
            }
        } else {
            val matcherMock = new ParameterMatcherMock(functionName);
            matcherMock.addMapping(parameters.getParameterMatchers(), returnValue);
            registeredMocks.put(functionName, matcherMock);
        }
    }

    /**
     * Checks if a mock is registered for the given function.
     *
     * @param functionName the fully qualified function name
     * @return true if a mock is registered
     */
    public boolean hasMock(String functionName) {
        return registeredMocks.containsKey(functionName);
    }

    /**
     * Removes a mock registration.
     *
     * @param functionName the fully qualified function name
     */
    public void clearMock(String functionName) {
        registeredMocks.remove(functionName);
    }

    /**
     * Removes all mock registrations.
     */
    public void clearAllMocks() {
        registeredMocks.clear();
    }

    private void validateFunctionName(String functionName) {
        val parts = functionName.split(Pattern.quote(NAME_DELIMITER));
        if (parts.length != EXPECTED_PARTS_IN_FULLY_QUALIFIED_NAME) {
            throw new SaplTestException(ERROR_MOCK_INVALID_FULL_NAME.formatted(functionName));
        }
    }

    private void ensureNoExistingMock(String functionName) {
        if (registeredMocks.containsKey(functionName)) {
            throw new SaplTestException(ERROR_MOCK_ALREADY_REGISTERED.formatted(functionName));
        }
    }

    /**
     * Internal interface for mock definitions.
     */
    private sealed interface FunctionMockDefinition {

        Value evaluate(FunctionInvocation invocation);

    }

    /**
     * Mock that always returns the same value.
     */
    private record FixedValueMock(String functionName, Value returnValue) implements FunctionMockDefinition {

        @Override
        public Value evaluate(FunctionInvocation invocation) {
            return returnValue;
        }

    }

    /**
     * Mock that returns values from a sequence.
     */
    private static final class SequenceMock implements FunctionMockDefinition {

        private final String       functionName;
        private final Queue<Value> values;

        SequenceMock(String functionName, Value... initialValues) {
            this.functionName = functionName;
            this.values       = new LinkedList<>(Arrays.asList(initialValues));
        }

        void addValues(Value... newValues) {
            values.addAll(Arrays.asList(newValues));
        }

        @Override
        public Value evaluate(FunctionInvocation invocation) {
            val nextValue = values.poll();
            if (nextValue == null) {
                throw new SaplTestException(ERROR_SEQUENCE_EXHAUSTED.formatted(functionName));
            }
            return nextValue;
        }

    }

    /**
     * Mock that computes the return value dynamically.
     */
    private record ComputedMock(String functionName, Function<FunctionInvocation, Value> computation)
            implements FunctionMockDefinition {

        @Override
        public Value evaluate(FunctionInvocation invocation) {
            return computation.apply(invocation);
        }

    }

    /**
     * Mock that returns different values based on parameter matchers.
     */
    private static final class ParameterMatcherMock implements FunctionMockDefinition {

        private final String                        functionName;
        private final List<ParameterMatcherMapping> mappings = new ArrayList<>();

        ParameterMatcherMock(String functionName) {
            this.functionName = functionName;
        }

        void addMapping(List<Matcher<Value>> matchers, Value returnValue) {
            mappings.add(new ParameterMatcherMapping(matchers, returnValue));
        }

        @Override
        public Value evaluate(FunctionInvocation invocation) {
            for (val mapping : mappings) {
                if (mapping.matches(invocation.arguments())) {
                    return mapping.returnValue();
                }
            }
            throw new SaplTestException(ERROR_NO_MATCHING_PARAMETER_MOCK.formatted(functionName));
        }

        private record ParameterMatcherMapping(List<Matcher<Value>> matchers, Value returnValue) {

            boolean matches(List<Value> actualArguments) {
                if (matchers.size() != actualArguments.size()) {
                    return false;
                }
                for (int i = 0; i < matchers.size(); i++) {
                    if (!matchers.get(i).matches(actualArguments.get(i))) {
                        return false;
                    }
                }
                return true;
            }

        }

    }

}
