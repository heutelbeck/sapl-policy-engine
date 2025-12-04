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

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.*;
import io.sapl.api.model.Value;
import io.sapl.grammar.sapl.*;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.eclipse.emf.ecore.EObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Compiles SAPL filter expressions (|- operator) and subtemplates (:: operator)
 * into optimized executable
 * representations.
 * <p>
 * Filters transform values by applying functions, supporting both simple
 * ({@code value |- func}) and extended
 * ({@code value |- { @.field : func }}) syntax. Subtemplates apply templates to
 * values, with implicit array mapping.
 */
@UtilityClass
public class FilterCompiler {

    public static final String  COMPILE_ERROR_NO_DYNAMIC_FILTER_ARGUMENTS_IN_EACH = "Compilation failed. Dynamic filter arguments are not supported in extended 'each' filters.";
    private static final String COMPILE_ERROR_STEP_TYPE_NOT_SUPPORTED             = "Compilation failed. Step type not supported: %s.";
    public static final String  COMPILE_ERROR_STREAM_EXPRESSION_IN_PURE_FILTER    = "Compilation failed. Stream expression in pure filter arguments.";
    private static final String COMPILE_ERROR_UNKNOWN_FILTER_TYPE                 = "Compilation failed. Unknown filter type: %s.";

    private static final String RUNTIME_ERROR_ARRAY_ACCESS_TYPE_MISMATCH                 = "Array access type mismatch. Expect an integer, was: %s.";
    private static final String RUNTIME_ERROR_ARRAY_INDEX_OUT_OF_BOUNDS                  = "Array index out of bounds: %d (size: %d).";
    private static final String RUNTIME_ERROR_ATTRIBUTE_FINDER_NOT_PERMITTED             = "AttributeFinderStep not permitted in filter selection steps.";
    private static final String RUNTIME_ERROR_ATTRIBUTE_UNION_REQUIRES_OBJECT            = "Cannot apply attribute union to non-object value.";
    private static final String RUNTIME_ERROR_CONDITION_TYPE_MISMATCH                    = "Type mismatch. Expected the condition expression to return a Boolean, but was '%s'.";
    private static final String RUNTIME_ERROR_DYNAMIC_ARGS_NOT_SUPPORTED_EXTENDED        = "Dynamic filter arguments not yet supported in path-based extended filters.";
    private static final String RUNTIME_ERROR_DYNAMIC_ARGS_NOT_SUPPORTED_RECURSIVE_INDEX = "Dynamic filter arguments not yet supported in recursive index filters.";
    private static final String RUNTIME_ERROR_DYNAMIC_ARGS_NOT_SUPPORTED_RECURSIVE_KEY   = "Dynamic filter arguments not yet supported in recursive key filters.";
    public static final String  RUNTIME_ERROR_DYNAMIC_CONDITION_UNSUPPORTED              = "Dynamic conditions in filter condition steps are not supported. The condition must evaluate to a constant boolean value.";
    private static final String RUNTIME_ERROR_DYNAMIC_EXPRESSION_NOT_SUPPORTED           = "Dynamic expressions in filter expression steps are not supported. The expression must evaluate to a constant value.";
    private static final String RUNTIME_ERROR_EACH_REQUIRES_ARRAY                        = "Cannot use 'each' keyword with non-array values.";
    private static final String RUNTIME_ERROR_EXPRESSION_STEP_REQUIRES_ARRAY             = "Cannot access array index on non-array value.";
    private static final String RUNTIME_ERROR_EXTENDED_FILTER_REQUIRES_VALUE             = "Extended filter operations require Value inputs.";
    private static final String RUNTIME_ERROR_EXTENDED_FILTER_UNSUPPORTED_NON_VALUE      = "Non-value results in extended filter statements not yet supported.";
    private static final String RUNTIME_ERROR_FIELD_NOT_FOUND_IN_OBJECT                  = "Field '%s' not found in object.";
    private static final String RUNTIME_ERROR_FIELD_REQUIRES_OBJECT                      = "Cannot access field '%s' on non-object value.";
    private static final String RUNTIME_ERROR_FILTER_REQUIRES_VALUE                      = "Filter operations require Value inputs.";
    private static final String RUNTIME_ERROR_FILTERS_CANNOT_BE_APPLIED_TO_UNDEFINED     = "Filters cannot be applied to undefined values.";
    private static final String RUNTIME_ERROR_INDEX_OUT_OF_BOUNDS                        = "Index out of bounds. Index must be between 0 and %d (inclusive), but got: %d.";
    private static final String RUNTIME_ERROR_INDEX_STEP_REQUIRES_ARRAY                  = "Cannot apply index step to non-array value.";
    private static final String RUNTIME_ERROR_INDEX_UNION_REQUIRES_ARRAY                 = "Cannot apply index union to non-array value.";
    private static final String RUNTIME_ERROR_KEY_STEP_REQUIRES_OBJECT                   = "Cannot apply key step to non-object value.";
    private static final String RUNTIME_ERROR_KEY_STEP_REQUIRES_OBJECT_ARRAY_ELEMENT     = "Cannot apply key step to non-object array element.";
    private static final String RUNTIME_ERROR_OBJECT_ACCESS_TYPE_MISMATCH                = "Object access type mismatch. Expect a string, was: %s.";
    private static final String RUNTIME_ERROR_SLICING_REQUIRES_ARRAY                     = "Cannot apply slicing step to non-array value.";
    private static final String RUNTIME_ERROR_SLICING_STEP_ZERO                          = "Step must not be zero.";
    private static final String RUNTIME_ERROR_UNEXPECTED_FILTER_RESULT_TYPE              = "Unexpected filter result type.";
    private static final String RUNTIME_ERROR_UNEXPECTED_NON_VALUE_ARRAY_MAPPING         = "Unexpected non-value result in array mapping with constant arguments.";
    private static final String RUNTIME_ERROR_UNEXPECTED_NON_VALUE_CONSTANT_ARGS         = "Unexpected non-value result with constant arguments.";
    private static final String RUNTIME_ERROR_UNEXPECTED_NON_VALUE_EXPRESSION_STEP       = "Unexpected non-value result in expression step filter.";
    private static final String RUNTIME_ERROR_UNEXPECTED_NON_VALUE_RECURSIVE_INDEX       = "Unexpected non-value result in recursive index filter.";
    private static final String RUNTIME_ERROR_UNEXPECTED_NON_VALUE_RECURSIVE_KEY         = "Unexpected non-value result in recursive key filter.";
    private static final String RUNTIME_ERROR_WILDCARD_REQUIRES_ARRAY_OR_OBJECT          = "Cannot apply wildcard step to non-array/non-object value.";

    /**
     * Compiles a filter expression (|- operator).
     * <p>
     * Filters apply functions to values, optionally using the 'each' keyword to map
     * over arrays. Extended filters
     * support complex targeting with paths.
     *
     * @param parent
     * the expression to filter
     * @param filter
     * the filter component (simple or extended)
     * @param context
     * the compilation context
     *
     * @return the compiled filter expression
     */
    public CompiledExpression compileFilter(CompiledExpression parent, FilterComponent filter,
            CompilationContext context) {
        if (parent instanceof ErrorValue) {
            return parent;
        }
        if (parent instanceof UndefinedValue(ValueMetadata metadata)) {
            return Error.at(filter, metadata, RUNTIME_ERROR_FILTERS_CANNOT_BE_APPLIED_TO_UNDEFINED);
        }
        return switch (filter) {
        case FilterSimple simple     -> compileSimpleFilter(parent, simple, context);
        case FilterExtended extended -> compileExtendedFilter(parent, extended, context);
        default                      -> throw new SaplCompilerException(
                String.format(COMPILE_ERROR_UNKNOWN_FILTER_TYPE, filter.getClass().getSimpleName()), filter);
        };
    }

