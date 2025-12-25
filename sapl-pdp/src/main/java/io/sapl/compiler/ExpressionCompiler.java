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

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureExpression;
import io.sapl.api.model.StreamExpression;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueMetadata;

import static io.sapl.compiler.operators.StepOperators.attributeUnion;
import static io.sapl.compiler.operators.StepOperators.indexOrKeyStep;
import static io.sapl.compiler.operators.StepOperators.indexStep;
import static io.sapl.compiler.operators.StepOperators.indexUnion;
import static io.sapl.compiler.operators.StepOperators.keyStep;
import static io.sapl.compiler.operators.StepOperators.recursiveIndexStep;
import static io.sapl.compiler.operators.StepOperators.recursiveKeyStep;
import static io.sapl.compiler.operators.StepOperators.recursiveWildcardStep;
import static io.sapl.compiler.operators.StepOperators.sliceArray;
import static io.sapl.compiler.operators.StepOperators.wildcardStep;

import static io.sapl.compiler.operators.BooleanOperators.not;
import static io.sapl.compiler.operators.ComparisonOperators.compileRegularExpressionOperator;
import static io.sapl.compiler.operators.ComparisonOperators.isContainedIn;
import static io.sapl.compiler.operators.ComparisonOperators.matchesRegularExpression;
import static io.sapl.compiler.operators.ComparisonOperators.notEquals;
import static io.sapl.compiler.StringsUtil.unquoteString;
import io.sapl.compiler.operators.ComparisonOperators;
import static io.sapl.compiler.operators.NumberOperators.add;
import static io.sapl.compiler.operators.NumberOperators.divide;
import static io.sapl.compiler.operators.NumberOperators.greaterThan;
import static io.sapl.compiler.operators.NumberOperators.greaterThanOrEqual;
import static io.sapl.compiler.operators.NumberOperators.lessThan;
import static io.sapl.compiler.operators.NumberOperators.lessThanOrEqual;
import static io.sapl.compiler.operators.NumberOperators.modulo;
import static io.sapl.compiler.operators.NumberOperators.multiply;
import static io.sapl.compiler.operators.NumberOperators.subtract;
import static io.sapl.compiler.operators.NumberOperators.unaryMinus;
import static io.sapl.compiler.operators.NumberOperators.unaryPlus;
import io.sapl.grammar.antlr.SAPLParser.ActionIdContext;
import io.sapl.grammar.antlr.SAPLParser.AdditionContext;
import io.sapl.grammar.antlr.SAPLParser.AttributeFinderDotStepContext;
import io.sapl.grammar.antlr.SAPLParser.HeadAttributeFinderDotStepContext;
import io.sapl.grammar.antlr.SAPLParser.ArrayContext;
import io.sapl.grammar.antlr.SAPLParser.ArrayValueContext;
import io.sapl.grammar.antlr.SAPLParser.BasicContext;
import io.sapl.grammar.antlr.SAPLParser.BasicExprContext;
import io.sapl.grammar.antlr.SAPLParser.BasicExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.BasicIdentifierContext;
import io.sapl.grammar.antlr.SAPLParser.BooleanLiteralContext;
import io.sapl.grammar.antlr.SAPLParser.BooleanValueContext;
import io.sapl.grammar.antlr.SAPLParser.ComparisonContext;
import io.sapl.grammar.antlr.SAPLParser.EagerAndContext;
import io.sapl.grammar.antlr.SAPLParser.EagerOrContext;
import io.sapl.grammar.antlr.SAPLParser.EnvironmentIdContext;
import io.sapl.grammar.antlr.SAPLParser.EnvAttributeBasicContext;
import io.sapl.grammar.antlr.SAPLParser.EnvHeadAttributeBasicContext;
import io.sapl.grammar.antlr.SAPLParser.EqualityContext;
import io.sapl.grammar.antlr.SAPLParser.ExclusiveOrContext;
import io.sapl.grammar.antlr.SAPLParser.ExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.FalseLiteralContext;
import io.sapl.grammar.antlr.SAPLParser.FunctionBasicContext;
import io.sapl.grammar.antlr.SAPLParser.FunctionIdentifierContext;
import io.sapl.grammar.antlr.SAPLParser.GroupBasicContext;
import io.sapl.grammar.antlr.SAPLParser.IdPairKeyContext;
import io.sapl.grammar.antlr.SAPLParser.IndexSubscriptContext;
import io.sapl.grammar.antlr.SAPLParser.KeyDotStepContext;
import io.sapl.grammar.antlr.SAPLParser.EscapedKeyDotStepContext;
import io.sapl.grammar.antlr.SAPLParser.EscapedKeySubscriptContext;
import io.sapl.grammar.antlr.SAPLParser.BracketStepContext;
import io.sapl.grammar.antlr.SAPLParser.WildcardDotStepContext;
import io.sapl.grammar.antlr.SAPLParser.WildcardSubscriptContext;
import io.sapl.grammar.antlr.SAPLParser.ExpressionSubscriptContext;
import io.sapl.grammar.antlr.SAPLParser.SlicingSubscriptContext;
import io.sapl.grammar.antlr.SAPLParser.ConditionSubscriptContext;
import io.sapl.grammar.antlr.SAPLParser.IndexUnionSubscriptContext;
import io.sapl.grammar.antlr.SAPLParser.AttributeUnionSubscriptContext;
import io.sapl.grammar.antlr.SAPLParser.RecursiveKeyDotDotStepContext;
import io.sapl.grammar.antlr.SAPLParser.RecursiveWildcardDotDotStepContext;
import io.sapl.grammar.antlr.SAPLParser.RecursiveIndexDotDotStepContext;
import io.sapl.grammar.antlr.SAPLParser.RecursiveIdKeyStepContext;
import io.sapl.grammar.antlr.SAPLParser.RecursiveStringKeyStepContext;
import io.sapl.grammar.antlr.SAPLParser.StepContext;
import io.sapl.grammar.antlr.SAPLParser.IdentifierBasicContext;
import io.sapl.grammar.antlr.SAPLParser.LazyAndContext;
import io.sapl.grammar.antlr.SAPLParser.LazyOrContext;
import io.sapl.grammar.antlr.SAPLParser.MultiplicationContext;
import io.sapl.grammar.antlr.SAPLParser.NotExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.NullValueContext;
import io.sapl.grammar.antlr.SAPLParser.NumberLiteralContext;
import io.sapl.grammar.antlr.SAPLParser.NumberValueContext;
import io.sapl.grammar.antlr.SAPLParser.ObjectContext;
import io.sapl.grammar.antlr.SAPLParser.ObjectValueContext;
import io.sapl.grammar.antlr.SAPLParser.PairContext;
import io.sapl.grammar.antlr.SAPLParser.PlainIdContext;
import io.sapl.grammar.antlr.SAPLParser.RelativeBasicContext;
import io.sapl.grammar.antlr.SAPLParser.RelativeLocationBasicContext;
import io.sapl.grammar.antlr.SAPLParser.ReservedIdContext;
import io.sapl.grammar.antlr.SAPLParser.ReservedIdentifierContext;
import io.sapl.grammar.antlr.SAPLParser.ResourceIdContext;
import io.sapl.grammar.antlr.SAPLParser.SaplIdContext;
import io.sapl.grammar.antlr.SAPLParser.StringLiteralContext;
import io.sapl.grammar.antlr.SAPLParser.StringPairKeyContext;
import io.sapl.grammar.antlr.SAPLParser.StringValueContext;
import io.sapl.grammar.antlr.SAPLParser.SubjectIdContext;
import io.sapl.grammar.antlr.SAPLParser.TrueLiteralContext;
import io.sapl.grammar.antlr.SAPLParser.UnaryExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.UnaryMinusExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.UnaryPlusExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.UndefinedValueContext;
import io.sapl.grammar.antlr.SAPLParser.ValueBasicContext;
import io.sapl.grammar.antlr.SAPLParser.ValueContext;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Compiles SAPL ANTLR parse tree expressions into optimized executable
 * representations. Performs compile-time constant folding and type-based
 * optimization to generate efficient evaluation code.
 */
@UtilityClass
public class ExpressionCompiler {

    // ==================== Arithmetic Operation Errors ====================
    private static final String ERROR_EXPECTED_VALUE_FOR_ARITHMETIC = "Expected a value for arithmetic operation.";
    private static final String ERROR_UNKNOWN_ARITHMETIC_OPERATOR   = "Unknown arithmetic operator: %s.";

    // ==================== Boolean Operation Errors ====================
    private static final String ERROR_BOOLEAN_OPERAND_REQUIRED   = "Boolean operation requires boolean operands, got: %s.";
    private static final String ERROR_CONDITION_REQUIRES_BOOLEAN = "Condition step requires boolean result, got: %s.";
    private static final String ERROR_EAGER_AND_REQUIRES_BOOLEAN = "Eager AND requires boolean operands, got: %s.";
    private static final String ERROR_EAGER_OR_REQUIRES_BOOLEAN  = "Eager OR requires boolean operands, got: %s.";
    private static final String ERROR_STREAM_IN_CONDITION        = "Stream expressions not supported in condition step.";
    private static final String ERROR_XOR_REQUIRES_BOOLEAN       = "XOR requires boolean operands, got: %s.";

    // ==================== Compilation Errors ====================
    private static final String ERROR_MISSING_FUNCTION_DEFINITION = "Missing function definition.";
    private static final String ERROR_MISSING_FUNCTION_IDENTIFIER = "Missing function identifier.";
    private static final String ERROR_MISSING_IDENTIFIER          = "Missing identifier.";
    private static final String ERROR_NO_FUNCTION_BROKER          = "No function broker available.";

