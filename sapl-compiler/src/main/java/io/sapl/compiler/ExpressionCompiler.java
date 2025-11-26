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
import io.sapl.api.model.Value;
import io.sapl.compiler.operators.BooleanOperators;
import io.sapl.compiler.operators.ComparisonOperators;
import io.sapl.compiler.operators.NumberOperators;
import io.sapl.compiler.operators.StepOperators;
import io.sapl.grammar.sapl.*;
import io.sapl.grammar.sapl.Object;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

import static io.sapl.compiler.operators.BooleanOperators.TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR;

/**
 * Compiles SAPL abstract syntax tree expressions into optimized executable
 * representations. Performs compile-time
 * constant folding and type-based optimization to generate efficient evaluation
 * code.
 */
@UtilityClass
public class ExpressionCompiler {

    private static final String ERROR_ASSEMBLE_OBJECT_NON_VALUE_NATURE = "assembleObjectValue called with non-VALUE nature: %s";
    private static final String ERROR_LEFT_OPERAND_MISSING             = "Left operand of %s missing";
    private static final String ERROR_RIGHT_OPERAND_MISSING            = "Right operand of %s missing";
    private static final String ERROR_UNEXPECTED_EXPRESSION            = "unexpected expression: %s.";
    private static final String ERROR_UNEXPECTED_VALUE                 = "unexpected value: %s.";
    private static final String ERROR_UNIMPLEMENTED                    = "unimplemented";

    private static final Value UNIMPLEMENTED = Value.error(ERROR_UNIMPLEMENTED);

    /**
     * Compiles a SAPL expression from the abstract syntax tree into an optimized
     * executable form. Dispatches to
     * specialized compilation methods based on expression type.
     *
     * @param expression
     * the AST expression to compile
     * @param context
     * the compilation context containing variables and function broker
     *
     * @return the compiled expression ready for evaluation, or null if input is
     * null
     */
    public CompiledExpression compileExpression(Expression expression, CompilationContext context) {
        if (expression == null) {
            // Intentional. Downstream, this cleans up the code and removes multiple null
            // checks.
            return null;
        }
        return switch (expression) {
        case Or or                 -> compileLazyOr(or, context);
        case And and               -> compileLazyAnd(and, context);
        case EagerOr eagerOr       -> compileBinaryOperator(eagerOr, BooleanOperators::or, context);
        case EagerAnd eagerAnd     -> compileBinaryOperator(eagerAnd, BooleanOperators::and, context);
        case XOr xor               -> compileBinaryOperator(xor, BooleanOperators::xor, context);
        case Not not               -> compileUnaryOperator(not, BooleanOperators::not, context);
        case Multi multi           -> compileBinaryOperator(multi, NumberOperators::multiply, context);
        case Div div               -> compileBinaryOperator(div, NumberOperators::divide, context);
        case Modulo modulo         -> compileBinaryOperator(modulo, NumberOperators::modulo, context);
        case Plus plus             -> compileBinaryOperator(plus, NumberOperators::add, context);
        case Minus minus           -> compileBinaryOperator(minus, NumberOperators::subtract, context);
        case Less less             -> compileBinaryOperator(less, NumberOperators::lessThan, context);
        case LessEquals lessEquals -> compileBinaryOperator(lessEquals, NumberOperators::lessThanOrEqual, context);
        case More more             -> compileBinaryOperator(more, NumberOperators::greaterThan, context);
        case MoreEquals moreEquals -> compileBinaryOperator(moreEquals, NumberOperators::greaterThanOrEqual, context);
        case UnaryPlus unaryPlus   -> compileUnaryOperator(unaryPlus, NumberOperators::unaryPlus, context);
        case UnaryMinus unaryMinus -> compileUnaryOperator(unaryMinus, NumberOperators::unaryMinus, context);
        case ElementOf elementOf   -> compileBinaryOperator(elementOf, ComparisonOperators::isContainedIn, context);
        case Equals equals         -> compileBinaryOperator(equals, ComparisonOperators::equals, context);
        case NotEquals notEquals   -> compileBinaryOperator(notEquals, ComparisonOperators::notEquals, context);
        case Regex regex           ->
            compileBinaryOperator(regex, ComparisonOperators::matchesRegularExpression, context);
        case BasicExpression basic -> compileBasicExpression(basic, context);
        default                    ->
            throw new SaplCompilerException(String.format(ERROR_UNEXPECTED_EXPRESSION, expression));
        };
    }

    /**
     * Compiles a lazy OR operation with short-circuit evaluation. Returns the left
     * operand immediately if it is true at
     * compile time, avoiding compilation of the right operand. For runtime
     * evaluation, only evaluates the right operand
     * when the left operand is false, enabling efficient attribute resolution and
     * avoiding unnecessary PIP
     * subscriptions.
     */
    private CompiledExpression compileLazyOr(Or or, CompilationContext context) {
        val left      = compileExpression(or.getLeft(), context);
        val leftCheck = checkShortCircuitLeft(left, Value.TRUE);
        if (leftCheck != null) {
            return leftCheck;
        }
        val right = compileExpression(or.getRight(), context);
        if (Value.TRUE.equals(right) || right instanceof ErrorValue) {
            return right;
        }
        return assembleLazyBoolean(left, right, Value.TRUE, BooleanOperators::or);
    }

    /**
     * Checks if the left operand should short-circuit the lazy boolean operation.
     * Returns early for short-circuit
     * value, errors, or type mismatches.
     */
    private CompiledExpression checkShortCircuitLeft(CompiledExpression left, Value shortCircuitValue) {
        if (shortCircuitValue.equals(left) || left instanceof ErrorValue) {
            return left;
        }
        if (left instanceof Value && !(left instanceof BooleanValue)) {
            return Value.error(TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR, left);
        }
        return null;
    }

    /**
     * Assembles a lazy boolean operation from compiled operands. Dispatches to the
     * appropriate handler based on operand
     * types (Value, Pure, Stream).
     */
    private CompiledExpression assembleLazyBoolean(CompiledExpression left, CompiledExpression right,
            Value shortCircuitValue, java.util.function.BinaryOperator<Value> operator) {
        if (left instanceof Value leftVal && right instanceof Value rightVal) {
            return operator.apply(leftVal, rightVal);
        }
        if (left instanceof PureExpression pureLeft && right instanceof PureExpression pureRight) {
            return new PureExpression(ctx -> evaluatePureLazyBoolean(pureLeft, pureRight, ctx, shortCircuitValue),
                    pureLeft.isSubscriptionScoped() || pureRight.isSubscriptionScoped());
        }
        if (left instanceof Value leftVal && right instanceof PureExpression pureRight) {
            return new PureExpression(ctx -> operator.apply(leftVal, pureRight.evaluate(ctx)),
                    pureRight.isSubscriptionScoped());
        }
        if (left instanceof PureExpression pureLeft && right instanceof Value rightVal) {
            return new PureExpression(ctx -> evaluatePureLazyBooleanWithConstantRight(pureLeft, rightVal, ctx,
                    shortCircuitValue, operator), pureLeft.isSubscriptionScoped());
        }
        return new StreamExpression(evaluateLazyBooleanWithStreams(left, right, shortCircuitValue));
    }

    /**
     * Evaluates pure left operand with constant right operand, applying
     * short-circuit logic.
     */
    private static Value evaluatePureLazyBooleanWithConstantRight(PureExpression pureLeft, Value rightVal,
            EvaluationContext ctx, Value shortCircuitValue, java.util.function.BinaryOperator<Value> operator) {
        val leftValue = pureLeft.evaluate(ctx);
        if (shortCircuitValue.equals(leftValue) || leftValue instanceof ErrorValue) {
            return leftValue;
        }
        if (!(leftValue instanceof BooleanValue)) {
            return Value.error(TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR, leftValue);
        }
        return operator.apply(leftValue, rightVal);
    }