    /**
     * Compiles a simple filter expression: {@code value |- [each] func(args)}.
     * <p>
     * Without 'each': applies function to entire value. With 'each': maps function
     * over array elements.
     *
     * @param parent
     * the parent expression to filter
     * @param filter
     * the simple filter AST node
     * @param context
     * the compilation context
     *
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
                return Error.at(filter, ValueMetadata.EMPTY, RUNTIME_ERROR_FILTER_REQUIRES_VALUE);
            }

            // Handle 'each' keyword - map function over array elements
            if (filter.isEach()) {
                return applyFilterFunctionToEachArrayElement(value, filter, finalArguments, context);
            }

            // Apply filter function with parent as left-hand argument
            return applyFilterFunctionToValue(filter, value, functionIdentifier, finalArguments, context);
        };

        // Wrap the operation to handle PureExpression/StreamExpression parents
        return wrapFilterOperation(filter, parent, filterOp);
    }

    /**
     * Wraps a filter operation to handle Value, PureExpression, and
     * StreamExpression parents.
     * <p>
     * This is the core wrapper pattern that enables filters to work with all
     * expression types, similar to how
     * ExpressionCompiler handles step operations.
     *
     * @param parent
     * the parent expression (Value, PureExpression, or StreamExpression)
     * @param filterOperation
     * the filter operation to apply (works on CompiledExpression)
     *
     * @return the wrapped filter result
     */
    private CompiledExpression wrapFilterOperation(EObject astNode, CompiledExpression parent,
            UnaryOperator<CompiledExpression> filterOperation) {
        if (parent instanceof ErrorValue || parent instanceof UndefinedValue) {
            return parent;
        }
        return switch (parent) {
        case Value value                  -> filterOperation.apply(value);
        case StreamExpression(var stream) -> new StreamExpression(
                stream.flatMap(v -> ExpressionCompiler.compiledExpressionToFlux(filterOperation.apply(v))));
        case PureExpression pureParent    -> new PureExpression(
                ctx -> evaluateFilterResult(astNode, filterOperation.apply(pureParent.evaluate(ctx)), ctx),
                pureParent.isSubscriptionScoped());
        };
    }

    /**
     * Evaluates a filter result to a Value, handling Value, PureExpression, or
     * error cases.
     */
    private static Value evaluateFilterResult(EObject astNode, CompiledExpression result, EvaluationContext ctx) {
        if (result instanceof Value value) {
            return value;
        }
        if (result instanceof PureExpression pureExpression) {
            return pureExpression.evaluate(ctx);
        }
        return Error.at(astNode, ValueMetadata.EMPTY, RUNTIME_ERROR_UNEXPECTED_FILTER_RESULT_TYPE);
    }

    /**
     * Applies a filter function to each element of an array value.
     * <p>
     * Maps the function over array elements, filtering out undefined results. This
     * implements the 'each' keyword
     * semantics.
     *
     * @param parentValue
     * the array value to filter
     * @param filter
     * the filter AST node
     * @param arguments
     * the compiled filter arguments
     * @param context
     * the compilation context
     *
     * @return an array with the function applied to each element
     */
    private CompiledExpression applyFilterFunctionToEachArrayElement(Value parentValue, FilterSimple filter,
            CompiledArguments arguments, CompilationContext context) {
        // Validate parent is an array
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Error.at(filter, parentValue.metadata(), RUNTIME_ERROR_EACH_REQUIRES_ARRAY);
        }

        val functionIdentifier = ImportResolver.resolveFunctionIdentifierByImports(filter, filter.getIdentifier());

