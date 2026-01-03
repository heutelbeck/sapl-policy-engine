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
package io.sapl.compiler;

import static io.sapl.compiler.StringsUtil.unquoteString;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.*;
import io.sapl.grammar.antlr.SAPLParser.*;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.ParserRuleContext;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Compiles SAPL filter expressions (|- operator) into optimized executable
 * representations.
 * <p>
 * Filters transform values by applying functions, supporting both simple
 * ({@code value |- func}) and extended
 * ({@code value |- { @.field : func }}) syntax.
 */
@UtilityClass
public class FilterCompiler {

    public static final String  COMPILE_ERROR_NO_DYNAMIC_FILTER_ARGUMENTS_IN_EACH = "Compilation failed. Dynamic filter arguments are not supported in extended 'each' filters.";
    private static final String COMPILE_ERROR_STEP_TYPE_NOT_SUPPORTED             = "Compilation failed. Step type not supported: %s.";
    public static final String  COMPILE_ERROR_STREAM_EXPRESSION_IN_PURE_FILTER    = "Compilation failed. Stream expression in pure filter arguments.";
    private static final String COMPILE_ERROR_UNKNOWN_FILTER_TYPE                 = "Compilation failed. Unknown filter type: %s.";

    private static final String RUNTIME_ERROR_ARRAY_ACCESS_TYPE_MISMATCH                 = "Array access type mismatch. Expected an integer, was: %s.";
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
    private static final String RUNTIME_ERROR_OBJECT_ACCESS_TYPE_MISMATCH                = "Object access type mismatch. Expected a string, was: %s.";
    private static final String RUNTIME_ERROR_SLICING_REQUIRES_ARRAY                     = "Cannot apply slicing step to non-array value.";
    private static final String RUNTIME_ERROR_SLICING_STEP_ZERO                          = "Step must not be zero.";
    private static final String RUNTIME_ERROR_UNEXPECTED_FILTER_RESULT_TYPE              = "Unexpected filter result type.";
    private static final String RUNTIME_ERROR_UNEXPECTED_NON_VALUE_ARRAY_MAPPING         = "Unexpected non-value result in array mapping with constant arguments.";
    private static final String RUNTIME_ERROR_UNEXPECTED_NON_VALUE_CONSTANT_ARGS         = "Unexpected non-value result with constant arguments.";
    private static final String RUNTIME_ERROR_UNEXPECTED_NON_VALUE_EXPRESSION_STEP       = "Unexpected non-value result in expression step filter.";
    private static final String RUNTIME_ERROR_UNEXPECTED_NON_VALUE_RECURSIVE_INDEX       = "Unexpected non-value result in recursive index filter.";
    private static final String RUNTIME_ERROR_UNEXPECTED_NON_VALUE_RECURSIVE_KEY         = "Unexpected non-value result in recursive key filter.";
    private static final String RUNTIME_ERROR_WILDCARD_REQUIRES_ARRAY_OR_OBJECT          = "Cannot apply wildcard step to non-array/non-object value.";
    private static final String RUNTIME_ERROR_INVALID_FUNCTION_NAME                      = "Invalid function name '%s'. Function names must be fully qualified (e.g., 'library.function'). Check that the function is imported correctly.";

    /**
     * Pre-compiled filter statement containing the resolved function name and
     * compiled arguments. Resolving at compile time avoids closure capture bugs
     * where the CompilationContext imports list may be cleared before runtime.
     */
    private record CompiledFilterStatement(
            FilterStatementContext statement,
            String functionIdentifier,
            CompiledArguments arguments) {}

    /**
     * Safely creates a FunctionInvocation and evaluates it, returning an error if
     * the
     * function name is invalid.
     */
    private static Value safeEvaluateFunction(String functionIdentifier, List<Value> arguments, FunctionBroker broker) {
        try {
            val invocation = new FunctionInvocation(functionIdentifier, arguments);
            return broker.evaluateFunction(invocation);
        } catch (IllegalArgumentException e) {
            return Error.create(RUNTIME_ERROR_INVALID_FUNCTION_NAME.formatted(functionIdentifier));
        }
    }

    /**
     * Compiles a filter expression (|- operator).
     * <p>
     * Filters apply functions to values, optionally using the 'each' keyword to map
     * over arrays. Extended filters support complex targeting with paths.
     *
     * @param parent the expression to filter
     * @param filter the filter component (simple or extended)
     * @param context the compilation context
     * @return the compiled filter expression
     */
    public CompiledExpression compileFilter(CompiledExpression parent, FilterComponentContext filter,
            CompilationContext context) {
        if (parent instanceof ErrorValue) {
            return parent;
        }
        if (parent instanceof UndefinedValue(ValueMetadata metadata)) {
            return Error.at(filter, metadata, RUNTIME_ERROR_FILTERS_CANNOT_BE_APPLIED_TO_UNDEFINED);
        }
        return switch (filter) {
        case FilterSimpleContext simple     -> compileSimpleFilter(parent, simple, context);
        case FilterExtendedContext extended -> compileExtendedFilter(parent, extended, context);
        default                             -> throw new SaplCompilerException(
                String.format(COMPILE_ERROR_UNKNOWN_FILTER_TYPE, filter.getClass().getSimpleName()), filter);
        };
    }