    /**
     * Evaluates lazy boolean with stream expressions using switchMap for
     * short-circuit semantics.
     */
    private static Flux<Value> evaluateLazyBooleanWithStreams(CompiledExpression left, CompiledExpression right,
            Value shortCircuitValue) {
        val leftFlux  = compiledExpressionToFlux(left);
        val rightFlux = compiledExpressionToFlux(right);
        return leftFlux.switchMap(leftValue -> {
            if (shortCircuitValue.equals(leftValue) || leftValue instanceof ErrorValue) {
                return Flux.just(leftValue);
            }
            if (!(leftValue instanceof BooleanValue)) {
                return Flux.just(Value.error(TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR, leftValue));
            }
            return rightFlux.map(rightValue -> combineBooleanResult(leftValue, rightValue, shortCircuitValue));
        });
    }

    /**
     * Combines boolean result from right operand with left operand's secret flag.
     */
    private static Value combineBooleanResult(Value leftValue, Value rightValue, Value shortCircuitValue) {
        if (rightValue instanceof ErrorValue) {
            return rightValue;
        }
        if (!(rightValue instanceof BooleanValue rightBool)) {
            return Value.error(TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR, rightValue);
        }
        val resultValue = shortCircuitValue.equals(Value.TRUE) ? rightBool.value()
                : rightBool.value() && ((BooleanValue) leftValue).value();
        return new BooleanValue(resultValue, rightValue.secret() || leftValue.secret());
    }

    /**
     * Evaluates lazy boolean operation with pure expressions using short-circuit
     * semantics.
     */
    private static Value evaluatePureLazyBoolean(PureExpression pureLeft, PureExpression pureRight,
            EvaluationContext ctx, Value shortCircuitValue) {
        val left = pureLeft.evaluate(ctx);
        if (shortCircuitValue.equals(left) || left instanceof ErrorValue) {
            return left;
        }
        if (!(left instanceof BooleanValue)) {
            return Value.error(TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR, left);
        }
        val right = pureRight.evaluate(ctx);
        if (shortCircuitValue.equals(right)) {
            return new BooleanValue(shortCircuitValue.equals(Value.TRUE), right.secret() || left.secret());
        }
        if (right instanceof ErrorValue) {
            return right;
        }
        if (!(right instanceof BooleanValue rightBool)) {
            return Value.error(TYPE_MISMATCH_BOOLEAN_EXPECTED_ERROR, right);
        }
        val resultValue = !shortCircuitValue.equals(Value.TRUE) && rightBool.value();
        return new BooleanValue(resultValue, right.secret() || left.secret());
    }

    /**
     * Compiles a lazy AND operation with short-circuit evaluation. Returns the left
     * operand immediately if it is false
     * at compile time, avoiding compilation of the right operand. For runtime
     * evaluation, only evaluates the right
     * operand when the left operand is true, enabling efficient attribute
     * resolution and avoiding unnecessary PIP
     * subscriptions.
     */
    private CompiledExpression compileLazyAnd(And and, CompilationContext context) {
        return compileLazyAnd(compileExpression(and.getLeft(), context), and.getRight(), context);
    }

    /**
     * Compiles a lazy AND with pre-compiled left operand. Used for target
     * expression compilation where match expression
     * is combined with target.
     */
    public CompiledExpression compileLazyAnd(CompiledExpression left, Expression rightArgument,
            CompilationContext context) {
        val leftCheck = checkShortCircuitLeft(left, Value.FALSE);
        if (leftCheck != null) {
            return leftCheck;
        }
        val right = compileExpression(rightArgument, context);
        if (right == null) {
            return left;
        }
        if (Value.FALSE.equals(right) || right instanceof ErrorValue) {
            return right;
        }
        return assembleLazyBoolean(left, right, Value.FALSE, BooleanOperators::and);
    }

    /**
     * Compiles a binary operation by recursively compiling its operands and
     * combining them. Performs pre-compilation
     * optimization for regex patterns when the right operand is a constant.
     *
     * @param astOperator
     * the binary operator AST node
     * @param operator
     * the operation to apply to compiled operands
     * @param context
     * the compilation context
     *
     * @return the compiled binary operation expression
     */
    private CompiledExpression compileBinaryOperator(BinaryOperator astOperator,
            java.util.function.BinaryOperator<Value> operator, CompilationContext context) {

        val left  = compileExpression(astOperator.getLeft(), context);
        val right = compileExpression(astOperator.getRight(), context);
        if (left == null) {
            return Value.error(ERROR_LEFT_OPERAND_MISSING, astOperator.getClass().getSimpleName());
        }
        if (right == null) {
            return Value.error(ERROR_RIGHT_OPERAND_MISSING, astOperator.getClass().getSimpleName());
        }
        // Special case for regex. Here if the right side is a text constant, we can
        // immediately pre-compile the expression and do not need to do it at policy
        // evaluation time. We do it here, to have a re-usable assembleBinaryOperation
        // method, as that is a pretty critical and complex method that then can be
        // re-used for assembling special cases of steps as well without repeating
        // 99% of the logic.
        if (right instanceof Value valRight && astOperator instanceof Regex) {
            val compiledRegex = ComparisonOperators.compileRegularExpressionOperator(valRight);
            operator = (l, ignoredBecauseWeUseTheCompiledRegex) -> compiledRegex.apply(l);
        }
        return assembleBinaryOperation(left, right, operator);
    }

    /**
     * Assembles two compiled expressions into a binary operation. Performs constant
     * folding when both operands are
     * values. Creates optimized stream or pure expression wrappers based on operand
     * types.
     *
     * @param left
     * the compiled left operand
     * @param right
     * the compiled right operand
     * @param operation
     * the binary operation to apply
     *
     * @return the assembled binary operation expression
     */
    private CompiledExpression assembleBinaryOperation(CompiledExpression left, CompiledExpression right,
            java.util.function.BinaryOperator<Value> operation) {
        if (left instanceof ErrorValue) {
            return left;
        }
        if (right instanceof ErrorValue) {
            return right;
        }
        if (left instanceof Value leftValue && right instanceof Value rightValue) {
            return operation.apply(leftValue, rightValue);
        }
        if (left instanceof StreamExpression || right instanceof StreamExpression) {
            return assembleBinaryStreamOperator(left, right, operation);
        }
        if (left instanceof PureExpression subLeft && right instanceof PureExpression subRight) {
            return new PureExpression(ctx -> operation.apply(subLeft.evaluate(ctx), subRight.evaluate(ctx)),
                    subLeft.isSubscriptionScoped() || subRight.isSubscriptionScoped());
        }
        if (left instanceof PureExpression subLeft && right instanceof Value valRight) {
            return new PureExpression(ctx -> operation.apply(subLeft.evaluate(ctx), valRight),
                    subLeft.isSubscriptionScoped());
        }
        if (left instanceof Value valLeft && right instanceof PureExpression subRight) {
            return new PureExpression(ctx -> operation.apply(valLeft, subRight.evaluate(ctx)),
                    subRight.isSubscriptionScoped());
        }
        throw new SaplCompilerException(
                "Unexpected expression types. Should not be possible: " + left.getClass().getSimpleName() + " and "
                        + right.getClass().getSimpleName() + ". This indicates an implementation bug.");
    }

    /**
     * Assembles a binary operation involving at least one stream expression. Uses
     * Flux.combineLatest to reactively
     * combine the latest values from both operands.
     *
     * @param leftExpression
     * the left compiled expression
     * @param rightExpression
     * the right compiled expression
     * @param operation
     * the binary operation to apply
     *
     * @return a stream expression that combines the operand streams
     */
    private StreamExpression assembleBinaryStreamOperator(CompiledExpression leftExpression,
            CompiledExpression rightExpression, java.util.function.BinaryOperator<Value> operation) {
        val stream = Flux.combineLatest(compiledExpressionToFlux(leftExpression),
                compiledExpressionToFlux(rightExpression), operation);
        return new StreamExpression(stream);
    }

