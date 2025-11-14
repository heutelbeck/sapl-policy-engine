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
import org.eclipse.emf.common.util.EList;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Compiles SAPL abstract syntax tree expressions into optimized executable
 * representations. Performs compile-time constant folding and type-based
 * optimization to generate efficient evaluation code.
 */
@UtilityClass
public class ExpressionCompiler {

    private static final Value UNIMPLEMENTED = Value.error("unimplemented");

    /**
     * Compiles a SAPL expression from the abstract syntax tree into an optimized
     * executable form. Dispatches to specialized compilation methods based on
     * expression type.
     *
     * @param expression the AST expression to compile
     * @param context the compilation context containing variables and function
     *            broker
     * @return the compiled expression ready for evaluation, or null if input is
     *         null
     */
    public CompiledExpression compileExpression(Expression expression, CompilationContext context) {
        if (expression == null) {
            return null;
        }
        return switch (expression) {
            case Or or -> compileBinaryOperation(or, BooleanOperators::or, context); // TODO add lazy !
            case EagerOr eagerOr -> compileBinaryOperation(eagerOr, BooleanOperators::or, context);
            case XOr xor -> compileBinaryOperation(xor, BooleanOperators::xor, context);
            case And and -> compileBinaryOperation(and, BooleanOperators::and, context); // TODO add lazy !
            case EagerAnd eagerAnd -> compileBinaryOperation(eagerAnd, BooleanOperators::and, context);
            case Not not -> compileUnaryOperation(not, BooleanOperators::not, context);
            case Multi multi -> compileBinaryOperation(multi, NumberOperators::multiply, context);
            case Div div -> compileBinaryOperation(div, NumberOperators::divide, context);
            case Modulo modulo -> compileBinaryOperation(modulo, NumberOperators::modulo, context);
            case Plus plus -> compileBinaryOperation(plus, NumberOperators::add, context);
            case Minus minus -> compileBinaryOperation(minus, NumberOperators::subtract, context);
            case Less less -> compileBinaryOperation(less, NumberOperators::lessThan, context);
            case LessEquals lessEquals -> compileBinaryOperation(lessEquals, NumberOperators::lessThanOrEqual, context);
            case More more -> compileBinaryOperation(more, NumberOperators::greaterThan, context);
            case MoreEquals moreEquals ->
                    compileBinaryOperation(moreEquals, NumberOperators::greaterThanOrEqual, context);
            case UnaryPlus unaryPlus -> compileUnaryOperation(unaryPlus, NumberOperators::unaryPlus, context);
            case UnaryMinus unaryMinus -> compileUnaryOperation(unaryMinus, NumberOperators::unaryMinus, context);
            case ElementOf elementOf -> compileBinaryOperation(elementOf, ComparisonOperators::isContainedIn, context);
            case Equals equals -> compileBinaryOperation(equals, ComparisonOperators::equals, context);
            case NotEquals notEquals -> compileBinaryOperation(notEquals, ComparisonOperators::notEquals, context);
            case Regex regex -> compileBinaryOperation(regex, ComparisonOperators::matchesRegularExpression, context);
            case BasicExpression basic -> compileBasicExpression(basic, context);
            default -> throw new SaplCompilerException("unexpected expression: " + expression + ".");
        };
    }

    /**
     * Compiles a binary operation by recursively compiling its operands and
     * combining them. Performs pre-compilation optimization for regex patterns when
     * the right operand is a constant.
     *
     * @param operator the binary operator AST node
     * @param operation the operation to apply to compiled operands
     * @param context the compilation context
     * @return the compiled binary operation expression
     */
    private CompiledExpression compileBinaryOperation(BinaryOperator operator,
                                                      java.util.function.BinaryOperator<Value> operation, CompilationContext context) {
        val left = compileExpression(operator.getLeft(), context);
        val right = compileExpression(operator.getRight(), context);
        // Special case for regex. Here if the right side is a text constant, we can
        // immediately pre-compile the expression and do not need to do it at policy
        // evaluation time. We do it here, to have a re-usable assembleBinaryOperation
        // method, as that is a pretty critical and complex method that then can be
        // re-used for assembling special cases of steps as well without repeating
        // 99% of the logic.
        if (right instanceof Value valRight && operator instanceof Regex) {
            val compiledRegex = ComparisonOperators.compileRegularExpressionOperation(valRight);
            operation = (l, ignoredBecauseWeUseTheCompiledRegex) -> compiledRegex.apply(l);
        }
        return assembleBinaryOperation(left, right, operation);
    }

