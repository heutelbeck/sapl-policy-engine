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
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureExpression;
import io.sapl.api.model.StreamExpression;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.grammar.sapl.ArraySlicingStep;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.AttributeUnionStep;
import io.sapl.grammar.sapl.ConditionStep;
import io.sapl.grammar.sapl.ExpressionStep;
import io.sapl.grammar.sapl.FilterComponent;
import io.sapl.grammar.sapl.FilterExtended;
import io.sapl.grammar.sapl.FilterSimple;
import io.sapl.grammar.sapl.HeadAttributeFinderStep;
import io.sapl.grammar.sapl.IndexStep;
import io.sapl.grammar.sapl.IndexUnionStep;
import io.sapl.grammar.sapl.KeyStep;
import io.sapl.grammar.sapl.RecursiveIndexStep;
import io.sapl.grammar.sapl.RecursiveKeyStep;
import io.sapl.grammar.sapl.RecursiveWildcardStep;
import io.sapl.grammar.sapl.Step;
import io.sapl.grammar.sapl.WildcardStep;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Compiles SAPL filter expressions (|- operator) and subtemplates (:: operator)
 * into optimized executable representations.
 * <p>
 * Filters transform values by applying functions, supporting both simple
 * ({@code value |- func}) and extended ({@code value |- { @.field : func }})
 * syntax. Subtemplates apply templates to values, with implicit array mapping.
 */
@UtilityClass
public class FilterCompiler {

    private static final String FILTERS_CANNOT_BE_APPLIED_TO_UNDEFINED   = "Filters cannot be applied to undefined values.";
    private static final String EACH_REQUIRES_ARRAY                      = "Cannot use 'each' keyword with non-array values.";
    private static final String ATTRIBUTE_FINDER_NOT_PERMITTED_IN_FILTER = "AttributeFinderStep not permitted in filter selection steps.";

    /**
     * Compiles a filter expression (|- operator).
     * <p>
     * Filters apply functions to values, optionally using the 'each' keyword to
     * map over arrays. Extended filters support complex targeting with paths.
     *
     * @param parent the expression to filter
     * @param filter the filter component (simple or extended)
     * @param context the compilation context
     * @return the compiled filter expression
     */
    public CompiledExpression compileFilter(CompiledExpression parent, FilterComponent filter,
            CompilationContext context) {
        if (parent instanceof ErrorValue) {
            return parent;
        }
        if (parent instanceof UndefinedValue) {
            return Value.error(FILTERS_CANNOT_BE_APPLIED_TO_UNDEFINED);
        }
        return switch (filter) {
        case FilterSimple simple     -> compileSimpleFilter(parent, simple, context);
        case FilterExtended extended -> compileExtendedFilter(parent, extended, context);
        default                      ->
            throw new SaplCompilerException("Unknown filter type: " + filter.getClass().getSimpleName());
        };
    }

    /**
     * Compiles a simple filter expression: {@code value |- [each] func(args)}.
     * <p>
     * Without 'each': applies function to entire value. With 'each': maps function
     * over array elements.
     *
     * @param parent the parent expression to filter
     * @param filter the simple filter AST node
     * @param context the compilation context
     * @return the compiled filter expression
     */
    private CompiledExpression compileSimpleFilter(CompiledExpression parent, FilterSimple filter,
            CompilationContext context) {
        // Compile filter arguments
        var arguments = CompiledArguments.EMPTY_ARGUMENTS;
        if (filter.getArguments() != null && filter.getArguments().getArgs() != null) {
            arguments = ExpressionCompiler.compileArguments(filter.getArguments().getArgs(), context);
        }

        val finalArguments     = arguments;
        val functionIdentifier = ImportResolver.resolveFunctionIdentifierByImports(filter, filter.getIdentifier());

        // Define the filter operation that works on Values
        java.util.function.UnaryOperator<CompiledExpression> filterOp = parentValue -> {
            if (!(parentValue instanceof Value value)) {
                return Value.error("Filter operations require Value inputs.");
            }

            // Handle 'each' keyword - map function over array elements
            if (filter.isEach()) {
                return applyFilterFunctionToEachArrayElement(value, filter, finalArguments, context);
            }

            // Apply filter function with parent as left-hand argument
            return applyFilterFunctionToValue(value, functionIdentifier, finalArguments, context);
        };

        // Wrap the operation to handle PureExpression/StreamExpression parents
        return wrapFilterOperation(parent, filterOp);
    }

    /**
     * Wraps a filter operation to handle Value, PureExpression, and
     * StreamExpression
     * parents.
     * <p>
     * This is the core wrapper pattern that enables filters to work with all
     * expression types, similar to how ExpressionCompiler handles step operations.
     *
     * @param parent the parent expression (Value, PureExpression, or
     * StreamExpression)
     * @param filterOperation the filter operation to apply (works on
     * CompiledExpression)
     * @return the wrapped filter result
     */
    private CompiledExpression wrapFilterOperation(CompiledExpression parent,
            java.util.function.UnaryOperator<CompiledExpression> filterOperation) {
        if (parent instanceof ErrorValue || parent instanceof UndefinedValue) {
            return parent;
        }
        return switch (parent) {
        case Value value                  -> filterOperation.apply(value);
        case StreamExpression(var stream) -> new StreamExpression(
                stream.flatMap(v -> ExpressionCompiler.compiledExpressionToFlux(filterOperation.apply(v))));
        case PureExpression pureParent    -> new PureExpression(ctx -> {
                                          val     evaluatedParent = pureParent.evaluate(ctx);
                                          val     result          = filterOperation.apply(evaluatedParent);
                                          return (result instanceof Value v) ? v
                                                  : (result instanceof PureExpression pe) ? pe.evaluate(ctx)
                                                          : Value.error("Unexpected filter result type");
                                      },
                pureParent.isSubscriptionScoped());
        };
    }

    /**
     * Applies a filter function to each element of an array value.
     * <p>
     * Maps the function over array elements, filtering out undefined results.
     * This implements the 'each' keyword semantics.
     *
     * @param parentValue the array value to filter
     * @param filter the filter AST node
     * @param arguments the compiled filter arguments
     * @param context the compilation context
     * @return an array with the function applied to each element
     */
    private CompiledExpression applyFilterFunctionToEachArrayElement(Value parentValue, FilterSimple filter,
            CompiledArguments arguments, CompilationContext context) {
        // Validate parent is an array
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Value.error(EACH_REQUIRES_ARRAY);
        }

        val functionIdentifier = ImportResolver.resolveFunctionIdentifierByImports(filter, filter.getIdentifier());