    /**
     * Compiles a simple filter expression: {@code value |- [each] func(args)}.
     * <p>
     * Without 'each': applies function to entire value. With 'each': maps function
     * over array elements.
     */
    private CompiledExpression compileSimpleFilter(CompiledExpression parent, FilterSimpleContext filter,
            CompilationContext context) {
        val arguments          = compileFilterArguments(filter.arguments(), context);
        val finalArguments     = arguments;
        val functionIdentifier = resolveFilterFunctionName(filter.functionIdentifier(), context);

        UnaryOperator<CompiledExpression> filterOp = parentValue -> {
            if (!(parentValue instanceof Value value)) {
                return Error.at(filter, ValueMetadata.EMPTY, RUNTIME_ERROR_FILTER_REQUIRES_VALUE);
            }

            if (filter.each != null) {
                return applyFilterFunctionToEachArrayElement(value, filter, functionIdentifier, finalArguments,
                        context);
            }

            return applyFilterFunctionToValue(filter, value, functionIdentifier, finalArguments, context);
        };

        return wrapFilterOperation(filter, parent, filterOp);
    }

    private String resolveFilterFunctionName(FunctionIdentifierContext functionId, CompilationContext context) {
        val rawName = extractFunctionName(functionId);
        return context.resolveFunctionName(rawName);
    }

    private String extractFunctionName(FunctionIdentifierContext functionId) {
        if (functionId == null || functionId.idFragment == null || functionId.idFragment.isEmpty()) {
            return "";
        }
        val builder = new StringBuilder();
        for (int i = 0; i < functionId.idFragment.size(); i++) {
            if (i > 0) {
                builder.append('.');
            }
            builder.append(getIdentifierName(functionId.idFragment.get(i)));
        }
        return builder.toString();
    }

    private String getIdentifierName(SaplIdContext saplId) {
        if (saplId == null) {
            return "";
        }
        return switch (saplId) {
        case PlainIdContext c            -> c.ID().getText();
        case ReservedIdentifierContext c -> getReservedIdName(c.reservedId());
        default                          -> "";
        };
    }

    private String getReservedIdName(ReservedIdContext reservedId) {
        return switch (reservedId) {
        case SubjectIdContext ignored     -> "subject";
        case ActionIdContext ignored      -> "action";
        case ResourceIdContext ignored    -> "resource";
        case EnvironmentIdContext ignored -> "environment";
        default                           -> "";
        };
    }

    private CompiledArguments compileFilterArguments(ArgumentsContext argumentsCtx, CompilationContext context) {
        if (argumentsCtx == null || argumentsCtx.args == null || argumentsCtx.args.isEmpty()) {
            return CompiledArguments.EMPTY_ARGUMENTS;
        }
        return ExpressionCompiler.compileArguments(argumentsCtx.args, context);
    }

