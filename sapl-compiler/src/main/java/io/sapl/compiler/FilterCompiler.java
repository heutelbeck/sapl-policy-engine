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
import io.sapl.grammar.sapl.FilterComponent;
import io.sapl.grammar.sapl.FilterExtended;
import io.sapl.grammar.sapl.FilterSimple;
import io.sapl.grammar.sapl.IndexStep;
import io.sapl.grammar.sapl.KeyStep;
import io.sapl.grammar.sapl.Step;
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

    private static final String FILTERS_CANNOT_BE_APPLIED_TO_UNDEFINED = "Filters cannot be applied to undefined values.";
    private static final String EACH_REQUIRES_ARRAY                    = "Cannot use 'each' keyword with non-array values.";

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

        // For now, only handle Value parent (Step 1-2)
        if (!(parent instanceof Value parentValue)) {
            throw new SaplCompilerException("Non-value filter parents not yet implemented. Step 15-16.");
        }

        // Handle 'each' keyword - map function over array elements
        if (filter.isEach()) {
            return applyFilterFunctionToEachArrayElement(parentValue, filter, arguments, context);
        }

        // Apply filter function with parent as left-hand argument
        return applyFilterFunctionToValue(parentValue, filter, arguments, context);
    }

    /**
     * Applies a filter function to a constant value with the value as left-hand
     * argument.
     * <p>
     * Performs constant folding if all arguments are values, otherwise creates
     * appropriate expression type.
     *
     * @param parentValue the value to filter
     * @param filter the filter AST node
     * @param arguments the compiled filter arguments
     * @param context the compilation context
     * @return the result of applying the filter function
     */
    private CompiledExpression applyFilterFunctionToValue(Value parentValue, FilterSimple filter,
            CompiledArguments arguments, CompilationContext context) {
        val functionIdentifier = ImportResolver.resolveFunctionIdentifierByImports(filter, filter.getIdentifier());
        return applyFilterFunctionToValue(parentValue, functionIdentifier, arguments, context);
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

        // Map function over each element, filtering out undefined results
        val builder = ArrayValue.builder();
        for (val element : arrayValue) {
            val result = applyFilterFunctionToValue(element, filter, arguments, context);

            // For constant folding, we get a Value back
            if (result instanceof Value resultValue) {
                // Filter out undefined values (filter.remove() semantics)
                if (!(resultValue instanceof UndefinedValue)) {
                    builder.add(resultValue);
                }
            } else {
                // If we get a non-Value result, we can't do this at compile time
                throw new SaplCompilerException("Non-value results in 'each' filter not yet implemented. Step 17.");
            }
        }

        return builder.build();
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
        if (!(parent instanceof Value parentValue)) {
            throw new SaplCompilerException("Non-value filter parents not yet implemented.");
        }

        var currentValue = parentValue;
        for (val statement : filter.getStatements()) {
            if (statement.isEach()) {
                throw new SaplCompilerException("'each' keyword in extended filters not yet implemented.");
            }

            var arguments = CompiledArguments.EMPTY_ARGUMENTS;
            if (statement.getArguments() != null && statement.getArguments().getArgs() != null) {
                arguments = ExpressionCompiler.compileArguments(statement.getArguments().getArgs(), context);
            }

            val functionIdentifier = ImportResolver.resolveFunctionIdentifierByImports(statement,
                    statement.getIdentifier());

            if (statement.getTarget() != null && !statement.getTarget().getSteps().isEmpty()) {
                currentValue = applyFilterFunctionToPath(currentValue, statement.getTarget().getSteps(),
                        functionIdentifier, arguments, context);
            } else {
                val result = applyFilterFunctionToValue(currentValue, functionIdentifier, arguments, context);

                if (result instanceof Value resultValue) {
                    currentValue = resultValue;
                } else {
                    throw new SaplCompilerException("Non-value results in extended filter not yet implemented.");
                }
            }
        }

        return currentValue;
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
        if (steps.size() == 1) {
            return applySingleStepFilter(parentValue, steps.get(0), functionIdentifier, arguments, context);
        }

        throw new SaplCompilerException("Multi-step paths not yet implemented.");
    }

    /**
     * Applies a filter function to a value at a single-step path.
     *
     * @param parentValue the parent value
     * @param step the single step to navigate
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated value
     */
    private Value applySingleStepFilter(Value parentValue, Step step, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (step instanceof KeyStep keyStep) {
            return applyKeyStepFilter(parentValue, keyStep.getId(), functionIdentifier, arguments, context);
        }

        if (step instanceof IndexStep indexStep) {
            return applyIndexStepFilter(parentValue, indexStep.getIndex().intValue(), functionIdentifier, arguments,
                    context);
        }

        throw new SaplCompilerException("Step type not supported: " + step.getClass().getSimpleName());
    }

    /**
     * Applies a filter function to an object field accessed by key.
     *
     * @param parentValue the parent object value
     * @param key the field key
     * @param functionIdentifier the function identifier
     * @param arguments the function arguments
     * @param context the compilation context
     * @return the updated object with the filtered field
     */
    private Value applyKeyStepFilter(Value parentValue, String key, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ObjectValue objectValue)) {
            return Value.error("Cannot apply key step to non-object value.");
        }

        val fieldValue = objectValue.get(key);
        if (fieldValue == null) {
            return Value.error("Field '" + key + "' not found in object.");
        }

        val result = applyFilterFunctionToValue(fieldValue, functionIdentifier, arguments, context);

        if (!(result instanceof Value filteredValue)) {
            throw new SaplCompilerException("Non-value results in path filter not yet implemented.");
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

        return builder.build();
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
    private Value applyIndexStepFilter(Value parentValue, int index, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Value.error("Cannot apply index step to non-array value.");
        }

        if (index < 0 || index >= arrayValue.size()) {
            return Value.error("Array index out of bounds: " + index + " (size: " + arrayValue.size() + ").");
        }

        val elementValue = arrayValue.get(index);
        val result       = applyFilterFunctionToValue(elementValue, functionIdentifier, arguments, context);

        if (!(result instanceof Value filteredValue)) {
            throw new SaplCompilerException("Non-value results in index filter not yet implemented.");
        }

        val builder = ArrayValue.builder();
        for (int i = 0; i < arrayValue.size(); i++) {
            if (i == index) {
                if (!(filteredValue instanceof UndefinedValue)) {
                    builder.add(filteredValue);
                }
            } else {
                builder.add(arrayValue.get(i));
            }
        }

        return builder.build();
    }
}