        // Handle based on argument nature
        return switch (arguments.nature()) {
        case VALUE  -> {
            // Constant folding: map function over each element
            val builder = ArrayValue.builder();
            for (val element : arrayValue) {
                val result = applyFilterFunctionToValue(element, functionIdentifier, arguments, context);

                // For constant folding, we get a Value back
                if (result instanceof Value resultValue) {
                    // Filter out undefined values (filter.remove() semantics)
                    if (!(resultValue instanceof UndefinedValue)) {
                        builder.add(resultValue);
                    }
                } else {
                    // Should not happen with VALUE arguments, but handle defensively
                    yield createEachRuntimeExpression(arrayValue, functionIdentifier, arguments, context, false);
                }
            }
            yield builder.build();
        }
        case PURE   -> createEachRuntimeExpression(arrayValue, functionIdentifier, arguments, context, false);
        case STREAM -> createEachRuntimeExpression(arrayValue, functionIdentifier, arguments, context, true);
        };
    }

    /**
     * Creates a runtime expression for 'each' filter operation.
     * <p>
     * This is used when filter arguments contain dynamic expressions.
     *
     * @param arrayValue the array value to filter
     * @param functionIdentifier the function identifier
     * @param arguments the compiled filter arguments
     * @param context the compilation context
     * @param isStream whether arguments contain streaming expressions
     * @return a PureExpression or StreamExpression
     */
    private CompiledExpression createEachRuntimeExpression(ArrayValue arrayValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context, boolean isStream) {
        if (isStream) {
            // Stream case: arguments change over time
            val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux)
                    .toList();
            val stream  = reactor.core.publisher.Flux.combineLatest(sources, argValues -> {
                            val builder = ArrayValue.builder();
                            for (val element : arrayValue) {
                                val valueArguments = new ArrayList<Value>(argValues.length + 1);
                                valueArguments.add(element);
                                for (var argValue : argValues) {
                                    valueArguments.add((Value) argValue);
                                }
                                val invocation = new FunctionInvocation(functionIdentifier, valueArguments);
                                val result     = context.getFunctionBroker().evaluateFunction(invocation);
                                // Filter out undefined
                                if (!(result instanceof UndefinedValue)) {
                                    builder.add(result);
                                }
                            }
                            return (Value) builder.build();
                        });
            return new StreamExpression(stream);
        } else {
            // Pure case: arguments are dynamic but don't stream
            return new PureExpression(ctx -> {
                val builder = ArrayValue.builder();
                for (val element : arrayValue) {
                    val valueArguments = new ArrayList<Value>(arguments.arguments().length + 1);
                    valueArguments.add(element);
                    for (val argument : arguments.arguments()) {
                        switch (argument) {
                        case Value value                   -> valueArguments.add(value);
                        case PureExpression pureExpression -> valueArguments.add(pureExpression.evaluate(ctx));
                        case StreamExpression ignored      -> throw new SaplCompilerException(
                                "Stream expression in pure filter arguments. Should not be possible.");
                        }
                    }
                    val invocation = new FunctionInvocation(functionIdentifier, valueArguments);
                    val result     = ctx.functionBroker().evaluateFunction(invocation);
                    // Filter out undefined
                    if (!(result instanceof UndefinedValue)) {
                        builder.add(result);
                    }
                }
                return builder.build();
            }, arguments.isSubscriptionScoped());
        }
    }

    /**
     * Compiles an extended filter expression: {@code value |- { stmt1, stmt2, ...
     * }}.
     * <p>
     * Applies multiple filter statements to a value, optionally targeting specific
     * paths within the value.
     *
     * @param parent the parent expression to filter
     * @param filter the extended filter AST node
     * @param context the compilation context
     * @return the compiled filter expression
     */
    private CompiledExpression compileExtendedFilter(CompiledExpression parent, FilterExtended filter,
            CompilationContext context) {
        // Define the extended filter operation that works on Values
        java.util.function.UnaryOperator<CompiledExpression> filterOp = parentExpr -> {
            if (!(parentExpr instanceof Value currentValue)) {
                return Value.error("Extended filter operations require Value inputs.");
            }

            for (val statement : filter.getStatements()) {
                var arguments = CompiledArguments.EMPTY_ARGUMENTS;
                if (statement.getArguments() != null && statement.getArguments().getArgs() != null) {
                    arguments = ExpressionCompiler.compileArguments(statement.getArguments().getArgs(), context);
                }

                val functionIdentifier = ImportResolver.resolveFunctionIdentifierByImports(statement,
                        statement.getIdentifier());

                if (statement.isEach()) {
                    currentValue = applyEachFilterStatement(currentValue, statement.getTarget(), functionIdentifier,
                            arguments, context);
                } else if (statement.getTarget() != null && !statement.getTarget().getSteps().isEmpty()) {
                    currentValue = applyFilterFunctionToPath(currentValue, statement.getTarget().getSteps(),
                            functionIdentifier, arguments, context);
                } else {
                    val result = applyFilterFunctionToValue(currentValue, functionIdentifier, arguments, context);

                    if (result instanceof Value resultValue) {
                        currentValue = resultValue;
                    } else {
                        return Value.error("Non-value results in extended filter statements not yet supported.");
                    }
                }
            }

            return currentValue;
        };

        // Wrap the operation to handle PureExpression/StreamExpression parents
        return wrapFilterOperation(parent, filterOp);
    }

    /**
     * Applies a filter function to a value given the function identifier.
     *
     * @param parentValue the value to filter
     * @param functionIdentifier the function identifier
     * @param arguments the compiled filter arguments
     * @param context the compilation context
     * @return the result of applying the filter function
     */
    private CompiledExpression applyFilterFunctionToValue(Value parentValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        return switch (arguments.nature()) {
        case VALUE  -> {
            val valueArguments = new ArrayList<Value>(arguments.arguments().length + 1);
            valueArguments.add(parentValue);
            for (val arg : arguments.arguments()) {
                val argValue = (Value) arg;
                if (argValue instanceof ErrorValue) {
                    yield argValue;
                }
                valueArguments.add(argValue);
            }
            val invocation = new FunctionInvocation(functionIdentifier, valueArguments);
            yield context.getFunctionBroker().evaluateFunction(invocation);
        }
        case PURE   -> new PureExpression(ctx -> {
                    val valueArguments = new ArrayList<Value>(arguments.arguments().length + 1);
                    valueArguments.add(parentValue);
                    for (val argument : arguments.arguments()) {
                        switch (argument) {
                        case Value value                       -> valueArguments.add(value);
                        case PureExpression pureExpression     -> valueArguments.add(pureExpression.evaluate(ctx));
                        case StreamExpression ignored          -> throw new SaplCompilerException(
                                "Stream expression in pure filter arguments. Should not be possible.");
                        }
                    }
                    val invocation = new FunctionInvocation(functionIdentifier, valueArguments);
                    return ctx.functionBroker().evaluateFunction(invocation);
                }, arguments.isSubscriptionScoped());
        case STREAM -> {
            val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux)
                    .toList();
            val stream  = reactor.core.publisher.Flux.combineLatest(sources, argValues -> {
                            val valueArguments = new ArrayList<Value>(argValues.length + 1);
                            valueArguments.add(parentValue);
                            for (val argValue : argValues) {
                                valueArguments.add((Value) argValue);
                            }
                            return valueArguments;
                        })
                    .flatMap(valueArgs -> reactor.core.publisher.Flux.deferContextual(ctx -> {
                        val invocation = new FunctionInvocation(functionIdentifier, valueArgs);
                        val evaluationContext = ctx.get(io.sapl.api.model.EvaluationContext.class);
                        return reactor.core.publisher.Flux
                                .just(evaluationContext.functionBroker().evaluateFunction(invocation));
                    }));
            yield new StreamExpression(stream);
        }
        };
    }

    /**
     * Applies a filter function to a value at a specific path.
     * <p>
     * Navigates to the path, applies the filter, and updates the parent value with
     * the result.
     *
     * @param parentValue the value containing the path
     * @param steps the path steps to navigate
     * @param functionIdentifier the function to apply
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated parent value with the filter applied at the path
     */
    private Value applyFilterFunctionToPath(Value parentValue, org.eclipse.emf.common.util.EList<Step> steps,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        return applyFilterFunctionToPathRecursive(parentValue, steps, 0, functionIdentifier, arguments, context);
    }

    private Value applyFilterFunctionToPathRecursive(Value parentValue, org.eclipse.emf.common.util.EList<Step> steps,
            int stepIndex, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        // Check for dynamic arguments - not yet supported in path-based extended
        // filters
        if (arguments.nature() != Nature.VALUE) {
            return Value.error("Dynamic filter arguments not yet supported in path-based extended filters.");
        }

        if (stepIndex == steps.size() - 1) {
            val result = applySingleStepFilter(parentValue, steps.get(stepIndex), functionIdentifier, arguments,
                    context);
            // With VALUE arguments, result should always be a Value
            if (!(result instanceof Value resultValue)) {
                return Value.error("Unexpected non-value result with constant arguments.");
            }
            return resultValue;
        }

        val currentStep = steps.get(stepIndex);

        return switch (currentStep) {
        case KeyStep keyStep                       -> applyFilterToNestedPath(parentValue, keyStep.getId(), steps,
                stepIndex + 1, functionIdentifier, arguments, context);
        case IndexStep indexStep                   -> applyFilterToNestedArrayElement(parentValue,
                indexStep.getIndex().intValue(), steps, stepIndex + 1, functionIdentifier, arguments, context);
        case ArraySlicingStep slicingStep          -> applyFilterToNestedArraySlice(parentValue, slicingStep, steps,
                stepIndex + 1, functionIdentifier, arguments, context);
        case WildcardStep ignored                  ->
            applyFilterToNestedWildcard(parentValue, steps, stepIndex + 1, functionIdentifier, arguments, context);
        case RecursiveKeyStep recursiveKeyStep     -> applyFilterToNestedRecursiveKey(parentValue,
                recursiveKeyStep.getId(), steps, stepIndex + 1, functionIdentifier, arguments, context);
        case RecursiveWildcardStep ignored         -> applyFilterToNestedRecursiveWildcard(parentValue, steps,
                stepIndex + 1, functionIdentifier, arguments, context);
        case RecursiveIndexStep recursiveIndexStep -> applyFilterToNestedRecursiveIndex(parentValue,
                recursiveIndexStep.getIndex().intValue(), steps, stepIndex + 1, functionIdentifier, arguments, context);
        case AttributeUnionStep attributeUnionStep -> applyFilterToNestedAttributeUnion(parentValue,
                attributeUnionStep.getAttributes(), steps, stepIndex + 1, functionIdentifier, arguments, context);
        case IndexUnionStep indexUnionStep         -> applyFilterToNestedIndexUnion(parentValue,
                indexUnionStep.getIndices(), steps, stepIndex + 1, functionIdentifier, arguments, context);
        case AttributeFinderStep ignored           -> Value.error(ATTRIBUTE_FINDER_NOT_PERMITTED_IN_FILTER);
        case HeadAttributeFinderStep ignored       -> Value.error(ATTRIBUTE_FINDER_NOT_PERMITTED_IN_FILTER);
        case ConditionStep conditionStep           -> applyFilterToNestedCondition(parentValue, conditionStep, steps,
                stepIndex + 1, functionIdentifier, arguments, context);
        case ExpressionStep expressionStep         -> applyFilterToNestedExpression(parentValue, expressionStep, steps,
                stepIndex + 1, functionIdentifier, arguments, context);
        default                                    -> throw new SaplCompilerException(
                "Step type not supported in multi-step path: " + currentStep.getClass().getSimpleName());
        };
    }

    /**
     * Applies a filter function to a value at a single-step path.
     *
     * @param parentValue the parent value
     * @param step the single step to navigate
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated value (maybe a Value, PureExpression, or
     * StreamExpression)
     */
    private CompiledExpression applySingleStepFilter(Value parentValue, Step step, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        return switch (step) {
        case KeyStep keyStep                       ->
            applyKeyStepFilter(parentValue, keyStep.getId(), functionIdentifier, arguments, context);
        case IndexStep indexStep                   ->
            applyIndexStepFilter(parentValue, indexStep.getIndex().intValue(), functionIdentifier, arguments, context);
        case ArraySlicingStep slicingStep          ->
            applySlicingStepFilter(parentValue, slicingStep, functionIdentifier, arguments, context);
        case WildcardStep ignored                  ->
            applyWildcardStepFilter(parentValue, functionIdentifier, arguments, context);
        case RecursiveKeyStep recursiveKeyStep     ->
            applyRecursiveKeyStepFilter(parentValue, recursiveKeyStep.getId(), functionIdentifier, arguments, context);
        case RecursiveWildcardStep ignored         ->
            applyRecursiveWildcardStepFilter(parentValue, functionIdentifier, arguments, context);
        case RecursiveIndexStep recursiveIndexStep -> applyRecursiveIndexStepFilter(parentValue,
                recursiveIndexStep.getIndex().intValue(), functionIdentifier, arguments, context);
        case AttributeUnionStep attributeUnionStep -> applyAttributeUnionStepFilter(parentValue,
                attributeUnionStep.getAttributes(), functionIdentifier, arguments, context);
        case IndexUnionStep indexUnionStep         ->
            applyIndexUnionStepFilter(parentValue, indexUnionStep.getIndices(), functionIdentifier, arguments, context);
        case AttributeFinderStep ignored           -> Value.error(ATTRIBUTE_FINDER_NOT_PERMITTED_IN_FILTER);
        case HeadAttributeFinderStep ignored       -> Value.error(ATTRIBUTE_FINDER_NOT_PERMITTED_IN_FILTER);
        case ConditionStep conditionStep           ->
            applyConditionStepFilter(parentValue, conditionStep, functionIdentifier, arguments, context);
        case ExpressionStep expressionStep         ->
            applyExpressionStepFilter(parentValue, expressionStep, functionIdentifier, arguments, context);
        default                                    ->
            throw new SaplCompilerException("Step type not supported: " + step.getClass().getSimpleName());
        };
    }

    /**
     * Applies a filter function to an object field accessed by key.
     * <p>
     * For arrays: applies the key step to each array element (implicit array
     * mapping).
     * For objects: applies the filter to the specified field.
     *
     * @param parentValue the parent object or array value
     * @param key the field key
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated object/array with the filtered field(s)
     */
    private CompiledExpression applyKeyStepFilter(Value parentValue, String key, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        // Handle implicit array mapping: apply key step to each element
        if (parentValue instanceof ArrayValue arrayValue) {
            // Check if we can fold a constant (all arguments are values)
            if (arguments.nature() == Nature.VALUE) {
                val builder = ArrayValue.builder();
                for (val element : arrayValue) {
                    val result = applyKeyStepFilter(element, key, functionIdentifier, arguments, context);

                    if (result instanceof ErrorValue) {
                        return result;
                    }

                    if (!(result instanceof Value resultValue)) {
                        // Should not happen with VALUE arguments, but be defensive
                        return Value.error("Unexpected non-value result in array mapping with constant arguments.");
                    }

                    builder.add(resultValue);
                }
                return builder.build();
            } else {
                // Dynamic arguments: need runtime expression for array mapping
                return createKeyStepArrayMappingExpression(arrayValue, key, functionIdentifier, arguments, context);
            }
        }

        if (!(parentValue instanceof ObjectValue objectValue)) {
            return Value.error("Cannot apply key step to non-object value.");
        }

        val fieldValue = objectValue.get(key);
        if (fieldValue == null) {
            return Value.error("Field '" + key + "' not found in object.");
        }

        // Handle based on argument nature to support dynamic filter operations
        return switch (arguments.nature()) {
        case VALUE  -> {
            val result = applyFilterFunctionToValue(fieldValue, functionIdentifier, arguments, context);
            if (!(result instanceof Value filteredValue)) {
                // Defensive: should not happen with VALUE arguments
                yield Value.error("Unexpected non-value result from filter with constant arguments.");
            }
            val builder = ObjectValue.builder();
            for (val entry : objectValue.entrySet()) {
                if (entry.getKey().equals(key)) {
                    if (!(filteredValue instanceof UndefinedValue)) {
                        builder.put(entry.getKey(), filteredValue);
                    }
                } else {
                    builder.put(entry.getKey(), entry.getValue());
                }
            }
            yield builder.build();
        }
        case PURE   -> // Dynamic pure arguments: defer object reconstruction to runtime
            new PureExpression(ctx -> {
                val valueArguments = new ArrayList<Value>(arguments.arguments().length + 1);
                valueArguments.add(fieldValue);
                for (val argument : arguments.arguments()) {
                    switch (argument) {
                    case Value value                   -> valueArguments.add(value);
                    case PureExpression pureExpression -> valueArguments.add(pureExpression.evaluate(ctx));
                    case StreamExpression ignored      -> throw new SaplCompilerException(
                            "Stream expression in pure filter arguments. Should not be possible.");
                    }
                }
                val invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                val filterResult = ctx.functionBroker().evaluateFunction(invocation);

                val builder = ObjectValue.builder();
                if (filterResult instanceof UndefinedValue) {
                    // Remove field
                    for (val entry : objectValue.entrySet()) {
                        if (!entry.getKey().equals(key)) {
                            builder.put(entry.getKey(), entry.getValue());
                        }
                    }
                } else {
                    // Replace field value
                    for (val entry : objectValue.entrySet()) {
                        if (entry.getKey().equals(key)) {
                            builder.put(entry.getKey(), filterResult);
                        } else {
                            builder.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
                return builder.build();
            }, arguments.isSubscriptionScoped());
        case STREAM -> {
            // Dynamic streaming arguments: defer object reconstruction to runtime with
            // reactive streams
            val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux)
                    .toList();
            val stream  = reactor.core.publisher.Flux.combineLatest(sources, argValues -> {
                            val valueArguments = new ArrayList<Value>(argValues.length + 1);
                            valueArguments.add(fieldValue);
                            for (var argValue : argValues) {
                                valueArguments.add((Value) argValue);
                            }
                            val invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                            val filterResult = context.getFunctionBroker().evaluateFunction(invocation);

                            val builder = ObjectValue.builder();
                            if (filterResult instanceof UndefinedValue) {
                                // Remove field
                                for (val entry : objectValue.entrySet()) {
                                    if (!entry.getKey().equals(key)) {
                                        builder.put(entry.getKey(), entry.getValue());
                                    }
                                }
                                return builder.build();
                            } else {
                                // Replace field value
                                for (val entry : objectValue.entrySet()) {
                                    if (entry.getKey().equals(key)) {
                                        builder.put(entry.getKey(), filterResult);
                                    } else {
                                        builder.put(entry.getKey(), entry.getValue());
                                    }
                                }
                                return (Value) builder.build();
                            }
                        });
            yield new StreamExpression(stream);
        }
        };
    }

    /**
     * Creates a runtime expression for key step array mapping with dynamic
     * arguments.
     * <p>
     * Used when arguments are PURE or STREAM and implicit array mapping is needed.
     *
     * @param arrayValue the array value to map over
     * @param key the field key to access in each element
     * @param functionIdentifier the function identifier
     * @param arguments the compiled filter arguments
     * @param context the compilation context
     * @return a PureExpression or StreamExpression
     */
    private CompiledExpression createKeyStepArrayMappingExpression(ArrayValue arrayValue, String key,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        boolean isStream = arguments.nature() == Nature.STREAM;

        if (isStream) {
            // Stream case: arguments change over time
            val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux)
                    .toList();
            val stream  = reactor.core.publisher.Flux.combineLatest(sources, argValues -> {
                            val builder = ArrayValue.builder();
                            for (val element : arrayValue) {
                                // Each element should be an object with the key field
                                if (!(element instanceof ObjectValue elementObj)) {
                                    return Value.error("Cannot apply key step to non-object array element.");
                                }

                                val fieldValue = elementObj.get(key);
                                if (fieldValue == null) {
                                    // Field not found, skip this element
                                    continue;
                                }

                                // Apply filter function to field value
                                val valueArguments = new ArrayList<Value>(argValues.length + 1);
                                valueArguments.add(fieldValue);
                                for (var argValue : argValues) {
                                    valueArguments.add((Value) argValue);
                                }
                                val invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                                val filterResult = context.getFunctionBroker().evaluateFunction(invocation);

                                // Rebuild element object with filtered field
                                val elemBuilder = ObjectValue.builder();
                                if (filterResult instanceof UndefinedValue) {
                                    // Remove field from element
                                    for (val entry : elementObj.entrySet()) {
                                        if (!entry.getKey().equals(key)) {
                                            elemBuilder.put(entry.getKey(), entry.getValue());
                                        }
                                    }
                                } else {
                                    // Replace field value in element
                                    for (val entry : elementObj.entrySet()) {
                                        if (entry.getKey().equals(key)) {
                                            elemBuilder.put(entry.getKey(), filterResult);
                                        } else {
                                            elemBuilder.put(entry.getKey(), entry.getValue());
                                        }
                                    }
                                }
                                builder.add(elemBuilder.build());
                            }
                            return builder.build();
                        });
            return new StreamExpression(stream);
        } else {
            // Pure case: arguments are dynamic but don't stream
            return new PureExpression(ctx -> {
                val builder = ArrayValue.builder();
                for (val element : arrayValue) {
                    // Each element should be an object with the key field
                    if (!(element instanceof ObjectValue elementObj)) {
                        return Value.error("Cannot apply key step to non-object array element.");
                    }

                    val fieldValue = elementObj.get(key);
                    if (fieldValue == null) {
                        // Field not found, skip this element
                        continue;
                    }

                    // Evaluate arguments
                    val valueArguments = new ArrayList<Value>(arguments.arguments().length + 1);
                    valueArguments.add(fieldValue);
                    for (val argument : arguments.arguments()) {
                        switch (argument) {
                        case Value value                   -> valueArguments.add(value);
                        case PureExpression pureExpression -> valueArguments.add(pureExpression.evaluate(ctx));
                        case StreamExpression ignored      -> throw new SaplCompilerException(
                                "Stream expression in pure filter arguments. Should not be possible.");
                        }
                    }
                    val invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                    val filterResult = ctx.functionBroker().evaluateFunction(invocation);

                    // Rebuild element object with filtered field
                    val elemBuilder = ObjectValue.builder();
                    if (filterResult instanceof UndefinedValue) {
                        // Remove field from element
                        for (val entry : elementObj.entrySet()) {
                            if (!entry.getKey().equals(key)) {
                                elemBuilder.put(entry.getKey(), entry.getValue());
                            }
                        }
                    } else {
                        // Replace field value in element
                        for (val entry : elementObj.entrySet()) {
                            if (entry.getKey().equals(key)) {
                                elemBuilder.put(entry.getKey(), filterResult);
                            } else {
                                elemBuilder.put(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                    builder.add(elemBuilder.build());
                }
                return builder.build();
            }, arguments.isSubscriptionScoped());
        }
    }

    /**
     * Applies a filter function to an array element accessed by index.
     *
     * @param parentValue the parent array value
     * @param index the element index
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated array with the filtered element
     */
    private CompiledExpression applyIndexStepFilter(Value parentValue, int index, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Value.error("Cannot apply index step to non-array value.");
        }

        val normalizedIndex = normalizeIndex(index, arrayValue.size());
        if (normalizedIndex < 0 || normalizedIndex >= arrayValue.size()) {
            return Value.error("Array index out of bounds: " + index + " (size: " + arrayValue.size() + ").");
        }

        val elementValue = arrayValue.get(normalizedIndex);

        return switch (arguments.nature()) {
        case VALUE  -> {
            val result = applyFilterFunctionToValue(elementValue, functionIdentifier, arguments, context);
            if (!(result instanceof Value filteredValue)) {
                yield Value.error("Unexpected non-value result with constant arguments.");
            }

            val builder = ArrayValue.builder();
            for (int i = 0; i < arrayValue.size(); i++) {
                if (i == normalizedIndex) {
                    if (!(filteredValue instanceof UndefinedValue)) {
                        builder.add(filteredValue);
                    }
                } else {
                    builder.add(arrayValue.get(i));
                }
            }
            yield builder.build();
        }
        case PURE   -> new PureExpression(ctx -> {
                    val valueArguments = new ArrayList<Value>(arguments.arguments().length + 1);
                    valueArguments.add(elementValue);
                    for (val argument : arguments.arguments()) {
                        switch (argument) {
                        case Value value                       -> valueArguments.add(value);
                        case PureExpression pureExpression     -> valueArguments.add(pureExpression.evaluate(ctx));
                        case StreamExpression ignored          -> throw new SaplCompilerException(
                                "Stream expression in pure filter arguments. Should not be possible.");
                        }
                    }
                    val     invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                    val     filterResult = ctx.functionBroker().evaluateFunction(invocation);

                    val builder = ArrayValue.builder();
                    for (int i = 0; i < arrayValue.size(); i++) {
                        if (i == normalizedIndex) {
                            if (!(filterResult instanceof UndefinedValue)) {
                                builder.add(filterResult);
                            }
                        } else {
                            builder.add(arrayValue.get(i));
                        }
                    }
                    return builder.build();
                }, arguments.isSubscriptionScoped());
        case STREAM -> {
            val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux)
                    .toList();
            val stream  = reactor.core.publisher.Flux.combineLatest(sources, argValues -> {
                            val valueArguments = new ArrayList<Value>(argValues.length + 1);
                            valueArguments.add(elementValue);
                            for (var argValue : argValues) {
                                valueArguments.add((Value) argValue);
                            }
                            val invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                            val filterResult = context.getFunctionBroker().evaluateFunction(invocation);

                            val builder = ArrayValue.builder();
                            for (int i = 0; i < arrayValue.size(); i++) {
                                if (i == normalizedIndex) {
                                    if (!(filterResult instanceof UndefinedValue)) {
                                        builder.add(filterResult);
                                    }
                                } else {
                                    builder.add(arrayValue.get(i));
                                }
                            }
                            return (Value) builder.build();
                        });
            yield new StreamExpression(stream);
        }
        };
    }

    /**
     * Applies a filter function to array elements accessed by slice.
     *
     * @param parentValue the parent array value
     * @param slicingStep the slicing step with start, end, and step parameters
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated array with filtered slice elements
     */
    private CompiledExpression applySlicingStepFilter(Value parentValue, ArraySlicingStep slicingStep,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Value.error("Cannot apply slicing step to non-array value.");
        }

        val arraySize = arrayValue.size();
        val step      = slicingStep.getStep() != null ? slicingStep.getStep().intValue() : 1;

        if (step == 0) {
            return Value.error("Step must not be zero.");
        }

        var index = slicingStep.getIndex() != null ? slicingStep.getIndex().intValue() : 0;
        if (index < 0) {
            index += arraySize;
        }

        var to = slicingStep.getTo() != null ? slicingStep.getTo().intValue() : arraySize;
        if (to < 0) {
            to += arraySize;
        }

        val finalIndex = index;
        val finalTo    = to;

        return switch (arguments.nature()) {
        case VALUE  -> {
            val builder = ArrayValue.builder();
            for (int i = 0; i < arraySize; i++) {
                if (isInNormalizedSlice(i, finalIndex, finalTo, step)) {
                    val elementValue = arrayValue.get(i);
                    val result       = applyFilterFunctionToValue(elementValue, functionIdentifier, arguments, context);

                    if (!(result instanceof Value filteredValue)) {
                        yield Value.error("Unexpected non-value result with constant arguments.");
                    }

                    if (!(filteredValue instanceof UndefinedValue)) {
                        builder.add(filteredValue);
                    }
                } else {
                    builder.add(arrayValue.get(i));
                }
            }
            yield builder.build();
        }
        case PURE   -> new PureExpression(ctx -> {
                    val builder = ArrayValue.builder();
                    for (int i = 0; i < arraySize; i++) {
                        if (isInNormalizedSlice(i, finalIndex, finalTo, step)) {
                            val elementValue = arrayValue.get(i);

                            val valueArguments = new ArrayList<Value>(arguments.arguments().length + 1);
                            valueArguments.add(elementValue);
                            for (val argument : arguments.arguments()) {
                                switch (argument) {
                                case Value value                       -> valueArguments.add(value);
                                case PureExpression pureExpression     ->
                                    valueArguments.add(pureExpression.evaluate(ctx));
                                case StreamExpression ignored          -> throw new SaplCompilerException(
                                        "Stream expression in pure filter arguments. Should not be possible.");
                                }
                            }
                            val     invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                            val     filterResult = ctx.functionBroker().evaluateFunction(invocation);

                            if (!(filterResult instanceof UndefinedValue)) {
                                builder.add(filterResult);
                            }
                        } else {
                            builder.add(arrayValue.get(i));
                        }
                    }
                    return builder.build();
                }, arguments.isSubscriptionScoped());
        case STREAM -> {
            val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux)
                    .toList();
            val stream  = reactor.core.publisher.Flux.combineLatest(sources, argValues -> {
                            val builder = ArrayValue.builder();
                            for (int i = 0; i < arraySize; i++) {
                                if (isInNormalizedSlice(i, finalIndex, finalTo, step)) {
                                    val elementValue = arrayValue.get(i);

                                    val valueArguments = new ArrayList<Value>(argValues.length + 1);
                                    valueArguments.add(elementValue);
                                    for (var argValue : argValues) {
                                        valueArguments.add((Value) argValue);
                                    }
                                    val invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                                    val filterResult = context.getFunctionBroker().evaluateFunction(invocation);

                                    if (!(filterResult instanceof UndefinedValue)) {
                                        builder.add(filterResult);
                                    }
                                } else {
                                    builder.add(arrayValue.get(i));
                                }
                            }
                            return (Value) builder.build();
                        });
            yield new StreamExpression(stream);
        }
        };
    }

    private boolean isInNormalizedSlice(int i, int from, int to, int step) {
        if (i < from || i >= to) {
            return false;
        }
        if (step > 0) {
            return (i - from) % step == 0;
        }
        return (to - i) % step == 0;
    }

    /**
     * Applies a filter function to a nested path through an object field.
     * <p>
     * For arrays: applies the nested path to each array element (implicit array
     * mapping).
     * For objects: applies the filter to the specified nested field.
     *
     * @param parentValue the parent object or array value
     * @param fieldName the field name to navigate through
     * @param steps all path steps
     * @param stepIndex the current step index (points to next step after field)
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated parent object/array
     */
    private Value applyFilterToNestedPath(Value parentValue, String fieldName,
            org.eclipse.emf.common.util.EList<Step> steps, int stepIndex, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        // Handle implicit array mapping: apply nested path to each element
        if (parentValue instanceof ArrayValue arrayValue) {
            val builder = ArrayValue.builder();
            for (val element : arrayValue) {
                val result = applyFilterToNestedPath(element, fieldName, steps, stepIndex, functionIdentifier,
                        arguments, context);

                if (result instanceof ErrorValue) {
                    return result;
                }

                builder.add(result);
            }
            return builder.build();
        }

        if (!(parentValue instanceof ObjectValue objectValue)) {
            return Value.error("Cannot access field '" + fieldName + "' on non-object value.");
        }

        val fieldValue = objectValue.get(fieldName);
        if (fieldValue == null) {
            return parentValue;
        }

        val updatedFieldValue = applyFilterFunctionToPathRecursive(fieldValue, steps, stepIndex, functionIdentifier,
                arguments, context);

        if (updatedFieldValue instanceof ErrorValue) {
            return updatedFieldValue;
        }

        val builder = ObjectValue.builder();
        for (val entry : objectValue.entrySet()) {
            if (entry.getKey().equals(fieldName)) {
                builder.put(fieldName, updatedFieldValue);
            } else {
                builder.put(entry.getKey(), entry.getValue());
            }
        }

        return builder.build();
    }

    /**
     * Applies a filter function to a nested path through an array element.
     *
     * @param parentValue the parent array value
     * @param index the array index to navigate through
     * @param steps all path steps
     * @param stepIndex the current step index (points to next step after index)
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated parent array
     */
    private Value applyFilterToNestedArrayElement(Value parentValue, int index,
            org.eclipse.emf.common.util.EList<Step> steps, int stepIndex, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Value.error("Cannot access array index on non-array value.");
        }

        val normalizedIndex = normalizeIndex(index, arrayValue.size());
        if (normalizedIndex < 0 || normalizedIndex >= arrayValue.size()) {
            return parentValue;
        }

        val elementValue        = arrayValue.get(normalizedIndex);
        val updatedElementValue = applyFilterFunctionToPathRecursive(elementValue, steps, stepIndex, functionIdentifier,
                arguments, context);

        if (updatedElementValue instanceof ErrorValue) {
            return updatedElementValue;
        }

        val builder = ArrayValue.builder();
        for (int i = 0; i < arrayValue.size(); i++) {
            if (i == normalizedIndex) {
                builder.add(updatedElementValue);
            } else {
                builder.add(arrayValue.get(i));
            }
        }

        return builder.build();
    }

    /**
     * Applies a filter function to nested paths through array slice elements.
     *
     * @param parentValue the parent array value
     * @param slicingStep the slicing step defining the slice range
     * @param steps all path steps
     * @param stepIndex the current step index (points to next step after slice)
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated parent array
     */
    private Value applyFilterToNestedArraySlice(Value parentValue, ArraySlicingStep slicingStep,
            org.eclipse.emf.common.util.EList<Step> steps, int stepIndex, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Value.error("Cannot apply slicing step to non-array value.");
        }

        val arraySize = arrayValue.size();
        val step      = slicingStep.getStep() != null ? slicingStep.getStep().intValue() : 1;

        if (step == 0) {
            return Value.error("Step must not be zero.");
        }

        var index = slicingStep.getIndex() != null ? slicingStep.getIndex().intValue() : 0;
        if (index < 0) {
            index += arraySize;
        }

        var to = slicingStep.getTo() != null ? slicingStep.getTo().intValue() : arraySize;
        if (to < 0) {
            to += arraySize;
        }

        val builder = ArrayValue.builder();
        for (int i = 0; i < arraySize; i++) {
            if (isInNormalizedSlice(i, index, to, step)) {
                val elementValue        = arrayValue.get(i);
                val updatedElementValue = applyFilterFunctionToPathRecursive(elementValue, steps, stepIndex,
                        functionIdentifier, arguments, context);

                if (updatedElementValue instanceof ErrorValue) {
                    return updatedElementValue;
                }

                builder.add(updatedElementValue);
            } else {
                builder.add(arrayValue.get(i));
            }
        }

        return builder.build();
    }

    /**
     * Applies a filter statement with 'each' keyword to array elements.
     * <p>
     * The 'each' keyword applies the filter to each element of an array.
     * Elements that result in UNDEFINED are filtered out.
     *
     * @param parentValue the parent array value
     * @param target the target path (maybe null)
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the filtered array
     */
    private Value applyEachFilterStatement(Value parentValue, io.sapl.grammar.sapl.BasicRelative target,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Value.error("Cannot use 'each' keyword with non-array values.");
        }

        val builder = ArrayValue.builder();
        for (val element : arrayValue) {
            Value result;

            if (target != null && !target.getSteps().isEmpty()) {
                result = applyFilterFunctionToPath(element, target.getSteps(), functionIdentifier, arguments, context);
            } else {
                val filterResult = applyFilterFunctionToValue(element, functionIdentifier, arguments, context);
                if (!(filterResult instanceof Value)) {
                    throw new SaplCompilerException(
                            "Dynamic filter arguments are not supported in extended 'each' filters.");
                }
                result = (Value) filterResult;
            }

            if (result instanceof ErrorValue) {
                return result;
            }

            if (!(result instanceof UndefinedValue)) {
                builder.add(result);
            }
        }

        return builder.build();
    }

    /**
     * Applies a filter function to all elements/fields via wildcard step.
     * <p>
     * For arrays: applies filter to each element (preserves array structure).
     * For objects: applies filter to each field value (preserves object structure).
     *
     * @param parentValue the parent value (array or object)
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the filtered value with all elements/fields transformed
     */
    private CompiledExpression applyWildcardStepFilter(Value parentValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (parentValue instanceof ArrayValue arrayValue) {
            return applyWildcardToArray(arrayValue, functionIdentifier, arguments, context);
        }

        if (parentValue instanceof ObjectValue objectValue) {
            return applyWildcardToObject(objectValue, functionIdentifier, arguments, context);
        }

        return Value.error("Cannot apply wildcard step to non-array/non-object value.");
    }

    private CompiledExpression applyWildcardToArray(ArrayValue arrayValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        return switch (arguments.nature()) {
        case VALUE  -> {
            val builder = ArrayValue.builder();
            for (val element : arrayValue) {
                val result = applyFilterFunctionToValue(element, functionIdentifier, arguments, context);

                if (!(result instanceof Value filteredValue)) {
                    yield Value.error("Unexpected non-value result with constant arguments.");
                }

                if (!(filteredValue instanceof UndefinedValue)) {
                    builder.add(filteredValue);
                }
            }
            yield builder.build();
        }
        case PURE   -> new PureExpression(ctx -> {
                    val builder = ArrayValue.builder();
                    for (val element : arrayValue) {
                        val valueArguments = new ArrayList<Value>(arguments.arguments().length + 1);
                        valueArguments.add(element);
                        for (val argument : arguments.arguments()) {
                            switch (argument) {
                            case Value value                       -> valueArguments.add(value);
                            case PureExpression pureExpression     -> valueArguments.add(pureExpression.evaluate(ctx));
                            case StreamExpression ignored          -> throw new SaplCompilerException(
                                    "Stream expression in pure filter arguments. Should not be possible.");
                            }
                        }
                        val     invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                        val     filterResult = ctx.functionBroker().evaluateFunction(invocation);

                        if (!(filterResult instanceof UndefinedValue)) {
                            builder.add(filterResult);
                        }
                    }
                    return builder.build();
                }, arguments.isSubscriptionScoped());
        case STREAM -> {
            val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux)
                    .toList();
            val stream  = reactor.core.publisher.Flux.combineLatest(sources, argValues -> {
                            val builder = ArrayValue.builder();
                            for (val element : arrayValue) {
                                val valueArguments = new ArrayList<Value>(argValues.length + 1);
                                valueArguments.add(element);
                                for (var argValue : argValues) {
                                    valueArguments.add((Value) argValue);
                                }
                                val invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                                val filterResult = context.getFunctionBroker().evaluateFunction(invocation);

                                if (!(filterResult instanceof UndefinedValue)) {
                                    builder.add(filterResult);
                                }
                            }
                            return (Value) builder.build();
                        });
            yield new StreamExpression(stream);
        }
        };
    }

    private CompiledExpression applyWildcardToObject(ObjectValue objectValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        return switch (arguments.nature()) {
        case VALUE  -> {
            val builder = ObjectValue.builder();
            for (val entry : objectValue.entrySet()) {
                val result = applyFilterFunctionToValue(entry.getValue(), functionIdentifier, arguments, context);

                if (!(result instanceof Value filteredValue)) {
                    yield Value.error("Unexpected non-value result with constant arguments.");
                }

                if (!(filteredValue instanceof UndefinedValue)) {
                    builder.put(entry.getKey(), filteredValue);
                }
            }
            yield builder.build();
        }
        case PURE   -> new PureExpression(ctx -> {
                    val builder = ObjectValue.builder();
                    for (val entry : objectValue.entrySet()) {
                        val valueArguments = new ArrayList<Value>(arguments.arguments().length + 1);
                        valueArguments.add(entry.getValue());
                        for (val argument : arguments.arguments()) {
                            switch (argument) {
                            case Value value                       -> valueArguments.add(value);
                            case PureExpression pureExpression     -> valueArguments.add(pureExpression.evaluate(ctx));
                            case StreamExpression ignored          -> throw new SaplCompilerException(
                                    "Stream expression in pure filter arguments. Should not be possible.");
                            }
                        }
                        val     invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                        val     filterResult = ctx.functionBroker().evaluateFunction(invocation);

                        if (!(filterResult instanceof UndefinedValue)) {
                            builder.put(entry.getKey(), filterResult);
                        }
                    }
                    return builder.build();
                }, arguments.isSubscriptionScoped());
        case STREAM -> {
            val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux)
                    .toList();
            val stream  = reactor.core.publisher.Flux.combineLatest(sources, argValues -> {
                            val builder = ObjectValue.builder();
                            for (val entry : objectValue.entrySet()) {
                                val valueArguments = new ArrayList<Value>(argValues.length + 1);
                                valueArguments.add(entry.getValue());
                                for (var argValue : argValues) {
                                    valueArguments.add((Value) argValue);
                                }
                                val invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                                val filterResult = context.getFunctionBroker().evaluateFunction(invocation);

                                if (!(filterResult instanceof UndefinedValue)) {
                                    builder.put(entry.getKey(), filterResult);
                                }
                            }
                            return (Value) builder.build();
                        });
            yield new StreamExpression(stream);
        }
        };
    }

    /**
     * Applies a filter function to nested paths through wildcard elements/fields.
     *
     * @param parentValue the parent value (array or object)
     * @param steps all path steps
     * @param stepIndex the current step index (points to next step after wildcard)
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated parent value
     */
    private Value applyFilterToNestedWildcard(Value parentValue, org.eclipse.emf.common.util.EList<Step> steps,
            int stepIndex, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (parentValue instanceof ArrayValue arrayValue) {
            val builder = ArrayValue.builder();
            for (val element : arrayValue) {
                val updatedElement = applyFilterFunctionToPathRecursive(element, steps, stepIndex, functionIdentifier,
                        arguments, context);

                if (updatedElement instanceof ErrorValue) {
                    return updatedElement;
                }

                builder.add(updatedElement);
            }
            return builder.build();
        }

        if (parentValue instanceof ObjectValue objectValue) {
            val builder = ObjectValue.builder();
            for (val entry : objectValue.entrySet()) {
                val updatedValue = applyFilterFunctionToPathRecursive(entry.getValue(), steps, stepIndex,
                        functionIdentifier, arguments, context);

                if (updatedValue instanceof ErrorValue) {
                    return updatedValue;
                }

                builder.put(entry.getKey(), updatedValue);
            }
            return builder.build();
        }

        return Value.error("Cannot apply wildcard step to non-array/non-object value.");
    }

    /**
     * Applies a filter function to all matching keys recursively (recursive
     * descent).
     * <p>
     * Recursively searches for the key in all nested objects and arrays,
     * applies the filter to all matching values.
     *
     * @param parentValue the parent value to search
     * @param key the key to match
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated value with filters applied to all matching keys
     */
    private CompiledExpression applyRecursiveKeyStepFilter(Value parentValue, String key, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        // Check for dynamic arguments - not yet supported in recursive filters
        if (arguments.nature() != Nature.VALUE) {
            return Value.error("Dynamic filter arguments not yet supported in recursive key filters.");
        }

        if (parentValue instanceof ObjectValue objectValue) {
            return applyRecursiveKeyToObject(objectValue, key, functionIdentifier, arguments, context);
        }

        if (parentValue instanceof ArrayValue arrayValue) {
            return applyRecursiveKeyToArray(arrayValue, key, functionIdentifier, arguments, context);
        }

        // Non-array/non-object: no matches, return unchanged
        return parentValue;
    }

    private Value applyRecursiveKeyToObject(ObjectValue objectValue, String key, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val builder = ObjectValue.builder();

        for (val entry : objectValue.entrySet()) {
            if (entry.getKey().equals(key)) {
                // This field matches - apply filter
                val result = applyFilterFunctionToValue(entry.getValue(), functionIdentifier, arguments, context);

                if (!(result instanceof Value filteredValue)) {
                    return Value.error("Unexpected non-value result with constant arguments.");
                }

                if (filteredValue instanceof ErrorValue) {
                    return filteredValue;
                }

                if (!(filteredValue instanceof UndefinedValue)) {
                    builder.put(entry.getKey(), filteredValue);
                }
            } else {
                // This field doesn't match - recurse into it
                val recursedResult = applyRecursiveKeyStepFilter(entry.getValue(), key, functionIdentifier, arguments,
                        context);

                if (!(recursedResult instanceof Value recursedValue)) {
                    return Value.error("Unexpected non-value result in recursive key filter.");
                }

                if (recursedValue instanceof ErrorValue) {
                    return recursedValue;
                }

                builder.put(entry.getKey(), recursedValue);
            }
        }

        return builder.build();
    }

    private Value applyRecursiveKeyToArray(ArrayValue arrayValue, String key, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val builder = ArrayValue.builder();

        for (val element : arrayValue) {
            // Arrays don't have keys, so just recurse into each element
            val recursedResult = applyRecursiveKeyStepFilter(element, key, functionIdentifier, arguments, context);

            if (!(recursedResult instanceof Value recursedValue)) {
                return Value.error("Unexpected non-value result in recursive key filter.");
            }

            if (recursedValue instanceof ErrorValue) {
                return recursedValue;
            }

            builder.add(recursedValue);
        }

        return builder.build();
    }

    /**
     * Applies a filter function recursively to all nested structures (recursive
     * wildcard).
     * <p>
     * Note: For filtering, recursive wildcard behaves like regular wildcard at the
     * current level.
     * This matches the original implementation behavior where recursive descent
     * doesn't translate well to filtering.
     *
     * @param parentValue the parent value to search
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated value with filters applied recursively
     */
    private CompiledExpression applyRecursiveWildcardStepFilter(Value parentValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        // For filtering, @..* is treated as @.* (regular wildcard)
        // This matches the original implementation where recursive descent doesn't
        // translate well to filtering
        return applyWildcardStepFilter(parentValue, functionIdentifier, arguments, context);
    }

    /**
     * Applies a filter function to all matching indices recursively (recursive
     * index descent).
     * <p>
     * Recursively searches for the index in all nested arrays, applies the filter
     * to all matching elements.
     *
     * @param parentValue the parent value to search
     * @param index the index to match
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated value with filters applied to all matching indices
     */
    private CompiledExpression applyRecursiveIndexStepFilter(Value parentValue, int index, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        // Check for dynamic arguments - not yet supported in recursive filters
        if (arguments.nature() != Nature.VALUE) {
            return Value.error("Dynamic filter arguments not yet supported in recursive index filters.");
        }

        if (parentValue instanceof ArrayValue arrayValue) {
            return applyRecursiveIndexToArray(arrayValue, index, functionIdentifier, arguments, context);
        }

        if (parentValue instanceof ObjectValue objectValue) {
            return applyRecursiveIndexToObject(objectValue, index, functionIdentifier, arguments, context);
        }

        // Non-array/non-object: no matches, return unchanged
        return parentValue;
    }

    private Value applyRecursiveIndexToArray(ArrayValue arrayValue, int index, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val builder      = ArrayValue.builder();
        val arraySize    = arrayValue.size();
        val idx          = normalizeIndex(index, arraySize);
        var elementCount = 0;

        for (val element : arrayValue) {
            // First, recurse into the element to handle any nested structures
            val recursedResult = applyRecursiveIndexStepFilter(element, index, functionIdentifier, arguments, context);

            if (!(recursedResult instanceof Value recursedValue)) {
                return Value.error("Unexpected non-value result in recursive index filter.");
            }

            if (recursedValue instanceof ErrorValue) {
                return recursedValue;
            }

            // Then, if this is the matching index at THIS level, apply filter
            if (elementCount == idx) {
                val result = applyFilterFunctionToValue(recursedValue, functionIdentifier, arguments, context);

                if (!(result instanceof Value filteredValue)) {
                    return Value.error("Unexpected non-value result with constant arguments.");
                }

                if (filteredValue instanceof ErrorValue) {
                    return filteredValue;
                }

                if (!(filteredValue instanceof UndefinedValue)) {
                    builder.add(filteredValue);
                }
            } else {
                builder.add(recursedValue);
            }
            elementCount++;
        }

        return builder.build();
    }

    private Value applyRecursiveIndexToObject(ObjectValue objectValue, int index, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val builder = ObjectValue.builder();

        for (val entry : objectValue.entrySet()) {
            // Objects don't have indices, so just recurse into each field value
            val recursedResult = applyRecursiveIndexStepFilter(entry.getValue(), index, functionIdentifier, arguments,
                    context);

            if (!(recursedResult instanceof Value recursedValue)) {
                return Value.error("Unexpected non-value result in recursive index filter.");
            }

            if (recursedValue instanceof ErrorValue) {
                return recursedValue;
            }

            builder.put(entry.getKey(), recursedValue);
        }

        return builder.build();
    }

    private static int normalizeIndex(int idx, int size) {
        return idx < 0 ? size + idx : idx;
    }

    /**
     * Applies a filter function to selected object attributes (attribute union).
     * <p>
     * Applies the filter only to the specified attributes of an object.
     *
     * @param parentValue the parent object value
     * @param attributes the list of attribute names to filter
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated object with filtered attributes
     */
    private CompiledExpression applyAttributeUnionStepFilter(Value parentValue,
            org.eclipse.emf.common.util.EList<String> attributes, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ObjectValue objectValue)) {
            return Value.error("Cannot apply attribute union to non-object value.");
        }

        return switch (arguments.nature()) {
        case VALUE  -> {
            val builder = ObjectValue.builder();

            for (val entry : objectValue.entrySet()) {
                if (attributes.contains(entry.getKey())) {
                    // This attribute is in the union - apply filter
                    val result = applyFilterFunctionToValue(entry.getValue(), functionIdentifier, arguments, context);

                    if (!(result instanceof Value filteredValue)) {
                        yield Value.error("Unexpected non-value result with constant arguments.");
                    }

                    if (filteredValue instanceof ErrorValue) {
                        yield filteredValue;
                    }

                    if (!(filteredValue instanceof UndefinedValue)) {
                        builder.put(entry.getKey(), filteredValue);
                    }
                } else {
                    // This attribute is not in the union - keep unchanged
                    builder.put(entry.getKey(), entry.getValue());
                }
            }
            yield builder.build();
        }
        case PURE   -> new PureExpression(ctx -> {
                    val builder = ObjectValue.builder();

                    for (val entry : objectValue.entrySet()) {
                        if (attributes.contains(entry.getKey())) {
                            val valueArguments = new ArrayList<Value>(arguments.arguments().length + 1);
                            valueArguments.add(entry.getValue());
                            for (val argument : arguments.arguments()) {
                                switch (argument) {
                                case Value value                       -> valueArguments.add(value);
                                case PureExpression pureExpression     ->
                                    valueArguments.add(pureExpression.evaluate(ctx));
                                case StreamExpression ignored          -> throw new SaplCompilerException(
                                        "Stream expression in pure filter arguments. Should not be possible.");
                                }
                            }
                            val     invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                            val     filterResult = ctx.functionBroker().evaluateFunction(invocation);

                            if (!(filterResult instanceof UndefinedValue)) {
                                builder.put(entry.getKey(), filterResult);
                            }
                        } else {
                            builder.put(entry.getKey(), entry.getValue());
                        }
                    }
                    return builder.build();
                }, arguments.isSubscriptionScoped());
        case STREAM -> {
            val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux)
                    .toList();
            val stream  = reactor.core.publisher.Flux.combineLatest(sources, argValues -> {
                            val builder = ObjectValue.builder();

                            for (val entry : objectValue.entrySet()) {
                                if (attributes.contains(entry.getKey())) {
                                    val valueArguments = new ArrayList<Value>(argValues.length + 1);
                                    valueArguments.add(entry.getValue());
                                    for (var argValue : argValues) {
                                        valueArguments.add((Value) argValue);
                                    }
                                    val invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                                    val filterResult = context.getFunctionBroker().evaluateFunction(invocation);

                                    if (!(filterResult instanceof UndefinedValue)) {
                                        builder.put(entry.getKey(), filterResult);
                                    }
                                } else {
                                    builder.put(entry.getKey(), entry.getValue());
                                }
                            }
                            return (Value) builder.build();
                        });
            yield new StreamExpression(stream);
        }
        };
    }

    /**
     * Applies a filter function to selected array indices (index union).
     * <p>
     * Applies the filter only to elements at the specified indices.
     * Supports negative indices.
     *
     * @param parentValue the parent array value
     * @param indices the list of indices to filter
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated array with filtered elements
     */
    private CompiledExpression applyIndexUnionStepFilter(Value parentValue,
            org.eclipse.emf.common.util.EList<java.math.BigDecimal> indices, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Value.error("Cannot apply index union to non-array value.");
        }

        val arraySize = arrayValue.size();

        // Normalize indices (handle negative indices)
        val normalizedIndices = new ArrayList<Integer>();
        for (val index : indices) {
            int idx = index.intValue();
            if (idx < 0) {
                idx += arraySize;
            }
            if (idx >= 0 && idx < arraySize) {
                normalizedIndices.add(idx);
            }
        }

        return switch (arguments.nature()) {
        case VALUE  -> {
            val builder = ArrayValue.builder();

            for (int i = 0; i < arraySize; i++) {
                if (normalizedIndices.contains(i)) {
                    // This index is in the union - apply filter
                    val result = applyFilterFunctionToValue(arrayValue.get(i), functionIdentifier, arguments, context);

                    if (!(result instanceof Value filteredValue)) {
                        yield Value.error("Unexpected non-value result with constant arguments.");
                    }

                    if (filteredValue instanceof ErrorValue) {
                        yield filteredValue;
                    }

                    if (!(filteredValue instanceof UndefinedValue)) {
                        builder.add(filteredValue);
                    }
                } else {
                    // This index is not in the union - keep unchanged
                    builder.add(arrayValue.get(i));
                }
            }
            yield builder.build();
        }
        case PURE   -> new PureExpression(ctx -> {
                    val builder = ArrayValue.builder();

                    for (int i = 0; i < arraySize; i++) {
                        if (normalizedIndices.contains(i)) {
                            val valueArguments = new ArrayList<Value>(arguments.arguments().length + 1);
                            valueArguments.add(arrayValue.get(i));
                            for (val argument : arguments.arguments()) {
                                switch (argument) {
                                case Value value                       -> valueArguments.add(value);
                                case PureExpression pureExpression     ->
                                    valueArguments.add(pureExpression.evaluate(ctx));
                                case StreamExpression ignored          -> throw new SaplCompilerException(
                                        "Stream expression in pure filter arguments. Should not be possible.");
                                }
                            }
                            val     invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                            val     filterResult = ctx.functionBroker().evaluateFunction(invocation);

                            if (!(filterResult instanceof UndefinedValue)) {
                                builder.add(filterResult);
                            }
                        } else {
                            builder.add(arrayValue.get(i));
                        }
                    }
                    return builder.build();
                }, arguments.isSubscriptionScoped());
        case STREAM -> {
            val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux)
                    .toList();
            val stream  = reactor.core.publisher.Flux.combineLatest(sources, argValues -> {
                            val builder = ArrayValue.builder();

                            for (int i = 0; i < arraySize; i++) {
                                if (normalizedIndices.contains(i)) {
                                    val valueArguments = new ArrayList<Value>(argValues.length + 1);
                                    valueArguments.add(arrayValue.get(i));
                                    for (var argValue : argValues) {
                                        valueArguments.add((Value) argValue);
                                    }
                                    val invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
                                    val filterResult = context.getFunctionBroker().evaluateFunction(invocation);

                                    if (!(filterResult instanceof UndefinedValue)) {
                                        builder.add(filterResult);
                                    }
                                } else {
                                    builder.add(arrayValue.get(i));
                                }
                            }
                            return (Value) builder.build();
                        });
            yield new StreamExpression(stream);
        }
        };
    }

    /**
     * Applies a filter function to nested paths through recursive key descent.
     *
     * @param parentValue the parent value
     * @param key the key to match recursively
     * @param steps all path steps
     * @param stepIndex the current step index (points to next step after recursive
     * key)
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated parent value
     */
    private Value applyFilterToNestedRecursiveKey(Value parentValue, String key,
            org.eclipse.emf.common.util.EList<Step> steps, int stepIndex, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (parentValue instanceof ObjectValue objectValue) {
            val builder = ObjectValue.builder();

            for (val entry : objectValue.entrySet()) {
                if (entry.getKey().equals(key)) {
                    // This field matches - continue with remaining steps
                    val updatedValue = applyFilterFunctionToPathRecursive(entry.getValue(), steps, stepIndex,
                            functionIdentifier, arguments, context);

                    if (updatedValue instanceof ErrorValue) {
                        return updatedValue;
                    }

                    builder.put(entry.getKey(), updatedValue);
                } else {
                    // This field doesn't match - recurse into it
                    val recursedValue = applyFilterToNestedRecursiveKey(entry.getValue(), key, steps, stepIndex,
                            functionIdentifier, arguments, context);

                    if (recursedValue instanceof ErrorValue) {
                        return recursedValue;
                    }

                    builder.put(entry.getKey(), recursedValue);
                }
            }

            return builder.build();
        }

        if (parentValue instanceof ArrayValue arrayValue) {
            val builder = ArrayValue.builder();

            for (val element : arrayValue) {
                val recursedValue = applyFilterToNestedRecursiveKey(element, key, steps, stepIndex, functionIdentifier,
                        arguments, context);

                if (recursedValue instanceof ErrorValue) {
                    return recursedValue;
                }

                builder.add(recursedValue);
            }

            return builder.build();
        }

        return parentValue;
    }

    /**
     * Applies a filter function to nested paths through recursive wildcard descent.
     *
     * @param parentValue the parent value
     * @param steps all path steps
     * @param stepIndex the current step index (points to next step after recursive
     * wildcard)
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated parent value
     */
    private Value applyFilterToNestedRecursiveWildcard(Value parentValue, org.eclipse.emf.common.util.EList<Step> steps,
            int stepIndex, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        // For nested recursive wildcard, treat it like regular wildcard at current
        // level
        // This matches the original implementation behavior
        return applyFilterToNestedWildcard(parentValue, steps, stepIndex, functionIdentifier, arguments, context);
    }

    /**
     * Applies a filter function to nested paths through recursive index descent.
     *
     * @param parentValue the parent value
     * @param index the index to match recursively
     * @param steps all path steps
     * @param stepIndex the current step index (points to next step after recursive
     * index)
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated parent value
     */
    private Value applyFilterToNestedRecursiveIndex(Value parentValue, int index,
            org.eclipse.emf.common.util.EList<Step> steps, int stepIndex, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (parentValue instanceof ArrayValue arrayValue) {
            val builder      = ArrayValue.builder();
            val arraySize    = arrayValue.size();
            val idx          = normalizeIndex(index, arraySize);
            var elementCount = 0;

            for (val element : arrayValue) {
                // First, recurse into the element
                val recursedValue = applyFilterToNestedRecursiveIndex(element, index, steps, stepIndex,
                        functionIdentifier, arguments, context);

                if (recursedValue instanceof ErrorValue) {
                    return recursedValue;
                }

                // Then, if this is the matching index at THIS level, apply remaining steps
                if (elementCount == idx) {
                    val updatedValue = applyFilterFunctionToPathRecursive(recursedValue, steps, stepIndex,
                            functionIdentifier, arguments, context);

                    if (updatedValue instanceof ErrorValue) {
                        return updatedValue;
                    }

                    builder.add(updatedValue);
                } else {
                    builder.add(recursedValue);
                }
                elementCount++;
            }

            return builder.build();
        }

        if (parentValue instanceof ObjectValue objectValue) {
            val builder = ObjectValue.builder();

            for (val entry : objectValue.entrySet()) {
                val recursedValue = applyFilterToNestedRecursiveIndex(entry.getValue(), index, steps, stepIndex,
                        functionIdentifier, arguments, context);

                if (recursedValue instanceof ErrorValue) {
                    return recursedValue;
                }

                builder.put(entry.getKey(), recursedValue);
            }

            return builder.build();
        }

        return parentValue;
    }

    /**
     * Applies a filter function to nested paths through attribute union.
     *
     * @param parentValue the parent object value
     * @param attributes the list of attribute names
     * @param steps all path steps
     * @param stepIndex the current step index (points to next step after union)
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated parent object
     */
    private Value applyFilterToNestedAttributeUnion(Value parentValue,
            org.eclipse.emf.common.util.EList<String> attributes, org.eclipse.emf.common.util.EList<Step> steps,
            int stepIndex, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ObjectValue objectValue)) {
            return Value.error("Cannot apply attribute union to non-object value.");
        }

        val builder = ObjectValue.builder();

        for (val entry : objectValue.entrySet()) {
            if (attributes.contains(entry.getKey())) {
                // This attribute is in the union - continue with remaining steps
                val updatedValue = applyFilterFunctionToPathRecursive(entry.getValue(), steps, stepIndex,
                        functionIdentifier, arguments, context);

                if (updatedValue instanceof ErrorValue) {
                    return updatedValue;
                }

                builder.put(entry.getKey(), updatedValue);
            } else {
                // This attribute is not in the union - keep unchanged
                builder.put(entry.getKey(), entry.getValue());
            }
        }

        return builder.build();
    }

    /**
     * Applies a filter function to nested paths through index union.
     *
     * @param parentValue the parent array value
     * @param indices the list of indices
     * @param steps all path steps
     * @param stepIndex the current step index (points to next step after union)
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated parent array
     */
    private Value applyFilterToNestedIndexUnion(Value parentValue,
            org.eclipse.emf.common.util.EList<java.math.BigDecimal> indices,
            org.eclipse.emf.common.util.EList<Step> steps, int stepIndex, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Value.error("Cannot apply index union to non-array value.");
        }

        val arraySize = arrayValue.size();
        val builder   = ArrayValue.builder();

        // Normalize indices (handle negative indices)
        val normalizedIndices = new ArrayList<Integer>();
        for (val index : indices) {
            int idx = index.intValue();
            if (idx < 0) {
                idx += arraySize;
            }
            if (idx >= 0 && idx < arraySize) {
                normalizedIndices.add(idx);
            }
        }

        for (int i = 0; i < arraySize; i++) {
            if (normalizedIndices.contains(i)) {
                // This index is in the union - continue with remaining steps
                val updatedValue = applyFilterFunctionToPathRecursive(arrayValue.get(i), steps, stepIndex,
                        functionIdentifier, arguments, context);

                if (updatedValue instanceof ErrorValue) {
                    return updatedValue;
                }

                builder.add(updatedValue);
            } else {
                // This index is not in the union - keep unchanged
                builder.add(arrayValue.get(i));
            }
        }

        return builder.build();
    }

    /**
     * Applies a filter function to elements/fields matching a condition (condition
     * step).
     * <p>
     * For arrays: applies filter to elements where condition evaluates to true. For
     * objects: applies filter to field values where condition evaluates to true.
     *
     * @param parentValue the parent value (array or object)
     * @param conditionStep the condition step with the expression to evaluate
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the filtered value
     */
    private CompiledExpression applyConditionStepFilter(Value parentValue, ConditionStep conditionStep,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        // Non-array/non-object: no elements to match condition, return unchanged
        if (!(parentValue instanceof ArrayValue) && !(parentValue instanceof ObjectValue)) {
            return parentValue;
        }

        // Compile condition expression once
        val conditionExpr = ExpressionCompiler.compileExpression(conditionStep.getExpression(), context);

        // Check if condition is static (constant value)
        if (!(conditionExpr instanceof Value conditionValue)) {
            return Value.error(
                    "Dynamic conditions in filter condition steps are not supported. The condition must evaluate to a constant boolean value.");
        }

        if (conditionValue instanceof ErrorValue) {
            return conditionValue;
        }

        if (!(conditionValue instanceof io.sapl.api.model.BooleanValue booleanResult)) {
            return Value.error("Type mismatch. Expected the condition expression to return a Boolean, but was '"
                    + conditionValue.getClass().getSimpleName() + "'.");
        }

        // With constant condition: if false, return unchanged; if true, apply to all
        // elements (wildcard)
        if (!booleanResult.value()) {
            return parentValue;
        }

        // Condition is constant true - apply filter like wildcard
        return applyWildcardStepFilter(parentValue, functionIdentifier, arguments, context);
    }

    /**
     * Applies a filter function to nested paths through condition-matched
     * elements/fields.
     *
     * @param parentValue the parent value (array or object)
     * @param conditionStep the condition step
     * @param steps all path steps
     * @param stepIndex the current step index (points to next step after
     * condition)
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated parent value
     */
    private Value applyFilterToNestedCondition(Value parentValue, ConditionStep conditionStep,
            org.eclipse.emf.common.util.EList<Step> steps, int stepIndex, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        // Non-array/non-object: no elements to match condition, return unchanged
        if (!(parentValue instanceof ArrayValue) && !(parentValue instanceof ObjectValue)) {
            return parentValue;
        }

        // Compile condition expression once
        val conditionExpr = ExpressionCompiler.compileExpression(conditionStep.getExpression(), context);

        // Check if condition is static (constant value)
        if (!(conditionExpr instanceof Value conditionValue)) {
            return Value.error(
                    "Dynamic conditions in filter condition steps are not supported. The condition must evaluate to a constant boolean value.");
        }

        if (conditionValue instanceof ErrorValue) {
            return conditionValue;
        }

        if (!(conditionValue instanceof io.sapl.api.model.BooleanValue booleanResult)) {
            return Value.error("Type mismatch. Expected the condition expression to return a Boolean, but was '"
                    + conditionValue.getClass().getSimpleName() + "'.");
        }

        // With constant condition: if false, return unchanged; if true, descend like
        // wildcard
        if (!booleanResult.value()) {
            return parentValue;
        }

        // Condition is constant true - descend into all elements/fields
        return applyFilterToNestedWildcard(parentValue, steps, stepIndex, functionIdentifier, arguments, context);
    }

    /**
     * Applies a filter function using an expression step to compute the key/index.
     * <p>
     * Expression steps allow dynamic key/index selection: @[(expression)]
     * <p>
     * For arrays: expression must evaluate to a number (index) For objects:
     * expression must evaluate to a string (key)
     *
     * @param parentValue the parent value (array or object)
     * @param expressionStep the expression step
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the filtered value
     */
    private CompiledExpression applyExpressionStepFilter(Value parentValue, ExpressionStep expressionStep,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        // Non-array/non-object: no action
        if (!(parentValue instanceof ArrayValue) && !(parentValue instanceof ObjectValue)) {
            return parentValue;
        }

        // Compile expression once to get constant key/index
        val keyOrIndexExpr = ExpressionCompiler.compileExpression(expressionStep.getExpression(), context);

        if (!(keyOrIndexExpr instanceof Value keyOrIndexValue)) {
            return Value.error(
                    "Dynamic expressions in filter expression steps are not supported. The expression must evaluate to a constant value.");
        }

        if (keyOrIndexValue instanceof ErrorValue) {
            return keyOrIndexValue;
        }

        // Delegate to appropriate step handler based on parent type
        if (parentValue instanceof ArrayValue arrayValue) {
            if (!(keyOrIndexValue instanceof io.sapl.api.model.NumberValue numberValue)) {
                return Value.error("Array access type mismatch. Expect an integer, was: "
                        + keyOrIndexValue.getClass().getSimpleName());
            }
            val result = applyIndexStepFilter(arrayValue, numberValue.value().intValue(), functionIdentifier, arguments,
                    context);
            // Path-based filters reject dynamic arguments, so result is always a Value
            if (!(result instanceof Value resultValue)) {
                return Value.error("Unexpected non-value result in expression step filter.");
            }
            return resultValue;
        }

        val objectValue = (ObjectValue) parentValue;
        if (!(keyOrIndexValue instanceof io.sapl.api.model.TextValue textValue)) {
            return Value.error(
                    "Object access type mismatch. Expect a string, was: " + keyOrIndexValue.getClass().getSimpleName());
        }

        String key = textValue.value();
        if (!objectValue.containsKey(key)) {
            return parentValue;
        }
        val result = applyKeyStepFilter(objectValue, key, functionIdentifier, arguments, context);
        // Path-based filters reject dynamic arguments, so result is always a Value
        if (!(result instanceof Value resultValue)) {
            return Value.error("Unexpected non-value result in expression step filter.");
        }
        return resultValue;

    }

    /**
     * Applies a filter function to nested paths through an expression-selected
     * element/field.
     * <p>
     * The expression is evaluated to get a key (for objects) or index (for arrays),
     * then the filter continues with remaining steps on that element.
     *
     * @param parentValue the parent value (array or object)
     * @param expressionStep the expression step
     * @param steps all path steps
     * @param stepIndex the current step index (points to next step after
     * expression)
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated parent value
     */
    private Value applyFilterToNestedExpression(Value parentValue, ExpressionStep expressionStep,
            org.eclipse.emf.common.util.EList<Step> steps, int stepIndex, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        // Non-array/non-object: no action
        if (!(parentValue instanceof ArrayValue) && !(parentValue instanceof ObjectValue)) {
            return parentValue;
        }

        // Compile expression once to get constant key/index
        val keyOrIndexExpr = ExpressionCompiler.compileExpression(expressionStep.getExpression(), context);

        if (!(keyOrIndexExpr instanceof Value keyOrIndexValue)) {
            return Value.error(
                    "Dynamic expressions in filter expression steps are not supported. The expression must evaluate to a constant value.");
        }

        if (keyOrIndexValue instanceof ErrorValue) {
            return keyOrIndexValue;
        }

        // Delegate to appropriate nested handler based on parent type
        if (parentValue instanceof ArrayValue arrayValue) {
            if (!(keyOrIndexValue instanceof io.sapl.api.model.NumberValue numberValue)) {
                return Value.error("Array access type mismatch. Expect an integer, was: "
                        + keyOrIndexValue.getClass().getSimpleName());
            }

            int index = numberValue.value().intValue();
            if (index < 0 || index >= arrayValue.size()) {
                return Value.error("Index out of bounds. Index must be between 0 and " + (arrayValue.size() - 1)
                        + ", was: " + index);
            }

            return applyFilterToNestedArrayElement(arrayValue, index, steps, stepIndex, functionIdentifier, arguments,
                    context);
        }

        val objectValue = (ObjectValue) parentValue;
        if (!(keyOrIndexValue instanceof io.sapl.api.model.TextValue textValue)) {
            return Value.error(
                    "Object access type mismatch. Expect a string, was: " + keyOrIndexValue.getClass().getSimpleName());
        }

        String key = textValue.value();
        if (!objectValue.containsKey(key)) {
            return parentValue;
        }

        return applyFilterToNestedPath(objectValue, key, steps, stepIndex, functionIdentifier, arguments, context);

    }
}