    // ==================== Index and Subscript Errors ====================
    private static final String ERROR_INVALID_ARRAY_INDEX       = "Invalid array index: %s.";
    private static final String ERROR_INVALID_RECURSIVE_INDEX   = "Invalid recursive index: %s.";
    private static final String ERROR_STEP_NOT_IMPLEMENTED      = "Step type not yet implemented.";
    private static final String ERROR_SUBSCRIPT_NOT_IMPLEMENTED = "Subscript type not yet implemented.";

    // ==================== Operator Errors ====================
    private static final String ERROR_UNKNOWN_COMPARISON_OPERATOR = "Unknown comparison operator: %s.";
    private static final String ERROR_UNKNOWN_EQUALITY_OPERATOR   = "Unknown equality operator: %s.";

    // ==================== Type Errors ====================
    private static final String ERROR_UNEXPECTED_BASIC_EXPRESSION = "Unexpected basic expression type.";
    private static final String ERROR_UNEXPECTED_UNARY_EXPRESSION = "Unexpected unary expression type.";
    private static final String ERROR_UNEXPECTED_VALUE_TYPE       = "Unexpected value type.";

    // ==================== Value Errors ====================
    private static final String ERROR_EXPECTED_VALUE_FOR_FUNCTION = "Expected a value for function argument.";
    private static final String ERROR_INVALID_BOOLEAN_LITERAL     = "Invalid boolean literal.";
    private static final String ERROR_INVALID_NUMBER_FORMAT       = "Invalid number format: %s.";
    private static final String ERROR_MISSING_NUMBER_TOKEN        = "Missing number token.";
    private static final String ERROR_MISSING_STRING_TOKEN        = "Missing string token.";