    /**
     * Assembles two compiled expressions into a binary operation. Performs constant
     * folding when both operands are values. Creates optimized stream or pure
     * expression wrappers based on operand types.
     *
     * @param left the compiled left operand
     * @param right the compiled right operand
     * @param operation the binary operation to apply
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
            return compileBinaryStreamOperator(left, right, operation);
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
        throw new SaplCompilerException("Unexpected expression types. Should not be possible: "
                + left.getClass().getSimpleName() + " and " + right.getClass().getSimpleName() + ".");
    }

    /**
     * Compiles a binary operation involving at least one stream expression. Uses
     * Flux.combineLatest to reactively combine the latest values from both operands.
     *
     * @param leftExpression the left compiled expression
     * @param rightExpression the right compiled expression
     * @param operation the binary operation to apply
     * @return a stream expression that combines the operand streams
     */
    private StreamExpression compileBinaryStreamOperator(CompiledExpression leftExpression,
                                                         CompiledExpression rightExpression, java.util.function.BinaryOperator<Value> operation) {
        val stream = Flux.combineLatest(compiledExpressionToFlux(leftExpression),
                compiledExpressionToFlux(rightExpression), operation);
        return new StreamExpression(stream);
    }

    /**
     * Converts any compiled expression into a Flux stream. Values become single-item
     * streams, stream expressions expose their internal stream, and pure expressions
     * are deferred for evaluation.
     *
     * @param expression the compiled expression to convert
     * @return a Flux stream emitting the expression's values
     */
    private Flux<Value> compiledExpressionToFlux(CompiledExpression expression) {
        return switch (expression) {
            case Value value -> Flux.just(value);
            case StreamExpression stream -> stream.stream();
            case PureExpression pureExpression -> deferPureExpressionEvaluation(pureExpression);
        };
    }

    /**
     * Wraps a pure expression in a deferred Flux that will evaluate it when
     * subscribed, using the evaluation context from the Reactor context.
     *
     * @param expression the pure expression to defer
     * @return a Flux that evaluates the expression on subscription
     */
    private Flux<Value> deferPureExpressionEvaluation(PureExpression expression) {
        return Flux.deferContextual(ctx -> Flux.just(expression.evaluate(ctx.get(EvaluationContext.class))));
    }

    /**
     * Compiles a unary operation by compiling its operand and applying the operation.
     * Performs constant folding for value operands. Creates stream or pure expression
     * wrappers for deferred operands.
     *
     * @param operator the unary operator AST node
     * @param operation the unary operation to apply
     * @param context the compilation context
     * @return the compiled unary operation expression
     */
    private CompiledExpression compileUnaryOperation(UnaryOperator operator,
                                                     java.util.function.UnaryOperator<Value> operation, CompilationContext context) {
        val expression = compileExpression(operator.getExpression(), context);
        if (expression instanceof Value value) {
            return operation.apply(value);
        }
        if (expression instanceof StreamExpression(Flux<Value> stream)) {
            return new StreamExpression(stream.map(operation));
        }
        val subExpression = (PureExpression) expression;
        return new PureExpression(ctx -> operation.apply(subExpression.evaluate(ctx)),
                subExpression.isSubscriptionScoped());
    }

    /**
     * Compiles a basic expression by dispatching to the appropriate handler based on
     * the specific basic expression type.
     *
     * @param expression the basic expression AST node
     * @param context the compilation context
     * @return the compiled basic expression
     */
    private CompiledExpression compileBasicExpression(BasicExpression expression, CompilationContext context) {
        return switch (expression) {
            case BasicGroup group -> compileExpression(group.getExpression(), context);
            case BasicValue value -> compileValue(value, context);
            case BasicFunction function -> compileBasicFunction(function, context);
            case BasicEnvironmentAttribute envAttribute -> UNIMPLEMENTED;
            case BasicEnvironmentHeadAttribute envHeadAttribute -> UNIMPLEMENTED;
            case BasicIdentifier identifier -> compileIdentifier(identifier, context);
            case BasicRelative ignored -> compileBasicRelative(context);
            default -> throw new SaplCompilerException("unexpected expression: " + expression + ".");
        };
    }