        // Handle based on argument nature
        return switch (arguments.nature()) {
        case VALUE  -> {
            // Constant folding: map function over each element
            val builder = ArrayValue.builder().withMetadata(arrayValue.metadata());
            for (val element : arrayValue) {
                val result = applyFilterFunctionToValue(filter, element, functionIdentifier, arguments, context);

                // For constant folding, we get a Value back
                if (result instanceof Value resultValue) {
                    // Filter out undefined values (filter.remove() semantics)
                    if (!(resultValue instanceof UndefinedValue)) {
                        builder.add(resultValue);
                    }
                } else {
                    // Should not happen with VALUE arguments, but handle defensively
                    yield createEachRuntimeExpression(filter, arrayValue, functionIdentifier, arguments, context,
                            false);
                }
            }
            yield builder.build();
        }
        case PURE   -> createEachRuntimeExpression(filter, arrayValue, functionIdentifier, arguments, context, false);
        case STREAM -> createEachRuntimeExpression(filter, arrayValue, functionIdentifier, arguments, context, true);
        };
    }

    /**
     * Creates a runtime expression for 'each' filter operation.
     * <p>
     * This is used when filter arguments contain dynamic expressions.
     */
    private CompiledExpression createEachRuntimeExpression(EObject astNode, ArrayValue arrayValue,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context, boolean isStream) {
        if (isStream) {
            return createStreamEachExpression(arrayValue, functionIdentifier, arguments, context);
        }
        return createPureEachExpression(astNode, arrayValue, functionIdentifier, arguments);
    }

    private CompiledExpression createStreamEachExpression(ArrayValue arrayValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux).toList();
        val stream  = reactor.core.publisher.Flux.combineLatest(sources,
                argValues -> applyFilterToEachElement(arrayValue, functionIdentifier, valueArrayFrom(argValues),
                        context.getFunctionBroker()));
        return new StreamExpression(stream);
    }

    private CompiledExpression createPureEachExpression(EObject astNode, ArrayValue arrayValue,
            String functionIdentifier, CompiledArguments arguments) {
        return new PureExpression(
                ctx -> applyFilterToEachElement(arrayValue, functionIdentifier,
                        resolveArgumentsToValues(astNode, arguments.arguments(), ctx), ctx.functionBroker()),
                arguments.isSubscriptionScoped());
    }

    private Value applyFilterToEachElement(ArrayValue arrayValue, String functionIdentifier,
            List<Value> resolvedArguments, FunctionBroker functionBroker) {
        val builder = ArrayValue.builder().withMetadata(arrayValue.metadata());
        for (val element : arrayValue) {
            val valueArguments = new ArrayList<Value>(resolvedArguments.size() + 1);
            valueArguments.add(element);
            valueArguments.addAll(resolvedArguments);

            val invocation = new FunctionInvocation(functionIdentifier, valueArguments);
            val result     = functionBroker.evaluateFunction(invocation);

            if (!(result instanceof UndefinedValue)) {
                builder.add(result);
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
     */
    private CompiledExpression compileExtendedFilter(CompiledExpression parent, FilterExtended filter,
            CompilationContext context) {
        UnaryOperator<CompiledExpression> filterOp = parentExpr -> {
            if (!(parentExpr instanceof Value currentValue)) {
                return Error.at(filter, ValueMetadata.EMPTY, RUNTIME_ERROR_EXTENDED_FILTER_REQUIRES_VALUE);
            }
            for (val statement : filter.getStatements()) {
                currentValue = applyFilterStatement(currentValue, statement, context);
                if (currentValue instanceof ErrorValue) {
                    return currentValue;
                }
            }
            return currentValue;
        };
        return wrapFilterOperation(filter, parent, filterOp);
    }

    private Value applyFilterStatement(Value currentValue, FilterStatement statement, CompilationContext context) {
        val arguments          = compileStatementArguments(statement, context);
        val functionIdentifier = ImportResolver.resolveFunctionIdentifierByImports(statement,
                statement.getIdentifier());

        if (statement.isEach()) {
            return applyEachFilterStatement(statement, currentValue, statement.getTarget(), functionIdentifier,
                    arguments, context);
        }

        if (hasTargetPath(statement)) {
            return applyFilterFunctionToPath(statement, currentValue, statement.getTarget().getSteps(),
                    functionIdentifier, arguments, context);
        }

        return applyDirectFilterToValue(statement, currentValue, functionIdentifier, arguments, context);
    }

    private CompiledArguments compileStatementArguments(FilterStatement statement, CompilationContext context) {
        if (statement.getArguments() != null && statement.getArguments().getArgs() != null) {
            return ExpressionCompiler.compileArguments(statement.getArguments().getArgs(), context);
        }
        return CompiledArguments.EMPTY_ARGUMENTS;
    }

    private boolean hasTargetPath(FilterStatement statement) {
        return statement.getTarget() != null && !statement.getTarget().getSteps().isEmpty();
    }

    private Value applyDirectFilterToValue(EObject astNode, Value currentValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val result = applyFilterFunctionToValue(astNode, currentValue, functionIdentifier, arguments, context);
        if (result instanceof Value resultValue) {
            return resultValue;
        }
        return Error.at(astNode, currentValue.metadata(), RUNTIME_ERROR_EXTENDED_FILTER_UNSUPPORTED_NON_VALUE);
    }

    /**
     * Applies a filter function to a value given the function identifier.
     *
     * @param parentValue
     * the value to filter
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the compiled filter arguments
     * @param context
     * the compilation context
     *
     * @return the result of applying the filter function
     */
    private CompiledExpression applyFilterFunctionToValue(EObject astNode, Value parentValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        return FilterApplicationStrategy.applyFilterToValue(astNode, parentValue, functionIdentifier, arguments,
                context);
    }

    /**
     * Applies a filter function to a value at a specific path.
     * <p>
     * Navigates to the path, applies the filter, and updates the parent value with
     * the result.
     *
     * @param parentValue
     * the value containing the path
     * @param steps
     * the path steps to navigate
     * @param functionIdentifier
     * the function to apply
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated parent value with the filter applied at the path
     */
    private Value applyFilterFunctionToPath(EObject astNode, Value parentValue, List<Step> steps,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        return applyFilterFunctionToPathRecursive(astNode, parentValue, steps, 0, functionIdentifier, arguments,
                context);
    }

    private Value applyFilterFunctionToPathRecursive(EObject astNode, Value parentValue, List<Step> steps,
            int stepIndex, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        // Check for dynamic arguments - not yet supported in path-based extended
        // filters
        if (arguments.nature() != Nature.VALUE) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_DYNAMIC_ARGS_NOT_SUPPORTED_EXTENDED);
        }

        if (stepIndex == steps.size() - 1) {
            val result = applySingleStepFilter(parentValue, steps.get(stepIndex), functionIdentifier, arguments,
                    context);
            // With VALUE arguments, result should always be a Value
            if (!(result instanceof Value resultValue)) {
                return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_UNEXPECTED_NON_VALUE_CONSTANT_ARGS);
            }
            return resultValue;
        }

        val currentStep = steps.get(stepIndex);

        return switch (currentStep) {
        case KeyStep keyStep                       -> applyFilterToNestedPath(currentStep, parentValue, keyStep.getId(),
                steps, stepIndex + 1, functionIdentifier, arguments, context);
        case IndexStep indexStep                   -> applyFilterToNestedArrayElement(currentStep, parentValue,
                indexStep.getIndex().intValue(), steps, stepIndex + 1, functionIdentifier, arguments, context);
        case ArraySlicingStep slicingStep          -> applyFilterToNestedArraySlice(parentValue, slicingStep, steps,
                stepIndex + 1, functionIdentifier, arguments, context);
        case WildcardStep ignored                  -> applyFilterToNestedWildcard(currentStep, parentValue, steps,
                stepIndex + 1, functionIdentifier, arguments, context);
        case RecursiveKeyStep recursiveKeyStep     -> applyFilterToNestedRecursiveKey(currentStep, parentValue,
                recursiveKeyStep.getId(), steps, stepIndex + 1, functionIdentifier, arguments, context);
        case RecursiveWildcardStep ignored         -> applyFilterToNestedRecursiveWildcard(currentStep, parentValue,
                steps, stepIndex + 1, functionIdentifier, arguments, context);
        case RecursiveIndexStep recursiveIndexStep -> applyFilterToNestedRecursiveIndex(currentStep, parentValue,
                recursiveIndexStep.getIndex().intValue(), steps, stepIndex + 1, functionIdentifier, arguments, context);
        case AttributeUnionStep attributeUnionStep -> applyFilterToNestedAttributeUnion(currentStep, parentValue,
                attributeUnionStep.getAttributes(), steps, stepIndex + 1, functionIdentifier, arguments, context);
        case IndexUnionStep indexUnionStep         -> applyFilterToNestedIndexUnion(currentStep, parentValue,
                indexUnionStep.getIndices(), steps, stepIndex + 1, functionIdentifier, arguments, context);
        case AttributeFinderStep ignored           ->
            Error.at(currentStep, parentValue.metadata(), RUNTIME_ERROR_ATTRIBUTE_FINDER_NOT_PERMITTED);
        case HeadAttributeFinderStep ignored       ->
            Error.at(currentStep, parentValue.metadata(), RUNTIME_ERROR_ATTRIBUTE_FINDER_NOT_PERMITTED);
        case ConditionStep conditionStep           -> applyFilterToNestedCondition(currentStep, parentValue,
                conditionStep, steps, stepIndex + 1, functionIdentifier, arguments, context);
        case ExpressionStep expressionStep         -> applyFilterToNestedExpression(currentStep, parentValue,
                expressionStep, steps, stepIndex + 1, functionIdentifier, arguments, context);
        default                                    -> throw new SaplCompilerException(
                COMPILE_ERROR_STEP_TYPE_NOT_SUPPORTED.formatted(currentStep.getClass().getSimpleName()), currentStep);
        };
    }

    /**
     * Applies a filter function to a value at a single-step path.
     *
     * @param parentValue
     * the parent value
     * @param step
     * the single step to navigate
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated value (maybe a Value, PureExpression, or
     * StreamExpression)
     */
    private CompiledExpression applySingleStepFilter(Value parentValue, Step step, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        return switch (step) {
        case KeyStep keyStep                       ->
            applyKeyStepFilter(step, parentValue, keyStep.getId(), functionIdentifier, arguments, context);
        case IndexStep indexStep                   -> applyIndexStepFilter(step, parentValue,
                indexStep.getIndex().intValue(), functionIdentifier, arguments, context);
        case ArraySlicingStep slicingStep          ->
            applySlicingStepFilter(step, parentValue, slicingStep, functionIdentifier, arguments, context);
        case WildcardStep ignored                  ->
            applyWildcardStepFilter(step, parentValue, functionIdentifier, arguments, context);
        case RecursiveKeyStep recursiveKeyStep     -> applyRecursiveKeyStepFilter(step, parentValue,
                recursiveKeyStep.getId(), functionIdentifier, arguments, context);
        case RecursiveWildcardStep ignored         ->
            applyRecursiveWildcardStepFilter(step, parentValue, functionIdentifier, arguments, context);
        case RecursiveIndexStep recursiveIndexStep -> applyRecursiveIndexStepFilter(step, parentValue,
                recursiveIndexStep.getIndex().intValue(), functionIdentifier, arguments, context);
        case AttributeUnionStep attributeUnionStep -> applyAttributeUnionStepFilter(step, parentValue,
                attributeUnionStep.getAttributes(), functionIdentifier, arguments, context);
        case IndexUnionStep indexUnionStep         -> applyIndexUnionStepFilter(step, parentValue,
                indexUnionStep.getIndices(), functionIdentifier, arguments, context);
        case AttributeFinderStep ignored           ->
            Error.at(step, parentValue.metadata(), RUNTIME_ERROR_ATTRIBUTE_FINDER_NOT_PERMITTED);
        case HeadAttributeFinderStep ignored       ->
            Error.at(step, parentValue.metadata(), RUNTIME_ERROR_ATTRIBUTE_FINDER_NOT_PERMITTED);
        case ConditionStep conditionStep           ->
            applyConditionStepFilter(step, parentValue, conditionStep, functionIdentifier, arguments, context);
        case ExpressionStep expressionStep         ->
            applyExpressionStepFilter(step, parentValue, expressionStep, functionIdentifier, arguments, context);
        default                                    -> throw new SaplCompilerException(
                COMPILE_ERROR_STEP_TYPE_NOT_SUPPORTED.formatted(step.getClass().getSimpleName()), step);
        };
    }

    /**
     * Applies a filter function to an object field accessed by key.
     * <p>
     * For arrays: applies the key step to each array element (implicit array
     * mapping). For objects: applies the filter
     * to the specified field.
     *
     * @param parentValue
     * the parent object or array value
     * @param key
     * the field key
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated object/array with the filtered field(s)
     */
    private CompiledExpression applyKeyStepFilter(EObject astNode, Value parentValue, String key,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        // Handle implicit array mapping: apply key step to each element
        if (parentValue instanceof ArrayValue arrayValue) {
            // Check if we can fold a constant (all arguments are values)
            if (arguments.nature() == Nature.VALUE) {
                val builder = ArrayValue.builder().withMetadata(arrayValue.metadata());
                for (val element : arrayValue) {
                    val result = applyKeyStepFilter(astNode, element, key, functionIdentifier, arguments, context);

                    if (result instanceof ErrorValue) {
                        return result;
                    }

                    if (!(result instanceof Value resultValue)) {
                        // Should not happen with VALUE arguments, but be defensive
                        return Error.at(astNode, parentValue.metadata(),
                                RUNTIME_ERROR_UNEXPECTED_NON_VALUE_ARRAY_MAPPING);
                    }

                    builder.add(resultValue);
                }
                return builder.build();
            } else {
                // Dynamic arguments: need runtime expression for array mapping
                return createKeyStepArrayMappingExpression(astNode, arrayValue, key, functionIdentifier, arguments,
                        context);
            }
        }

        if (!(parentValue instanceof ObjectValue objectValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_KEY_STEP_REQUIRES_OBJECT);
        }

        val fieldValue = objectValue.get(key);
        if (fieldValue == null) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_FIELD_NOT_FOUND_IN_OBJECT.formatted(key));
        }

        return FilterApplicationStrategy.applyFilterToObjectFields(astNode, objectValue, k -> k.equals(key),
                functionIdentifier, arguments, context);
    }

    /**
     * Creates a runtime expression for key step array mapping with dynamic
     * arguments.
     * <p>
     * Used when arguments are PURE or STREAM and implicit array mapping is needed.
     *
     * @param arrayValue
     * the array value to map over
     * @param key
     * the field key to access in each element
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the compiled filter arguments
     * @param context
     * the compilation context
     *
     * @return a PureExpression or StreamExpression
     */
    private CompiledExpression createKeyStepArrayMappingExpression(EObject astNode, ArrayValue arrayValue, String key,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (arguments.nature() == Nature.STREAM) {
            return createStreamKeyStepArrayMapping(astNode, arrayValue, key, functionIdentifier, arguments, context);
        }
        return createPureKeyStepArrayMapping(astNode, arrayValue, key, functionIdentifier, arguments);
    }

    private CompiledExpression createStreamKeyStepArrayMapping(EObject astNode, ArrayValue arrayValue, String key,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux).toList();
        val stream  = reactor.core.publisher.Flux.combineLatest(sources,
                argValues -> applyKeyFilterToArrayElements(astNode, arrayValue, key, functionIdentifier,
                        valueArrayFrom(argValues), context.getFunctionBroker()));
        return new StreamExpression(stream);
    }

    private CompiledExpression createPureKeyStepArrayMapping(EObject astNode, ArrayValue arrayValue, String key,
            String functionIdentifier, CompiledArguments arguments) {
        return new PureExpression(
                ctx -> applyKeyFilterToArrayElements(astNode, arrayValue, key, functionIdentifier,
                        resolveArgumentsToValues(astNode, arguments.arguments(), ctx), ctx.functionBroker()),
                arguments.isSubscriptionScoped());
    }

    private Value applyKeyFilterToArrayElements(EObject astNode, ArrayValue arrayValue, String key,
            String functionIdentifier, List<Value> resolvedArguments,
            io.sapl.api.functions.FunctionBroker functionBroker) {
        val builder = ArrayValue.builder().withMetadata(arrayValue.metadata());
        for (val element : arrayValue) {
            if (!(element instanceof ObjectValue elementObj)) {
                return Error.at(astNode, arrayValue.metadata(), RUNTIME_ERROR_KEY_STEP_REQUIRES_OBJECT_ARRAY_ELEMENT);
            }

            val fieldValue = elementObj.get(key);
            if (fieldValue == null) {
                continue;
            }

            val valueArguments = new ArrayList<Value>(resolvedArguments.size() + 1);
            valueArguments.add(fieldValue);
            valueArguments.addAll(resolvedArguments);

            val invocation   = new FunctionInvocation(functionIdentifier, valueArguments);
            val filterResult = functionBroker.evaluateFunction(invocation);

            builder.add(rebuildObjectWithFilteredField(elementObj, key, filterResult));
        }
        return builder.build();
    }

    private ObjectValue rebuildObjectWithFilteredField(ObjectValue originalObject, String key, Value filterResult) {
        val builder = ObjectValue.builder().withMetadata(originalObject.metadata());
        for (val entry : originalObject.entrySet()) {
            if (entry.getKey().equals(key)) {
                if (!(filterResult instanceof UndefinedValue)) {
                    builder.put(entry.getKey(), filterResult);
                }
            } else {
                builder.put(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

    private List<Value> valueArrayFrom(java.lang.Object[] argValues) {
        val result = new ArrayList<Value>(argValues.length);
        for (var argValue : argValues) {
            result.add((Value) argValue);
        }
        return result;
    }

    private List<Value> resolveArgumentsToValues(EObject astNode, CompiledExpression[] arguments,
            EvaluationContext ctx) {
        val result = new ArrayList<Value>(arguments.length);
        for (val argument : arguments) {
            switch (argument) {
            case Value value                   -> result.add(value);
            case PureExpression pureExpression -> result.add(pureExpression.evaluate(ctx));
            case StreamExpression ignored      ->
                throw new SaplCompilerException(COMPILE_ERROR_STREAM_EXPRESSION_IN_PURE_FILTER, astNode);
            }
        }
        return result;
    }

    /**
     * Applies a filter function to an array element accessed by index.
     *
     * @param parentValue
     * the parent array value
     * @param index
     * the element index
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated array with the filtered element
     */
    private CompiledExpression applyIndexStepFilter(EObject astNode, Value parentValue, int index,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_INDEX_STEP_REQUIRES_ARRAY);
        }

        val normalizedIndex = FilterCollectionRebuilder.normalizeIndex(index, arrayValue.size());
        if (normalizedIndex < 0 || normalizedIndex >= arrayValue.size()) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_ARRAY_INDEX_OUT_OF_BOUNDS, index,
                    arrayValue.size());
        }

        return FilterApplicationStrategy.applyFilterToArrayElements(astNode, arrayValue, i -> i == normalizedIndex,
                functionIdentifier, arguments, context);
    }

    /**
     * Applies a filter function to array elements accessed by slice.
     *
     * @param parentValue
     * the parent array value
     * @param slicingStep
     * the slicing step with start, end, and step parameters
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated array with filtered slice elements
     */
    private CompiledExpression applySlicingStepFilter(EObject astNode, Value parentValue, ArraySlicingStep slicingStep,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_SLICING_REQUIRES_ARRAY);
        }

        val arraySize = arrayValue.size();
        val step      = slicingStep.getStep() != null ? slicingStep.getStep().intValue() : 1;

        if (step == 0) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_SLICING_STEP_ZERO);
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

        return FilterApplicationStrategy.applyFilterToArrayElements(astNode, arrayValue,
                i -> isInNormalizedSlice(i, finalIndex, finalTo, step), functionIdentifier, arguments, context);
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
     * mapping). For objects: applies the
     * filter to the specified nested field.
     *
     * @param parentValue
     * the parent object or array value
     * @param fieldName
     * the field name to navigate through
     * @param steps
     * all path steps
     * @param stepIndex
     * the current step index (points to next step after field)
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated parent object/array
     */
    private Value applyFilterToNestedPath(EObject astNode, Value parentValue, String fieldName, List<Step> steps,
            int stepIndex, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        // Handle implicit array mapping: apply nested path to each element
        if (parentValue instanceof ArrayValue arrayValue) {
            return FilterCollectionRebuilder.traverseArray(arrayValue, element -> applyFilterToNestedPath(astNode,
                    element, fieldName, steps, stepIndex, functionIdentifier, arguments, context));
        }

        if (!(parentValue instanceof ObjectValue objectValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_FIELD_REQUIRES_OBJECT, fieldName);
        }

        val fieldValue = objectValue.get(fieldName);
        if (fieldValue == null) {
            return parentValue;
        }

        val updatedFieldValue = applyFilterFunctionToPathRecursive(astNode, fieldValue, steps, stepIndex,
                functionIdentifier, arguments, context);
        if (updatedFieldValue instanceof ErrorValue) {
            return updatedFieldValue;
        }

        return FilterCollectionRebuilder.traverseObjectSelective(objectValue, key -> key.equals(fieldName),
                key -> updatedFieldValue);
    }

    /**
     * Applies a filter function to a nested path through an array element.
     *
     * @param parentValue
     * the parent array value
     * @param index
     * the array index to navigate through
     * @param steps
     * all path steps
     * @param stepIndex
     * the current step index (points to next step after index)
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated parent array
     */
    private Value applyFilterToNestedArrayElement(EObject astNode, Value parentValue, int index, List<Step> steps,
            int stepIndex, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_EXPRESSION_STEP_REQUIRES_ARRAY);
        }

        val normalizedIndex = FilterCollectionRebuilder.normalizeIndex(index, arrayValue.size());
        if (normalizedIndex < 0 || normalizedIndex >= arrayValue.size()) {
            return parentValue;
        }

        val elementValue        = arrayValue.get(normalizedIndex);
        val updatedElementValue = applyFilterFunctionToPathRecursive(astNode, elementValue, steps, stepIndex,
                functionIdentifier, arguments, context);
        if (updatedElementValue instanceof ErrorValue) {
            return updatedElementValue;
        }

        return FilterCollectionRebuilder.traverseArraySelective(arrayValue, i -> i == normalizedIndex,
                i -> updatedElementValue);
    }

    /**
     * Applies a filter function to nested paths through array slice elements.
     *
     * @param parentValue
     * the parent array value
     * @param slicingStep
     * the slicing step defining the slice range
     * @param steps
     * all path steps
     * @param stepIndex
     * the current step index (points to next step after slice)
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated parent array
     */
    private Value applyFilterToNestedArraySlice(Value parentValue, ArraySlicingStep slicingStep, List<Step> steps,
            int stepIndex, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Error.at(slicingStep, parentValue.metadata(), RUNTIME_ERROR_SLICING_REQUIRES_ARRAY);
        }

        val arraySize = arrayValue.size();
        val step      = slicingStep.getStep() != null ? slicingStep.getStep().intValue() : 1;

        if (step == 0) {
            return Error.at(slicingStep, parentValue.metadata(), RUNTIME_ERROR_SLICING_STEP_ZERO);
        }

        val from = FilterCollectionRebuilder
                .normalizeIndex(slicingStep.getIndex() != null ? slicingStep.getIndex().intValue() : 0, arraySize);
        val to   = FilterCollectionRebuilder
                .normalizeIndex(slicingStep.getTo() != null ? slicingStep.getTo().intValue() : arraySize, arraySize);

        return FilterCollectionRebuilder.traverseArraySelective(arrayValue, i -> isInNormalizedSlice(i, from, to, step),
                i -> applyFilterFunctionToPathRecursive(slicingStep, arrayValue.get(i), steps, stepIndex,
                        functionIdentifier, arguments, context));
    }

    /**
     * Applies a filter statement with 'each' keyword to array elements.
     * <p>
     * The 'each' keyword applies the filter to each element of an array. Elements
     * that result in UNDEFINED are filtered
     * out.
     *
     * @param parentValue
     * the parent array value
     * @param target
     * the target path (maybe null)
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the filtered array
     */
    private Value applyEachFilterStatement(EObject astNode, Value parentValue,
            io.sapl.grammar.sapl.BasicRelative target, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_EACH_REQUIRES_ARRAY);
        }

        val builder = ArrayValue.builder().withMetadata(arrayValue.metadata());
        for (val element : arrayValue) {
            Value result;

            if (target != null && !target.getSteps().isEmpty()) {
                result = applyFilterFunctionToPath(astNode, element, target.getSteps(), functionIdentifier, arguments,
                        context);
            } else {
                val filterResult = applyFilterFunctionToValue(target, element, functionIdentifier, arguments, context);
                if (!(filterResult instanceof Value)) {
                    throw new SaplCompilerException(COMPILE_ERROR_NO_DYNAMIC_FILTER_ARGUMENTS_IN_EACH, astNode);
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
     * For arrays: applies filter to each element (preserves array structure). For
     * objects: applies filter to each field
     * value (preserves object structure).
     *
     * @param parentValue
     * the parent value (array or object)
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the filtered value with all elements/fields transformed
     */
    private CompiledExpression applyWildcardStepFilter(EObject astNode, Value parentValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (parentValue instanceof ArrayValue arrayValue) {
            return applyWildcardToArray(astNode, arrayValue, functionIdentifier, arguments, context);
        }

        if (parentValue instanceof ObjectValue objectValue) {
            return applyWildcardToObject(astNode, objectValue, functionIdentifier, arguments, context);
        }

        return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_WILDCARD_REQUIRES_ARRAY_OR_OBJECT);
    }

    private CompiledExpression applyWildcardToArray(EObject astNode, ArrayValue arrayValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        return FilterApplicationStrategy.applyFilterToAllArrayElements(astNode, arrayValue, functionIdentifier,
                arguments, context);
    }

    private CompiledExpression applyWildcardToObject(EObject astNode, ObjectValue objectValue,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        return FilterApplicationStrategy.applyFilterToAllObjectFields(astNode, objectValue, functionIdentifier,
                arguments, context);
    }

    /**
     * Applies a filter function to nested paths through wildcard elements/fields.
     *
     * @param parentValue
     * the parent value (array or object)
     * @param steps
     * all path steps
     * @param stepIndex
     * the current step index (points to next step after wildcard)
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated parent value
     */
    private Value applyFilterToNestedWildcard(EObject astNode, Value parentValue, List<Step> steps, int stepIndex,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (parentValue instanceof ArrayValue arrayValue) {
            return FilterCollectionRebuilder.traverseArray(arrayValue,
                    element -> applyFilterFunctionToPathRecursive(astNode, element, steps, stepIndex,
                            functionIdentifier, arguments, context));
        }

        if (parentValue instanceof ObjectValue objectValue) {
            return FilterCollectionRebuilder.traverseObject(objectValue,
                    fieldValue -> applyFilterFunctionToPathRecursive(astNode, fieldValue, steps, stepIndex,
                            functionIdentifier, arguments, context));
        }

        return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_WILDCARD_REQUIRES_ARRAY_OR_OBJECT);
    }

    /**
     * Applies a filter function to all matching keys recursively (recursive
     * descent).
     * <p>
     * Recursively searches for the key in all nested objects and arrays, applies
     * the filter to all matching values.
     *
     * @param parentValue
     * the parent value to search
     * @param key
     * the key to match
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated value with filters applied to all matching keys
     */
    private CompiledExpression applyRecursiveKeyStepFilter(EObject astNode, Value parentValue, String key,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        // Check for dynamic arguments - not yet supported in recursive filters
        if (arguments.nature() != Nature.VALUE) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_DYNAMIC_ARGS_NOT_SUPPORTED_RECURSIVE_KEY);
        }

        if (parentValue instanceof ObjectValue objectValue) {
            return applyRecursiveKeyToObject(astNode, objectValue, key, functionIdentifier, arguments, context);
        }

        if (parentValue instanceof ArrayValue arrayValue) {
            return applyRecursiveKeyToArray(astNode, arrayValue, key, functionIdentifier, arguments, context);
        }

        // Non-array/non-object: no matches, return unchanged
        return parentValue;
    }

    private Value applyRecursiveKeyToObject(EObject astNode, ObjectValue objectValue, String key,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        val builder = ObjectValue.builder().withMetadata(objectValue.metadata());

        for (val entry : objectValue.entrySet()) {
            val processedValue = entry.getKey().equals(key)
                    ? applyFilterToMatchingKey(astNode, entry.getValue(), functionIdentifier, arguments, context)
                    : applyFilterToNonMatchingKey(astNode, entry.getValue(), key, functionIdentifier, arguments,
                            context);

            if (processedValue instanceof ErrorValue) {
                return processedValue;
            }

            if (!(processedValue instanceof UndefinedValue)) {
                builder.put(entry.getKey(), processedValue);
            }
        }

        return builder.build();
    }

    private Value applyFilterToMatchingKey(EObject astNode, Value fieldValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val result = applyFilterFunctionToValue(astNode, fieldValue, functionIdentifier, arguments, context);
        if (!(result instanceof Value filteredValue)) {
            return Error.at(astNode, fieldValue.metadata(), RUNTIME_ERROR_UNEXPECTED_NON_VALUE_CONSTANT_ARGS);
        }
        return filteredValue;
    }

    private Value applyFilterToNonMatchingKey(EObject astNode, Value fieldValue, String key, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val recursedResult = applyRecursiveKeyStepFilter(astNode, fieldValue, key, functionIdentifier, arguments,
                context);
        if (!(recursedResult instanceof Value recursedValue)) {
            return Error.at(astNode, fieldValue.metadata(), RUNTIME_ERROR_UNEXPECTED_NON_VALUE_RECURSIVE_KEY);
        }
        return recursedValue;
    }

    private Value applyRecursiveKeyToArray(EObject astNode, ArrayValue arrayValue, String key,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        // Arrays don't have keys, so just recurse into each element
        return FilterCollectionRebuilder.traverseArray(arrayValue, element -> {
            val recursedResult = applyRecursiveKeyStepFilter(astNode, element, key, functionIdentifier, arguments,
                    context);
            if (!(recursedResult instanceof Value recursedValue)) {
                return Error.at(astNode, arrayValue.metadata(), RUNTIME_ERROR_UNEXPECTED_NON_VALUE_RECURSIVE_KEY);
            }
            return recursedValue;
        });
    }

    /**
     * Applies a filter function recursively to all nested structures (recursive
     * wildcard).
     * <p>
     * Note: For filtering, recursive wildcard behaves like regular wildcard at the
     * current level. This matches the
     * original implementation behavior where recursive descent doesn't translate
     * well to filtering.
     *
     * @param parentValue
     * the parent value to search
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated value with filters applied recursively
     */
    private CompiledExpression applyRecursiveWildcardStepFilter(EObject astNode, Value parentValue,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        // For filtering, @..* is treated as @.* (regular wildcard)
        // This matches the original implementation where recursive descent doesn't
        // translate well to filtering
        return applyWildcardStepFilter(astNode, parentValue, functionIdentifier, arguments, context);
    }

    /**
     * Applies a filter function to all matching indices recursively (recursive
     * index descent).
     * <p>
     * Recursively searches for the index in all nested arrays, applies the filter
     * to all matching elements.
     *
     * @param parentValue
     * the parent value to search
     * @param index
     * the index to match
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated value with filters applied to all matching indices
     */
    private CompiledExpression applyRecursiveIndexStepFilter(EObject astNode, Value parentValue, int index,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        // Check for dynamic arguments - not yet supported in recursive filters
        if (arguments.nature() != Nature.VALUE) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_DYNAMIC_ARGS_NOT_SUPPORTED_RECURSIVE_INDEX);
        }

        if (parentValue instanceof ArrayValue arrayValue) {
            return applyRecursiveIndexToArray(astNode, arrayValue, index, functionIdentifier, arguments, context);
        }

        if (parentValue instanceof ObjectValue objectValue) {
            return applyRecursiveIndexToObject(astNode, objectValue, index, functionIdentifier, arguments, context);
        }

        // Non-array/non-object: no matches, return unchanged
        return parentValue;
    }

    private Value applyRecursiveIndexToArray(EObject astNode, ArrayValue arrayValue, int index,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        val builder         = ArrayValue.builder().withMetadata(arrayValue.metadata());
        val normalizedIndex = FilterCollectionRebuilder.normalizeIndex(index, arrayValue.size());

        for (int elementIndex = 0; elementIndex < arrayValue.size(); elementIndex++) {
            val element        = arrayValue.get(elementIndex);
            val recursedResult = applyRecursiveIndexStepFilter(astNode, element, index, functionIdentifier, arguments,
                    context);

            if (!(recursedResult instanceof Value recursedValue)) {
                return Error.at(astNode, arrayValue.metadata(), RUNTIME_ERROR_UNEXPECTED_NON_VALUE_RECURSIVE_INDEX);
            }
            if (recursedValue instanceof ErrorValue) {
                return recursedValue;
            }

            val processedValue = (elementIndex == normalizedIndex)
                    ? applyFilterToMatchingIndex(astNode, recursedValue, functionIdentifier, arguments, context)
                    : recursedValue;

            if (processedValue instanceof ErrorValue) {
                return processedValue;
            }
            if (!(processedValue instanceof UndefinedValue)) {
                builder.add(processedValue);
            }
        }

        return builder.build();
    }

    private Value applyFilterToMatchingIndex(EObject astNode, Value value, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val result = applyFilterFunctionToValue(astNode, value, functionIdentifier, arguments, context);
        if (!(result instanceof Value filteredValue)) {
            return Error.at(astNode, value.metadata(), RUNTIME_ERROR_UNEXPECTED_NON_VALUE_CONSTANT_ARGS);
        }
        return filteredValue;
    }

    private Value applyRecursiveIndexToObject(EObject astNode, ObjectValue objectValue, int index,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        // Objects don't have indices, so just recurse into each field value
        return FilterCollectionRebuilder.traverseObject(objectValue, fieldValue -> {
            val recursedResult = applyRecursiveIndexStepFilter(astNode, fieldValue, index, functionIdentifier,
                    arguments, context);
            if (!(recursedResult instanceof Value recursedValue)) {
                return Error.at(astNode, objectValue.metadata(), RUNTIME_ERROR_UNEXPECTED_NON_VALUE_RECURSIVE_INDEX);
            }
            return recursedValue;
        });
    }

    /**
     * Applies a filter function to selected object attributes (attribute union).
     * <p>
     * Applies the filter only to the specified attributes of an object.
     *
     * @param parentValue
     * the parent object value
     * @param attributes
     * the list of attribute names to filter
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated object with filtered attributes
     */
    private CompiledExpression applyAttributeUnionStepFilter(EObject astNode, Value parentValue,
            List<String> attributes, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        if (!(parentValue instanceof ObjectValue objectValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_ATTRIBUTE_UNION_REQUIRES_OBJECT);
        }

        return FilterApplicationStrategy.applyFilterToObjectFields(astNode, objectValue, attributes::contains,
                functionIdentifier, arguments, context);
    }

    /**
     * Applies a filter function to selected array indices (index union).
     * <p>
     * Applies the filter only to elements at the specified indices. Supports
     * negative indices.
     *
     * @param parentValue
     * the parent array value
     * @param indices
     * the list of indices to filter
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated array with filtered elements
     */
    private CompiledExpression applyIndexUnionStepFilter(EObject astNode, Value parentValue,
            List<java.math.BigDecimal> indices, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            var metadata = parentValue.metadata();
            for (val arg : arguments.arguments()) {
                if (arg instanceof Value v) {
                    metadata = metadata.merge(v.metadata());
                }
            }
            return Error.at(astNode, metadata, RUNTIME_ERROR_INDEX_UNION_REQUIRES_ARRAY);
        }

        val arraySize         = arrayValue.size();
        val normalizedIndices = indices.stream()
                .map(index -> FilterCollectionRebuilder.normalizeIndex(index.intValue(), arraySize))
                .filter(index -> index >= 0 && index < arraySize).toList();

        return FilterApplicationStrategy.applyFilterToArrayElements(astNode, arrayValue, normalizedIndices::contains,
                functionIdentifier, arguments, context);
    }

    /**
     * Applies a filter function to nested paths through recursive key descent.
     *
     * @param parentValue
     * the parent value
     * @param key
     * the key to match recursively
     * @param steps
     * all path steps
     * @param stepIndex
     * the current step index (points to next step after recursive key)
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated parent value
     */
    private Value applyFilterToNestedRecursiveKey(EObject astNode, Value parentValue, String key, List<Step> steps,
            int stepIndex, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (parentValue instanceof ObjectValue objectValue) {
            return FilterCollectionRebuilder.traverseObjectSelective(objectValue, fieldKey -> fieldKey.equals(key),
                    fieldKey -> applyFilterFunctionToPathRecursive(astNode, objectValue.get(fieldKey), steps, stepIndex,
                            functionIdentifier, arguments, context),
                    fieldKey -> applyFilterToNestedRecursiveKey(astNode, objectValue.get(fieldKey), key, steps,
                            stepIndex, functionIdentifier, arguments, context));
        }

        if (parentValue instanceof ArrayValue arrayValue) {
            return FilterCollectionRebuilder.traverseArray(arrayValue,
                    element -> applyFilterToNestedRecursiveKey(astNode, element, key, steps, stepIndex,
                            functionIdentifier, arguments, context));
        }

        return parentValue;
    }

    /**
     * Applies a filter function to nested paths through recursive wildcard descent.
     *
     * @param parentValue
     * the parent value
     * @param steps
     * all path steps
     * @param stepIndex
     * the current step index (points to next step after recursive wildcard)
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated parent value
     */
    private Value applyFilterToNestedRecursiveWildcard(EObject astNode, Value parentValue, List<Step> steps,
            int stepIndex, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        // For nested recursive wildcard, treat it like regular wildcard at current
        // level
        // This matches the original implementation behavior
        return applyFilterToNestedWildcard(astNode, parentValue, steps, stepIndex, functionIdentifier, arguments,
                context);
    }

    /**
     * Applies a filter function to nested paths through recursive index descent.
     *
     * @param parentValue
     * the parent value
     * @param index
     * the index to match recursively
     * @param steps
     * all path steps
     * @param stepIndex
     * the current step index (points to next step after recursive index)
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated parent value
     */
    private Value applyFilterToNestedRecursiveIndex(EObject astNode, Value parentValue, int index, List<Step> steps,
            int stepIndex, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (parentValue instanceof ArrayValue arrayValue) {
            val normalizedIndex = FilterCollectionRebuilder.normalizeIndex(index, arrayValue.size());
            return applyRecursiveIndexToArrayNested(astNode, arrayValue, index, normalizedIndex, steps, stepIndex,
                    functionIdentifier, arguments, context);
        }

        if (parentValue instanceof ObjectValue objectValue) {
            return FilterCollectionRebuilder.traverseObject(objectValue,
                    fieldValue -> applyFilterToNestedRecursiveIndex(astNode, fieldValue, index, steps, stepIndex,
                            functionIdentifier, arguments, context));
        }

        return parentValue;
    }

    private Value applyRecursiveIndexToArrayNested(EObject astNode, ArrayValue arrayValue, int rawIndex,
            int normalizedIndex, List<Step> steps, int stepIndex, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val builder = ArrayValue.builder().withMetadata(arrayValue.metadata());
        for (int i = 0; i < arrayValue.size(); i++) {
            // First recurse into element
            val recursedValue = applyFilterToNestedRecursiveIndex(astNode, arrayValue.get(i), rawIndex, steps,
                    stepIndex, functionIdentifier, arguments, context);
            if (recursedValue instanceof ErrorValue) {
                return recursedValue;
            }

            // Then apply remaining steps if matching index
            if (i == normalizedIndex) {
                val updatedValue = applyFilterFunctionToPathRecursive(astNode, recursedValue, steps, stepIndex,
                        functionIdentifier, arguments, context);
                if (updatedValue instanceof ErrorValue) {
                    return updatedValue;
                }
                builder.add(updatedValue);
            } else {
                builder.add(recursedValue);
            }
        }
        return builder.build();
    }

    /**
     * Applies a filter function to nested paths through attribute union.
     *
     * @param parentValue
     * the parent object value
     * @param attributes
     * the list of attribute names
     * @param steps
     * all path steps
     * @param stepIndex
     * the current step index (points to next step after union)
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated parent object
     */
    private Value applyFilterToNestedAttributeUnion(EObject astNode, Value parentValue, List<String> attributes,
            List<Step> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        if (!(parentValue instanceof ObjectValue objectValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_ATTRIBUTE_UNION_REQUIRES_OBJECT);
        }

        return FilterCollectionRebuilder.traverseObjectSelective(objectValue, attributes::contains,
                fieldKey -> applyFilterFunctionToPathRecursive(astNode, objectValue.get(fieldKey), steps, stepIndex,
                        functionIdentifier, arguments, context));
    }

    /**
     * Applies a filter function to nested paths through index union.
     *
     * @param parentValue
     * the parent array value
     * @param indices
     * the list of indices
     * @param steps
     * all path steps
     * @param stepIndex
     * the current step index (points to next step after union)
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated parent array
     */
    private Value applyFilterToNestedIndexUnion(EObject astNode, Value parentValue, List<java.math.BigDecimal> indices,
            List<Step> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_INDEX_UNION_REQUIRES_ARRAY);
        }

        val arraySize         = arrayValue.size();
        val normalizedIndices = indices.stream()
                .map(index -> FilterCollectionRebuilder.normalizeIndex(index.intValue(), arraySize))
                .filter(index -> index >= 0 && index < arraySize).toList();

        return FilterCollectionRebuilder.traverseArraySelective(arrayValue, normalizedIndices::contains,
                i -> applyFilterFunctionToPathRecursive(astNode, arrayValue.get(i), steps, stepIndex,
                        functionIdentifier, arguments, context));
    }

    /**
     * Applies a filter function to elements/fields matching a condition (condition
     * step).
     * <p>
     * For arrays: applies filter to elements where condition evaluates to true. For
     * objects: applies filter to field
     * values where condition evaluates to true.
     *
     * @param parentValue
     * the parent value (array or object)
     * @param conditionStep
     * the condition step with the expression to evaluate
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the filtered value
     */
    private CompiledExpression applyConditionStepFilter(EObject astNode, Value parentValue, ConditionStep conditionStep,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        // Non-array/non-object: no elements to match condition, return unchanged
        if (!(parentValue instanceof ArrayValue) && !(parentValue instanceof ObjectValue)) {
            return parentValue;
        }

        // Compile condition expression once
        val conditionExpr = ExpressionCompiler.compileExpression(conditionStep.getExpression(), context);

        // Check if condition is static (constant value)
        if (!(conditionExpr instanceof Value conditionValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_DYNAMIC_CONDITION_UNSUPPORTED);
        }
        val metadata = conditionValue.metadata().merge(parentValue.metadata());
        if (conditionValue instanceof ErrorValue) {
            return conditionValue.withMetadata(metadata);
        }

        if (!(conditionValue instanceof BooleanValue booleanResult)) {
            return Error.at(astNode, metadata, RUNTIME_ERROR_CONDITION_TYPE_MISMATCH,
                    conditionValue.getClass().getSimpleName());
        }

        // With constant condition: if false, return unchanged; if true, apply to all
        // elements (wildcard)
        if (!booleanResult.value()) {
            return parentValue;
        }

        // Condition is constant true - apply filter like wildcard
        return applyWildcardStepFilter(astNode, parentValue, functionIdentifier, arguments, context);
    }

    /**
     * Applies a filter function to nested paths through condition-matched
     * elements/fields.
     *
     * @param parentValue
     * the parent value (array or object)
     * @param conditionStep
     * the condition step
     * @param steps
     * all path steps
     * @param stepIndex
     * the current step index (points to next step after condition)
     * @param functionIdentifier
     * the function identifier
     * @param arguments
     * the function arguments
     * @param context
     * the compilation context
     *
     * @return the updated parent value
     */
    private Value applyFilterToNestedCondition(EObject astNode, Value parentValue, ConditionStep conditionStep,
            List<Step> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        // Non-array/non-object: no elements to match condition, return unchanged
        if (!(parentValue instanceof ArrayValue) && !(parentValue instanceof ObjectValue)) {
            return parentValue;
        }

        // Compile condition expression once
        val conditionExpr = ExpressionCompiler.compileExpression(conditionStep.getExpression(), context);

        // Check if condition is static (constant value)
        if (!(conditionExpr instanceof Value conditionValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_DYNAMIC_CONDITION_UNSUPPORTED);
        }
        val metadata = conditionValue.metadata().merge(parentValue.metadata());
        if (conditionValue instanceof ErrorValue) {
            return conditionValue.withMetadata(metadata);
        }
        if (!(conditionValue instanceof io.sapl.api.model.BooleanValue booleanResult)) {
            return Error.at(astNode, metadata, RUNTIME_ERROR_CONDITION_TYPE_MISMATCH,
                    conditionValue.getClass().getSimpleName());
        }

        // With constant condition: if false, return unchanged; if true, descend like
        // wildcard
        if (!booleanResult.value()) {
            return parentValue.withMetadata(metadata);
        }

        // Condition is constant true - descend into all elements/fields
        return applyFilterToNestedWildcard(astNode, parentValue, steps, stepIndex, functionIdentifier, arguments,
                context);
    }

    /**
     * Applies a filter function using an expression step to compute the key/index.
     * <p>
     * Expression steps allow dynamic key/index selection: @[(expression)]
     * <p>
     * For arrays: expression must evaluate to a number (index) For objects:
     * expression must evaluate to a string (key)
     */
    private CompiledExpression applyExpressionStepFilter(EObject astNode, Value parentValue,
            ExpressionStep expressionStep, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        if (isNotArrayOrObject(parentValue)) {
            return parentValue;
        }

        val keyOrIndexValue = compileExpressionStepValue(expressionStep, context);
        if (keyOrIndexValue instanceof ErrorValue) {
            return keyOrIndexValue;
        }

        if (parentValue instanceof ArrayValue arrayValue) {
            return applyExpressionFilterToArray(astNode, arrayValue, keyOrIndexValue, functionIdentifier, arguments,
                    context);
        }

        return applyExpressionFilterToObject(astNode, (ObjectValue) parentValue, keyOrIndexValue, functionIdentifier,
                arguments, context);
    }

    private CompiledExpression applyExpressionFilterToArray(EObject astNode, ArrayValue arrayValue,
            Value keyOrIndexValue, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        val metadata = arrayValue.metadata().merge(keyOrIndexValue.metadata());
        if (!(keyOrIndexValue instanceof NumberValue numberValue)) {
            return Error.at(astNode, metadata, RUNTIME_ERROR_ARRAY_ACCESS_TYPE_MISMATCH,
                    keyOrIndexValue.getClass().getSimpleName());
        }
        val result = applyIndexStepFilter(astNode, arrayValue, numberValue.value().intValue(), functionIdentifier,
                arguments, context);
        if (!(result instanceof Value resultValue)) {
            return Error.at(astNode, metadata, RUNTIME_ERROR_UNEXPECTED_NON_VALUE_EXPRESSION_STEP);
        }
        return resultValue.withMetadata(metadata);
    }

    private CompiledExpression applyExpressionFilterToObject(EObject astNode, ObjectValue objectValue,
            Value keyOrIndexValue, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        val metadata = objectValue.metadata().merge(keyOrIndexValue.metadata());
        if (!(keyOrIndexValue instanceof TextValue textValue)) {
            return Error.at(astNode, metadata, RUNTIME_ERROR_OBJECT_ACCESS_TYPE_MISMATCH,
                    keyOrIndexValue.getClass().getSimpleName());
        }
        val key = textValue.value();
        if (!objectValue.containsKey(key)) {
            return objectValue.withMetadata(metadata);
        }
        val result = applyKeyStepFilter(astNode, objectValue, key, functionIdentifier, arguments, context);
        if (!(result instanceof Value resultValue)) {
            return Error.at(astNode, metadata, RUNTIME_ERROR_UNEXPECTED_NON_VALUE_EXPRESSION_STEP);
        }
        return resultValue.withMetadata(metadata);
    }

    /**
     * Applies a filter function to nested paths through an expression-selected
     * element/field.
     */
    private Value applyFilterToNestedExpression(EObject astNode, Value parentValue, ExpressionStep expressionStep,
            List<Step> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        if (isNotArrayOrObject(parentValue)) {
            return parentValue;
        }

        val keyOrIndexValue = compileExpressionStepValue(expressionStep, context);
        if (keyOrIndexValue instanceof ErrorValue) {
            return keyOrIndexValue;
        }

        if (parentValue instanceof ArrayValue arrayValue) {
            return applyNestedExpressionToArray(astNode, arrayValue, keyOrIndexValue, steps, stepIndex,
                    functionIdentifier, arguments, context);
        }

        return applyNestedExpressionToObject(astNode, (ObjectValue) parentValue, keyOrIndexValue, steps, stepIndex,
                functionIdentifier, arguments, context);
    }

    private Value applyNestedExpressionToArray(EObject astNode, ArrayValue arrayValue, Value keyOrIndexValue,
            List<Step> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        val metadata = arrayValue.metadata().merge(keyOrIndexValue.metadata());
        if (!(keyOrIndexValue instanceof NumberValue numberValue)) {
            return Error.at(astNode, metadata, RUNTIME_ERROR_ARRAY_ACCESS_TYPE_MISMATCH,
                    keyOrIndexValue.getClass().getSimpleName());
        }
        int index = numberValue.value().intValue();
        if (index < 0 || index >= arrayValue.size()) {
            return Error.at(astNode, metadata, RUNTIME_ERROR_INDEX_OUT_OF_BOUNDS, arrayValue.size() - 1, index);
        }
        return applyFilterToNestedArrayElement(astNode, arrayValue, index, steps, stepIndex, functionIdentifier,
                arguments, context).withMetadata(metadata);
    }

    private Value applyNestedExpressionToObject(EObject astNode, ObjectValue objectValue, Value keyOrIndexValue,
            List<Step> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        val metadata = objectValue.metadata().merge(keyOrIndexValue.metadata());
        if (!(keyOrIndexValue instanceof TextValue textValue)) {
            return Error.at(astNode, metadata, RUNTIME_ERROR_OBJECT_ACCESS_TYPE_MISMATCH,
                    keyOrIndexValue.getClass().getSimpleName());
        }
        val key = textValue.value();
        if (!objectValue.containsKey(key)) {
            return objectValue.withMetadata(metadata);
        }
        return applyFilterToNestedPath(astNode, objectValue, key, steps, stepIndex, functionIdentifier, arguments,
                context).withMetadata(metadata);
    }

    private boolean isNotArrayOrObject(Value value) {
        return !(value instanceof ArrayValue) && !(value instanceof ObjectValue);
    }

    private Value compileExpressionStepValue(ExpressionStep expressionStep, CompilationContext context) {
        val keyOrIndexExpr = ExpressionCompiler.compileExpression(expressionStep.getExpression(), context);
        if (!(keyOrIndexExpr instanceof Value keyOrIndexValue)) {
            return Error.at(expressionStep, ValueMetadata.EMPTY, RUNTIME_ERROR_DYNAMIC_EXPRESSION_NOT_SUPPORTED);
        }
        return keyOrIndexValue;
    }
}