    /**
     * Compiles a unary operation by compiling its operand and applying the
     * operation. Performs constant folding for
     * value operands. Creates stream or pure expression wrappers for deferred
     * operands.
     *
     * @param operator
     * the unary operator AST node
     * @param operation
     * the unary operation to apply
     * @param context
     * the compilation context
     *
     * @return the compiled unary operation expression
     */
    private CompiledExpression compileUnaryOperator(UnaryOperator operator,
            java.util.function.UnaryOperator<Value> operation, CompilationContext context) {
        val expression = compileExpression(operator.getExpression(), context);
        return switch (expression) {
        case Value value                          -> operation.apply(value);
        case PureExpression pureExpression        -> new PureExpression(
                ctx -> operation.apply(pureExpression.evaluate(ctx)), pureExpression.isSubscriptionScoped());
        case StreamExpression(Flux<Value> stream) -> new StreamExpression(stream.map(operation));
        };
    }

    /**
     * Compiles a basic expression by dispatching to the appropriate handler based
     * on the specific basic expression
     * type.
     * <p>
     * Grammar: BasicExpression: Basic (FILTER filter=FilterComponent | SUBTEMPLATE
     * subtemplate=BasicExpression)?
     * <p>
     * First compiles the Basic part, then applies any filter or subtemplate.
     *
     * @param expression
     * the basic expression AST node
     * @param context
     * the compilation context
     *
     * @return the compiled basic expression
     */
    private CompiledExpression compileBasicExpression(BasicExpression expression, CompilationContext context) {
        // First compile the Basic part
        val basicCompiled = switch (expression) {
        case BasicGroup group                               ->
            compileSteps(compileExpression(group.getExpression(), context), group.getSteps(), context);
        case BasicValue value                               -> compileValue(value, context);
        case BasicFunction function                         -> compileBasicFunction(function, context);
        case BasicEnvironmentAttribute envAttribute         ->
            AttributeCompiler.compileEnvironmentAttribute(envAttribute, context);
        case BasicEnvironmentHeadAttribute envHeadAttribute ->
            AttributeCompiler.compileHeadEnvironmentAttribute(envHeadAttribute, context);
        case BasicIdentifier identifier                     -> compileIdentifier(identifier, context);
        case BasicRelative relativeValue                    -> compileBasicRelative(relativeValue, context);
        default                                             ->
            throw new SaplCompilerException(String.format(ERROR_UNEXPECTED_EXPRESSION, expression));
        };

        // Then apply filter or subtemplate if present
        return applyFilterOrSubtemplate(basicCompiled, expression, context);
    }

    /**
     * Applies filter or subtemplate to a compiled expression if present in the AST.
     *
     * @param compiled
     * the compiled basic expression
     * @param expression
     * the original AST node (may have filter/subtemplate)
     * @param context
     * the compilation context
     *
     * @return the expression with filter/subtemplate applied, or original if none
     */
    private CompiledExpression applyFilterOrSubtemplate(CompiledExpression compiled, BasicExpression expression,
            CompilationContext context) {
        if (expression.getFilter() != null) {
            return FilterCompiler.compileFilter(compiled, expression.getFilter(), context);
        }
        if (expression.getSubtemplate() != null) {
            return SubtemplateCompiler.compileSubtemplate(compiled, expression.getSubtemplate(), context);
        }
        return compiled;
    }

    /**
     * Compiles a basic relative expression that references the relative value from
     * the evaluation context.
     *
     * @param relativeValue
     * the relative value AST node
     * @param context
     * the compilation context
     *
     * @return a pure expression that extracts the relative value and applies steps
     */
    private CompiledExpression compileBasicRelative(BasicRelative relativeValue, CompilationContext context) {
        CompiledExpression compiled = new PureExpression(EvaluationContext::relativeValue, false);
        return compileSteps(compiled, relativeValue.getSteps(), context);

    }

    /**
     * Compiles a function call by compiling its arguments and dispatching to the
     * appropriate handler based on argument
     * types.
     *
     * @param function
     * the function AST node
     * @param context
     * the compilation context
     *
     * @return the compiled function call expression
     */
    private CompiledExpression compileBasicFunction(BasicFunction function, CompilationContext context) {
        var arguments = CompiledArguments.EMPTY_ARGUMENTS;
        if (function.getArguments() != null && function.getArguments().getArgs() != null) {
            arguments = compileArguments(function.getArguments().getArgs(), context);
        }
        CompiledExpression compiled = switch (arguments.nature()) {
        case VALUE  -> foldFunctionWithValueParameters(function, arguments.arguments(), context);
        case PURE   -> compileFunctionWithPureParameters(function, arguments, context);
        case STREAM -> compileFunctionWithStreamParameters(function, arguments);
        };
        return compileSteps(compiled, function.getSteps(), context);
    }