    /**
     * Compiles a basic relative expression that references the relative value from
     * the evaluation context.
     *
     * @param context the compilation context
     * @return a pure expression that extracts the relative value
     */
    private static CompiledExpression compileBasicRelative(CompilationContext context) {
        return new PureExpression(EvaluationContext::relativeValue, false);
    }

    /**
     * Compiles a function call by compiling its arguments and dispatching to the
     * appropriate handler based on argument types.
     *
     * @param function the function AST node
     * @param context the compilation context
     * @return the compiled function call expression
     */
    private CompiledExpression compileBasicFunction(BasicFunction function, CompilationContext context) {
        if (context.isDynamicLibrariesEnabled()) {
            throw new SaplCompilerException(
                    "Dynamic function libraries are not supported in this version of the compiler.");
        }
        var arguments = CompiledArguments.EMPTY_ARGUMENTS;
        if (function.getArguments() != null && function.getArguments().getArgs() != null) {
            arguments = compileArguments(function.getArguments().getArgs(), context);
        }
        return switch (arguments.nature()) {
            case VALUE -> compileFunctionWithValueParameters(function, arguments.arguments(), context);
            case PURE -> compileFunctionWithPureParameters(function, arguments, context);
            case STREAM -> compileFunctionWithStreamParameters(function, arguments, context);
        };
    }