    /**
     * Wraps a filter operation to handle Value, PureExpression, and
     * StreamExpression parents.
     */
    private CompiledExpression wrapFilterOperation(ParserRuleContext astNode, CompiledExpression parent,
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

    private static Value evaluateFilterResult(ParserRuleContext astNode, CompiledExpression result,
            EvaluationContext ctx) {
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
     */
    private CompiledExpression applyFilterFunctionToEachArrayElement(Value parentValue, FilterSimpleContext filter,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Error.at(filter, parentValue.metadata(), RUNTIME_ERROR_EACH_REQUIRES_ARRAY);
        }

        return switch (arguments.nature()) {
        case VALUE  -> {
            val builder = ArrayValue.builder().withMetadata(arrayValue.metadata());
            for (val element : arrayValue) {
                val result = applyFilterFunctionToValue(filter, element, functionIdentifier, arguments, context);
                if (result instanceof Value resultValue) {
                    if (!(resultValue instanceof UndefinedValue)) {
                        builder.add(resultValue);
                    }
                } else {
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

    private CompiledExpression createEachRuntimeExpression(ParserRuleContext astNode, ArrayValue arrayValue,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context, boolean isStream) {
        if (isStream) {
            return createStreamEachExpression(arrayValue, functionIdentifier, arguments, context);
        }
        return createPureEachExpression(astNode, arrayValue, functionIdentifier, arguments);
    }

    private CompiledExpression createStreamEachExpression(ArrayValue arrayValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux).toList();
        val stream  = Flux.combineLatest(sources, argValues -> applyFilterToEachElement(arrayValue, functionIdentifier,
                valueArrayFrom(argValues), context.getFunctionBroker()));
        return new StreamExpression(stream);
    }

    private CompiledExpression createPureEachExpression(ParserRuleContext astNode, ArrayValue arrayValue,
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

            val result = safeEvaluateFunction(functionIdentifier, valueArguments, functionBroker);
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
     * Compiles an extended filter expression: {@code value |- { stmt1, stmt2, ...
     * }}.
     */
    private CompiledExpression compileExtendedFilter(CompiledExpression parent, FilterExtendedContext filter,
            CompilationContext context) {
        // Pre-compile all filter statements at compile time to capture resolved
        // function names
        val compiledStatements = filter.filterStatement().stream()
                .map(stmt -> compileFilterStatementData(stmt, context)).toList();

        UnaryOperator<CompiledExpression> filterOp = parentExpr -> {
            if (!(parentExpr instanceof Value currentValue)) {
                return Error.at(filter, ValueMetadata.EMPTY, RUNTIME_ERROR_EXTENDED_FILTER_REQUIRES_VALUE);
            }
            for (val compiled : compiledStatements) {
                currentValue = applyCompiledFilterStatement(currentValue, compiled, context);
                if (currentValue instanceof ErrorValue) {
                    return currentValue;
                }
            }
            return currentValue;
        };
        return wrapFilterOperation(filter, parent, filterOp);
    }

    private CompiledFilterStatement compileFilterStatementData(FilterStatementContext statement,
            CompilationContext context) {
        val arguments          = compileFilterArguments(statement.arguments(), context);
        val functionIdentifier = resolveFilterFunctionName(statement.functionIdentifier(), context);
        return new CompiledFilterStatement(statement, functionIdentifier, arguments);
    }

    private Value applyCompiledFilterStatement(Value currentValue, CompiledFilterStatement compiled,
            CompilationContext context) {
        val statement          = compiled.statement();
        val functionIdentifier = compiled.functionIdentifier();
        val arguments          = compiled.arguments();

        if (statement.each != null) {
            return applyEachFilterStatement(statement, currentValue, statement.target, functionIdentifier, arguments,
                    context);
        }

        if (hasTargetPath(statement)) {
            return applyFilterFunctionToPath(statement, currentValue, statement.target.step(), functionIdentifier,
                    arguments, context);
        }

        return applyDirectFilterToValue(statement, currentValue, functionIdentifier, arguments, context);
    }

    private boolean hasTargetPath(FilterStatementContext statement) {
        return statement.target != null && statement.target.step() != null && !statement.target.step().isEmpty();
    }

    private Value applyDirectFilterToValue(ParserRuleContext astNode, Value currentValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val result = applyFilterFunctionToValue(astNode, currentValue, functionIdentifier, arguments, context);
        if (result instanceof Value resultValue) {
            return resultValue;
        }
        return Error.at(astNode, currentValue.metadata(), RUNTIME_ERROR_EXTENDED_FILTER_UNSUPPORTED_NON_VALUE);
    }

    private CompiledExpression applyFilterFunctionToValue(ParserRuleContext astNode, Value parentValue,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        return FilterApplicationStrategy.applyFilterToValue(astNode, parentValue, functionIdentifier, arguments,
                context);
    }

    /**
     * Applies a filter function to a value at a specific path.
     */
    private Value applyFilterFunctionToPath(ParserRuleContext astNode, Value parentValue, List<StepContext> steps,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        return applyFilterFunctionToPathRecursive(astNode, parentValue, steps, 0, functionIdentifier, arguments,
                context);
    }

    private Value applyFilterFunctionToPathRecursive(ParserRuleContext astNode, Value parentValue,
            List<StepContext> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        if (arguments.nature() != Nature.VALUE) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_DYNAMIC_ARGS_NOT_SUPPORTED_EXTENDED);
        }

        if (stepIndex == steps.size() - 1) {
            val result = applySingleStepFilter(parentValue, steps.get(stepIndex), functionIdentifier, arguments,
                    context);
            if (!(result instanceof Value resultValue)) {
                return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_UNEXPECTED_NON_VALUE_CONSTANT_ARGS);
            }
            return resultValue;
        }

        val currentStep = steps.get(stepIndex);
        return applyFilterToNestedStep(astNode, parentValue, currentStep, steps, stepIndex + 1, functionIdentifier,
                arguments, context);
    }

    private Value applyFilterToNestedStep(ParserRuleContext astNode, Value parentValue, StepContext step,
            List<StepContext> steps, int nextStepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        return switch (step) {
        case KeyDotStepContext c                       -> applyFilterToNestedPath(astNode, parentValue,
                getStepKeyName(c.keyStep()), steps, nextStepIndex, functionIdentifier, arguments, context);
        case EscapedKeyDotStepContext c                ->
            applyFilterToNestedPath(astNode, parentValue, unquoteString(c.escapedKeyStep().STRING().getText()), steps,
                    nextStepIndex, functionIdentifier, arguments, context);
        case WildcardDotStepContext ignored            -> applyFilterToNestedWildcard(astNode, parentValue, steps,
                nextStepIndex, functionIdentifier, arguments, context);
        case BracketStepContext c                      -> applyFilterToNestedBracketStep(astNode, parentValue, c, steps,
                nextStepIndex, functionIdentifier, arguments, context);
        case RecursiveKeyDotDotStepContext c           ->
            applyFilterToNestedRecursiveKey(astNode, parentValue, getRecursiveKeyName(c.recursiveKeyStep()), steps,
                    nextStepIndex, functionIdentifier, arguments, context);
        case RecursiveWildcardDotDotStepContext c      -> applyFilterToNestedRecursiveWildcard(astNode, parentValue,
                steps, nextStepIndex, functionIdentifier, arguments, context);
        case RecursiveIndexDotDotStepContext c         ->
            applyFilterToNestedRecursiveIndex(astNode, parentValue, getRecursiveIndexValue(c.recursiveIndexStep()),
                    steps, nextStepIndex, functionIdentifier, arguments, context);
        case AttributeFinderDotStepContext ignored     ->
            Error.at(step, parentValue.metadata(), RUNTIME_ERROR_ATTRIBUTE_FINDER_NOT_PERMITTED);
        case HeadAttributeFinderDotStepContext ignored ->
            Error.at(step, parentValue.metadata(), RUNTIME_ERROR_ATTRIBUTE_FINDER_NOT_PERMITTED);
        case null, default                             -> throw new SaplCompilerException(
                COMPILE_ERROR_STEP_TYPE_NOT_SUPPORTED.formatted(step.getClass().getSimpleName()), step);
        };
    }

    private Value applyFilterToNestedBracketStep(ParserRuleContext astNode, Value parentValue, BracketStepContext step,
            List<StepContext> steps, int nextStepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        val subscript = step.subscript();
        return switch (subscript) {
        case EscapedKeySubscriptContext c     ->
            applyFilterToNestedPath(astNode, parentValue, unquoteString(c.escapedKeyStep().STRING().getText()), steps,
                    nextStepIndex, functionIdentifier, arguments, context);
        case WildcardSubscriptContext ignored -> applyFilterToNestedWildcard(astNode, parentValue, steps, nextStepIndex,
                functionIdentifier, arguments, context);
        case IndexSubscriptContext c          -> applyFilterToNestedArrayElement(astNode, parentValue,
                parseIndex(c.indexStep().signedNumber()), steps, nextStepIndex, functionIdentifier, arguments, context);
        case SlicingSubscriptContext c        -> applyFilterToNestedArraySlice(parentValue, c.arraySlicingStep(), steps,
                nextStepIndex, functionIdentifier, arguments, context);
        case ConditionSubscriptContext c      -> applyFilterToNestedCondition(astNode, parentValue, c.conditionStep(),
                steps, nextStepIndex, functionIdentifier, arguments, context);
        case ExpressionSubscriptContext c     -> applyFilterToNestedExpression(astNode, parentValue, c.expressionStep(),
                steps, nextStepIndex, functionIdentifier, arguments, context);
        case IndexUnionSubscriptContext c     -> applyFilterToNestedIndexUnion(astNode, parentValue,
                getIndexUnionValues(c.indexUnionStep()), steps, nextStepIndex, functionIdentifier, arguments, context);
        case AttributeUnionSubscriptContext c ->
            applyFilterToNestedAttributeUnion(astNode, parentValue, getAttributeUnionKeys(c.attributeUnionStep()),
                    steps, nextStepIndex, functionIdentifier, arguments, context);
        case null, default                    -> throw new SaplCompilerException(
                COMPILE_ERROR_STEP_TYPE_NOT_SUPPORTED.formatted(subscript.getClass().getSimpleName()), step);
        };
    }

    private CompiledExpression applySingleStepFilter(Value parentValue, StepContext step, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        return switch (step) {
        case KeyDotStepContext c                       ->
            applyKeyStepFilter(step, parentValue, getStepKeyName(c.keyStep()), functionIdentifier, arguments, context);
        case EscapedKeyDotStepContext c                -> applyKeyStepFilter(step, parentValue,
                unquoteString(c.escapedKeyStep().STRING().getText()), functionIdentifier, arguments, context);
        case WildcardDotStepContext ignored            ->
            applyWildcardStepFilter(step, parentValue, functionIdentifier, arguments, context);
        case BracketStepContext c                      ->
            applySingleBracketStepFilter(parentValue, c, functionIdentifier, arguments, context);
        case RecursiveKeyDotDotStepContext c           -> applyRecursiveKeyStepFilter(step, parentValue,
                getRecursiveKeyName(c.recursiveKeyStep()), functionIdentifier, arguments, context);
        case RecursiveWildcardDotDotStepContext c      ->
            applyRecursiveWildcardStepFilter(step, parentValue, functionIdentifier, arguments, context);
        case RecursiveIndexDotDotStepContext c         -> applyRecursiveIndexStepFilter(step, parentValue,
                getRecursiveIndexValue(c.recursiveIndexStep()), functionIdentifier, arguments, context);
        case AttributeFinderDotStepContext ignored     ->
            Error.at(step, parentValue.metadata(), RUNTIME_ERROR_ATTRIBUTE_FINDER_NOT_PERMITTED);
        case HeadAttributeFinderDotStepContext ignored ->
            Error.at(step, parentValue.metadata(), RUNTIME_ERROR_ATTRIBUTE_FINDER_NOT_PERMITTED);
        case null, default                             -> throw new SaplCompilerException(
                COMPILE_ERROR_STEP_TYPE_NOT_SUPPORTED.formatted(step.getClass().getSimpleName()), step);
        };
    }

    private CompiledExpression applySingleBracketStepFilter(Value parentValue, BracketStepContext step,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        val subscript = step.subscript();
        return switch (subscript) {
        case EscapedKeySubscriptContext c     -> applyKeyStepFilter(step, parentValue,
                unquoteString(c.escapedKeyStep().STRING().getText()), functionIdentifier, arguments, context);
        case WildcardSubscriptContext ignored ->
            applyWildcardStepFilter(step, parentValue, functionIdentifier, arguments, context);
        case IndexSubscriptContext c          -> applyIndexStepFilter(step, parentValue,
                parseIndex(c.indexStep().signedNumber()), functionIdentifier, arguments, context);
        case SlicingSubscriptContext c        ->
            applySlicingStepFilter(step, parentValue, c.arraySlicingStep(), functionIdentifier, arguments, context);
        case ConditionSubscriptContext c      ->
            applyConditionStepFilter(step, parentValue, c.conditionStep(), functionIdentifier, arguments, context);
        case ExpressionSubscriptContext c     ->
            applyExpressionStepFilter(step, parentValue, c.expressionStep(), functionIdentifier, arguments, context);
        case IndexUnionSubscriptContext c     -> applyIndexUnionStepFilter(step, parentValue,
                getIndexUnionValues(c.indexUnionStep()), functionIdentifier, arguments, context);
        case AttributeUnionSubscriptContext c -> applyAttributeUnionStepFilter(step, parentValue,
                getAttributeUnionKeys(c.attributeUnionStep()), functionIdentifier, arguments, context);
        case null, default                    -> throw new SaplCompilerException(
                COMPILE_ERROR_STEP_TYPE_NOT_SUPPORTED.formatted(subscript.getClass().getSimpleName()), step);
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Step filter implementations
    // ═══════════════════════════════════════════════════════════════════════════

    private CompiledExpression applyKeyStepFilter(ParserRuleContext astNode, Value parentValue, String key,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (parentValue instanceof ArrayValue arrayValue) {
            if (arguments.nature() == Nature.VALUE) {
                val builder = ArrayValue.builder().withMetadata(arrayValue.metadata());
                for (val element : arrayValue) {
                    val result = applyKeyStepFilter(astNode, element, key, functionIdentifier, arguments, context);
                    if (result instanceof ErrorValue) {
                        return result;
                    }
                    if (!(result instanceof Value resultValue)) {
                        return Error.at(astNode, parentValue.metadata(),
                                RUNTIME_ERROR_UNEXPECTED_NON_VALUE_ARRAY_MAPPING);
                    }
                    builder.add(resultValue);
                }
                return builder.build();
            } else {
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

    private CompiledExpression createKeyStepArrayMappingExpression(ParserRuleContext astNode, ArrayValue arrayValue,
            String key, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (arguments.nature() == Nature.STREAM) {
            return createStreamKeyStepArrayMapping(astNode, arrayValue, key, functionIdentifier, arguments, context);
        }
        return createPureKeyStepArrayMapping(astNode, arrayValue, key, functionIdentifier, arguments);
    }

    private CompiledExpression createStreamKeyStepArrayMapping(ParserRuleContext astNode, ArrayValue arrayValue,
            String key, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux).toList();
        val stream  = Flux.combineLatest(sources, argValues -> applyKeyFilterToArrayElements(astNode, arrayValue, key,
                functionIdentifier, valueArrayFrom(argValues), context.getFunctionBroker()));
        return new StreamExpression(stream);
    }

    private CompiledExpression createPureKeyStepArrayMapping(ParserRuleContext astNode, ArrayValue arrayValue,
            String key, String functionIdentifier, CompiledArguments arguments) {
        return new PureExpression(
                ctx -> applyKeyFilterToArrayElements(astNode, arrayValue, key, functionIdentifier,
                        resolveArgumentsToValues(astNode, arguments.arguments(), ctx), ctx.functionBroker()),
                arguments.isSubscriptionScoped());
    }

    private Value applyKeyFilterToArrayElements(ParserRuleContext astNode, ArrayValue arrayValue, String key,
            String functionIdentifier, List<Value> resolvedArguments, FunctionBroker functionBroker) {
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

            val filterResult = safeEvaluateFunction(functionIdentifier, valueArguments, functionBroker);
            if (filterResult instanceof ErrorValue) {
                return filterResult;
            }

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

    private CompiledExpression applyIndexStepFilter(ParserRuleContext astNode, Value parentValue, int index,
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

    private CompiledExpression applySlicingStepFilter(ParserRuleContext astNode, Value parentValue,
            ArraySlicingStepContext slicingStep, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_SLICING_REQUIRES_ARRAY);
        }

        val arraySize = arrayValue.size();
        val step      = slicingStep.stepValue != null ? parseIndex(slicingStep.stepValue) : 1;

        if (step == 0) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_SLICING_STEP_ZERO);
        }

        int index = slicingStep.index != null ? parseIndex(slicingStep.index) : 0;
        if (index < 0) {
            index += arraySize;
        }

        int to = slicingStep.to != null ? parseIndex(slicingStep.to) : arraySize;
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

    private CompiledExpression applyWildcardStepFilter(ParserRuleContext astNode, Value parentValue,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (parentValue instanceof ArrayValue arrayValue) {
            return FilterApplicationStrategy.applyFilterToAllArrayElements(astNode, arrayValue, functionIdentifier,
                    arguments, context);
        }
        if (parentValue instanceof ObjectValue objectValue) {
            return FilterApplicationStrategy.applyFilterToAllObjectFields(astNode, objectValue, functionIdentifier,
                    arguments, context);
        }
        return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_WILDCARD_REQUIRES_ARRAY_OR_OBJECT);
    }

    private CompiledExpression applyRecursiveKeyStepFilter(ParserRuleContext astNode, Value parentValue, String key,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (arguments.nature() != Nature.VALUE) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_DYNAMIC_ARGS_NOT_SUPPORTED_RECURSIVE_KEY);
        }

        if (parentValue instanceof ObjectValue objectValue) {
            return applyRecursiveKeyToObject(astNode, objectValue, key, functionIdentifier, arguments, context);
        }

        if (parentValue instanceof ArrayValue arrayValue) {
            return applyRecursiveKeyToArray(astNode, arrayValue, key, functionIdentifier, arguments, context);
        }

        return parentValue;
    }

    private Value applyRecursiveKeyToObject(ParserRuleContext astNode, ObjectValue objectValue, String key,
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

    private Value applyFilterToMatchingKey(ParserRuleContext astNode, Value fieldValue, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val result = applyFilterFunctionToValue(astNode, fieldValue, functionIdentifier, arguments, context);
        if (!(result instanceof Value filteredValue)) {
            return Error.at(astNode, fieldValue.metadata(), RUNTIME_ERROR_UNEXPECTED_NON_VALUE_CONSTANT_ARGS);
        }
        return filteredValue;
    }

    private Value applyFilterToNonMatchingKey(ParserRuleContext astNode, Value fieldValue, String key,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        val recursedResult = applyRecursiveKeyStepFilter(astNode, fieldValue, key, functionIdentifier, arguments,
                context);
        if (!(recursedResult instanceof Value recursedValue)) {
            return Error.at(astNode, fieldValue.metadata(), RUNTIME_ERROR_UNEXPECTED_NON_VALUE_RECURSIVE_KEY);
        }
        return recursedValue;
    }

    private Value applyRecursiveKeyToArray(ParserRuleContext astNode, ArrayValue arrayValue, String key,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        return FilterCollectionRebuilder.traverseArray(arrayValue, element -> {
            val recursedResult = applyRecursiveKeyStepFilter(astNode, element, key, functionIdentifier, arguments,
                    context);
            if (!(recursedResult instanceof Value recursedValue)) {
                return Error.at(astNode, arrayValue.metadata(), RUNTIME_ERROR_UNEXPECTED_NON_VALUE_RECURSIVE_KEY);
            }
            return recursedValue;
        });
    }

    private CompiledExpression applyRecursiveWildcardStepFilter(ParserRuleContext astNode, Value parentValue,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        return applyWildcardStepFilter(astNode, parentValue, functionIdentifier, arguments, context);
    }

    private CompiledExpression applyRecursiveIndexStepFilter(ParserRuleContext astNode, Value parentValue, int index,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (arguments.nature() != Nature.VALUE) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_DYNAMIC_ARGS_NOT_SUPPORTED_RECURSIVE_INDEX);
        }

        if (parentValue instanceof ArrayValue arrayValue) {
            return applyRecursiveIndexToArray(astNode, arrayValue, index, functionIdentifier, arguments, context);
        }

        if (parentValue instanceof ObjectValue objectValue) {
            return applyRecursiveIndexToObject(astNode, objectValue, index, functionIdentifier, arguments, context);
        }

        return parentValue;
    }

    private Value applyRecursiveIndexToArray(ParserRuleContext astNode, ArrayValue arrayValue, int index,
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

    private Value applyFilterToMatchingIndex(ParserRuleContext astNode, Value value, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val result = applyFilterFunctionToValue(astNode, value, functionIdentifier, arguments, context);
        if (!(result instanceof Value filteredValue)) {
            return Error.at(astNode, value.metadata(), RUNTIME_ERROR_UNEXPECTED_NON_VALUE_CONSTANT_ARGS);
        }
        return filteredValue;
    }

    private Value applyRecursiveIndexToObject(ParserRuleContext astNode, ObjectValue objectValue, int index,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        return FilterCollectionRebuilder.traverseObject(objectValue, fieldValue -> {
            val recursedResult = applyRecursiveIndexStepFilter(astNode, fieldValue, index, functionIdentifier,
                    arguments, context);
            if (!(recursedResult instanceof Value recursedValue)) {
                return Error.at(astNode, objectValue.metadata(), RUNTIME_ERROR_UNEXPECTED_NON_VALUE_RECURSIVE_INDEX);
            }
            return recursedValue;
        });
    }

    private CompiledExpression applyAttributeUnionStepFilter(ParserRuleContext astNode, Value parentValue,
            List<String> attributes, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        if (!(parentValue instanceof ObjectValue objectValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_ATTRIBUTE_UNION_REQUIRES_OBJECT);
        }

        return FilterApplicationStrategy.applyFilterToObjectFields(astNode, objectValue, attributes::contains,
                functionIdentifier, arguments, context);
    }

    private CompiledExpression applyIndexUnionStepFilter(ParserRuleContext astNode, Value parentValue,
            List<BigDecimal> indices, String functionIdentifier, CompiledArguments arguments,
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

    private CompiledExpression applyConditionStepFilter(ParserRuleContext astNode, Value parentValue,
            ConditionStepContext conditionStep, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        if (!(parentValue instanceof ArrayValue) && !(parentValue instanceof ObjectValue)) {
            return parentValue;
        }

        val conditionExpr = ExpressionCompiler.compileExpression(conditionStep.expression(), context);

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

        if (!booleanResult.value()) {
            return parentValue;
        }

        return applyWildcardStepFilter(astNode, parentValue, functionIdentifier, arguments, context);
    }

    private CompiledExpression applyExpressionStepFilter(ParserRuleContext astNode, Value parentValue,
            ExpressionStepContext expressionStep, String functionIdentifier, CompiledArguments arguments,
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

    private CompiledExpression applyExpressionFilterToArray(ParserRuleContext astNode, ArrayValue arrayValue,
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

    private CompiledExpression applyExpressionFilterToObject(ParserRuleContext astNode, ObjectValue objectValue,
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

    // ═══════════════════════════════════════════════════════════════════════════
    // Nested path filter implementations
    // ═══════════════════════════════════════════════════════════════════════════

    private Value applyFilterToNestedPath(ParserRuleContext astNode, Value parentValue, String fieldName,
            List<StepContext> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
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

    private Value applyFilterToNestedArrayElement(ParserRuleContext astNode, Value parentValue, int index,
            List<StepContext> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
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

    private Value applyFilterToNestedArraySlice(Value parentValue, ArraySlicingStepContext slicingStep,
            List<StepContext> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Error.at(slicingStep, parentValue.metadata(), RUNTIME_ERROR_SLICING_REQUIRES_ARRAY);
        }

        val arraySize = arrayValue.size();
        val step      = slicingStep.stepValue != null ? parseIndex(slicingStep.stepValue) : 1;

        if (step == 0) {
            return Error.at(slicingStep, parentValue.metadata(), RUNTIME_ERROR_SLICING_STEP_ZERO);
        }

        val from = FilterCollectionRebuilder
                .normalizeIndex(slicingStep.index != null ? parseIndex(slicingStep.index) : 0, arraySize);
        val to   = FilterCollectionRebuilder
                .normalizeIndex(slicingStep.to != null ? parseIndex(slicingStep.to) : arraySize, arraySize);

        return FilterCollectionRebuilder.traverseArraySelective(arrayValue, i -> isInNormalizedSlice(i, from, to, step),
                i -> applyFilterFunctionToPathRecursive(slicingStep, arrayValue.get(i), steps, stepIndex,
                        functionIdentifier, arguments, context));
    }

    private Value applyFilterToNestedWildcard(ParserRuleContext astNode, Value parentValue, List<StepContext> steps,
            int stepIndex, String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
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

    private Value applyFilterToNestedRecursiveKey(ParserRuleContext astNode, Value parentValue, String key,
            List<StepContext> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
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

    private Value applyFilterToNestedRecursiveWildcard(ParserRuleContext astNode, Value parentValue,
            List<StepContext> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
        return applyFilterToNestedWildcard(astNode, parentValue, steps, stepIndex, functionIdentifier, arguments,
                context);
    }

    private Value applyFilterToNestedRecursiveIndex(ParserRuleContext astNode, Value parentValue, int index,
            List<StepContext> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
            CompilationContext context) {
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

    private Value applyRecursiveIndexToArrayNested(ParserRuleContext astNode, ArrayValue arrayValue, int rawIndex,
            int normalizedIndex, List<StepContext> steps, int stepIndex, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        val builder = ArrayValue.builder().withMetadata(arrayValue.metadata());
        for (int i = 0; i < arrayValue.size(); i++) {
            val recursedValue = applyFilterToNestedRecursiveIndex(astNode, arrayValue.get(i), rawIndex, steps,
                    stepIndex, functionIdentifier, arguments, context);
            if (recursedValue instanceof ErrorValue) {
                return recursedValue;
            }

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

    private Value applyFilterToNestedAttributeUnion(ParserRuleContext astNode, Value parentValue,
            List<String> attributes, List<StepContext> steps, int stepIndex, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ObjectValue objectValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_ATTRIBUTE_UNION_REQUIRES_OBJECT);
        }

        return FilterCollectionRebuilder.traverseObjectSelective(objectValue, attributes::contains,
                fieldKey -> applyFilterFunctionToPathRecursive(astNode, objectValue.get(fieldKey), steps, stepIndex,
                        functionIdentifier, arguments, context));
    }

    private Value applyFilterToNestedIndexUnion(ParserRuleContext astNode, Value parentValue, List<BigDecimal> indices,
            List<StepContext> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
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

    private Value applyFilterToNestedCondition(ParserRuleContext astNode, Value parentValue,
            ConditionStepContext conditionStep, List<StepContext> steps, int stepIndex, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue) && !(parentValue instanceof ObjectValue)) {
            return parentValue;
        }

        val conditionExpr = ExpressionCompiler.compileExpression(conditionStep.expression(), context);

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

        if (!booleanResult.value()) {
            return parentValue.withMetadata(metadata);
        }

        return applyFilterToNestedWildcard(astNode, parentValue, steps, stepIndex, functionIdentifier, arguments,
                context);
    }

    private Value applyFilterToNestedExpression(ParserRuleContext astNode, Value parentValue,
            ExpressionStepContext expressionStep, List<StepContext> steps, int stepIndex, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
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

    private Value applyNestedExpressionToArray(ParserRuleContext astNode, ArrayValue arrayValue, Value keyOrIndexValue,
            List<StepContext> steps, int stepIndex, String functionIdentifier, CompiledArguments arguments,
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

    private Value applyNestedExpressionToObject(ParserRuleContext astNode, ObjectValue objectValue,
            Value keyOrIndexValue, List<StepContext> steps, int stepIndex, String functionIdentifier,
            CompiledArguments arguments, CompilationContext context) {
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

    private Value applyEachFilterStatement(ParserRuleContext astNode, Value parentValue, BasicRelativeContext target,
            String functionIdentifier, CompiledArguments arguments, CompilationContext context) {
        if (!(parentValue instanceof ArrayValue arrayValue)) {
            return Error.at(astNode, parentValue.metadata(), RUNTIME_ERROR_EACH_REQUIRES_ARRAY);
        }

        val builder = ArrayValue.builder().withMetadata(arrayValue.metadata());
        for (val element : arrayValue) {
            Value result;

            if (target != null && target.step() != null && !target.step().isEmpty()) {
                result = applyFilterFunctionToPath(astNode, element, target.step(), functionIdentifier, arguments,
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

    // ═══════════════════════════════════════════════════════════════════════════
    // Utility methods
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean isNotArrayOrObject(Value value) {
        return !(value instanceof ArrayValue) && !(value instanceof ObjectValue);
    }

    private Value compileExpressionStepValue(ExpressionStepContext expressionStep, CompilationContext context) {
        val keyOrIndexExpr = ExpressionCompiler.compileExpression(expressionStep.expression(), context);
        if (!(keyOrIndexExpr instanceof Value keyOrIndexValue)) {
            return Error.at(expressionStep, ValueMetadata.EMPTY, RUNTIME_ERROR_DYNAMIC_EXPRESSION_NOT_SUPPORTED);
        }
        return keyOrIndexValue;
    }

    private List<Value> valueArrayFrom(Object[] argValues) {
        val result = new ArrayList<Value>(argValues.length);
        for (var argValue : argValues) {
            result.add((Value) argValue);
        }
        return result;
    }

    private List<Value> resolveArgumentsToValues(ParserRuleContext astNode, CompiledExpression[] arguments,
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

    private String getStepKeyName(KeyStepContext keyStep) {
        return getIdentifierName(keyStep.saplId());
    }

    private String getRecursiveKeyName(RecursiveKeyStepContext recursiveKey) {
        return switch (recursiveKey) {
        case RecursiveIdKeyStepContext c     -> getIdentifierName(c.saplId());
        case RecursiveStringKeyStepContext c -> unquoteString(c.STRING().getText());
        default                              -> "";
        };
    }

    private int getRecursiveIndexValue(RecursiveIndexStepContext recursiveIndex) {
        return parseIndex(recursiveIndex.signedNumber());
    }

    private List<BigDecimal> getIndexUnionValues(IndexUnionStepContext indexUnion) {
        val indices = new ArrayList<BigDecimal>(indexUnion.indices.size());
        for (val indexCtx : indexUnion.indices) {
            indices.add(new BigDecimal(indexCtx.getText()));
        }
        return indices;
    }

    private List<String> getAttributeUnionKeys(AttributeUnionStepContext attributeUnion) {
        val keys = new ArrayList<String>(attributeUnion.attributes.size());
        for (val attrCtx : attributeUnion.attributes) {
            keys.add(unquoteString(attrCtx.getText()));
        }
        return keys;
    }

    private int parseIndex(SignedNumberContext signedNumber) {
        return Integer.parseInt(signedNumber.getText());
    }

}
