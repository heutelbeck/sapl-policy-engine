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
package io.sapl.compiler;

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.*;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.eclipse.emf.ecore.EObject;

import java.util.ArrayList;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Utility for applying filter functions with different argument natures (VALUE,
 * PURE, STREAM).
 * <p>
 * Provides methods for applying filters to:
 * <ul>
 * <li>Single values</li>
 * <li>Array elements (selective or all)</li>
 * <li>Object fields (selective or all)</li>
 * </ul>
 * <p>
 * Each method switches on the argument nature to enable compile-time
 * optimization (constant folding) or runtime
 * evaluation.
 */
@UtilityClass
class FilterApplicationStrategy {

    private static final String ERROR_UNEXPECTED_NON_VALUE_CONSTANT_ARGS = "Unexpected non-value result with constant arguments.";

    /**
     * Applies a filter function to a single value.
     *
     * @param targetValue
     * the value to filter
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the filtered result
     */
    public CompiledExpression applyFilterToValue(EObject astNode, Value targetValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        return switch (arguments.nature()) {
        case VALUE  -> applyValueFilterToValue(targetValue, functionIdentifier, arguments, context);
        case PURE   -> applyPureFilterToValue(astNode, targetValue, functionIdentifier, arguments);
        case STREAM -> applyStreamFilterToValue(targetValue, functionIdentifier, arguments);
        };
    }

    /**
     * Applies a filter function to specific array elements.
     *
     * @param arrayValue
     * the array to filter
     * @param indexMatcher
     * predicate determining which indices to filter
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the filtered array
     */
    public CompiledExpression applyFilterToArrayElements(EObject astNode, ArrayValue arrayValue,
            IntPredicate indexMatcher, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        return switch (arguments.nature()) {
        case VALUE  ->
            applyValueFilterToArrayElements(astNode, arrayValue, indexMatcher, functionIdentifier, arguments, context);
        case PURE   -> applyPureFilterToArrayElements(astNode, arrayValue, indexMatcher, functionIdentifier, arguments);
        case STREAM ->
            applyStreamFilterToArrayElements(arrayValue, indexMatcher, functionIdentifier, arguments, context);
        };
    }

    /**
     * Applies a filter function to all array elements.
     *
     * @param arrayValue
     * the array to filter
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the filtered array
     */
    public CompiledExpression applyFilterToAllArrayElements(EObject astNode, ArrayValue arrayValue,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        return applyFilterToArrayElements(astNode, arrayValue, i -> true, functionIdentifier, arguments, context);
    }

    /**
     * Applies a filter function to specific object fields.
     *
     * @param objectValue
     * the object to filter
     * @param keyMatcher
     * predicate determining which keys to filter
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the filtered object
     */
    public CompiledExpression applyFilterToObjectFields(EObject astNode, ObjectValue objectValue,
            Predicate<String> keyMatcher, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        return switch (arguments.nature()) {
        case VALUE  ->
            applyValueFilterToObjectFields(astNode, objectValue, keyMatcher, functionIdentifier, arguments, context);
        case PURE   -> applyPureFilterToObjectFields(astNode, objectValue, keyMatcher, functionIdentifier, arguments);
        case STREAM -> applyStreamFilterToObjectFields(objectValue, keyMatcher, functionIdentifier, arguments, context);
        };
    }

    /**
     * Applies a filter function to all object fields.
     *
     * @param objectValue
     * the object to filter
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the filtered object
     */
    public CompiledExpression applyFilterToAllObjectFields(EObject astNode, ObjectValue objectValue,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        return applyFilterToObjectFields(astNode, objectValue, key -> true, functionIdentifier, arguments, context);
    }

    // =========================================================================
    // VALUE filter implementations (Nature.VALUE) - compile-time constant
    // folding
    // =========================================================================

    /**
     * Applies a filter with constant arguments (Nature.VALUE) to a single value.
     * <p>
     * Enables compile-time constant folding.
     */
    private CompiledExpression applyValueFilterToValue(Value targetValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val valueArguments = FilterArgumentEvaluator.extractValueArguments(arguments, targetValue);
        if (valueArguments.size() == 1 && valueArguments.getFirst() instanceof ErrorValue error) {
            return error;
        }

        val invocation = new FunctionInvocation(functionIdentifier, valueArguments);
        return context.getFunctionBroker().evaluateFunction(invocation);
    }

    /**
     * Applies a filter with constant arguments (Nature.VALUE) to array elements.
     * <p>
     * Rebuilds array with filtered elements, filtering out undefined values.
     */
    private CompiledExpression applyValueFilterToArrayElements(EObject astNode, ArrayValue arrayValue,
            IntPredicate indexMatcher, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        return FilterCollectionRebuilder.rebuildArray(arrayValue, indexMatcher, i -> {
            val result = applyValueFilterToValue(arrayValue.get(i), functionIdentifier, arguments, context);
            return (result instanceof Value v) ? v
                    : Error.at(astNode, arrayValue.metadata(), ERROR_UNEXPECTED_NON_VALUE_CONSTANT_ARGS);
        });
    }

    /**
     * Applies a filter with constant arguments (Nature.VALUE) to object fields.
     * <p>
     * Rebuilds object with filtered fields, filtering out undefined values.
     */
    private CompiledExpression applyValueFilterToObjectFields(EObject astNode, ObjectValue objectValue,
            Predicate<String> keyMatcher, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        return FilterCollectionRebuilder.rebuildObject(objectValue, keyMatcher, key -> {
            val result = applyValueFilterToValue(objectValue.get(key), functionIdentifier, arguments, context);
            return (result instanceof Value v) ? v
                    : Error.at(astNode, objectValue.metadata(), ERROR_UNEXPECTED_NON_VALUE_CONSTANT_ARGS);
        });
    }