    /**
     * Compiles a SAPL expression from the ANTLR parse tree into an optimized
     * executable form.
     *
     * @param expression the parse tree expression context to compile
     * @param context the compilation context containing variables and function
     * broker
     * @return the compiled expression ready for evaluation, or null if input is
     * null
     */
    public CompiledExpression compileExpression(ExpressionContext expression, CompilationContext context) {
        if (expression == null) {
            return null;
        }
        val lazyOr = expression.lazyOr();
        return lazyOr != null ? compileLazyOr(lazyOr, context) : null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Operator Chain - Precedence from lowest to highest
    // ═══════════════════════════════════════════════════════════════════════════

    private CompiledExpression compileLazyOr(LazyOrContext lazyOr, CompilationContext context) {
        val operands = lazyOr.lazyAnd();
        if (operands.isEmpty()) {
            return null;
        }
        if (operands.size() == 1) {
            return compileLazyAnd(operands.getFirst(), context);
        }

        // Lazy OR: short-circuit evaluation - if left is true, skip right
        val compiled = operands.stream().map(op -> compileLazyAnd(op, context)).toArray(CompiledExpression[]::new);
        return compileLazyBooleanChain(compiled, true, lazyOr);
    }

    private CompiledExpression compileLazyAnd(LazyAndContext lazyAnd, CompilationContext context) {
        val operands = lazyAnd.eagerOr();
        if (operands.isEmpty()) {
            return null;
        }
        if (operands.size() == 1) {
            return compileEagerOr(operands.getFirst(), context);
        }

        // Lazy AND: short-circuit evaluation - if left is false, skip right
        val compiled = operands.stream().map(op -> compileEagerOr(op, context)).toArray(CompiledExpression[]::new);
        return compileLazyBooleanChain(compiled, false, lazyAnd);
    }

    private CompiledExpression compileEagerOr(EagerOrContext eagerOr, CompilationContext context) {
        val operands = eagerOr.exclusiveOr();
        if (operands.isEmpty()) {
            return null;
        }
        if (operands.size() == 1) {
            return compileExclusiveOr(operands.getFirst(), context);
        }

        // Eager OR: evaluate all operands
        val compiled = operands.stream().map(op -> compileExclusiveOr(op, context)).toArray(CompiledExpression[]::new);
        return compileEagerBooleanChain(compiled, eagerOr, ExpressionCompiler::applyEagerOrOperator);
    }

    private CompiledExpression compileExclusiveOr(ExclusiveOrContext exclusiveOr, CompilationContext context) {
        val operands = exclusiveOr.eagerAnd();
        if (operands.isEmpty()) {
            return null;
        }
        if (operands.size() == 1) {
            return compileEagerAnd(operands.getFirst(), context);
        }

        // XOR: evaluate all operands
        val compiled = operands.stream().map(op -> compileEagerAnd(op, context)).toArray(CompiledExpression[]::new);
        return compileEagerBooleanChain(compiled, exclusiveOr, ExpressionCompiler::applyXorOperator);
    }

    private CompiledExpression compileEagerAnd(EagerAndContext eagerAnd, CompilationContext context) {
        val operands = eagerAnd.equality();
        if (operands.isEmpty()) {
            return null;
        }
        if (operands.size() == 1) {
            return compileEquality(operands.getFirst(), context);
        }

        // Eager AND: evaluate all operands
        val compiled = operands.stream().map(op -> compileEquality(op, context)).toArray(CompiledExpression[]::new);
        return compileEagerBooleanChain(compiled, eagerAnd, ExpressionCompiler::applyEagerAndOperator);
    }

    private CompiledExpression compileEquality(EqualityContext equality, CompilationContext context) {
        val operands = equality.comparison();
        if (operands.isEmpty()) {
            return null;
        }
        if (operands.size() == 1) {
            return compileComparison(operands.getFirst(), context);
        }

        // Binary equality/inequality: left op right
        val left     = compileComparison(operands.getFirst(), context);
        val right    = compileComparison(operands.get(1), context);
        val operator = extractFirstOperatorSymbol(equality);

        // Optimization: pre-compile regex pattern when right side is a constant
        if ("=~".equals(operator) && right instanceof Value patternValue) {
            val compiledRegex = compileRegularExpressionOperator(equality, patternValue);
            return compileUnaryOp(left, equality, (ctx, value) -> compiledRegex.apply(value));
        }

        return compileBinaryOp(left, right, operator, equality, ExpressionCompiler::applyEqualityOperator);
    }

    /**
     * Compiles a unary operation where the operator is pre-computed (e.g.,
     * pre-compiled regex).
     */
    private CompiledExpression compileUnaryOp(CompiledExpression operand, ParserRuleContext context,
            java.util.function.BiFunction<ParserRuleContext, Value, Value> operation) {
        return switch (operand) {
        case ErrorValue error                                      -> error;
        case Value value                                           -> operation.apply(context, value);
        case PureExpression(var evaluator, var subscriptionScoped) ->
            new PureExpression(ctx -> operation.apply(context, evaluator.apply(ctx)), subscriptionScoped);
        case StreamExpression(var stream)                          ->
            new StreamExpression(stream.map(value -> operation.apply(context, value)));
        };
    }

    private CompiledExpression compileComparison(ComparisonContext comparison, CompilationContext context) {
        val operands = comparison.addition();
        if (operands.isEmpty()) {
            return null;
        }
        if (operands.size() == 1) {
            return compileAddition(operands.getFirst(), context);
        }

        // Binary comparison: left op right
        val left     = compileAddition(operands.getFirst(), context);
        val right    = compileAddition(operands.get(1), context);
        val operator = extractFirstOperatorSymbol(comparison);

        return compileBinaryOp(left, right, operator, comparison, ExpressionCompiler::applyComparisonOperator);
    }

    private CompiledExpression compileAddition(AdditionContext addition, CompilationContext context) {
        val operands = addition.multiplication();
        if (operands.isEmpty()) {
            return null;
        }
        if (operands.size() == 1) {
            return compileMultiplication(operands.getFirst(), context);
        }

        // Compile all operands
        val compiled = new CompiledExpression[operands.size()];
        for (int i = 0; i < operands.size(); i++) {
            compiled[i] = compileMultiplication(operands.get(i), context);
        }

        // Extract operators (+, -) from child nodes
        val operators = extractOperatorSymbols(addition);

        return compileArithmeticChain(compiled, operators, addition);
    }

    private CompiledExpression compileMultiplication(MultiplicationContext multiplication, CompilationContext context) {
        val operands = multiplication.unaryExpression();
        if (operands.isEmpty()) {
            return null;
        }
        if (operands.size() == 1) {
            return compileUnaryExpression(operands.getFirst(), context);
        }

        // Compile all operands
        val compiled = new CompiledExpression[operands.size()];
        for (int i = 0; i < operands.size(); i++) {
            compiled[i] = compileUnaryExpression(operands.get(i), context);
        }

        // Extract operators (*, /, %) from child nodes
        val operators = extractOperatorSymbols(multiplication);

        return compileArithmeticChain(compiled, operators, multiplication);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Unary Expressions - Pattern matching on labeled alternatives
    // ═══════════════════════════════════════════════════════════════════════════

    private CompiledExpression compileUnaryExpression(UnaryExpressionContext unaryExpr, CompilationContext context) {
        return switch (unaryExpr) {
        case BasicExprContext c            -> compileBasicExpression(c.basicExpression(), context);
        case NotExpressionContext c        -> compileNot(c, context);
        case UnaryMinusExpressionContext c -> compileUnaryMinus(c, context);
        case UnaryPlusExpressionContext c  -> compileUnaryPlus(c, context);
        case null, default                 -> Error.at(unaryExpr, ERROR_UNEXPECTED_UNARY_EXPRESSION);
        };
    }

    private CompiledExpression compileNot(NotExpressionContext notExpr, CompilationContext context) {
        val operand = compileUnaryExpression(notExpr.unaryExpression(), context);
        return switch (operand) {
        case ErrorValue error                                      -> error;
        case Value value                                           -> not(notExpr, value);
        case PureExpression(var evaluator, var subscriptionScoped) ->
            new PureExpression(ctx -> not(notExpr, evaluator.apply(ctx)), subscriptionScoped);
        case StreamExpression(var stream)                          ->
            new StreamExpression(stream.map(value -> not(notExpr, value)));
        };
    }

    private CompiledExpression compileUnaryMinus(UnaryMinusExpressionContext unaryMinus, CompilationContext context) {
        val operand = compileUnaryExpression(unaryMinus.unaryExpression(), context);
        return switch (operand) {
        case ErrorValue error                                      -> error;
        case Value value                                           -> unaryMinus(unaryMinus, value);
        case PureExpression(var evaluator, var subscriptionScoped) ->
            new PureExpression(ctx -> unaryMinus(unaryMinus, evaluator.apply(ctx)), subscriptionScoped);
        case StreamExpression(var stream)                          ->
            new StreamExpression(stream.map(value -> unaryMinus(unaryMinus, value)));
        };
    }

    private CompiledExpression compileUnaryPlus(UnaryPlusExpressionContext unaryPlus, CompilationContext context) {
        val operand = compileUnaryExpression(unaryPlus.unaryExpression(), context);
        return switch (operand) {
        case ErrorValue error                                      -> error;
        case Value value                                           -> unaryPlus(unaryPlus, value);
        case PureExpression(var evaluator, var subscriptionScoped) ->
            new PureExpression(ctx -> unaryPlus(unaryPlus, evaluator.apply(ctx)), subscriptionScoped);
        case StreamExpression(var stream)                          ->
            new StreamExpression(stream.map(value -> unaryPlus(unaryPlus, value)));
        };
    }

    static CompiledExpression compileBasicExpression(BasicExpressionContext basicExpr, CompilationContext context) {
        if (basicExpr == null) {
            return null;
        }
        val basic = basicExpr.basic();
        if (basic == null) {
            return null;
        }
        var result = compileBasic(basic, context);

        // Handle filter operator (|-)
        val filterComponent = basicExpr.filterComponent();
        if (filterComponent != null) {
            return FilterCompiler.compileFilter(result, filterComponent, context);
        }

        // Handle subtemplate operator (::)
        val subtemplateExpression = basicExpr.basicExpression();
        if (subtemplateExpression != null) {
            return SubtemplateCompiler.compileSubtemplate(result, subtemplateExpression, context);
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Basic Expressions - Clean pattern matching dispatch
    // ═══════════════════════════════════════════════════════════════════════════

    private CompiledExpression compileBasic(BasicContext basic, CompilationContext context) {
        return switch (basic) {
        case GroupBasicContext c            ->
            compileWithSteps(compileExpression(c.basicGroup().expression(), context), c.basicGroup().step(), context);
        case ValueBasicContext c            ->
            compileWithSteps(compileValue(c.basicValue().value(), context), c.basicValue().step(), context);
        case FunctionBasicContext c         -> compileFunction(c, context);
        case IdentifierBasicContext c       -> compileIdentifier(c.basicIdentifier(), context);
        case RelativeBasicContext c         ->
            compileWithSteps(new PureExpression(ctx -> ctx.relativeValue(), false), c.basicRelative().step(), context);
        case RelativeLocationBasicContext c -> compileWithSteps(
                new PureExpression(ctx -> ctx.relativeLocation(), false), c.basicRelativeLocation().step(), context);
        case EnvAttributeBasicContext c     ->
            compileWithSteps(AttributeCompiler.compileEnvironmentAttribute(c.basicEnvironmentAttribute(), context),
                    c.basicEnvironmentAttribute().step(), context);
        case EnvHeadAttributeBasicContext c -> compileWithSteps(
                AttributeCompiler.compileHeadEnvironmentAttribute(c.basicEnvironmentHeadAttribute(), context),
                c.basicEnvironmentHeadAttribute().step(), context);
        case null, default                  -> Error.at(basic, ERROR_UNEXPECTED_BASIC_EXPRESSION);
        };
    }

    private CompiledExpression compileIdentifier(BasicIdentifierContext basicId, CompilationContext context) {
        val saplId = basicId.saplId();
        if (saplId == null) {
            return Error.at(basicId, ERROR_MISSING_IDENTIFIER);
        }

        val variableName  = getIdentifierName(saplId);
        val localVariable = context.getVariable(variableName);
        val baseExpr      = localVariable != null ? localVariable
                : new PureExpression(ctx -> ctx.get(variableName), true);
        return compileWithSteps(baseExpr, basicId.step(), context);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Function Calls
    // ═══════════════════════════════════════════════════════════════════════════

    private CompiledExpression compileFunction(FunctionBasicContext funcBasic, CompilationContext context) {
        val basicFunction = funcBasic.basicFunction();
        if (basicFunction == null) {
            return Error.at(funcBasic, ERROR_MISSING_FUNCTION_DEFINITION);
        }

        val functionId = basicFunction.functionIdentifier();
        if (functionId == null) {
            return Error.at(funcBasic, ERROR_MISSING_FUNCTION_IDENTIFIER);
        }

        val rawFunctionName      = extractFunctionName(functionId);
        val resolvedFunctionName = context.resolveFunctionName(rawFunctionName);

        val argumentsCtx = basicFunction.arguments();
        val argList      = argumentsCtx != null ? argumentsCtx.args : List.<ExpressionContext>of();
        val compiledArgs = compileArguments(argList, context);

        val functionCall = compileFunctionCall(resolvedFunctionName, compiledArgs, funcBasic, context);
        return compileWithSteps(functionCall, basicFunction.step(), context);
    }

    private String extractFunctionName(FunctionIdentifierContext functionId) {
        val fragments = functionId.idFragment;
        if (fragments == null || fragments.isEmpty()) {
            return "";
        }
        val builder = new StringBuilder();
        for (int i = 0; i < fragments.size(); i++) {
            if (i > 0) {
                builder.append('.');
            }
            builder.append(getIdentifierName(fragments.get(i)));
        }
        return builder.toString();
    }

    private CompiledExpression compileFunctionCall(String functionName, CompiledArguments compiledArgs,
            FunctionBasicContext context, CompilationContext compContext) {
        val broker = compContext.getFunctionBroker();
        if (broker == null) {
            return Error.at(context, ERROR_NO_FUNCTION_BROKER);
        }

        return switch (compiledArgs.nature()) {
        case VALUE  -> invokeFunctionWithValues(functionName, compiledArgs.arguments(), broker, context);
        case PURE   -> compilePureFunctionCall(functionName, compiledArgs, broker, context);
        case STREAM -> compileStreamFunctionCall(functionName, compiledArgs, broker, context);
        };
    }

    private Value invokeFunctionWithValues(String functionName, CompiledExpression[] arguments,
            io.sapl.api.functions.FunctionBroker broker, FunctionBasicContext context) {
        val valueList = new ArrayList<Value>(arguments.length);
        for (val arg : arguments) {
            if (arg instanceof ErrorValue error) {
                return error;
            }
            if (!(arg instanceof Value value)) {
                return Error.at(context, ERROR_EXPECTED_VALUE_FOR_FUNCTION);
            }
            valueList.add(value);
        }
        return broker.evaluateFunction(new FunctionInvocation(functionName, valueList));
    }

    private CompiledExpression compilePureFunctionCall(String functionName, CompiledArguments compiledArgs,
            io.sapl.api.functions.FunctionBroker broker, FunctionBasicContext context) {
        return new PureExpression(ctx -> {
            val valueList = new ArrayList<Value>(compiledArgs.arguments().length);
            for (val arg : compiledArgs.arguments()) {
                val evaluated = arg instanceof PureExpression pure ? pure.evaluate(ctx) : arg;
                if (evaluated instanceof ErrorValue error) {
                    return error;
                }
                if (!(evaluated instanceof Value value)) {
                    return Error.at(context, ERROR_EXPECTED_VALUE_FOR_FUNCTION);
                }
                valueList.add(value);
            }
            return broker.evaluateFunction(new FunctionInvocation(functionName, valueList));
        }, compiledArgs.isSubscriptionScoped());
    }

    private CompiledExpression compileStreamFunctionCall(String functionName, CompiledArguments compiledArgs,
            io.sapl.api.functions.FunctionBroker broker, FunctionBasicContext context) {
        val sources = Arrays.stream(compiledArgs.arguments()).map(ExpressionCompiler::compiledExpressionToFlux)
                .toList();
        return new StreamExpression(Flux.combineLatest(sources, values -> {
            val valueList = new ArrayList<Value>(values.length);
            for (val value : values) {
                if (value instanceof ErrorValue error) {
                    return error;
                }
                if (!(value instanceof Value v)) {
                    return Error.at(context, ERROR_EXPECTED_VALUE_FOR_FUNCTION);
                }
                valueList.add(v);
            }
            return broker.evaluateFunction(new FunctionInvocation(functionName, valueList));
        }));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Steps - Field access, array indexing, wildcards
    // ═══════════════════════════════════════════════════════════════════════════

    private CompiledExpression compileWithSteps(CompiledExpression base, List<StepContext> steps,
            CompilationContext context) {
        if (steps == null || steps.isEmpty()) {
            return base;
        }

        var current = base;
        for (val step : steps) {
            current = compileStep(current, step, context);
            if (current instanceof ErrorValue) {
                return current;
            }
        }
        return current;
    }

    private CompiledExpression compileStep(CompiledExpression base, StepContext step, CompilationContext context) {
        return switch (step) {
        case AttributeFinderDotStepContext c      ->
            AttributeCompiler.compileAttributeFinderStep(base, c.attributeFinderStep(), context);
        case HeadAttributeFinderDotStepContext c  ->
            AttributeCompiler.compileHeadAttributeFinderStep(base, c.headAttributeFinderStep(), context);
        case KeyDotStepContext c                  ->
            compileKeyStep(base, getIdentifierName(c.keyStep().saplId()), step);
        case EscapedKeyDotStepContext c           ->
            compileKeyStep(base, unquoteString(c.escapedKeyStep().STRING().getText()), step);
        case WildcardDotStepContext c             -> compileWildcardStep(base, c);
        case BracketStepContext c                 -> compileBracketStep(base, c, context);
        case RecursiveKeyDotDotStepContext c      -> compileRecursiveKeyStep(base, c);
        case RecursiveWildcardDotDotStepContext c -> compileRecursiveWildcardStep(base, c);
        case RecursiveIndexDotDotStepContext c    -> compileRecursiveIndexStep(base, c);
        case null, default                        -> Error.at(step, ERROR_STEP_NOT_IMPLEMENTED);
        };
    }

    private CompiledExpression compileKeyStep(CompiledExpression base, String key, StepContext step) {
        return switch (base) {
        case ErrorValue error                                      -> error;
        case Value value                                           -> keyStep(step, value, key);
        case PureExpression(var evaluator, var subscriptionScoped) ->
            new PureExpression(ctx -> keyStep(step, evaluator.apply(ctx), key), subscriptionScoped);
        case StreamExpression(var stream)                          ->
            new StreamExpression(stream.map(value -> keyStep(step, value, key)));
        };
    }

    private CompiledExpression compileWildcardStep(CompiledExpression base, WildcardDotStepContext step) {
        return switch (base) {
        case ErrorValue error                                      -> error;
        case Value value                                           -> wildcardStep(step, value);
        case PureExpression(var evaluator, var subscriptionScoped) ->
            new PureExpression(ctx -> wildcardStep(step, evaluator.apply(ctx)), subscriptionScoped);
        case StreamExpression(var stream)                          ->
            new StreamExpression(stream.map(value -> wildcardStep(step, value)));
        };
    }

    private CompiledExpression compileBracketStep(CompiledExpression base, BracketStepContext bracketStep,
            CompilationContext context) {
        val subscript = bracketStep.subscript();
        return switch (subscript) {
        case EscapedKeySubscriptContext c     ->
            compileKeyStep(base, unquoteString(c.escapedKeyStep().STRING().getText()), bracketStep);
        case WildcardSubscriptContext c       -> compileWildcardSubscript(base, c);
        case IndexSubscriptContext c          -> compileIndexStep(base, c, bracketStep);
        case ExpressionSubscriptContext c     -> compileExpressionSubscript(base, c, bracketStep, context);
        case SlicingSubscriptContext c        -> compileSlicingStep(base, c, bracketStep);
        case ConditionSubscriptContext c      -> compileConditionStep(base, c, bracketStep, context);
        case IndexUnionSubscriptContext c     -> compileIndexUnionStep(base, c, bracketStep);
        case AttributeUnionSubscriptContext c -> compileAttributeUnionStep(base, c, bracketStep);
        case null, default                    -> Error.at(bracketStep, ERROR_SUBSCRIPT_NOT_IMPLEMENTED);
        };
    }

    private CompiledExpression compileWildcardSubscript(CompiledExpression base, WildcardSubscriptContext step) {
        return switch (base) {
        case ErrorValue error                                      -> error;
        case Value value                                           -> wildcardStep(step, value);
        case PureExpression(var evaluator, var subscriptionScoped) ->
            new PureExpression(ctx -> wildcardStep(step, evaluator.apply(ctx)), subscriptionScoped);
        case StreamExpression(var stream)                          ->
            new StreamExpression(stream.map(value -> wildcardStep(step, value)));
        };
    }

    private CompiledExpression compileIndexStep(CompiledExpression base, IndexSubscriptContext indexCtx,
            BracketStepContext step) {
        val        indexText = indexCtx.indexStep().signedNumber().getText();
        BigDecimal index;
        try {
            index = new BigDecimal(indexText);
        } catch (NumberFormatException e) {
            return Error.at(step, ERROR_INVALID_ARRAY_INDEX, indexText);
        }

        return switch (base) {
        case ErrorValue error                                      -> error;
        case Value value                                           -> indexStep(step, value, index, value.metadata());
        case PureExpression(var evaluator, var subscriptionScoped) -> new PureExpression(ctx -> {
                                                                   val parent = evaluator.apply(ctx);
                                                                   return indexStep(step, parent, index,
                                                                           parent.metadata());
                                                               },
                subscriptionScoped);
        case StreamExpression(var stream)                          ->
            new StreamExpression(stream.map(element -> indexStep(step, element, index, element.metadata())));
        };
    }

    private CompiledExpression compileExpressionSubscript(CompiledExpression base, ExpressionSubscriptContext exprCtx,
            BracketStepContext step, CompilationContext context) {
        val subscriptExpr = compileExpression(exprCtx.expressionStep().expression(), context);

        // If both base and subscript are constant values, evaluate now
        if (base instanceof Value baseValue && subscriptExpr instanceof Value subscriptValue) {
            return indexOrKeyStep(step, baseValue, subscriptValue);
        }

        // Handle pure or stream expressions
        val isStream = base instanceof StreamExpression || subscriptExpr instanceof StreamExpression;
        if (isStream) {
            val baseStream      = compiledExpressionToFlux(base);
            val subscriptStream = compiledExpressionToFlux(subscriptExpr);
            return new StreamExpression(Flux.combineLatest(baseStream, subscriptStream,
                    (parent, subscript) -> indexOrKeyStep(step, parent, subscript)));
        }

        // Pure expression
        val isSubscriptionScoped = (base instanceof PureExpression pb && pb.isSubscriptionScoped())
                || (subscriptExpr instanceof PureExpression pi && pi.isSubscriptionScoped());
        return new PureExpression(ctx -> {
            val baseValue      = base instanceof PureExpression pb ? pb.evaluate(ctx) : (Value) base;
            val subscriptValue = subscriptExpr instanceof PureExpression pi ? pi.evaluate(ctx) : (Value) subscriptExpr;
            return indexOrKeyStep(step, baseValue, subscriptValue);
        }, isSubscriptionScoped);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Additional Step Types - Slicing, Unions, Recursive, Conditions
    // ═══════════════════════════════════════════════════════════════════════════

    private CompiledExpression compileSlicingStep(CompiledExpression base, SlicingSubscriptContext slicingCtx,
            BracketStepContext step) {
        val slicingStep = slicingCtx.arraySlicingStep();
        val fromCtx     = slicingStep.index;
        val toCtx       = slicingStep.to;
        val stepCtx     = slicingStep.stepValue;

        BigDecimal from     = parseBigDecimalOrNull(fromCtx);
        BigDecimal to       = parseBigDecimalOrNull(toCtx);
        BigDecimal stepSize = parseBigDecimalOrNull(stepCtx);

        return switch (base) {
        case ErrorValue error                                      -> error;
        case Value value                                           -> sliceArray(step, value, from, to, stepSize);
        case PureExpression(var evaluator, var subscriptionScoped) ->
            new PureExpression(ctx -> sliceArray(step, evaluator.apply(ctx), from, to, stepSize), subscriptionScoped);
        case StreamExpression(var stream)                          ->
            new StreamExpression(stream.map(value -> sliceArray(step, value, from, to, stepSize)));
        };
    }

    private BigDecimal parseBigDecimalOrNull(io.sapl.grammar.antlr.SAPLParser.SignedNumberContext signedNumberCtx) {
        if (signedNumberCtx == null) {
            return null;
        }
        try {
            return new BigDecimal(signedNumberCtx.getText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private CompiledExpression compileConditionStep(CompiledExpression base, ConditionSubscriptContext conditionCtx,
            BracketStepContext step, CompilationContext context) {
        val conditionExpr = compileExpression(conditionCtx.conditionStep().expression(), context);

        // Check if condition depends on subscription-scoped runtime data (variables,
        // attributes).
        // If not subscription-scoped, the condition only depends on @ and #, which are
        // provided by the iteration - allowing compile-time evaluation when value is
        // constant.
        val isConditionSubscriptionScoped = conditionExpr instanceof PureExpression pe && pe.isSubscriptionScoped();

        return switch (base) {
        case ErrorValue error                                      -> error;
        case UndefinedValue undefined                              -> undefined;
        case Value value                                           -> {
            // Constant value - check if we can constant-fold
            if (!isConditionSubscriptionScoped) {
                yield filterValueByConditionAtCompileTime(value, conditionExpr, step, context);
            }
            // Subscription-scoped condition - must defer to runtime
            yield new PureExpression(ctx -> filterValueByConditionAtRuntime(value, conditionExpr, step, ctx), true);
        }
        case PureExpression(var evaluator, var subscriptionScoped) -> {
            val isSubScoped = subscriptionScoped || isConditionSubscriptionScoped;
            yield new PureExpression(ctx -> {
                val evaluated = evaluator.apply(ctx);
                if (evaluated instanceof ErrorValue || evaluated instanceof UndefinedValue) {
                    return evaluated;
                }
                return filterValueByConditionAtRuntime(evaluated, conditionExpr, step, ctx);
            }, isSubScoped);
        }
        case StreamExpression(var stream)                          -> new StreamExpression(stream.flatMap(value -> {
                                                                   if (value instanceof ErrorValue
                                                                           || value instanceof UndefinedValue) {
                                                                       return Flux.just(value);
                                                                   }
                                                                   return filterValueByConditionStream(value,
                                                                           conditionExpr, step);
                                                               }));
        };
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Condition Step Value Filtering - Dispatches to Array, Object, or Scalar
    // ───────────────────────────────────────────────────────────────────────────

    private Value filterValueByConditionAtCompileTime(Value value, CompiledExpression conditionExpr,
            BracketStepContext step, CompilationContext context) {
        val compileTimeContext = createCompileTimeContext(context);
        return switch (value) {
        case ArrayValue array   -> filterArrayByCondition(array, conditionExpr, step, compileTimeContext);
        case ObjectValue object -> filterObjectByCondition(object, conditionExpr, step, compileTimeContext);
        default                 -> filterScalarByCondition(value, conditionExpr, step, compileTimeContext);
        };
    }

    private Value filterValueByConditionAtRuntime(Value value, CompiledExpression conditionExpr,
            BracketStepContext step, io.sapl.api.model.EvaluationContext evalContext) {
        return switch (value) {
        case ArrayValue array   -> filterArrayByCondition(array, conditionExpr, step, evalContext);
        case ObjectValue object -> filterObjectByCondition(object, conditionExpr, step, evalContext);
        default                 -> filterScalarByCondition(value, conditionExpr, step, evalContext);
        };
    }

    private Flux<Value> filterValueByConditionStream(Value value, CompiledExpression conditionExpr,
            BracketStepContext step) {
        return Flux.deferContextual(reactorCtx -> {
            val evalContext = reactorCtx.get(io.sapl.api.model.EvaluationContext.class);
            val result      = filterValueByConditionAtRuntime(value, conditionExpr, step, evalContext);
            return Flux.just(result);
        });
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Array Condition Filtering
    // ───────────────────────────────────────────────────────────────────────────

    private Value filterArrayByCondition(ArrayValue array, CompiledExpression conditionExpr, BracketStepContext step,
            io.sapl.api.model.EvaluationContext evalContext) {
        val builder  = ArrayValue.builder();
        var metadata = array.metadata();
        for (var index = 0; index < array.size(); index++) {
            val element         = array.get(index);
            val location        = Value.of(index);
            val conditionResult = evaluateCondition(conditionExpr, element, location, evalContext);
            metadata = metadata.merge(conditionResult.metadata());
            if (conditionResult instanceof ErrorValue error) {
                return error.withMetadata(metadata);
            }
            if (conditionResult instanceof BooleanValue bool && bool.value()) {
                builder.add(element);
            } else if (!(conditionResult instanceof BooleanValue)) {
                return Error.at(step, metadata, ERROR_CONDITION_REQUIRES_BOOLEAN,
                        conditionResult.getClass().getSimpleName());
            }
        }
        return builder.build().withMetadata(metadata);
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Object Condition Filtering - filters key-value pairs by condition
    // ───────────────────────────────────────────────────────────────────────────

    private Value filterObjectByCondition(ObjectValue object, CompiledExpression conditionExpr, BracketStepContext step,
            io.sapl.api.model.EvaluationContext evalContext) {
        val builder  = ObjectValue.builder();
        var metadata = object.metadata();
        var index    = 0;
        for (val entry : object.entrySet()) {
            val entryValue      = entry.getValue();
            val location        = Value.of(index);
            val conditionResult = evaluateCondition(conditionExpr, entryValue, location, evalContext);
            metadata = metadata.merge(conditionResult.metadata());
            if (conditionResult instanceof ErrorValue error) {
                return error.withMetadata(metadata);
            }
            if (conditionResult instanceof BooleanValue bool && bool.value()) {
                builder.put(entry.getKey(), entryValue);
            } else if (!(conditionResult instanceof BooleanValue)) {
                return Error.at(step, metadata, ERROR_CONDITION_REQUIRES_BOOLEAN,
                        conditionResult.getClass().getSimpleName());
            }
            index++;
        }
        return builder.build().withMetadata(metadata);
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Scalar Condition Filtering - returns value if condition true, else undefined
    // ───────────────────────────────────────────────────────────────────────────

    private Value filterScalarByCondition(Value scalar, CompiledExpression conditionExpr, BracketStepContext step,
            io.sapl.api.model.EvaluationContext evalContext) {
        val location        = Value.of(0);
        val conditionResult = evaluateCondition(conditionExpr, scalar, location, evalContext);
        val metadata        = scalar.metadata().merge(conditionResult.metadata());
        if (conditionResult instanceof ErrorValue error) {
            return error.withMetadata(metadata);
        }
        if (conditionResult instanceof BooleanValue bool) {
            return bool.value() ? scalar.withMetadata(metadata) : Value.UNDEFINED.withMetadata(metadata);
        }
        return Error.at(step, metadata, ERROR_CONDITION_REQUIRES_BOOLEAN, conditionResult.getClass().getSimpleName());
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Condition Evaluation Helpers
    // ───────────────────────────────────────────────────────────────────────────

    private static io.sapl.api.model.EvaluationContext createCompileTimeContext(CompilationContext context) {
        return new io.sapl.api.model.EvaluationContext(null, null, null, null, context.getFunctionBroker(), null);
    }

    private Value evaluateCondition(CompiledExpression conditionExpr, Value element, Value location,
            io.sapl.api.model.EvaluationContext evalContext) {
        return switch (conditionExpr) {
        case Value value              -> value;
        case PureExpression pure      -> pure.evaluate(evalContext.withRelativeValue(element, location));
        case StreamExpression ignored -> Value.error(ERROR_STREAM_IN_CONDITION);
        };
    }

    private CompiledExpression compileIndexUnionStep(CompiledExpression base, IndexUnionSubscriptContext unionCtx,
            BracketStepContext step) {
        val indices = unionCtx.indexUnionStep().indices.stream()
                .map(signedNumber -> new BigDecimal(signedNumber.getText())).toList();

        return switch (base) {
        case ErrorValue error                                      -> error;
        case Value value                                           -> indexUnion(step, value, indices);
        case PureExpression(var evaluator, var subscriptionScoped) ->
            new PureExpression(ctx -> indexUnion(step, evaluator.apply(ctx), indices), subscriptionScoped);
        case StreamExpression(var stream)                          ->
            new StreamExpression(stream.map(value -> indexUnion(step, value, indices)));
        };
    }

    private CompiledExpression compileAttributeUnionStep(CompiledExpression base,
            AttributeUnionSubscriptContext unionCtx, BracketStepContext step) {
        val keys = unionCtx.attributeUnionStep().attributes.stream().map(token -> unquoteString(token.getText()))
                .toList();

        return switch (base) {
        case ErrorValue error                                      -> error;
        case Value value                                           -> attributeUnion(step, value, keys);
        case PureExpression(var evaluator, var subscriptionScoped) ->
            new PureExpression(ctx -> attributeUnion(step, evaluator.apply(ctx), keys), subscriptionScoped);
        case StreamExpression(var stream)                          ->
            new StreamExpression(stream.map(value -> attributeUnion(step, value, keys)));
        };
    }

    private CompiledExpression compileRecursiveKeyStep(CompiledExpression base, RecursiveKeyDotDotStepContext step) {
        val keyStep = step.recursiveKeyStep();
        val key     = switch (keyStep) {
                    case RecursiveIdKeyStepContext c     -> getIdentifierName(c.saplId());
                    case RecursiveStringKeyStepContext c -> unquoteString(c.STRING().getText());
                    case null, default                   -> "";
                    };

        return switch (base) {
        case ErrorValue error                                      -> error;
        case Value value                                           ->
            recursiveKeyStep(step, value, key, value.metadata());
        case PureExpression(var evaluator, var subscriptionScoped) -> new PureExpression(ctx -> {
                                                                   val parent = evaluator.apply(ctx);
                                                                   return recursiveKeyStep(step, parent, key,
                                                                           parent.metadata());
                                                               },
                subscriptionScoped);
        case StreamExpression(var stream)                          ->
            new StreamExpression(stream.map(value -> recursiveKeyStep(step, value, key, value.metadata())));
        };
    }

    private CompiledExpression compileRecursiveWildcardStep(CompiledExpression base,
            RecursiveWildcardDotDotStepContext step) {
        return switch (base) {
        case ErrorValue error                                      -> error;
        case Value value                                           -> recursiveWildcardStep(step, value);
        case PureExpression(var evaluator, var subscriptionScoped) ->
            new PureExpression(ctx -> recursiveWildcardStep(step, evaluator.apply(ctx)), subscriptionScoped);
        case StreamExpression(var stream)                          ->
            new StreamExpression(stream.map(value -> recursiveWildcardStep(step, value)));
        };
    }

    private CompiledExpression compileRecursiveIndexStep(CompiledExpression base,
            RecursiveIndexDotDotStepContext step) {
        val        indexCtx  = step.recursiveIndexStep();
        val        indexText = indexCtx.signedNumber().getText();
        BigDecimal index;
        try {
            index = new BigDecimal(indexText);
        } catch (NumberFormatException e) {
            return Error.at(step, ERROR_INVALID_RECURSIVE_INDEX, indexText);
        }

        return switch (base) {
        case ErrorValue error                                      -> error;
        case Value value                                           -> recursiveIndexStep(step, value, index);
        case PureExpression(var evaluator, var subscriptionScoped) ->
            new PureExpression(ctx -> recursiveIndexStep(step, evaluator.apply(ctx), index), subscriptionScoped);
        case StreamExpression(var stream)                          ->
            new StreamExpression(stream.map(value -> recursiveIndexStep(step, value, index)));
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Values - Clean pattern matching on labeled alternatives
    // ═══════════════════════════════════════════════════════════════════════════

    CompiledExpression compileValue(ValueContext value, CompilationContext context) {
        return switch (value) {
        case ObjectValueContext c          -> compileObject(c.object(), context);
        case ArrayValueContext c           -> compileArray(c.array(), context);
        case NumberValueContext c          -> compileNumber(c.numberLiteral());
        case StringValueContext c          -> compileString(c.stringLiteral());
        case BooleanValueContext c         -> compileBoolean(c.booleanLiteral());
        case NullValueContext ignored      -> Value.NULL;
        case UndefinedValueContext ignored -> Value.UNDEFINED;
        case null, default                 -> Error.at(value, ERROR_UNEXPECTED_VALUE_TYPE);
        };
    }

    private Value compileBoolean(BooleanLiteralContext boolLiteral) {
        return switch (boolLiteral) {
        case TrueLiteralContext ignored  -> Value.TRUE;
        case FalseLiteralContext ignored -> Value.FALSE;
        case null, default               -> Value.error(ERROR_INVALID_BOOLEAN_LITERAL);
        };
    }

    private Value compileNumber(NumberLiteralContext numLiteral) {
        val token = numLiteral.NUMBER();
        if (token == null) {
            return Value.error(ERROR_MISSING_NUMBER_TOKEN, SourceLocationUtil.fromContext(numLiteral));
        }
        try {
            return Value.of(new BigDecimal(token.getText()));
        } catch (NumberFormatException e) {
            return Value.error(ERROR_INVALID_NUMBER_FORMAT.formatted(token.getText()),
                    SourceLocationUtil.fromContext(numLiteral));
        }
    }

    private Value compileString(StringLiteralContext strLiteral) {
        val token = strLiteral.STRING();
        if (token == null) {
            return Value.error(ERROR_MISSING_STRING_TOKEN, SourceLocationUtil.fromContext(strLiteral));
        }
        return Value.of(unquoteString(token.getText()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Identifier Resolution - Pattern matching on labeled alternatives
    // ═══════════════════════════════════════════════════════════════════════════

    static String getIdentifierName(SaplIdContext saplId) {
        return switch (saplId) {
        case PlainIdContext c            -> c.ID().getText();
        case ReservedIdentifierContext c -> getReservedIdName(c.reservedId());
        case null, default               -> saplId != null ? saplId.getText() : "";
        };
    }

    private String getReservedIdName(ReservedIdContext reservedId) {
        return switch (reservedId) {
        case SubjectIdContext ignored     -> "subject";
        case ActionIdContext ignored      -> "action";
        case ResourceIdContext ignored    -> "resource";
        case EnvironmentIdContext ignored -> "environment";
        case null, default                -> reservedId != null ? reservedId.getText() : "";
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Object Compilation
    // ═══════════════════════════════════════════════════════════════════════════

    private CompiledExpression compileObject(ObjectContext objectCtx, CompilationContext context) {
        val pairs = objectCtx.pair();
        if (pairs == null || pairs.isEmpty()) {
            return Value.EMPTY_OBJECT;
        }
        return compileAttributesToObject(compileObjectAttributes(pairs, context));
    }

    private CompiledObjectAttributes compileObjectAttributes(List<PairContext> pairs, CompilationContext context) {
        if (pairs == null || pairs.isEmpty()) {
            return CompiledObjectAttributes.EMPTY_ATTRIBUTES;
        }

        val attributes           = HashMap.<String, CompiledExpression>newHashMap(pairs.size());
        var isStream             = false;
        var isPure               = false;
        var isSubscriptionScoped = false;

        for (val pair : pairs) {
            val key      = extractPairKey(pair);
            val compiled = compileExpression(pair.pairValue, context);

            if (compiled instanceof PureExpression pure) {
                isPure                = true;
                isSubscriptionScoped |= pure.isSubscriptionScoped();
            } else if (compiled instanceof StreamExpression) {
                isStream = true;
            }
            attributes.put(key, compiled);
        }

        val nature = Nature.from(isStream, isPure);
        return new CompiledObjectAttributes(nature, isSubscriptionScoped, attributes);
    }

    private String extractPairKey(PairContext pair) {
        val pairKey = pair.pairKey();
        return switch (pairKey) {
        case StringPairKeyContext c -> unquoteString(c.STRING().getText());
        case IdPairKeyContext c     -> getIdentifierName(c.saplId());
        case null, default          -> pairKey != null ? pairKey.getText() : "";
        };
    }

    private CompiledExpression compileAttributesToObject(CompiledObjectAttributes attributes) {
        return switch (attributes.nature()) {
        case VALUE  -> assembleObjectValue(attributes);
        case PURE   -> compilePureObject(attributes);
        case STREAM -> compileObjectStreamExpression(attributes);
        };
    }

    private Value assembleObjectValue(CompiledObjectAttributes attributes) {
        val builder = ObjectValue.builder();
        for (val entry : attributes.attributes().entrySet()) {
            val value = entry.getValue();
            if (value instanceof ErrorValue err) {
                return err;
            }
            if (value instanceof Value v && !(v instanceof UndefinedValue)) {
                builder.put(entry.getKey(), v);
            }
        }
        return builder.build();
    }

    private CompiledExpression compilePureObject(CompiledObjectAttributes attributes) {
        return new PureExpression(ctx -> {
            val builder = ObjectValue.builder();
            for (val entry : attributes.attributes().entrySet()) {
                val compiled  = entry.getValue();
                val evaluated = compiled instanceof PureExpression pure ? pure.evaluate(ctx) : compiled;
                if (evaluated instanceof ErrorValue err) {
                    return err;
                }
                if (evaluated instanceof Value v && !(v instanceof UndefinedValue)) {
                    builder.put(entry.getKey(), v);
                }
            }
            return builder.build();
        }, attributes.isSubscriptionScoped());
    }

    private record ObjectEntry(String key, Value value) {}

    private CompiledExpression compileObjectStreamExpression(CompiledObjectAttributes attributes) {
        val sources = new ArrayList<Flux<ObjectEntry>>(attributes.attributes().size());
        for (val entry : attributes.attributes().entrySet()) {
            sources.add(compiledExpressionToFlux(entry.getValue()).map(v -> new ObjectEntry(entry.getKey(), v)));
        }
        return new StreamExpression(Flux.combineLatest(sources, ExpressionCompiler::assembleObjectFromEntries));
    }

    private static Value assembleObjectFromEntries(Object[] entries) {
        val builder = ObjectValue.builder();
        for (val entry : entries) {
            val objEntry = (ObjectEntry) entry;
            if (objEntry.value instanceof ErrorValue err) {
                return err;
            }
            if (!(objEntry.value instanceof UndefinedValue)) {
                builder.put(objEntry.key, objEntry.value);
            }
        }
        return builder.build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Array Compilation
    // ═══════════════════════════════════════════════════════════════════════════

    private CompiledExpression compileArray(ArrayContext arrayCtx, CompilationContext context) {
        val items = arrayCtx.items;
        if (items == null || items.isEmpty()) {
            return Value.EMPTY_ARRAY;
        }
        return compileArgumentsToArray(compileArguments(items, context));
    }

    CompiledArguments compileArguments(List<ExpressionContext> arguments, CompilationContext context) {
        val compiled             = new CompiledExpression[arguments.size()];
        var isStream             = false;
        var isPure               = false;
        var isSubscriptionScoped = false;

        for (int i = 0; i < arguments.size(); i++) {
            val arg = compileExpression(arguments.get(i), context);
            if (arg instanceof PureExpression pure) {
                isPure                = true;
                isSubscriptionScoped |= pure.isSubscriptionScoped();
            } else if (arg instanceof StreamExpression) {
                isStream = true;
            }
            compiled[i] = arg;
        }

        val nature = Nature.from(isStream, isPure);
        return new CompiledArguments(nature, isSubscriptionScoped, compiled);
    }

    private CompiledExpression compileArgumentsToArray(CompiledArguments args) {
        return switch (args.nature()) {
        case VALUE  -> assembleArrayValue(args.arguments());
        case PURE   -> compilePureArray(args);
        case STREAM -> compileArrayStreamExpression(args);
        };
    }

    private Value assembleArrayValue(CompiledExpression[] arguments) {
        val builder = ArrayValue.builder();
        for (val arg : arguments) {
            if (arg instanceof ErrorValue err) {
                return err;
            }
            if (arg instanceof Value v && !(v instanceof UndefinedValue)) {
                builder.add(v);
            }
        }
        return builder.build();
    }

    private CompiledExpression compilePureArray(CompiledArguments args) {
        return new PureExpression(ctx -> {
            val builder = ArrayValue.builder();
            for (val arg : args.arguments()) {
                val evaluated = arg instanceof PureExpression pure ? pure.evaluate(ctx) : arg;
                if (evaluated instanceof ErrorValue err) {
                    return err;
                }
                if (evaluated instanceof Value v && !(v instanceof UndefinedValue)) {
                    builder.add(v);
                }
            }
            return builder.build();
        }, args.isSubscriptionScoped());
    }

    private CompiledExpression compileArrayStreamExpression(CompiledArguments args) {
        val sources = Arrays.stream(args.arguments()).map(ExpressionCompiler::compiledExpressionToFlux).toList();
        return new StreamExpression(Flux.combineLatest(sources, ExpressionCompiler::assembleArrayFromObjects));
    }

    private static Value assembleArrayFromObjects(Object[] arguments) {
        val builder = ArrayValue.builder();
        for (val arg : arguments) {
            if (arg instanceof ErrorValue err) {
                return err;
            }
            if (arg instanceof Value v && !(v instanceof UndefinedValue)) {
                builder.add(v);
            }
        }
        return builder.build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Arithmetic Operators
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Extracts operator symbols from child terminal nodes of a parser rule context.
     * In ANTLR, operators appear as terminal nodes between operand contexts.
     */
    private String[] extractOperatorSymbols(ParserRuleContext context) {
        val operators = new ArrayList<String>();
        for (int i = 0; i < context.getChildCount(); i++) {
            val child = context.getChild(i);
            if (child instanceof TerminalNode terminal) {
                operators.add(terminal.getText());
            }
        }
        return operators.toArray(String[]::new);
    }

    /**
     * Compiles a chain of arithmetic operations (e.g., a + b - c or a * b / c).
     * Handles constant folding, pure expressions, and stream expressions.
     */
    private CompiledExpression compileArithmeticChain(CompiledExpression[] operands, String[] operators,
            ParserRuleContext context) {
        // Analyze nature of operands
        var isStream             = false;
        var isPure               = false;
        var isSubscriptionScoped = false;

        for (val operand : operands) {
            if (operand instanceof PureExpression pure) {
                isPure                = true;
                isSubscriptionScoped |= pure.isSubscriptionScoped();
            } else if (operand instanceof StreamExpression) {
                isStream = true;
            }
        }

        // Constant folding - all operands are Values
        if (!isPure && !isStream) {
            return foldArithmeticConstants(operands, operators, context);
        }

        // Stream expression - at least one operand is a stream
        if (isStream) {
            return compileArithmeticStreamChain(operands, operators, context);
        }

        // Pure expression - at least one operand is a PureExpression
        return compileArithmeticPureChain(operands, operators, context, isSubscriptionScoped);
    }

    /**
     * Folds constant arithmetic values at compile time.
     */
    private Value foldArithmeticConstants(CompiledExpression[] operands, String[] operators,
            ParserRuleContext context) {
        if (!(operands[0] instanceof Value firstValue)) {
            return Error.at(context, ERROR_EXPECTED_VALUE_FOR_ARITHMETIC);
        }
        return applyArithmeticChain(firstValue, Arrays.copyOfRange(operands, 1, operands.length), operators, context);
    }

    /**
     * Applies arithmetic operations to a chain of values.
     */
    private Value applyArithmeticChain(Value accumulator, CompiledExpression[] remainingOperands, String[] operators,
            ParserRuleContext context) {
        var result = accumulator;
        for (int i = 0; i < remainingOperands.length; i++) {
            val operand  = remainingOperands[i];
            val operator = operators[i];
            if (!(operand instanceof Value value)) {
                return Error.at(context, ERROR_EXPECTED_VALUE_FOR_ARITHMETIC);
            }
            result = applyArithmeticOperator(result, value, operator, context);
            if (result instanceof ErrorValue) {
                return result;
            }
        }
        return result;
    }

    /**
     * Applies a single arithmetic operator to two values.
     * Uses NumberOperators which properly merge metadata from both operands.
     */
    private Value applyArithmeticOperator(Value left, Value right, String operator, ParserRuleContext context) {
        return switch (operator) {
        case "+" -> add(context, left, right);
        case "-" -> subtract(context, left, right);
        case "*" -> multiply(context, left, right);
        case "/" -> divide(context, left, right);
        case "%" -> modulo(context, left, right);
        default  -> Error.at(context, ERROR_UNKNOWN_ARITHMETIC_OPERATOR, operator);
        };
    }

    /**
     * Compiles arithmetic chain as a pure expression.
     */
    private CompiledExpression compileArithmeticPureChain(CompiledExpression[] operands, String[] operators,
            ParserRuleContext context, boolean isSubscriptionScoped) {
        return new PureExpression(ctx -> {
            // Evaluate first operand
            val first = operands[0] instanceof PureExpression pure ? pure.evaluate(ctx) : operands[0];
            if (!(first instanceof Value firstValue)) {
                return Error.at(context, ERROR_EXPECTED_VALUE_FOR_ARITHMETIC);
            }

            // Apply operations
            var result = firstValue;
            for (int i = 1; i < operands.length; i++) {
                val operand   = operands[i];
                val operator  = operators[i - 1];
                val evaluated = operand instanceof PureExpression pure ? pure.evaluate(ctx) : operand;
                if (!(evaluated instanceof Value value)) {
                    return Error.at(context, ERROR_EXPECTED_VALUE_FOR_ARITHMETIC);
                }
                result = applyArithmeticOperator(result, value, operator, context);
                if (result instanceof ErrorValue) {
                    return result;
                }
            }
            return result;
        }, isSubscriptionScoped);
    }

    /**
     * Compiles arithmetic chain as a stream expression using combineLatest.
     */
    private CompiledExpression compileArithmeticStreamChain(CompiledExpression[] operands, String[] operators,
            ParserRuleContext context) {
        val sources = Arrays.stream(operands).map(ExpressionCompiler::compiledExpressionToFlux).toList();
        return new StreamExpression(
                Flux.combineLatest(sources, values -> combineArithmeticValues(values, operators, context)));
    }

    /**
     * Combines values from multiple streams using arithmetic operators.
     */
    private static Value combineArithmeticValues(Object[] values, String[] operators, ParserRuleContext context) {
        if (!(values[0] instanceof Value result)) {
            return Error.at(context, ERROR_EXPECTED_VALUE_FOR_ARITHMETIC);
        }
        for (int i = 1; i < values.length; i++) {
            if (!(values[i] instanceof Value value)) {
                return Error.at(context, ERROR_EXPECTED_VALUE_FOR_ARITHMETIC);
            }
            result = applyArithmeticOperatorStatic(result, value, operators[i - 1], context);
            if (result instanceof ErrorValue) {
                return result;
            }
        }
        return result;
    }

    /**
     * Static version of applyArithmeticOperator for use in lambda context.
     * Uses NumberOperators which properly merge metadata from both operands.
     */
    private static Value applyArithmeticOperatorStatic(Value left, Value right, String operator,
            ParserRuleContext context) {
        return switch (operator) {
        case "+" -> add(context, left, right);
        case "-" -> subtract(context, left, right);
        case "*" -> multiply(context, left, right);
        case "/" -> divide(context, left, right);
        case "%" -> modulo(context, left, right);
        default  -> Error.at(context, ERROR_UNKNOWN_ARITHMETIC_OPERATOR, operator);
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Comparison and Equality Operators
    // ═══════════════════════════════════════════════════════════════════════════

    @FunctionalInterface
    private interface BinaryValueOperator {
        Value apply(Value left, Value right, String operator, ParserRuleContext context);
    }

    /**
     * Extracts the first operator symbol from a parser rule context.
     */
    private String extractFirstOperatorSymbol(ParserRuleContext context) {
        for (int i = 0; i < context.getChildCount(); i++) {
            val child = context.getChild(i);
            if (child instanceof TerminalNode terminal) {
                return terminal.getText();
            }
        }
        return "";
    }

    /**
     * Compiles a binary operation with two operands and an operator.
     */
    private CompiledExpression compileBinaryOp(CompiledExpression left, CompiledExpression right, String operator,
            ParserRuleContext context, BinaryValueOperator opFunction) {
        // Both are Values - constant fold
        if (left instanceof Value leftVal && right instanceof Value rightVal) {
            return opFunction.apply(leftVal, rightVal, operator, context);
        }

        // At least one is a stream
        if (left instanceof StreamExpression || right instanceof StreamExpression) {
            val leftFlux  = compiledExpressionToFlux(left);
            val rightFlux = compiledExpressionToFlux(right);
            return new StreamExpression(
                    Flux.combineLatest(leftFlux, rightFlux, (l, r) -> opFunction.apply(l, r, operator, context)));
        }

        // At least one is a PureExpression
        val isSubscriptionScoped = (left instanceof PureExpression pl && pl.isSubscriptionScoped())
                || (right instanceof PureExpression pr && pr.isSubscriptionScoped());

        return new PureExpression(ctx -> {
            val leftVal  = left instanceof PureExpression pl ? pl.evaluate(ctx) : (Value) left;
            val rightVal = right instanceof PureExpression pr ? pr.evaluate(ctx) : (Value) right;
            return opFunction.apply(leftVal, rightVal, operator, context);
        }, isSubscriptionScoped);
    }

    /**
     * Applies equality operators: ==, !=, =~
     * Uses ComparisonOperators which properly merge metadata from both operands.
     */
    private static Value applyEqualityOperator(Value left, Value right, String operator, ParserRuleContext context) {
        return switch (operator) {
        case "==" -> ComparisonOperators.equals(context, left, right);
        case "!=" -> notEquals(context, left, right);
        case "=~" -> matchesRegularExpression(context, left, right);
        default   -> Error.at(context, ERROR_UNKNOWN_EQUALITY_OPERATOR, operator);
        };
    }

    /**
     * Applies comparison operators: <, <=, >, >=, in
     * Uses NumberOperators and ComparisonOperators which properly merge metadata.
     */
    private static Value applyComparisonOperator(Value left, Value right, String operator, ParserRuleContext context) {
        return switch (operator) {
        case "<"  -> lessThan(context, left, right);
        case "<=" -> lessThanOrEqual(context, left, right);
        case ">"  -> greaterThan(context, left, right);
        case ">=" -> greaterThanOrEqual(context, left, right);
        case "in" -> isContainedIn(context, left, right);
        default   -> Error.at(context, ERROR_UNKNOWN_COMPARISON_OPERATOR, operator);
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Boolean Operators
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Compiles a chain of lazy boolean operations (short-circuit evaluation).
     *
     * @param operands compiled operands
     * @param shortCircuitValue the value that triggers short-circuit (true for OR,
     * false for AND)
     * @param context parse context for error reporting
     */
    private CompiledExpression compileLazyBooleanChain(CompiledExpression[] operands, boolean shortCircuitValue,
            ParserRuleContext context) {
        // Check for any streams - if present, we can't do true short-circuit evaluation
        var hasStream            = false;
        var hasPure              = false;
        var isSubscriptionScoped = false;

        for (val operand : operands) {
            if (operand instanceof StreamExpression) {
                hasStream = true;
            } else if (operand instanceof PureExpression pure) {
                hasPure               = true;
                isSubscriptionScoped |= pure.isSubscriptionScoped();
            }
        }

        // All constants - fold at compile time
        if (!hasStream && !hasPure) {
            return foldLazyBooleanConstants(operands, shortCircuitValue, context);
        }

        // Has streams - use combineLatest (can't truly short-circuit reactive streams)
        if (hasStream) {
            return compileLazyBooleanStreamChain(operands, shortCircuitValue, context);
        }

        // Pure expressions - can do lazy evaluation at runtime
        return compileLazyBooleanPureChain(operands, shortCircuitValue, context, isSubscriptionScoped);
    }

    private Value foldLazyBooleanConstants(CompiledExpression[] operands, boolean shortCircuitValue,
            ParserRuleContext context) {
        var metadata = ValueMetadata.EMPTY;
        for (val operand : operands) {
            if (!(operand instanceof Value value)) {
                return Error.at(context, metadata, ERROR_BOOLEAN_OPERAND_REQUIRED, operand.getClass().getSimpleName());
            }
            metadata = metadata.merge(value.metadata());
            if (value instanceof ErrorValue error) {
                return error.withMetadata(metadata);
            }
            if (!(value instanceof BooleanValue bool)) {
                return Error.at(context, metadata, ERROR_BOOLEAN_OPERAND_REQUIRED, value.getClass().getSimpleName());
            }
            if (bool.value() == shortCircuitValue) {
                return new BooleanValue(shortCircuitValue, metadata);
            }
        }
        return new BooleanValue(!shortCircuitValue, metadata);
    }

    private CompiledExpression compileLazyBooleanPureChain(CompiledExpression[] operands, boolean shortCircuitValue,
            ParserRuleContext context, boolean isSubscriptionScoped) {
        return new PureExpression(ctx -> {
            var metadata = ValueMetadata.EMPTY;
            for (val operand : operands) {
                val evaluated = operand instanceof PureExpression pure ? pure.evaluate(ctx) : operand;
                if (!(evaluated instanceof Value value)) {
                    return Error.at(context, metadata, ERROR_BOOLEAN_OPERAND_REQUIRED,
                            evaluated.getClass().getSimpleName());
                }
                metadata = metadata.merge(value.metadata());
                if (value instanceof ErrorValue error) {
                    return error.withMetadata(metadata);
                }
                if (!(value instanceof BooleanValue bool)) {
                    return Error.at(context, metadata, ERROR_BOOLEAN_OPERAND_REQUIRED,
                            value.getClass().getSimpleName());
                }
                if (bool.value() == shortCircuitValue) {
                    return new BooleanValue(shortCircuitValue, metadata);
                }
            }
            return new BooleanValue(!shortCircuitValue, metadata);
        }, isSubscriptionScoped);
    }

    /**
     * Compiles lazy boolean stream chain using switchMap for true short-circuit
     * evaluation. Only subscribes to subsequent streams when the current value
     * doesn't trigger short-circuit.
     */
    private CompiledExpression compileLazyBooleanStreamChain(CompiledExpression[] operands, boolean shortCircuitValue,
            ParserRuleContext context) {
        // Build the chain recursively from right to left, so the leftmost operand
        // is evaluated first and can short-circuit the rest
        return new StreamExpression(
                buildLazyBooleanStreamChain(operands, 0, shortCircuitValue, context, ValueMetadata.EMPTY));
    }

    private Flux<Value> buildLazyBooleanStreamChain(CompiledExpression[] operands, int index, boolean shortCircuitValue,
            ParserRuleContext context, ValueMetadata accumulatedMetadata) {
        if (index >= operands.length) {
            // All operands evaluated, none triggered short-circuit
            return Flux.just(new BooleanValue(!shortCircuitValue, accumulatedMetadata));
        }

        val currentFlux = compiledExpressionToFlux(operands[index]);

        return currentFlux.switchMap(value -> {
            val metadata = accumulatedMetadata.merge(value.metadata());

            // Error propagation
            if (value instanceof ErrorValue error) {
                return Flux.just(error.withMetadata(metadata));
            }

            // Type check
            if (!(value instanceof BooleanValue bool)) {
                return Flux.just(
                        Error.at(context, metadata, ERROR_BOOLEAN_OPERAND_REQUIRED, value.getClass().getSimpleName()));
            }

            // Short-circuit: return immediately without subscribing to remaining operands
            if (bool.value() == shortCircuitValue) {
                return Flux.just(new BooleanValue(shortCircuitValue, metadata));
            }

            // Continue to next operand
            return buildLazyBooleanStreamChain(operands, index + 1, shortCircuitValue, context, metadata);
        });
    }

    @FunctionalInterface
    private interface BooleanChainOperator {
        Value apply(Value[] values, ParserRuleContext context);
    }

    /**
     * Compiles a chain of eager boolean operations (all operands evaluated).
     */
    private CompiledExpression compileEagerBooleanChain(CompiledExpression[] operands, ParserRuleContext context,
            BooleanChainOperator operator) {
        // Analyze nature
        var hasStream            = false;
        var hasPure              = false;
        var isSubscriptionScoped = false;

        for (val operand : operands) {
            if (operand instanceof StreamExpression) {
                hasStream = true;
            } else if (operand instanceof PureExpression pure) {
                hasPure               = true;
                isSubscriptionScoped |= pure.isSubscriptionScoped();
            }
        }

        // All constants - fold at compile time
        if (!hasStream && !hasPure) {
            val values = Arrays.stream(operands).map(o -> (Value) o).toArray(Value[]::new);
            return operator.apply(values, context);
        }

        // Has streams
        if (hasStream) {
            val sources = Arrays.stream(operands).map(ExpressionCompiler::compiledExpressionToFlux).toList();
            return new StreamExpression(Flux.combineLatest(sources, values -> {
                val valueArray = Arrays.stream(values).map(v -> (Value) v).toArray(Value[]::new);
                return operator.apply(valueArray, context);
            }));
        }

        // Pure expressions
        val finalSubscriptionScoped = isSubscriptionScoped;
        return new PureExpression(ctx -> {
            val values = new Value[operands.length];
            for (int i = 0; i < operands.length; i++) {
                val op = operands[i];
                values[i] = op instanceof PureExpression pure ? pure.evaluate(ctx) : (Value) op;
            }
            return operator.apply(values, context);
        }, finalSubscriptionScoped);
    }

    private static Value applyEagerOrOperator(Value[] values, ParserRuleContext context) {
        var metadata = ValueMetadata.EMPTY;
        for (val value : values) {
            metadata = metadata.merge(value.metadata());
            if (value instanceof ErrorValue error) {
                return error.withMetadata(metadata);
            }
            if (!(value instanceof BooleanValue bool)) {
                return Error.at(context, metadata, ERROR_EAGER_OR_REQUIRES_BOOLEAN, value.getClass().getSimpleName());
            }
            if (bool.value()) {
                return new BooleanValue(true, metadata);
            }
        }
        return new BooleanValue(false, metadata);
    }

    private static Value applyEagerAndOperator(Value[] values, ParserRuleContext context) {
        var metadata = ValueMetadata.EMPTY;
        for (val value : values) {
            metadata = metadata.merge(value.metadata());
            if (value instanceof ErrorValue error) {
                return error.withMetadata(metadata);
            }
            if (!(value instanceof BooleanValue bool)) {
                return Error.at(context, metadata, ERROR_EAGER_AND_REQUIRES_BOOLEAN, value.getClass().getSimpleName());
            }
            if (!bool.value()) {
                return new BooleanValue(false, metadata);
            }
        }
        return new BooleanValue(true, metadata);
    }

    private static Value applyXorOperator(Value[] values, ParserRuleContext context) {
        var result   = false;
        var metadata = ValueMetadata.EMPTY;
        for (val value : values) {
            metadata = metadata.merge(value.metadata());
            if (value instanceof ErrorValue error) {
                return error.withMetadata(metadata);
            }
            if (!(value instanceof BooleanValue bool)) {
                return Error.at(context, metadata, ERROR_XOR_REQUIRES_BOOLEAN, value.getClass().getSimpleName());
            }
            result ^= bool.value();
        }
        return new BooleanValue(result, metadata);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Utilities
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Converts any compiled expression into a Flux stream.
     */
    public Flux<Value> compiledExpressionToFlux(CompiledExpression expression) {
        return switch (expression) {
        case Value value                  -> Flux.just(value);
        case StreamExpression(var stream) -> stream;
        case PureExpression pureExpr      -> pureExpr.flux();
        };
    }
}