    /**
     * Compiles a function call with stream parameters. Creates a stream expression
     * that combines the argument streams
     * and evaluates the function with each combination.
     *
     * @param function
     * the function AST node
     * @param arguments
     * the compiled arguments
     *
     * @return a stream expression that evaluates the function reactively
     */
    private CompiledExpression compileFunctionWithStreamParameters(BasicFunction function,
            CompiledArguments arguments) {
        val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux).toList();
        val stream  = Flux.combineLatest(sources, c -> c).flatMap(combined -> Flux.deferContextual(ctx -> Flux
                .just(evaluateFunctionWithValueParameters(function, combined, ctx.get(EvaluationContext.class)))));
        return new StreamExpression(stream);
    }

    /**
     * Compiles a function call with pure parameters. Creates a pure expression that
     * evaluates all arguments and then
     * invokes the function.
     *
     * @param function
     * the function AST node
     * @param arguments
     * the compiled arguments
     * @param context
     * the compilation context
     *
     * @return a pure expression that evaluates the function call
     */
    private CompiledExpression compileFunctionWithPureParameters(BasicFunction function, CompiledArguments arguments,
            CompilationContext context) {
        return new PureExpression(ctx -> {
            val valueArguments = new ArrayList<Value>(arguments.arguments().length);
            for (val argument : arguments.arguments()) {
                switch (argument) {
                case Value value                   -> valueArguments.add(value);
                case PureExpression pureExpression -> valueArguments.add(pureExpression.evaluate(ctx));
                case StreamExpression ignored      -> throw new SaplCompilerException(
                        "Encountered a stream expression during pure compilation path. Should not be possible.");
                }
            }
            val invocation = new FunctionInvocation(
                    ImportResolver.resolveFunctionIdentifierByImports(function, function.getIdentifier()),
                    valueArguments);
            return context.getFunctionBroker().evaluateFunction(invocation);
        }, arguments.isSubscriptionScoped());
    }

    /**
     * Evaluates a function call with known constant parameters at compile time.
     * Performs immediate function invocation
     * for compile-time constant folding.
     *
     * @param function
     * the function AST node
     * @param arguments
     * the compiled value arguments
     * @param context
     * the compilation context
     *
     * @return the function evaluation result as a CompiledExpression
     */
    private CompiledExpression foldFunctionWithValueParameters(BasicFunction function, CompiledExpression[] arguments,
            CompilationContext context) {
        val valueArguments = Arrays.stream(arguments).map(Value.class::cast).toList();
        val invocation     = new FunctionInvocation(
                ImportResolver.resolveFunctionIdentifierByImports(function, function.getIdentifier()), valueArguments);
        return context.getFunctionBroker().evaluateFunction(invocation);
    }

    /**
     * Evaluates a function call with known constant parameters during evaluation
     * time.
     *
     * @param function
     * the function AST node
     * @param arguments
     * the compiled value arguments
     * @param context
     * the evaluation context
     *
     * @return the function evaluation result as a Value
     */
    private Value evaluateFunctionWithValueParameters(BasicFunction function, java.lang.Object[] arguments,
            EvaluationContext context) {
        val valueArguments = Arrays.stream(arguments).map(Value.class::cast).toList();
        val invocation     = new FunctionInvocation(
                ImportResolver.resolveFunctionIdentifierByImports(function, function.getIdentifier()), valueArguments);
        return context.functionBroker().evaluateFunction(invocation);
    }

    /**
     * Compiles an identifier reference by checking local scope first, then creating
     * a pure expression that retrieves
     * the identifier from the evaluation context.
     *
     * @param identifier
     * the identifier AST node
     * @param context
     * the compilation context
     *
     * @return the compiled identifier reference
     */
    private CompiledExpression compileIdentifier(BasicIdentifier identifier, CompilationContext context) {
        val variableIdentifier = identifier.getIdentifier();
        val maybeLocalVariable = context.getVariable(variableIdentifier);
        if (maybeLocalVariable != null) {
            return maybeLocalVariable;
        }
        CompiledExpression compiledValue = new PureExpression(ctx -> ctx.get(variableIdentifier), true);
        return compileSteps(compiledValue, identifier.getSteps(), context);
    }

    /**
     * Compiles a value literal by extracting the value and compiling any subsequent
     * steps. Performs deduplication for
     * constant values.
     *
     * @param basic
     * the basic value AST node
     * @param context
     * the compilation context
     *
     * @return the compiled value expression
     */
    private CompiledExpression compileValue(BasicValue basic, CompilationContext context) {
        val value         = basic.getValue();
        var compiledValue = switch (value) {
                          case Object object        -> composeObject(object, context);
                          case Array array          -> composeArray(array, context);
                          case StringLiteral string -> Value.of(string.getString());
                          case NumberLiteral number -> Value.of(number.getNumber());
                          case TrueLiteral t        -> Value.TRUE;
                          case FalseLiteral f       -> Value.FALSE;
                          case NullLiteral nil      -> Value.NULL;
                          case UndefinedLiteral u   -> Value.UNDEFINED;
                          default                   ->
                              throw new SaplCompilerException(String.format(ERROR_UNEXPECTED_VALUE, value));
                          };
        return compileSteps(compiledValue, basic.getSteps(), context);
    }

    /**
     * Compiles a chain of steps by sequentially applying each step to the result of
     * the previous step.
     *
     * @param expression
     * the initial expression
     * @param steps
     * the list of steps to apply
     * @param context
     * the compilation context
     *
     * @return the expression with all steps compiled and applied
     */
    private CompiledExpression compileSteps(CompiledExpression expression, Collection<Step> steps,
            CompilationContext context) {
        if (steps == null || steps.isEmpty()) {
            return expression;
        }
        for (val step : steps) {
            expression = compileStep(expression, step, context);
        }
        if (expression instanceof Value constantValue) {
            return context.dedupe(constantValue);
        }
        return expression;
    }

    /**
     * Compiles a single step operation by dispatching to the appropriate step
     * handler based on step type.
     *
     * @param parent
     * the parent expression to which the step is applied
     * @param step
     * the step AST node
     * @param context
     * the compilation context
     *
     * @return the compiled step expression
     */
    private CompiledExpression compileStep(CompiledExpression parent, Step step, CompilationContext context) {
        return switch (step) {
        case KeyStep keyStep                                 ->
            compileStep(parent, p -> StepOperators.keyStep(p, keyStep.getId()));
        case EscapedKeyStep escapedKeyStep                   ->
            compileStep(parent, p -> StepOperators.keyStep(p, escapedKeyStep.getId()));
        case WildcardStep wildcardStep                       -> compileStep(parent, StepOperators::wildcardStep);
        case AttributeFinderStep attributeFinderStep         ->
            AttributeCompiler.compileAttributeFinderStep(parent, attributeFinderStep, context);
        case HeadAttributeFinderStep headAttributeFinderStep ->
            AttributeCompiler.compileHeadAttributeFinderStep(parent, headAttributeFinderStep, context);
        case RecursiveKeyStep recursiveKeyStep               ->
            compileStep(parent, p -> StepOperators.recursiveKeyStep(p, recursiveKeyStep.getId()));
        case RecursiveWildcardStep recursiveWildcardStep     ->
            compileStep(parent, StepOperators::recursiveWildcardStep);
        case RecursiveIndexStep recursiveIndexStep           ->
            compileStep(parent, p -> StepOperators.recursiveIndexStep(p, recursiveIndexStep.getIndex()));
        case IndexStep indexStep                             ->
            compileStep(parent, p -> StepOperators.indexStep(p, indexStep.getIndex()));
        case ArraySlicingStep arraySlicingStep               -> compileStep(parent, p -> StepOperators.sliceArray(p,
                arraySlicingStep.getIndex(), arraySlicingStep.getTo(), arraySlicingStep.getStep()));
        case ExpressionStep expressionStep                   -> compileExpressionStep(parent, expressionStep, context);
        case ConditionStep conditionStep                     -> compileConditionStep(parent, conditionStep, context);
        case IndexUnionStep indexUnionStep                   ->
            compileStep(parent, p -> StepOperators.indexUnion(p, indexUnionStep.getIndices()));
        case AttributeUnionStep attributeUnionStep           ->
            compileStep(parent, p -> StepOperators.attributeUnion(p, attributeUnionStep.getAttributes()));
        default                                              -> UNIMPLEMENTED;
        };
    }

    /**
     * Compiles an expression step that uses a sub-expression to determine the index
     * or key for accessing the parent
     * value.
     *
     * @param parentExpression
     * the parent expression being accessed
     * @param expressionStep
     * the expression step AST node
     * @param context
     * the compilation context
     *
     * @return the compiled expression step
     */
    private CompiledExpression compileExpressionStep(CompiledExpression parentExpression, ExpressionStep expressionStep,
            CompilationContext context) {
        val expressionStepExpression = compileExpression(expressionStep.getExpression(), context);
        return assembleBinaryOperation(parentExpression, expressionStepExpression, StepOperators::indexOrKeyStep);
    }

    /**
     * Compiles a step operation on a parent expression by applying a unary
     * operation. Handles constant folding for
     * value parents, and creates stream or pure expression wrappers for deferred
     * parents.
     *
     * @param parent
     * the parent expression
     * @param operation
     * the unary operation to apply
     *
     * @return the compiled step expression
     */
    private CompiledExpression compileStep(CompiledExpression parent,
            java.util.function.UnaryOperator<Value> operation) {
        if (parent instanceof ErrorValue || parent instanceof UndefinedValue) {
            return parent;
        }
        return switch (parent) {
        case Value value                          -> operation.apply(value);
        case StreamExpression(Flux<Value> stream) -> new StreamExpression(stream.map(operation));
        case PureExpression pureParent            ->
            new PureExpression(ctx -> operation.apply(pureParent.evaluate(ctx)), pureParent.isSubscriptionScoped());
        };
    }

    // ============================================================================
    // ConditionStep
    // ============================================================================

    /**
     * Compiles a condition step by dispatching to the appropriate handler based on
     * the parent and condition expression
     * type.
     *
     * @param parent
     * the parent expression to filter
     * @param expressionStep
     * the condition step AST node
     * @param context
     * the compilation context
     *
     * @return the compiled condition step expression
     */
    private CompiledExpression compileConditionStep(CompiledExpression parent, ConditionStep expressionStep,
            CompilationContext context) {
        val compiledConditionExpression = compileExpression(expressionStep.getExpression(), context);
        return switch (parent) {
        case Value valueParent             -> {
            if (valueParent instanceof ErrorValue || valueParent instanceof UndefinedValue) {
                yield parent;
            }
            yield switch (compiledConditionExpression) {
            case Value valueCondition             ->
                evaluateConditionOnValueParentWithConstantValueCondition(valueParent, valueCondition);
            case PureExpression pureCondition     ->
                compileConditionOnValueParentWithPureCondition(valueParent, pureCondition, context);
            case StreamExpression streamCondition ->
                compileConditionOnValueParentWithStreamCondition(valueParent, streamCondition);
            };
        }
        case PureExpression pureParent     -> switch (compiledConditionExpression) {
                                       case Value valueCondition                 ->
                                           compileConditionOnPureParentWithValueCondition(pureParent, valueCondition);
                                       case PureExpression pureCondition         ->
                                           compileConditionOnPureParentWithPureCondition(pureParent, pureCondition);
                                       case StreamExpression streamCondition     ->
                                           compileConditionOnPureParentWithStreamCondition(pureParent, streamCondition);
                                       };
        case StreamExpression streamParent -> switch (compiledConditionExpression) {
                                       case Value valueCondition                 ->
                                           compileConditionOnStreamParentWithValueCondition(streamParent,
                                                   valueCondition);
                                       case PureExpression pureCondition         ->
                                           compileConditionOnStreamParentWithPureCondition(streamParent, pureCondition);
                                       case StreamExpression streamCondition     ->
                                           compileConditionOnStreamParentWithStreamCondition(streamParent,
                                                   streamCondition);
                                       };
        };
    }

    // ============================================================================
    // ConditionStep -> Parent instanceof Value
    // ============================================================================

    // Value parent - Value condition

    /**
     * Evaluates a condition step on a constant value parent with a constant boolean
     * condition. Returns the parent if
     * condition is true, or an appropriate empty container or undefined.
     *
     * @param valueParent
     * the constant parent value
     * @param valueCondition
     * the constant condition value
     *
     * @return the filtered value or error if condition is not boolean
     */
    private Value evaluateConditionOnValueParentWithConstantValueCondition(Value valueParent, Value valueCondition) {
        if (valueParent instanceof ErrorValue || valueParent instanceof UndefinedValue) {
            return valueParent;
        }
        if (!(valueCondition instanceof BooleanValue)) {
            return Value.error(
                    "Condition in condition step must evaluate to a Boolean, but got: %s.".formatted(valueCondition));
        }
        if (valueCondition.equals(Value.TRUE)) {
            return valueParent;
        }
        return switch (valueParent) {
        case ObjectValue o -> Value.EMPTY_OBJECT;
        case ArrayValue a  -> Value.EMPTY_ARRAY;
        default            -> Value.UNDEFINED;
        };
    }

    // Value parent - Pure Condition

    /**
     * Compiles a condition step on a constant value parent with a pure condition
     * expression. Applies immediate constant
     * folding if condition is not subscription-scoped.
     *
     * @param valueParent
     * the constant parent value
     * @param pureCondition
     * the pure condition expression
     * @param context
     * the compilation context
     *
     * @return the compiled filtered expression
     */
    private CompiledExpression compileConditionOnValueParentWithPureCondition(Value valueParent,
            PureExpression pureCondition, CompilationContext context) {
        val pureResult = switch (valueParent) {
        case ArrayValue arrayParent   ->
            compileConditionStepOnArrayValueConstantWithPureCondition(arrayParent, pureCondition);
        case ObjectValue objectParent ->
            compileConditionStepOnObjectValueConstantWithPureCondition(objectParent, pureCondition);
        case Value scalarValue        ->
            compileConditionStepOnScalarValueConstantWithPureCondition(scalarValue, pureCondition);
        };
        if (pureResult.isSubscriptionScoped()) {
            return pureResult;
        }
        return pureResult.evaluate(temporaryRelativeFoldingEvaluationContext(context));
    }

    /**
     * Creates a temporary evaluation context for folding relative expressions at
     * compile time. Contains only the
     * function broker with no subscription variables.
     *
     * @param compilationContext
     * the compilation context providing the function broker
     *
     * @return an evaluation context for compile-time folding
     */
    private EvaluationContext temporaryRelativeFoldingEvaluationContext(CompilationContext compilationContext) {
        return new EvaluationContext("", "", "", null, compilationContext.getFunctionBroker(),
                compilationContext.getAttributeBroker());
    }

    /**
     * Compiles a condition step on a scalar value with a pure condition. Creates a
     * pure expression that evaluates the
     * condition with the scalar as relative value.
     *
     * @param scalarParent
     * the scalar parent value
     * @param pureCondition
     * the pure condition expression
     *
     * @return a pure expression evaluating the filtered scalar
     */
    private PureExpression compileConditionStepOnScalarValueConstantWithPureCondition(Value scalarParent,
            PureExpression pureCondition) {
        return new PureExpression(
                ctx -> returnValueIfConditionMetElseUndefined(scalarParent,
                        pureCondition.evaluate(ctx.withRelativeValue(scalarParent))),
                pureCondition.isSubscriptionScoped());
    }

    /**
     * Returns the value if the condition is true, otherwise returns undefined.
     * Validates that the condition evaluates
     * to a boolean value.
     *
     * @param value
     * the value to potentially return
     * @param condition
     * the condition result
     *
     * @return the value if condition is true, undefined if false, or error if
     * condition is not boolean
     */
    private Value returnValueIfConditionMetElseUndefined(Value value, Value condition) {
        if (value instanceof ErrorValue || value instanceof UndefinedValue) {
            return value;
        }
        if (!(condition instanceof BooleanValue booleanConstant)) {
            return Value.error(
                    "Type mismatch error. Conditions in condition steps must evaluate to a boolean value, but got: %s."
                            .formatted(condition));
        }
        return booleanConstant.equals(Value.TRUE) ? value : Value.UNDEFINED;
    }

    /**
     * Compiles a condition step on an array value with a pure condition. Creates a
     * pure expression that filters array
     * elements based on condition evaluation.
     *
     * @param arrayParent
     * the array parent value
     * @param pureCondition
     * the pure condition expression
     *
     * @return a pure expression building the filtered array
     */
    private PureExpression compileConditionStepOnArrayValueConstantWithPureCondition(ArrayValue arrayParent,
            PureExpression pureCondition) {
        return new PureExpression(
                ctx -> evaluateConditionStepOnArrayValueConstantWithPureCondition(ctx, arrayParent, pureCondition),
                pureCondition.isSubscriptionScoped());
    }

    /**
     * Evaluates a condition step on an array value by filtering elements where the
     * condition evaluates to true. Each
     * element is set as the relative value with its index as relative location.
     *
     * @param ctx
     * the evaluation context
     * @param arrayParent
     * the array to filter
     * @param pureCondition
     * the condition to evaluate per element
     *
     * @return the filtered array or error if condition evaluation fails
     */
    private Value evaluateConditionStepOnArrayValueConstantWithPureCondition(EvaluationContext ctx,
            ArrayValue arrayParent, PureExpression pureCondition) {
        val array = ArrayValue.builder();
        for (int i = 0; i < arrayParent.size(); i++) {
            val originalValue = arrayParent.get(i);
            val condition     = pureCondition.evaluate(ctx.withRelativeValue(originalValue, Value.of(i)));
            val newEntry      = returnValueIfConditionMetElseUndefined(originalValue, condition);
            if (newEntry instanceof ErrorValue) {
                return newEntry;
            }
            if (!(newEntry instanceof UndefinedValue)) {
                array.add(newEntry);
            }
        }
        return array.build();
    }

    /**
     * Compiles a condition step on an object value with a pure condition. Creates a
     * pure expression that filters object
     * properties based on condition evaluation.
     *
     * @param objectParent
     * the object parent value
     * @param pureCondition
     * the pure condition expression
     *
     * @return a pure expression building the filtered object
     */
    private PureExpression compileConditionStepOnObjectValueConstantWithPureCondition(ObjectValue objectParent,
            PureExpression pureCondition) {
        return new PureExpression(
                ctx -> evaluateConditionStepOnObjectValueConstantWithPureCondition(ctx, objectParent, pureCondition),
                pureCondition.isSubscriptionScoped());
    }

    /**
     * Evaluates a condition step on an object value by filtering properties where
     * the condition evaluates to true. Each
     * property value is set as the relative value with its key as relative
     * location.
     *
     * @param ctx
     * the evaluation context
     * @param objectParent
     * the object to filter
     * @param pureCondition
     * the condition to evaluate per property
     *
     * @return the filtered object or error if condition evaluation fails
     */
    private Value evaluateConditionStepOnObjectValueConstantWithPureCondition(EvaluationContext ctx,
            ObjectValue objectParent, PureExpression pureCondition) {
        val object = ObjectValue.builder();
        for (val entry : objectParent.entrySet()) {
            val key           = entry.getKey();
            val originalValue = entry.getValue();
            val condition     = pureCondition.evaluate(ctx.withRelativeValue(originalValue, Value.of(key)));
            val newEntry      = returnValueIfConditionMetElseUndefined(originalValue, condition);
            if (newEntry instanceof ErrorValue) {
                return newEntry;
            }
            if (!(newEntry instanceof UndefinedValue)) {
                object.put(key, newEntry);
            }
        }
        return object.build();
    }

    // Value parent - Stream condition

    /**
     * Compiles a condition step on a constant value parent with a stream condition.
     * Creates a stream expression that
     * evaluates the condition reactively.
     *
     * @param valueParent
     * the constant parent value
     * @param streamCondition
     * the stream condition expression
     *
     * @return a stream expression with the filtered value
     */
    private CompiledExpression compileConditionOnValueParentWithStreamCondition(Value valueParent,
            StreamExpression streamCondition) {
        return new StreamExpression(Flux.just(valueParent).flatMap(
                value -> evaluateConditionStepWithStreamConditionOnConstantValue(value, streamCondition.stream())));
    }

    /**
     * Evaluates a condition step with a streaming condition on a constant parent
     * value. Dispatches to specialized
     * handlers based on parent value type.
     *
     * @param parentValue
     * the parent value to filter
     * @param conditionStream
     * the condition stream
     *
     * @return a flux emitting filtered results
     */
    private Flux<Value> evaluateConditionStepWithStreamConditionOnConstantValue(Value parentValue,
            Flux<Value> conditionStream) {
        if (parentValue instanceof ErrorValue || parentValue instanceof UndefinedValue) {
            return Flux.just(parentValue);
        }
        return switch (parentValue) {
        case ObjectValue objectParent -> evaluateStreamConditionStepOnObjectValue(objectParent, conditionStream);
        case ArrayValue arrayParent   -> evaluateStreamConditionStepOnArrayValue(arrayParent, conditionStream);
        case Value scalarValue        -> setRelativeValueContext(conditionStream, scalarValue)
                .map(conditionValue -> returnValueIfConditionMetElseUndefined(scalarValue, conditionValue));
        };
    }

    /**
     * Evaluates a stream condition step on an object value. Creates streams for
     * each property evaluation and combines
     * them into filtered objects.
     *
     * @param objectParent
     * the object to filter
     * @param conditionStream
     * the condition stream
     *
     * @return a flux of filtered objects
     */
    private Flux<Value> evaluateStreamConditionStepOnObjectValue(ObjectValue objectParent,
            Flux<Value> conditionStream) {
        val sources = new ArrayList<Flux<ObjectEntry>>(objectParent.size());
        for (val entry : objectParent.entrySet()) {
            val key               = entry.getKey();
            val relativeLocation  = Value.of(key);
            val relativeValue     = entry.getValue();
            val objectEntryStream = setRelativeValueContext(conditionStream, relativeValue, relativeLocation)
                    .map(conditionValue -> returnValueIfConditionMetElseUndefined(relativeValue, conditionValue))
                    .map(filteredValue -> new ObjectEntry(key, filteredValue));
            sources.add(objectEntryStream);
        }
        return Flux.combineLatest(sources, ExpressionCompiler::assembleObjectValue);
    }

    /**
     * Evaluates a stream condition step on an array value. Creates streams for each
     * element evaluation and combines
     * them into filtered arrays.
     *
     * @param arrayParent
     * the array to filter
     * @param conditionStream
     * the condition stream
     *
     * @return a flux of filtered arrays
     */
    private Flux<Value> evaluateStreamConditionStepOnArrayValue(ArrayValue arrayParent, Flux<Value> conditionStream) {
        val sources = new ArrayList<Flux<Value>>(arrayParent.size());
        for (var i = 0; i < arrayParent.size(); i++) {
            val relativeLocation = Value.of(i);
            val relativeValue    = arrayParent.get(i);
            val elementStream    = setRelativeValueContext(conditionStream, relativeValue, relativeLocation)
                    .map(conditionValue -> returnValueIfConditionMetElseUndefined(relativeValue, conditionValue));
            sources.add(elementStream);
        }
        return Flux.combineLatest(sources, ExpressionCompiler::assembleArrayValue);
    }

    // ============================================================================
    // ConditionStep -> Parent instanceof PureExpression
    // ============================================================================

    // Pure parent - Value condition

    /**
     * Compiles a condition step on a pure parent with a constant value condition.
     * Evaluates the parent at runtime and
     * applies the constant condition.
     *
     * @param pureParent
     * the pure parent expression
     * @param valueCondition
     * the constant condition value
     *
     * @return a pure expression evaluating the filtered parent
     */
    private CompiledExpression compileConditionOnPureParentWithValueCondition(PureExpression pureParent,
            Value valueCondition) {
        return new PureExpression(ctx -> evaluateConditionOnValueParentWithConstantValueCondition(
                pureParent.evaluate(ctx), valueCondition), pureParent.isSubscriptionScoped());
    }

    // Pure parent - Pure condition

    /**
     * Compiles a condition step on a pure parent with a pure condition. Creates a
     * pure expression that evaluates both
     * parent and condition, then filters accordingly.
     *
     * @param pureParent
     * the pure parent expression
     * @param pureCondition
     * the pure condition expression
     *
     * @return a pure expression evaluating the filtered result
     */
    private CompiledExpression compileConditionOnPureParentWithPureCondition(PureExpression pureParent,
            PureExpression pureCondition) {

        return new PureExpression(ctx -> {
            val valueParent = pureParent.evaluate(ctx);
            if (valueParent instanceof ErrorValue || valueParent instanceof UndefinedValue) {
                return valueParent;
            }
            return switch (valueParent) {
            case ArrayValue arrayParent   ->
                evaluateConditionStepOnArrayValueConstantWithPureCondition(ctx, arrayParent, pureCondition);
            case ObjectValue objectParent ->
                evaluateConditionStepOnObjectValueConstantWithPureCondition(ctx, objectParent, pureCondition);
            case Value scalarValue        -> returnValueIfConditionMetElseUndefined(scalarValue,
                    pureCondition.evaluate(ctx.withRelativeValue(scalarValue)));
            };
        }, pureParent.isSubscriptionScoped() || pureCondition.isSubscriptionScoped());
    }

    // Pure parent - Stream condition

    /**
     * Compiles a condition step on a pure parent with a stream condition. Creates a
     * stream that evaluates the parent
     * and applies the streaming condition.
     *
     * @param pureParent
     * the pure parent expression
     * @param streamCondition
     * the stream condition expression
     *
     * @return a stream expression with filtered results
     */
    private CompiledExpression compileConditionOnPureParentWithStreamCondition(PureExpression pureParent,
            StreamExpression streamCondition) {
        return new StreamExpression(pureParent.flux()
                .flatMap(parentValue -> evaluateConditionStepWithStreamConditionOnConstantValue(parentValue,
                        streamCondition.stream())));
    }

    // ============================================================================
    // ConditionStep -> Parent instanceof StreamExpression
    // ============================================================================

    // Stream parent - Value condition

    /**
     * Compiles a condition step on a stream parent with a constant value condition.
     * Applies the constant condition to
     * each emitted parent value.
     *
     * @param streamParent
     * the stream parent expression
     * @param valueCondition
     * the constant condition value
     *
     * @return a stream expression with filtered values
     */
    private CompiledExpression compileConditionOnStreamParentWithValueCondition(StreamExpression streamParent,
            Value valueCondition) {
        return new StreamExpression(streamParent.stream().map(
                parentValue -> evaluateConditionOnValueParentWithConstantValueCondition(parentValue, valueCondition)));
    }

    // Stream parent - Pure condition

    /**
     * Compiles a condition step on a stream parent with a pure condition. Evaluates
     * the pure condition for each emitted
     * parent value.
     *
     * @param streamParent
     * the stream parent expression
     * @param pureCondition
     * the pure condition expression
     *
     * @return a stream expression with filtered values
     */
    private CompiledExpression compileConditionOnStreamParentWithPureCondition(StreamExpression streamParent,
            PureExpression pureCondition) {
        return new StreamExpression(streamParent.stream().flatMap(valueParent -> {
            if (valueParent instanceof ErrorValue || valueParent instanceof UndefinedValue) {
                return Mono.just(valueParent);
            }
            return Mono.deferContextual(reactiveCtx -> {
                val ctx    = reactiveCtx.get(EvaluationContext.class);
                val result = switch (valueParent) {
                           case ArrayValue arrayParent   -> evaluateConditionStepOnArrayValueConstantWithPureCondition(
                                   ctx, arrayParent, pureCondition);
                           case ObjectValue objectParent -> evaluateConditionStepOnObjectValueConstantWithPureCondition(
                                   ctx, objectParent, pureCondition);
                           case Value scalarValue        -> returnValueIfConditionMetElseUndefined(scalarValue,
                                   pureCondition.evaluate(ctx.withRelativeValue(scalarValue)));
                           };
                return Mono.just(result);
            });
        }));
    }

    // Stream parent - Stream condition

    /**
     * Compiles a condition step on a stream parent with a stream condition. Creates
     * a stream that applies the streaming
     * condition to each parent value.
     *
     * @param streamParent
     * the stream parent expression
     * @param streamCondition
     * the stream condition expression
     *
     * @return a stream expression with filtered values
     */
    private CompiledExpression compileConditionOnStreamParentWithStreamCondition(StreamExpression streamParent,
            StreamExpression streamCondition) {
        return new StreamExpression(streamParent.stream()
                .flatMap(parentValue -> evaluateConditionStepWithStreamConditionOnConstantValue(parentValue,
                        streamCondition.stream())));
    }

    // ============================================================================
    // Array composition
    // ============================================================================

    /**
     * Composes an array expression by compiling its items and delegating to the
     * appropriate array builder.
     *
     * @param array
     * the array AST node
     * @param context
     * the compilation context
     *
     * @return the compiled array expression, or empty array if no items
     */
    private CompiledExpression composeArray(Array array, CompilationContext context) {
        val items = array.getItems();
        if (items.isEmpty()) {
            return Value.EMPTY_ARRAY;
        }
        return compileArgumentsToArray(compileArguments(items, context));
    }

    /**
     * Dispatches compiled array arguments to the appropriate builder based on their
     * nature.
     *
     * @param compiledArguments
     * the compiled array element arguments
     *
     * @return the compiled array expression
     */
    private CompiledExpression compileArgumentsToArray(CompiledArguments compiledArguments) {
        return switch (compiledArguments.nature()) {
        case VALUE  -> assembleArrayValue(compiledArguments.arguments());
        case PURE   -> compilePureArray(compiledArguments);
        case STREAM -> compileArrayStreamExpression(compiledArguments);
        };
    }

    /**
     * Compiles expression arguments into a structured representation tracking
     * whether arguments are values, pure
     * expressions, or streams.
     *
     * @param arguments
     * the argument expression AST nodes
     * @param context
     * the compilation context
     *
     * @return the compiled arguments with nature classification
     */
    CompiledArguments compileArguments(List<Expression> arguments, CompilationContext context) {
        val compiledArguments    = new CompiledExpression[arguments.size()];
        var isPure               = false;
        var isStream             = false;
        var isSubscriptionScoped = false;
        for (int i = 0; i < arguments.size(); i++) {
            val compiledArgument = compileExpression(arguments.get(i), context);
            if (compiledArgument instanceof PureExpression pureExpression) {
                isPure = true;
                if (pureExpression.isSubscriptionScoped()) {
                    isSubscriptionScoped = true;
                }
            } else if (compiledArgument instanceof StreamExpression) {
                isStream = true;
            }
            compiledArguments[i] = compiledArgument;
        }
        var nature = Nature.VALUE;
        if (isStream) {
            nature = Nature.STREAM;
        } else if (isPure) {
            nature = Nature.PURE;
        }
        return new CompiledArguments(nature, isSubscriptionScoped, compiledArguments);
    }

    /**
     * Assembles an array value from element expressions. Used both at compile time
     * for constant folding and at runtime
     * during stream evaluation.
     *
     * @param arguments
     * the array element expressions (must be Values)
     *
     * @return the assembled array value, or error if any element is an error
     */
    private Value assembleArrayValue(java.lang.Object[] arguments) {
        val arrayBuilder = ArrayValue.builder();
        for (val argument : arguments) {
            if (argument instanceof ErrorValue errorValue) {
                return errorValue;
            }
            if (argument instanceof Value value && !(value instanceof UndefinedValue)) {
                arrayBuilder.add(value);
            }
        }
        return arrayBuilder.build();
    }

    /**
     * Compiles an array with pure element expressions. Creates a pure expression
     * that evaluates all elements and builds
     * the array at evaluation time.
     *
     * @param arguments
     * the compiled array element arguments
     *
     * @return a pure expression that builds the array
     */
    private CompiledExpression compilePureArray(CompiledArguments arguments) {
        return new PureExpression(ctx -> {
            val arrayBuilder = ArrayValue.builder();
            for (val argument : arguments.arguments()) {
                val evaluatedArgument = (argument instanceof PureExpression pureExpression)
                        ? pureExpression.evaluate(ctx)
                        : argument;
                if (evaluatedArgument instanceof ErrorValue errorValue) {
                    return errorValue;
                }
                if (evaluatedArgument instanceof Value value && !(value instanceof UndefinedValue)) {
                    arrayBuilder.add(value);
                }
            }
            return arrayBuilder.build();
        }, arguments.isSubscriptionScoped());
    }

    /**
     * Compiles an array with stream element expressions. Creates a stream
     * expression that combines element streams and
     * assembles arrays from each combination at runtime.
     *
     * @param arguments
     * the compiled array element arguments
     *
     * @return a stream expression that emits arrays
     */
    private CompiledExpression compileArrayStreamExpression(CompiledArguments arguments) {
        val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux).toList();
        val stream  = Flux.combineLatest(sources, ExpressionCompiler::assembleArrayValue);
        return new StreamExpression(stream);
    }

    // ============================================================================
    // Object composition
    // ============================================================================

    /**
     * Composes an object expression by compiling its members and delegating to the
     * appropriate object builder.
     *
     * @param object
     * the object AST node
     * @param context
     * the compilation context
     *
     * @return the compiled object expression, or empty object if no members
     */
    private CompiledExpression composeObject(Object object, CompilationContext context) {
        val members = object.getMembers();
        if (members.isEmpty()) {
            return Value.EMPTY_OBJECT;
        }
        return compileAttributesToObject(compileAttributes(members, context));
    }

    /**
     * Dispatches compiled object attributes to the appropriate builder based on
     * their nature.
     *
     * @param attributes
     * the compiled object attributes
     *
     * @return the compiled object expression
     */
    private CompiledExpression compileAttributesToObject(CompiledObjectAttributes attributes) {
        return switch (attributes.nature()) {
        case VALUE  -> assembleObjectValue(attributes);
        case PURE   -> compilePureObject(attributes);
        case STREAM -> compileObjectStreamExpression(attributes);
        };
    }

    /**
     * Record representing an object key-value entry.
     */
    private record ObjectEntry(String key, Value value) {}

    /**
     * Assembles an object value from property entries. Used both at compile time
     * for constant folding and at runtime
     * during stream evaluation.
     *
     * @param attributes
     * the object property entries
     *
     * @return the assembled object value, or error if any property value is an
     * error
     */
    private Value assembleObjectValue(java.lang.Object[] attributes) {
        val objectBuilder = ObjectValue.builder();
        for (val attribute : attributes) {
            val entry = (ObjectEntry) attribute;
            val value = entry.value;
            val key   = entry.key;
            if (value instanceof ErrorValue errorValue) {
                return errorValue;
            }
            if (!(value instanceof UndefinedValue)) {
                objectBuilder.put(key, value);
            }
        }
        return objectBuilder.build();
    }

    /**
     * Compiles an object with pure property expressions. Creates a pure expression
     * that evaluates all properties and
     * builds the object at evaluation time.
     *
     * @param attributes
     * the compiled object attributes
     *
     * @return a pure expression that builds the object
     */
    private CompiledExpression compilePureObject(CompiledObjectAttributes attributes) {
        return new PureExpression(ctx -> {
            val objectBuilder = ObjectValue.builder();
            for (val attribute : attributes.attributes().entrySet()) {
                val key                = attribute.getKey();
                val compiledAttribute  = attribute.getValue();
                val evaluatedAttribute = (compiledAttribute instanceof PureExpression pureExpression)
                        ? pureExpression.evaluate(ctx)
                        : compiledAttribute;
                if (evaluatedAttribute instanceof ErrorValue errorValue) {
                    return errorValue;
                }
                if (evaluatedAttribute instanceof Value value && !(value instanceof UndefinedValue)) {
                    objectBuilder.put(key, value);
                }
            }
            return objectBuilder.build();
        }, attributes.isSubscriptionScoped());
    }

    /**
     * Compiles an object with stream property expressions. Creates a stream
     * expression that combines property streams
     * and assembles objects from each combination at runtime.
     *
     * @param attributes
     * the compiled object attributes
     *
     * @return a stream expression that emits objects
     */
    private CompiledExpression compileObjectStreamExpression(CompiledObjectAttributes attributes) {
        val sources = new ArrayList<Flux<ObjectEntry>>(attributes.attributes().size());
        for (val entry : attributes.attributes().entrySet()) {
            sources.add(
                    compiledExpressionToFlux(entry.getValue()).map(value -> new ObjectEntry(entry.getKey(), value)));
        }
        val stream = Flux.combineLatest(sources, ExpressionCompiler::assembleObjectValue);
        return new StreamExpression(stream);
    }

    /**
     * Assembles an object value from compiled attribute expressions. Used both at
     * compile time for constant folding and
     * at runtime during stream evaluation.
     *
     * @param attributes
     * the compiled object attributes containing only Values
     *
     * @return the assembled object value, or error if any attribute is an error
     *
     * @throws SaplCompilerException
     * if attributes nature is not VALUE
     */
    private Value assembleObjectValue(CompiledObjectAttributes attributes) {
        if (attributes.nature() != Nature.VALUE) {
            throw new SaplCompilerException(String.format(ERROR_ASSEMBLE_OBJECT_NON_VALUE_NATURE, attributes.nature()));
        }
        val objectBuilder = ObjectValue.builder();
        for (val attribute : attributes.attributes().entrySet()) {
            val key   = attribute.getKey();
            val value = attribute.getValue();
            if (value instanceof ErrorValue errorValue) {
                return errorValue;
            }
            if (value instanceof Value v && !(value instanceof UndefinedValue)) {
                objectBuilder.put(key, v);
            }
        }
        return objectBuilder.build();
    }

    /**
     * Compiles object member expressions into a structured representation tracking
     * whether members are values, pure
     * expressions, or streams.
     *
     * @param members
     * the object member AST nodes
     * @param context
     * the compilation context
     *
     * @return the compiled object attributes with nature classification
     */
    private CompiledObjectAttributes compileAttributes(List<Pair> members, CompilationContext context) {
        if (members == null || members.isEmpty()) {
            return CompiledObjectAttributes.EMPTY_ATTRIBUTES;
        }
        val compiledArguments    = HashMap.<String, CompiledExpression>newHashMap(members.size());
        var isStream             = false;
        var isPure               = false;
        var isSubscriptionScoped = false;
        for (Pair pair : members) {
            val compiledAttribute = compileExpression(pair.getValue(), context);
            if (compiledAttribute instanceof PureExpression pureExpression) {
                isPure = true;
                if (pureExpression.isSubscriptionScoped()) {
                    isSubscriptionScoped = true;
                }
            } else if (compiledAttribute instanceof StreamExpression) {
                isStream = true;
            }
            compiledArguments.put(pair.getKey(), compiledAttribute);
        }
        var nature = Nature.VALUE;
        if (isStream) {
            nature = Nature.STREAM;
        } else if (isPure) {
            nature = Nature.PURE;
        }
        return new CompiledObjectAttributes(nature, isSubscriptionScoped, compiledArguments);
    }

    // ============================================================================
    // ADAPTERS & UTILITIES
    // ============================================================================

    /**
     * Converts any compiled expression into a Flux stream. Values become
     * single-item streams, stream expressions expose
     * their internal stream, and pure expressions are deferred for evaluation.
     *
     * @param expression
     * the compiled expression to convert
     *
     * @return a Flux stream emitting the expression's values
     */
    Flux<Value> compiledExpressionToFlux(CompiledExpression expression) {
        return switch (expression) {
        case Value value                          -> Flux.just(value);
        case StreamExpression(Flux<Value> stream) -> stream;
        case PureExpression pureExpression        -> pureExpression.flux();
        };
    }

    /**
     * Propagates the current reactive EvaluationContext while overriding the
     * {@code RELATIVE_VALUE} variable for
     * downstream operators. Sets relative location to {@link Value#UNDEFINED}.
     *
     * @param original
     * the original stream to enrich with a modified EvaluationContext
     * @param relativeValue
     * the value to expose as {@code RELATIVE_VALUE} in the evaluation context
     *
     * @return a Flux with EvaluationContext where {@code RELATIVE_VALUE} is set to
     * {@code relativeValue} and
     * {@code RELATIVE_LOCATION} is undefined
     */
    private Flux<Value> setRelativeValueContext(Flux<Value> original, Value relativeValue) {
        return setRelativeValueContext(original, relativeValue, Value.UNDEFINED);
    }

    /**
     * Propagates the current reactive EvaluationContext while overriding the
     * {@code RELATIVE_VALUE} and
     * {@code RELATIVE_LOCATION} variables for downstream operators. Retrieves the
     * existing context from Reactor context
     * and creates a new instance with the supplied relative values.
     *
     * @param original
     * the original stream to enrich with a modified EvaluationContext
     * @param relativeValue
     * the value to expose as {@code RELATIVE_VALUE} in the evaluation context
     * @param relativeLocation
     * the value to expose as {@code RELATIVE_LOCATION} in the evaluation context
     *
     * @return a Flux with EvaluationContext where {@code RELATIVE_VALUE} and
     * {@code RELATIVE_LOCATION} are set to the
     * given arguments
     */
    private Flux<Value> setRelativeValueContext(Flux<Value> original, Value relativeValue, Value relativeLocation) {
        return original.contextWrite(ctx -> {
            val evaluationContext = ctx.get(EvaluationContext.class);
            return ctx.put(EvaluationContext.class,
                    evaluationContext.withRelativeValue(relativeValue, relativeLocation));
        });
    }
}
