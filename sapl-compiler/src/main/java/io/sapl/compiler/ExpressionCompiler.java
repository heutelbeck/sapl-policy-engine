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
import java.util.List;

@UtilityClass
public class ExpressionCompiler {

    private static final Value UNIMPLEMENTED = Value.error("unimplemented");

    public CompiledExpression compileExpression(Expression expression, CompilationContext context) {
        if (expression == null) {
            return null;
        }
        return switch (expression) {
        case Or or                 -> compileBinaryOperation(or, BooleanOperators::or, context); // TODO add lazy !
        case EagerOr eagerOr       -> compileBinaryOperation(eagerOr, BooleanOperators::or, context);
        case XOr xor               -> compileBinaryOperation(xor, BooleanOperators::xor, context);
        case And and               -> compileBinaryOperation(and, BooleanOperators::and, context); // TODO add lazy !
        case EagerAnd eagerAnd     -> compileBinaryOperation(eagerAnd, BooleanOperators::and, context);
        case Not not               -> compileUnaryOperation(not, BooleanOperators::not, context);
        case Multi multi           -> compileBinaryOperation(multi, NumberOperators::multiply, context);
        case Div div               -> compileBinaryOperation(div, NumberOperators::divide, context);
        case Modulo modulo         -> compileBinaryOperation(modulo, NumberOperators::modulo, context);
        case Plus plus             -> compileBinaryOperation(plus, NumberOperators::add, context);
        case Minus minus           -> compileBinaryOperation(minus, NumberOperators::subtract, context);
        case Less less             -> compileBinaryOperation(less, NumberOperators::lessThan, context);
        case LessEquals lessEquals -> compileBinaryOperation(lessEquals, NumberOperators::lessThanOrEqual, context);
        case More more             -> compileBinaryOperation(more, NumberOperators::greaterThan, context);
        case MoreEquals moreEquals -> compileBinaryOperation(moreEquals, NumberOperators::greaterThanOrEqual, context);
        case UnaryPlus unaryPlus   -> compileUnaryOperation(unaryPlus, NumberOperators::unaryPlus, context);
        case UnaryMinus unaryMinus -> compileUnaryOperation(unaryMinus, NumberOperators::unaryMinus, context);
        case ElementOf elementOf   -> compileBinaryOperation(elementOf, ComparisonOperators::notEquals, context);
        case Equals equals         -> compileBinaryOperation(equals, ComparisonOperators::equals, context);
        case NotEquals notEquals   -> compileBinaryOperation(notEquals, ComparisonOperators::notEquals, context);
        case Regex regex           ->
            compileBinaryOperation(regex, ComparisonOperators::matchesRegularExpression, context);
        case BasicExpression basic -> compileBasicExpression(basic, context);
        default                    -> throw new SaplCompilerException("unexpected expression: " + expression + ".");
        };
    }