    /**
     * Compiles a function call with stream parameters. Creates a stream expression
     * that combines the argument streams and evaluates the function with each
     * combination.
     *
     * @param function the function AST node
     * @param arguments the compiled arguments
     * @param context the compilation context
     * @return a stream expression that evaluates the function reactively
     */
    private CompiledExpression compileFunctionWithStreamParameters(BasicFunction function, CompiledArguments arguments,
                                                                   CompilationContext context) {
        val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux).toList();
        val stream = Flux.<Value, Value>combineLatest(sources,
                combined -> compileFunctionWithValueParameters(function, (CompiledExpression[]) combined, context));
        return new StreamExpression(stream);
    }

    /**
     * Compiles a function call with pure parameters. Creates a pure expression that
     * evaluates all arguments and then invokes the function.
     *
     * @param function the function AST node
     * @param arguments the compiled arguments
     * @param context the compilation context
     * @return a pure expression that evaluates the function call
     */
    private CompiledExpression compileFunctionWithPureParameters(BasicFunction function, CompiledArguments arguments,
                                                                 CompilationContext context) {
        return new PureExpression(ctx -> {
            val valueArguments = new ArrayList<Value>(arguments.arguments().length);
            for (val argument : arguments.arguments()) {
                switch (argument) {
                    case Value value -> valueArguments.add(value);
                    case PureExpression pureExpression -> valueArguments.add(pureExpression.evaluate(ctx));
                    case StreamExpression ignored -> throw new SaplCompilerException(
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
     * Performs immediate function invocation for compile-time constant folding.
     * FIXME: Name suggests compilation but performs eager evaluation returning Value.
     * Options: (1) Rename to evaluateFunctionWithKnownParameters, (2) Keep name and
     * add comment, (3) Refactor to return CompiledExpression wrapper.
     *
     * @param function the function AST node
     * @param arguments the compiled value arguments
     * @param context the compilation context
     * @return the function evaluation result as a Value
     */
    private Value compileFunctionWithValueParameters(BasicFunction function, CompiledExpression[] arguments,
                                                     CompilationContext context) {
        val valueArguments = Arrays.stream(arguments).map(Value.class::cast).toList();
        val invocation = new FunctionInvocation(
                ImportResolver.resolveFunctionIdentifierByImports(function, function.getIdentifier()), valueArguments);
        return context.getFunctionBroker().evaluateFunction(invocation);
    }

    /**
     * Compiles an identifier reference by checking local scope first, then creating
     * a pure expression that retrieves the identifier from the evaluation context.
     *
     * @param identifier the identifier AST node
     * @param context the compilation context
     * @return the compiled identifier reference
     */
    private CompiledExpression compileIdentifier(BasicIdentifier identifier, CompilationContext context) {
        val variableIdentifier = identifier.getIdentifier();
        val maybeLocalVariable = context.localVariablesInScope.get(variableIdentifier);
        if (maybeLocalVariable != null) {
            return maybeLocalVariable;
        }
        return new PureExpression(ctx -> ctx.get(variableIdentifier), true);
    }

    /**
     * Compiles a value literal by extracting the value and compiling any subsequent
     * steps. Performs deduplication for constant values.
     *
     * @param basic the basic value AST node
     * @param context the compilation context
     * @return the compiled value expression
     */
    private CompiledExpression compileValue(BasicValue basic, CompilationContext context) {
        val value = basic.getValue();
        var compiledValue = switch (value) {
            case Object object -> composeObject(object, context);
            case Array array -> composeArray(array, context);
            case StringLiteral string -> Value.of(string.getString());
            case NumberLiteral number -> Value.of(number.getNumber());
            case TrueLiteral t -> Value.TRUE;
            case FalseLiteral f -> Value.FALSE;
            case NullLiteral nil -> Value.NULL;
            case UndefinedLiteral u -> Value.UNDEFINED;
            default -> throw new SaplCompilerException("unexpected value: " + value + ".");
        };

        compiledValue = compileSteps(compiledValue, basic.getSteps(), context);
        if (compiledValue instanceof Value constantValue) {
            return context.dedupe(constantValue);
        }
        return compiledValue;
    }

    /**
     * Compiles a chain of steps by sequentially applying each step to the result of
     * the previous step.
     *
     * @param expression the initial expression
     * @param steps the list of steps to apply
     * @param context the compilation context
     * @return the expression with all steps compiled and applied
     */
    private CompiledExpression compileSteps(CompiledExpression expression, EList<Step> steps,
                                            CompilationContext context) {
        if (steps == null || steps.isEmpty()) {
            return expression;
        }
        for (val step : steps) {
            expression = compileStep(expression, step, context);
        }
        return expression;
    }

    /**
     * Compiles a single step operation by dispatching to the appropriate step handler
     * based on step type.
     *
     * @param parent the parent expression to which the step is applied
     * @param step the step AST node
     * @param context the compilation context
     * @return the compiled step expression
     */
    private CompiledExpression compileStep(CompiledExpression parent, Step step, CompilationContext context) {
        return switch (step) {
            case KeyStep keyStep -> compileStep(parent, p -> StepOperators.keyStep(p, keyStep.getId()), context);
            case EscapedKeyStep escapedKeyStep ->
                    compileStep(parent, p -> StepOperators.keyStep(p, escapedKeyStep.getId()), context);
            case WildcardStep wildcardStep -> compileStep(parent, StepOperators::wildcardStep, context);
            case AttributeFinderStep attributeFinderStep -> UNIMPLEMENTED;
            case HeadAttributeFinderStep headAttributeFinderStep -> UNIMPLEMENTED;
            case RecursiveKeyStep recursiveKeyStep ->
                    compileStep(parent, p -> StepOperators.recursiveKeyStep(p, recursiveKeyStep.getId()), context);
            case RecursiveWildcardStep recursiveWildcardStep ->
                    compileStep(parent, StepOperators::recursiveWildcardStep, context);
            case RecursiveIndexStep recursiveIndexStep ->
                    compileStep(parent, p -> StepOperators.recursiveIndexStep(p, recursiveIndexStep.getIndex()), context);
            case IndexStep indexStep ->
                    compileStep(parent, p -> StepOperators.indexStep(p, indexStep.getIndex()), context);
            case ArraySlicingStep arraySlicingStep -> compileStep(parent, p -> StepOperators.sliceArray(p,
                    arraySlicingStep.getIndex(), arraySlicingStep.getTo(), arraySlicingStep.getStep()), context);
            case ExpressionStep expressionStep -> compileExpressionStep(parent, expressionStep, context);
            case ConditionStep conditionStep -> compileConditionStep(parent, conditionStep, context);
            case IndexUnionStep indexUnionStep ->
                    compileStep(parent, p -> StepOperators.indexUnion(p, indexUnionStep.getIndices()), context);
            case AttributeUnionStep attributeUnionStep ->
                    compileStep(parent, p -> StepOperators.attributeUnion(p, attributeUnionStep.getAttributes()), context);
            default -> UNIMPLEMENTED;
        };
    }

    /**
     * Compiles an expression step that uses a sub-expression to determine the index
     * or key for accessing the parent value.
     *
     * @param parentExpression the parent expression being accessed
     * @param expressionStep the expression step AST node
     * @param context the compilation context
     * @return the compiled expression step
     */
    private CompiledExpression compileExpressionStep(CompiledExpression parentExpression, ExpressionStep expressionStep,
                                                     CompilationContext context) {
        val expressionStepExpression = compileExpression(expressionStep.getExpression(), context);
        return assembleBinaryOperation(parentExpression, expressionStepExpression, StepOperators::indexOrKeyStep);
    }

    /**
     * Compiles a condition step by dispatching to the appropriate handler based on
     * the parent expression type.
     *
     * @param parent the parent expression to filter
     * @param expressionStep the condition step AST node
     * @param context the compilation context
     * @return the compiled condition step expression
     */
    private CompiledExpression compileConditionStep(CompiledExpression parent, ConditionStep expressionStep,
                                                    CompilationContext context) {
        return switch (parent) {
            case Value parentValue -> compileConditionStepOnValue(parentValue, expressionStep, context);
            case PureExpression pureParent -> compileConditionStepOnPureExpression(pureParent, expressionStep, context);
            case StreamExpression streamParent -> compileConditionStepOnStreamExpression(streamParent, expressionStep, context);
        };
    }

    /**
     * Compiles a condition step for a stream parent expression.
     *
     * @param streamParent the parent stream expression
     * @param expressionStep the condition step AST node
     * @param context the compilation context
     * @return the compiled condition step stream expression
     */
    private static CompiledExpression compileConditionStepOnStreamExpression(StreamExpression streamParent, ConditionStep expressionStep, CompilationContext context) {
        val compiledConditionExpression = compileExpression(expressionStep.getExpression(), context);

        return UNIMPLEMENTED;
    }

    /**
     * Compiles a condition step for a pure parent expression. Creates a pure
     * expression that evaluates the parent and condition, then filters based on the
     * condition result.
     * FIXME: Missing runtime array/object iteration logic for filtering collections.
     * When parent evaluates to ArrayValue/ObjectValue, should iterate elements and
     * filter, not treat as scalar.
     *
     * @param pureParent the parent pure expression
     * @param expressionStep the condition step AST node
     * @param context the compilation context
     * @return the compiled condition step expression
     */
    private CompiledExpression compileConditionStepOnPureExpression(PureExpression pureParent, ConditionStep expressionStep,
                                                                    CompilationContext context) {
        val compiledConditionExpression = compileExpression(expressionStep.getExpression(), context);
        return switch(compiledConditionExpression) {
            case Value conditionValue -> new PureExpression(ctx -> returnValueIfConditionMetElseUndefined(pureParent.evaluate(ctx), conditionValue ), pureParent.isSubscriptionScoped());
            case PureExpression pureCondition -> new PureExpression(ctx -> {
                val parentValue = pureParent.evaluate(ctx);
                val conditionValue = pureCondition.evaluate(ctx.withRelativeValue(parentValue, Value.UNDEFINED));
                return returnValueIfConditionMetElseUndefined(parentValue, conditionValue);
            }, pureParent.isSubscriptionScoped() || pureCondition.isSubscriptionScoped());
            case StreamExpression conditionStream ->  new StreamExpression(
                    Flux.<Value>deferContextual(ctx -> Flux.just(pureParent.evaluate(ctx.get(EvaluationContext.class))))
                            .flatMap(parentValue -> conditionStream.stream().contextWrite(ctx -> {
                                val evaluationContext = ctx.get(EvaluationContext.class);
                                return ctx.put(EvaluationContext.class,
                                        evaluationContext.withRelativeValue(parentValue,
                                                Value.UNDEFINED));
                            }).map(conditionValue -> returnValueIfConditionMetElseUndefined(parentValue, conditionValue)))
            );
        };
    }

    /**
     * Compiles a condition step for a known value parent. Handles early return for
     * errors and undefined values, then delegates to type-specific optimization.
     * FIXME: Name suggests compilation but performs compile-time folding/optimization.
     * Options: (1) Rename to foldConditionStepOnKnownValue to clarify, (2) Keep name
     * and document behavior, (3) Extract pure compilation logic separately.
     *
     * @param parent the parent value
     * @param expressionStep the condition step AST node
     * @param context the compilation context
     * @return the compiled condition step expression
     */
    private CompiledExpression compileConditionStepOnValue(Value parent, ConditionStep expressionStep,
                                                           CompilationContext context) {
        if (parent instanceof ErrorValue || parent instanceof UndefinedValue) {
            return parent;
        }
        val compiledConditionExpression = compileExpression(expressionStep.getExpression(), context);
        return optimizeConditionStepForKnownValue(parent, compiledConditionExpression, context);
    }

    /**
     * Optimizes a condition step when the parent value is known at compile time.
     * Dispatches to specialized handlers for arrays, objects, or scalar values to
     * build optimized evaluation code.
     *
     * @param parent the known parent value
     * @param compiledConditionExpression the compiled condition expression
     * @param context the compilation context
     * @return the optimized condition step expression
     */
    private CompiledExpression optimizeConditionStepForKnownValue(Value parent, CompiledExpression compiledConditionExpression,
                                                                  CompilationContext context) {
        return switch (parent) {
            case ArrayValue parentArray -> compileConditionStepForRelativeArray(parentArray, compiledConditionExpression, context);
            case ObjectValue parentObject -> compileConditionStepForRelativeObject(parentObject, compiledConditionExpression, context);
            case Value parentValue ->
                    compileConditionStepForRelativeValue(parentValue, Value.UNDEFINED, compiledConditionExpression, context);
        };
    }

    /**
     * Compiles a condition step for filtering an object's properties. Iterates over
     * entries at compile time, building optimized code for each property.
     * FIXME: Name suggests general compilation but performs compile-time iteration
     * over known object. Options: (1) Rename to foldConditionStepOverKnownObject,
     * (2) Document compile-time iteration behavior, (3) Extract iteration logic.
     *
     * @param relativeObject the object to filter
     * @param conditionExpression the compiled condition
     * @param context the compilation context
     * @return the compiled filtered object expression
     */
    private CompiledExpression compileConditionStepForRelativeObject(ObjectValue relativeObject,
                                                                     CompiledExpression conditionExpression,
                                                                     CompilationContext context) {
        if(relativeObject.isEmpty()) {
            return relativeObject;
        }
        val compiledArguments = new HashMap<String, CompiledExpression>(relativeObject.size());
        var isStream = false;
        var isPure = false;
        var isSubscriptionScoped = false;
        for (var pair : relativeObject.entrySet()) {
            val compiledAttribute = compileConditionStepForRelativeValue(pair.getValue(), Value.of(pair.getKey()), conditionExpression, context);
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
        val compiledObjectElementExpressions = new CompiledObjectAttributes(nature, isSubscriptionScoped, compiledArguments);
        return compileAttributesToObject(compiledObjectElementExpressions);
    }

    /**
     * Compiles a condition step for filtering an array's elements. Iterates over
     * elements at compile time, building optimized code for each element.
     * FIXME: Name suggests general compilation but performs compile-time iteration
     * over known array. Options: (1) Rename to foldConditionStepOverKnownArray,
     * (2) Document compile-time iteration behavior, (3) Extract iteration logic.
     *
     * @param relativeArray the array to filter
     * @param conditionExpression the compiled condition
     * @param context the compilation context
     * @return the compiled filtered array expression
     */
    private CompiledExpression compileConditionStepForRelativeArray(ArrayValue relativeArray,
                                                                    CompiledExpression conditionExpression,
                                                                    CompilationContext context) {
        if(relativeArray.isEmpty()) {
            return relativeArray;
        }
        val compiledArguments = new CompiledExpression[relativeArray.size()];
        var isPure = false;
        var isStream = false;
        var isSubscriptionScoped = false;
        for (int i = 0; i < relativeArray.size(); i++) {
            val compiledArgument = compileConditionStepForRelativeValue(relativeArray.get(i), Value.of(i), conditionExpression, context);
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
        val compiledArrayElementExpressions = new CompiledArguments(nature, isSubscriptionScoped, compiledArguments);
        return compileArgumentsToArray(compiledArrayElementExpressions);
    }

    /**
     * Dispatches compiled array arguments to the appropriate builder based on their
     * nature.
     *
     * @param compiledArguments the compiled array element arguments
     * @return the compiled array expression
     */
    private CompiledExpression compileArgumentsToArray(CompiledArguments compiledArguments) {
        return switch (compiledArguments.nature()) {
            case VALUE -> assembleArrayValue(compiledArguments.arguments());
            case PURE -> compilePureArray(compiledArguments);
            case STREAM -> compileArrayStreamExpression(compiledArguments);
        };
    }

    /**
     * Prepares a condition step for a single relative value. Handles both
     * compile-time evaluation when possible and deferred evaluation when needed.
     * FIXME: Complex hybrid behavior mixing compile-time evaluation with deferred
     * execution. Options: (1) Rename to prepareConditionStepForRelativeValue to
     * clarify hybrid nature, (2) Split into separate compile-time and runtime paths,
     * (3) Document the evaluation strategy clearly.
     *
     * @param relativeValue the value to test against the condition
     * @param relativeLocation the location of the value
     * @param conditionExpression the compiled condition
     * @param context the compilation context
     * @return the compiled condition test expression
     */
    private CompiledExpression compileConditionStepForRelativeValue(Value relativeValue, Value relativeLocation,
                                                                    CompiledExpression conditionExpression, CompilationContext context) {
        val relativeCondition = switch (conditionExpression) {
            case Value value -> value;

            case PureExpression pureConditionExpression when pureConditionExpression.isSubscriptionScoped() ->
                    new PureExpression(ctx ->
                            pureConditionExpression.evaluate(ctx.withRelativeValue(relativeValue, relativeLocation)), true);

            // This case is dealing with the situation, when the only thing that could not be folded at compile time of the
            // expression were the references to the relative value. But at this point that value is now known, and we can
            // evaluate the pure expression without the need for any other variables.
            case PureExpression pureConditionExpression -> pureConditionExpression.evaluate(
                    new EvaluationContext(Map.of(), context.getFunctionBroker()).withRelativeValue(relativeValue));

            case StreamExpression streamConditionExpression ->
                    new StreamExpression(streamConditionExpression.stream().contextWrite(ctx -> {
                        val evaluationContext = ctx.get(EvaluationContext.class);
                        return ctx.put(EvaluationContext.class,
                                evaluationContext.withRelativeValue(relativeValue,
                                        relativeLocation));
                    }));
        };
        return assembleBinaryOperation(relativeValue, relativeCondition,
                ExpressionCompiler::returnValueIfConditionMetElseUndefined);
    }

    /**
     * Returns the value if the condition is true, otherwise returns undefined.
     * Validates that the condition evaluates to a boolean value.
     *
     * @param value the value to potentially return
     * @param condition the condition result
     * @return the value if condition is true, undefined if false, or error if
     *         condition is not boolean
     */
    private Value returnValueIfConditionMetElseUndefined(Value value, Value condition) {
        if (!(condition instanceof BooleanValue booleanConstant)) {
            return Value.error(
                    "Type mismatch error. Conditions in condition steps must evaluate to a boolean value, but got: %s."
                            .formatted(condition));
        }
        return booleanConstant.equals(Value.TRUE) ? value : Value.UNDEFINED;
    }

    /**
     * Compiles a step operation on a parent expression by applying a unary operation.
     * Handles constant folding for value parents, and creates stream or pure
     * expression wrappers for deferred parents.
     *
     * @param parent the parent expression
     * @param operation the unary operation to apply
     * @param context the compilation context
     * @return the compiled step expression
     */
    private CompiledExpression compileStep(CompiledExpression parent, java.util.function.UnaryOperator<Value> operation,
                                           CompilationContext context) {
        return switch (parent) {
            case Value value -> operation.apply(value);
            case StreamExpression(Flux<Value> stream) -> new StreamExpression(stream.map(operation));
            case PureExpression pureParent ->
                    new PureExpression(ctx -> operation.apply(pureParent.evaluate(ctx)), pureParent.isSubscriptionScoped());
        };
    }

    /**
     * Composes an array expression by compiling its items and delegating to the
     * appropriate array builder.
     *
     * @param array the array AST node
     * @param context the compilation context
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
     * Assembles an array value from element expressions. Used both at compile time
     * for constant folding and at runtime during stream evaluation.
     *
     * @param arguments the array element expressions (must be Values)
     * @return the assembled array value, or error if any element is an error
     */
    private Value assembleArrayValue(CompiledExpression[] arguments) {
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
     * Compiles an array with pure element expressions. Creates a pure expression that
     * evaluates all elements and builds the array at evaluation time.
     *
     * @param arguments the compiled array element arguments
     * @return a pure expression that builds the array
     */
    private CompiledExpression compilePureArray(CompiledArguments arguments) {
        return new PureExpression(ctx -> {
            val arrayBuilder = ArrayValue.builder();
            for (val argument : arguments.arguments()) {
                // argument cannot be StreamExpression here!
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
     * Compiles an array with stream element expressions. Creates a stream expression
     * that combines element streams and assembles arrays from each combination at
     * runtime.
     *
     * @param arguments the compiled array element arguments
     * @return a stream expression that emits arrays
     */
    private CompiledExpression compileArrayStreamExpression(CompiledArguments arguments) {
        val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux).toList();
        val stream = Flux.<Value, Value>combineLatest(sources, combined -> assembleArrayValue((Value[]) combined));
        return new StreamExpression(stream);
    }

    /**
     * Composes an object expression by compiling its members and delegating to the
     * appropriate object builder.
     *
     * @param object the object AST node
     * @param context the compilation context
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
     * Dispatches compiled object attributes to the appropriate builder based on their
     * nature.
     *
     * @param attributes the compiled object attributes
     * @return the compiled object expression
     */
    private CompiledExpression compileAttributesToObject(CompiledObjectAttributes attributes) {
        return switch (attributes.nature()) {
            case VALUE -> assembleObjectValue(attributes);
            case PURE -> compilePureObject(attributes);
            case STREAM -> compileObjectStreamExpression(attributes);
        };
    }

    /**
     * Record representing an object key-value entry.
     */
    private record ObjectEntry(String key, Value value) {
    }

    /**
     * Assembles an object value from property entries. Used both at compile time for
     * constant folding and at runtime during stream evaluation.
     *
     * @param attributes the object property entries
     * @return the assembled object value, or error if any property value is an error
     */
    private Value assembleObjectValue(ObjectEntry[] attributes) {
        val objectBuilder = ObjectValue.builder();
        for (val attribute : attributes) {
            val value = attribute.value;
            val key = attribute.key;
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
     * that evaluates all properties and builds the object at evaluation time.
     *
     * @param attributes the compiled object attributes
     * @return a pure expression that builds the object
     */
    private CompiledExpression compilePureObject(CompiledObjectAttributes attributes) {
        return new PureExpression(ctx -> {
            val objectBuilder = ObjectValue.builder();
            for (val attribute : attributes.attributes().entrySet()) {
                val key = attribute.getKey();
                val compiledAttribute = attribute.getValue();
                // compiledAttribute cannot be a StreamExpression here
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
     * expression that combines property streams and assembles objects from each
     * combination at runtime.
     *
     * @param attributes the compiled object attributes
     * @return a stream expression that emits objects
     */
    private CompiledExpression compileObjectStreamExpression(CompiledObjectAttributes attributes) {
        val sources = new ArrayList<Flux<ObjectEntry>>(attributes.attributes().size());
        for (val entry : attributes.attributes().entrySet()) {
            sources.add(
                    compiledExpressionToFlux(entry.getValue()).map(value -> new ObjectEntry(entry.getKey(), value)));
        }
        val stream = Flux.<ObjectEntry, Value>combineLatest(sources,
                combined -> assembleObjectValue((ObjectEntry[]) combined));
        return new StreamExpression(stream);
    }

    /**
     * Assembles an object value from compiled attribute expressions. Used both at
     * compile time for constant folding and at runtime during stream evaluation.
     *
     * @param attributes the compiled object attributes containing only Values
     * @return the assembled object value, or error if any attribute is an error
     * @throws SaplCompilerException if attributes nature is not VALUE
     */
    private Value assembleObjectValue(CompiledObjectAttributes attributes) {
        if (attributes.nature() != Nature.VALUE) {
            throw new SaplCompilerException(
                    "assembleObjectValue called with non-VALUE nature: " + attributes.nature());
        }
        val objectBuilder = ObjectValue.builder();
        for (val attribute : attributes.attributes().entrySet()) {
            val key = attribute.getKey();
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
     * whether members are values, pure expressions, or streams.
     *
     * @param members the object member AST nodes
     * @param context the compilation context
     * @return the compiled object attributes with nature classification
     */
    private CompiledObjectAttributes compileAttributes(EList<Pair> members, CompilationContext context) {
        if (members == null || members.isEmpty()) {
            return CompiledObjectAttributes.EMPTY_ATTRIBUTES;
        }
        val compiledArguments = new HashMap<String, CompiledExpression>(members.size());
        var isStream = false;
        var isPure = false;
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

    /**
     * Compiles expression arguments into a structured representation tracking whether
     * arguments are values, pure expressions, or streams.
     *
     * @param arguments the argument expression AST nodes
     * @param context the compilation context
     * @return the compiled arguments with nature classification
     */
    private CompiledArguments compileArguments(EList<Expression> arguments, CompilationContext context) {
        val compiledArguments = new CompiledExpression[arguments.size()];
        var isPure = false;
        var isStream = false;
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
}