    // =========================================================================
    // PURE filter implementations (Nature.PURE) - runtime evaluation
    // =========================================================================

    /**
     * Applies a filter with pure arguments (Nature.PURE) to a single value.
     * <p>
     * Creates a PureExpression that evaluates arguments at runtime.
     */
    private CompiledExpression applyPureFilterToValue(EObject astNode, Value targetValue, String functionIdentifier,
            CompiledArguments arguments) {
        return new PureExpression(ctx -> {
            val valueArguments = FilterArgumentEvaluator.evaluatePureArguments(astNode, arguments, targetValue, ctx);
            val invocation     = new FunctionInvocation(functionIdentifier, valueArguments);
            return ctx.functionBroker().evaluateFunction(invocation);
        }, arguments.isSubscriptionScoped());
    }

    /**
     * Applies a filter with pure arguments (Nature.PURE) to array elements.
     * <p>
     * Creates a PureExpression that rebuilds the array at runtime.
     */
    private CompiledExpression applyPureFilterToArrayElements(EObject astNode, ArrayValue arrayValue,
            IntPredicate indexMatcher, String functionIdentifier, CompiledArguments arguments) {
        return new PureExpression(ctx -> FilterCollectionRebuilder.rebuildArray(arrayValue, indexMatcher, i -> {
            val valueArguments = FilterArgumentEvaluator.evaluatePureArguments(astNode, arguments, arrayValue.get(i),
                    ctx);
            val invocation     = new FunctionInvocation(functionIdentifier, valueArguments);
            return ctx.functionBroker().evaluateFunction(invocation);
        }), arguments.isSubscriptionScoped());
    }

    /**
     * Applies a filter with pure arguments (Nature.PURE) to object fields.
     * <p>
     * Creates a PureExpression that rebuilds the object at runtime.
     */
    private CompiledExpression applyPureFilterToObjectFields(EObject astNode, ObjectValue objectValue,
            Predicate<String> keyMatcher, String functionIdentifier, CompiledArguments arguments) {
        return new PureExpression(ctx -> FilterCollectionRebuilder.rebuildObject(objectValue, keyMatcher, key -> {
            val valueArguments = FilterArgumentEvaluator.evaluatePureArguments(astNode, arguments, objectValue.get(key),
                    ctx);
            val invocation     = new FunctionInvocation(functionIdentifier, valueArguments);
            return ctx.functionBroker().evaluateFunction(invocation);
        }), arguments.isSubscriptionScoped());
    }

    // =========================================================================
    // STREAM filter implementations (Nature.STREAM) - reactive streams
    // =========================================================================

    /**
     * Applies a filter with streaming arguments (Nature.STREAM) to a single value.
     * <p>
     * Creates a StreamExpression that combines argument streams reactively.
     */
    private CompiledExpression applyStreamFilterToValue(Value targetValue, String functionIdentifier,
            CompiledArguments arguments) {
        val argumentFlux = FilterArgumentEvaluator.combineStreamArguments(arguments, targetValue);

        val stream = argumentFlux.flatMap(valueArgs -> reactor.core.publisher.Flux.deferContextual(ctx -> {
            val invocation        = new FunctionInvocation(functionIdentifier, valueArgs);
            val evaluationContext = ctx.get(io.sapl.api.model.EvaluationContext.class);
            return reactor.core.publisher.Flux.just(evaluationContext.functionBroker().evaluateFunction(invocation));
        }));

        return new StreamExpression(stream);
    }

    /**
     * Applies a filter with streaming arguments (Nature.STREAM) to array elements.
     * <p>
     * Creates a StreamExpression that rebuilds the array for each argument
     * combination.
     */
    private CompiledExpression applyStreamFilterToArrayElements(ArrayValue arrayValue, IntPredicate indexMatcher,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        val argumentFlux = FilterArgumentEvaluator.combineStreamArguments(arguments, null);

        val stream = argumentFlux
                .map(argValues -> (Value) FilterCollectionRebuilder.rebuildArray(arrayValue, indexMatcher, i -> {
                    // Rebuild arguments with current element as left-hand arg
                    val valueArguments = new ArrayList<Value>(argValues.size());
                    valueArguments.add(arrayValue.get(i));
                    valueArguments.addAll(argValues.subList(1, argValues.size()));

                    val invocation = new FunctionInvocation(functionIdentifier, valueArguments);
                    return context.getFunctionBroker().evaluateFunction(invocation);
                }));

        return new StreamExpression(stream);
    }

    /**
     * Applies a filter with streaming arguments (Nature.STREAM) to object fields.
     * <p>
     * Creates a StreamExpression that rebuilds the object for each argument
     * combination.
     */
    private CompiledExpression applyStreamFilterToObjectFields(ObjectValue objectValue, Predicate<String> keyMatcher,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        val argumentFlux = FilterArgumentEvaluator.combineStreamArguments(arguments, null);

        val stream = argumentFlux
                .map(argValues -> (Value) FilterCollectionRebuilder.rebuildObject(objectValue, keyMatcher, key -> {
                    // Rebuild arguments with current field value as left-hand arg
                    val valueArguments = new ArrayList<Value>(argValues.size());
                    valueArguments.add(objectValue.get(key));
                    valueArguments.addAll(argValues.subList(1, argValues.size()));

                    val invocation = new FunctionInvocation(functionIdentifier, valueArguments);
                    return context.getFunctionBroker().evaluateFunction(invocation);
                }));

        return new StreamExpression(stream);
    }

}