    private CompiledExpression compileBinaryOperation(BinaryOperator operator,
            java.util.function.BinaryOperator<Value> operation, CompilationContext context) {
        val left  = compileExpression(operator.getLeft(), context);
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

    private StreamExpression compileBinaryStreamOperator(CompiledExpression leftExpression,
            CompiledExpression rightExpression, java.util.function.BinaryOperator<Value> operation) {
        val stream = Flux.combineLatest(compiledExpressionToFlux(leftExpression),
                compiledExpressionToFlux(leftExpression), operation);
        return new StreamExpression(stream);
    }

    private Flux<Value> compiledExpressionToFlux(CompiledExpression expression) {
        return switch (expression) {
        case Value value                   -> Flux.just(value);
        case StreamExpression stream       -> stream.stream();
        case PureExpression pureExpression -> deferPureExpressionEvaluation(pureExpression);
        };
    }

    private Flux<Value> deferPureExpressionEvaluation(PureExpression expression) {
        return Flux.deferContextual(ctx -> Flux.just(expression.evaluate(ctx.get(EvaluationContext.class))));
    }

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

    private StreamExpression compileUnaryStreamOperator(CompiledExpression input,
            java.util.function.UnaryOperator<Value> operation) {
        return new StreamExpression(compiledExpressionToFlux(input).map(operation));
    }

    private CompiledExpression compileBasicExpression(BasicExpression expression, CompilationContext context) {
        return switch (expression) {
        case BasicGroup group                               -> compileExpression(group.getExpression(), context);
        case BasicValue value                               -> compileValue(value, context);
        case BasicFunction function                         -> compileBasicFunction(function, context);
        case BasicEnvironmentAttribute envAttribute         -> UNIMPLEMENTED;
        case BasicEnvironmentHeadAttribute envHeadAttribute -> UNIMPLEMENTED;
        case BasicIdentifier identifier                     -> compileIdentifier(identifier, context);
        case BasicRelative relative                         -> UNIMPLEMENTED;
        default                                             ->
            throw new SaplCompilerException("unexpected expression: " + expression + ".");
        };
    }

    private CompiledExpression compileBasicFunction(BasicFunction function, CompilationContext context) {
        if (context.isDynamicLibrariesEnabled()) {
            return UNIMPLEMENTED;
        }
        var arguments = CompiledArguments.EMPTY_ARGUMENTS;
        if (function.getArguments() != null && function.getArguments().getArgs() != null) {
            arguments = compileArguments(function.getArguments().getArgs(), context);
        }
        return switch (arguments.nature()) {
        case VALUE  -> compileFunctionWithValueParameters(function, arguments.arguments(), context);
        case PURE   -> compileFunctionWithPureParameters(function, arguments, context);
        case STREAM -> compileFunctionWithStreamParameters(function, arguments, context);
        };
    }

    private CompiledExpression compileFunctionWithStreamParameters(BasicFunction function, CompiledArguments arguments,
            CompilationContext context) {
        val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux).toList();
        val stream  = Flux.<Value, Value>combineLatest(sources,
                combined -> compileFunctionWithValueParameters(function, (CompiledExpression[]) combined, context));
        return new StreamExpression(stream);
    }

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

    private Value compileFunctionWithValueParameters(BasicFunction function, CompiledExpression[] arguments,
            CompilationContext context) {
        val valueArguments = Arrays.stream(arguments).map(Value.class::cast).toList();
        val invocation     = new FunctionInvocation(
                ImportResolver.resolveFunctionIdentifierByImports(function, function.getIdentifier()), valueArguments);
        return context.getFunctionBroker().evaluateFunction(invocation);
    }

