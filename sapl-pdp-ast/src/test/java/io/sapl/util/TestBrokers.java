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
package io.sapl.util;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Value;
import io.sapl.compiler.CompilationContext;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Shared test utilities for creating broker mocks and evaluation contexts.
 * Centralizes common patterns used across compiler test classes.
 */
@UtilityClass
public class TestBrokers {

    /**
     * Default function broker with no registered functions.
     */
    public static final DefaultFunctionBroker DEFAULT_FUNCTION_BROKER = new DefaultFunctionBroker();

    /**
     * Attribute broker that returns an error for any attribute lookup.
     * Useful for tests that don't need attribute functionality.
     */
    public static final AttributeBroker ERROR_ATTRIBUTE_BROKER = new AttributeBroker() {
        @Override
        public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
            return Flux.just(Value.error("No attribute finder registered for: " + invocation.attributeName()));
        }

        @Override
        public List<Class<?>> getRegisteredLibraries() {
            return List.of();
        }
    };

    /**
     * Creates a function broker that responds to a single function name.
     *
     * @param expectedName the function name to respond to
     * @param fn the function implementation
     * @return a function broker
     */
    public static FunctionBroker functionBroker(String expectedName, Function<List<Value>, Value> fn) {
        return new FunctionBroker() {
            @Override
            public Value evaluateFunction(FunctionInvocation invocation) {
                if (invocation.functionName().equals(expectedName))
                    return fn.apply(invocation.arguments());
                return Value.error("Unknown function: " + invocation.functionName());
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    /**
     * Creates a function broker that supports multiple function registrations.
     *
     * @param functions map of function names to implementations
     * @return a function broker
     */
    public static FunctionBroker functionBroker(Map<String, Function<List<Value>, Value>> functions) {
        return new FunctionBroker() {
            @Override
            public Value evaluateFunction(FunctionInvocation invocation) {
                var fn = functions.get(invocation.functionName());
                if (fn == null)
                    return Value.error("Unknown function: " + invocation.functionName());
                return fn.apply(invocation.arguments());
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    /**
     * Creates a function broker that captures invocations for inspection.
     *
     * @param capture array to store the captured invocation (use single-element
     * array)
     * @param returnValue the value to return for all invocations
     * @return a function broker
     */
    public static FunctionBroker capturingFunctionBroker(FunctionInvocation[] capture, Value returnValue) {
        return new FunctionBroker() {
            @Override
            public Value evaluateFunction(FunctionInvocation invocation) {
                capture[0] = invocation;
                return returnValue;
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    /**
     * Creates a function broker that captures all invocations to a list.
     *
     * @param capture list to store all captured invocations
     * @param fn the function implementation
     * @return a function broker
     */
    public static FunctionBroker capturingFunctionBroker(List<FunctionInvocation> capture,
            Function<List<Value>, Value> fn) {
        return new FunctionBroker() {
            @Override
            public Value evaluateFunction(FunctionInvocation invocation) {
                capture.add(invocation);
                return fn.apply(invocation.arguments());
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    /**
     * Creates an attribute broker that responds to a single attribute name.
     *
     * @param expectedName the attribute name to respond to
     * @param values the values to emit in sequence
     * @return an attribute broker
     */
    public static AttributeBroker attributeBroker(String expectedName, Value... values) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                if (invocation.attributeName().equals(expectedName))
                    return Flux.fromArray(values);
                return Flux.just(Value.error("Unknown attribute: " + invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    /**
     * Creates an attribute broker that supports multiple attribute registrations.
     *
     * @param attributes map of attribute names to value arrays
     * @return an attribute broker
     */
    public static AttributeBroker attributeBroker(Map<String, Value[]> attributes) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                var values = attributes.get(invocation.attributeName());
                if (values != null)
                    return Flux.fromArray(values);
                return Flux.just(Value.error("Unknown attribute: " + invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    /**
     * Creates an attribute broker that captures invocations for inspection.
     *
     * @param capture array to store the captured invocation (use single-element
     * array)
     * @param returnValue the value to return
     * @return an attribute broker
     */
    public static AttributeBroker capturingAttributeBroker(AttributeFinderInvocation[] capture, Value returnValue) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                capture[0] = invocation;
                return Flux.just(returnValue);
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    /**
     * Creates an attribute broker from a custom stream function.
     *
     * @param streamFn function that produces the attribute stream
     * @return an attribute broker
     */
    public static AttributeBroker attributeBroker(Function<AttributeFinderInvocation, Flux<Value>> streamFn) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                return streamFn.apply(invocation);
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    /**
     * Creates an attribute broker that returns an error for a specific attribute.
     *
     * @param expectedName the attribute name to respond to
     * @param errorMessage the error message to return
     * @return an attribute broker that returns an error
     */
    public static AttributeBroker errorAttributeBroker(String expectedName, String errorMessage) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                if (invocation.attributeName().equals(expectedName)) {
                    return Flux.just(Value.error(errorMessage));
                }
                return Flux.just(Value.error("Unknown attribute: " + invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    /**
     * Creates an attribute broker that emits multiple values in sequence for
     * multiple attributes.
     *
     * @param attributeSequences map of attribute names to value sequences
     * @return an attribute broker
     */
    public static AttributeBroker sequenceBroker(Map<String, List<Value>> attributeSequences) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                var values = attributeSequences.get(invocation.attributeName());
                if (values != null)
                    return Flux.fromIterable(values);
                return Flux.just(Value.error("Unknown attribute: " + invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    /**
     * Creates an attribute broker that tracks whether it was subscribed to.
     * Useful for testing short-circuit behavior.
     *
     * @param subscribed AtomicBoolean that will be set to true when subscribed
     * @param returnValue the value to return when subscribed
     * @return an attribute broker
     */
    public static AttributeBroker trackingBroker(java.util.concurrent.atomic.AtomicBoolean subscribed,
            Value returnValue) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                subscribed.set(true);
                return Flux.just(returnValue);
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    /**
     * Creates an attribute broker that returns a single value for multiple
     * attribute names. Each attribute returns just its mapped value (single
     * emission).
     *
     * @param attributeValues map of attribute names to single values
     * @return an attribute broker
     */
    public static AttributeBroker multiAttributeBroker(Map<String, Value> attributeValues) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                var value = attributeValues.get(invocation.attributeName());
                if (value != null)
                    return Flux.just(value);
                return Flux.just(Value.error("Unknown attribute: " + invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    /**
     * Creates a compilation context with the given attribute broker.
     *
     * @param attrBroker the attribute broker
     * @return a compilation context
     */
    public static CompilationContext compilationContext(AttributeBroker attrBroker) {
        return new CompilationContext(DEFAULT_FUNCTION_BROKER, attrBroker);
    }

    /**
     * Creates a compilation context with the given function broker.
     *
     * @param fnBroker the function broker
     * @return a compilation context
     */
    public static CompilationContext compilationContext(FunctionBroker fnBroker) {
        return new CompilationContext(fnBroker, ERROR_ATTRIBUTE_BROKER);
    }

    /**
     * Creates a compilation context with function and attribute brokers.
     *
     * @param fnBroker the function broker
     * @param attrBroker the attribute broker
     * @return a compilation context
     */
    public static CompilationContext compilationContext(FunctionBroker fnBroker, AttributeBroker attrBroker) {
        return new CompilationContext(fnBroker, attrBroker);
    }

    /**
     * Creates an evaluation context with the given function broker.
     *
     * @param fnBroker the function broker
     * @param variables the variables to include
     * @return an evaluation context
     */
    public static EvaluationContext evaluationContext(FunctionBroker fnBroker, Map<String, Value> variables) {
        return new EvaluationContext("pdp", "config", "sub", null, variables, fnBroker, ERROR_ATTRIBUTE_BROKER,
                () -> "test-timestamp");
    }

    /**
     * Creates an evaluation context with the given attribute broker.
     *
     * @param attrBroker the attribute broker
     * @return an evaluation context
     */
    public static EvaluationContext evaluationContext(AttributeBroker attrBroker) {
        return new EvaluationContext("pdp", "config", "sub", null, Map.of(), DEFAULT_FUNCTION_BROKER, attrBroker,
                () -> "test-timestamp");
    }

    /**
     * Creates an evaluation context with attribute broker and subject variable.
     *
     * @param attrBroker the attribute broker
     * @param subject the subject value
     * @return an evaluation context
     */
    public static EvaluationContext evaluationContext(AttributeBroker attrBroker, Value subject) {
        return new EvaluationContext("pdp", "config", "sub", null, Map.of("subject", subject), DEFAULT_FUNCTION_BROKER,
                attrBroker, () -> "test-timestamp");
    }

    /**
     * Creates an evaluation context with both function and attribute brokers.
     *
     * @param fnBroker the function broker
     * @param attrBroker the attribute broker
     * @param variables the variables to include
     * @return an evaluation context
     */
    public static EvaluationContext evaluationContext(FunctionBroker fnBroker, AttributeBroker attrBroker,
            Map<String, Value> variables) {
        return new EvaluationContext("pdp", "config", "sub", null, variables, fnBroker, attrBroker,
                () -> "test-timestamp");
    }

    /**
     * Creates an evaluation context with both brokers but no variables.
     *
     * @param fnBroker the function broker
     * @param attrBroker the attribute broker
     * @return an evaluation context
     */
    public static EvaluationContext evaluationContext(FunctionBroker fnBroker, AttributeBroker attrBroker) {
        return evaluationContext(fnBroker, attrBroker, Map.of());
    }

    /**
     * Creates an evaluation context with attribute broker and variables.
     *
     * @param attrBroker the attribute broker
     * @param variables the variables to include
     * @return an evaluation context
     */
    public static EvaluationContext evaluationContext(AttributeBroker attrBroker, Map<String, Value> variables) {
        return new EvaluationContext("pdp", "config", "sub", null, variables, DEFAULT_FUNCTION_BROKER, attrBroker,
                () -> "test-timestamp");
    }

    /**
     * Creates an evaluation context with only variables (using default brokers).
     *
     * @param variables the variables to include
     * @return an evaluation context
     */
    public static EvaluationContext evaluationContext(Map<String, Value> variables) {
        return new EvaluationContext("pdp", "config", "sub", null, variables, DEFAULT_FUNCTION_BROKER,
                ERROR_ATTRIBUTE_BROKER, () -> "test-timestamp");
    }

}