    private CompiledExpression compileIdentifier(BasicIdentifier identifier, CompilationContext context) {
        val variableIdentifier = identifier.getIdentifier();
        val maybeLocalVariable = context.localVariablesInScope.get(variableIdentifier);
        if (maybeLocalVariable != null) {
            return maybeLocalVariable;
        }

        return new PureExpression(ctx -> {
            val variableExpression = ctx.get(variableIdentifier);
            if (variableExpression instanceof StreamExpression) {
                return Value.error(
                        "Encountered an attribute dependent expression during subscription dependent evaluation. Must not happen.");
            }
            if (variableExpression instanceof PureExpression subExpression) {
                return subExpression.evaluate(ctx);
            }
            return (Value) variableExpression;
        }, true);
    }

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
                              throw new SaplCompilerException("unexpected value: " + value + ".");
                          };

        compiledValue = compileSteps(compiledValue, basic.getSteps(), context);
        if (compiledValue instanceof Value constantValue) {
            return context.dedupe(constantValue);
        }
        return compiledValue;
    }

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

    private CompiledExpression compileStep(CompiledExpression parent, Step step, CompilationContext context) {
        return switch (step) {
        case KeyStep keyStep                                 ->
            compileStep(parent, p -> StepOperators.keyStep(p, keyStep.getId()), context);
        case EscapedKeyStep escapedKeyStep                   ->
            compileStep(parent, p -> StepOperators.keyStep(p, escapedKeyStep.getId()), context);
        case WildcardStep wildcardStep                       ->
            compileStep(parent, StepOperators::wildcardStep, context);
        case AttributeFinderStep attributeFinderStep         -> UNIMPLEMENTED;
        case HeadAttributeFinderStep headAttributeFinderStep -> UNIMPLEMENTED;
        case RecursiveKeyStep recursiveKeyStep               ->
            compileStep(parent, p -> StepOperators.recursiveKeyStep(p, recursiveKeyStep.getId()), context);
        case RecursiveWildcardStep recursiveWildcardStep     ->
            compileStep(parent, StepOperators::recursiveWildcardStep, context);
        case RecursiveIndexStep recursiveIndexStep           ->
            compileStep(parent, p -> StepOperators.recursiveIndexStep(p, recursiveIndexStep.getIndex()), context);
        case IndexStep indexStep                             ->
            compileStep(parent, p -> StepOperators.indexStep(p, indexStep.getIndex()), context);
        case ArraySlicingStep arraySlicingStep               -> compileStep(parent, p -> StepOperators.sliceArray(p,
                arraySlicingStep.getIndex(), arraySlicingStep.getTo(), arraySlicingStep.getStep()), context);
        case ExpressionStep expressionStep                   -> compileExpressionStep(parent, expressionStep, context);
        case ConditionStep conditionStep                     -> UNIMPLEMENTED;
        case IndexUnionStep indexUnionStep                   ->
            compileStep(parent, p -> StepOperators.indexUnion(p, indexUnionStep.getIndices()), context);
        case AttributeUnionStep attributeUnionStep           ->
            compileStep(parent, p -> StepOperators.attributeUnion(p, attributeUnionStep.getAttributes()), context);
        default                                              -> UNIMPLEMENTED;
        };
    }

    private CompiledExpression compileExpressionStep(CompiledExpression parent, ExpressionStep expressionStep,
            CompilationContext context) {

        if (parent instanceof ErrorValue || parent instanceof UndefinedValue) {
            return parent;
        }
        val compiledStepExpression = compileExpression(expressionStep.getExpression(), context);
        if (compiledStepExpression instanceof StreamExpression) {
            throw new SaplCompilerException("No attribute finders allowed in expression steps.");
        }
        if (parent instanceof Value value) {
            if (compiledStepExpression instanceof PureExpression pureExpression) {
                return new PureExpression(ctx -> indexOrKeyStep(value, pureExpression.evaluate(ctx)),
                        pureExpression.isSubscriptionScoped());
            } else {
                return UNIMPLEMENTED;
            }
        } else if (parent instanceof PureExpression pureParentExpression) {
            if (compiledStepExpression instanceof PureExpression pureExpression) {
                return new PureExpression(
                        ctx -> indexOrKeyStep(pureParentExpression.evaluate(ctx), pureExpression.evaluate(ctx)),
                        pureParentExpression.isSubscriptionScoped() || pureExpression.isSubscriptionScoped());
            } else {
                return UNIMPLEMENTED;
            }
        }
        val parentStream = (StreamExpression) parent;
        return UNIMPLEMENTED;
    }

    private CompiledExpression compileConditionStep(CompiledExpression parent, ExpressionStep expressionStep,
            CompilationContext context) {
        if (parent instanceof ErrorValue || parent instanceof UndefinedValue) {
            return parent;
        }
        val compiledConditionExpression = compileExpression(expressionStep.getExpression(), context);
        if (compiledConditionExpression instanceof StreamExpression) {
            throw new SaplCompilerException("No attribute finders allowed in condition steps.");
        }
        if (parent instanceof Value value) {
            if (parent instanceof ObjectValue objectValue) {
                if (compiledConditionExpression instanceof PureExpression pureExpression) {
                    if (pureExpression.isSubscriptionScoped()) {
                        // TODO CONTINUE HERE !
                    }
                    return new PureExpression(ctx -> indexOrKeyStep(value, pureExpression.evaluate(ctx)),
                            pureExpression.isSubscriptionScoped());
                } else {
                    return UNIMPLEMENTED;
                }
            }
        } else if (parent instanceof PureExpression pureParentExpression) {
            if (compiledConditionExpression instanceof PureExpression pureExpression) {
                return new PureExpression(
                        ctx -> indexOrKeyStep(pureParentExpression.evaluate(ctx), pureExpression.evaluate(ctx)),
                        pureParentExpression.isSubscriptionScoped() || pureExpression.isSubscriptionScoped());
            } else {
                return UNIMPLEMENTED;
            }
        }
        val parentStream = (StreamExpression) parent;
        return UNIMPLEMENTED;
    }

    private static Value indexOrKeyStep(Value value, Value expressionResult) {
        if (expressionResult instanceof NumberValue numberValue) {
            return StepOperators.indexStep(value, numberValue.value());
        } else if (expressionResult instanceof TextValue textValue) {
            return StepOperators.keyStep(value, textValue.value());
        } else {
            return Value.error("Expression in expression step must return a number or text, but got %s."
                    .formatted(expressionResult));
        }
    }

    private CompiledExpression compileStep(CompiledExpression parent, java.util.function.UnaryOperator<Value> operation,
            CompilationContext context) {
        return switch (parent) {
        case Value value                          -> operation.apply(value);
        case StreamExpression(Flux<Value> stream) -> new StreamExpression(stream.map(operation));
        case PureExpression pureParent            ->
            new PureExpression(ctx -> operation.apply(pureParent.evaluate(ctx)), pureParent.isSubscriptionScoped());
        };
    }

    private CompiledExpression composeArray(Array array, CompilationContext context) {
        val items = array.getItems();
        if (items.isEmpty()) {
            return Value.EMPTY_ARRAY;
        }
        val arguments = compileArguments(items, context);
        return switch (arguments.nature()) {
        case VALUE  -> compileValueArray(arguments.arguments());
        case PURE   -> compilePureArray(arguments);
        case STREAM -> compileArrayStreamExpression(arguments);
        };
    }

    private CompiledExpression compileArrayStreamExpression(CompiledArguments arguments) {
        val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux).toList();
        val stream  = Flux.<Value, Value>combineLatest(sources, combined -> compileValueArray((Value[]) combined));
        return new StreamExpression(stream);
    }

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

    private Value compileValueArray(CompiledExpression[] arguments) {
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

    private CompiledExpression composeObject(Object object, CompilationContext context) {
        val members = object.getMembers();
        if (members.isEmpty()) {
            return Value.EMPTY_OBJECT;
        }
        CompiledObjectAttributes attributes = compileAttributes(members, context);
        return switch (attributes.nature()) {
        case VALUE  -> compileValueObject(attributes);
        case PURE   -> compilePureObject(attributes);
        case STREAM -> compileObjectStreamExpression(attributes);
        };
    }

    private record ObjectEntry(String key, Value value) {}

    private CompiledExpression compileObjectStreamExpression(CompiledObjectAttributes attributes) {
        val sources = new ArrayList<Flux<ObjectEntry>>(attributes.attributes().size());
        for (val entry : attributes.attributes().entrySet()) {
            sources.add(
                    compiledExpressionToFlux(entry.getValue()).map(value -> new ObjectEntry(entry.getKey(), value)));
        }
        val stream = Flux.<ObjectEntry, Value>combineLatest(sources,
                combined -> compileValueObject((ObjectEntry[]) combined));
        return new StreamExpression(stream);
    }

    private Value compileValueObject(ObjectEntry[] attributes) {
        val objectBuilder = ObjectValue.builder();
        for (val attribute : attributes) {
            val value = attribute.value;
            val key   = attribute.key;
            if (value instanceof ErrorValue errorValue) {
                return errorValue;
            }
            if (!(value instanceof UndefinedValue)) {
                objectBuilder.put(key, value);
            }
        }
        return objectBuilder.build();
    }

    private CompiledExpression compilePureObject(CompiledObjectAttributes attributes) {
        return new PureExpression(ctx -> {
            val objectBuilder = ObjectValue.builder();
            for (val attribute : attributes.attributes().entrySet()) {
                val key               = attribute.getKey();
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

    private CompiledExpression compileValueObject(CompiledObjectAttributes attributes) {
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

    private CompiledObjectAttributes compileAttributes(EList<Pair> members, CompilationContext context) {
        if (members == null || members.isEmpty()) {
            return CompiledObjectAttributes.EMPTY_ATTRIBUTES;
        }
        val compiledArguments    = new HashMap<String, CompiledExpression>(members.size());
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
        if (isPure) {
            nature = Nature.STREAM;
        } else if (isStream) {
            nature = Nature.PURE;
        }
        return new CompiledObjectAttributes(nature, isSubscriptionScoped, compiledArguments);
    }

    private CompiledArguments compileArguments(EList<Expression> arguments, CompilationContext context) {
        if (arguments == null || arguments.isEmpty()) {
            return CompiledArguments.EMPTY_ARGUMENTS;
        }
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
}
