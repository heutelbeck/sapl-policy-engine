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
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.*;
import io.sapl.api.model.Value;
import io.sapl.compiler.operators.BooleanOperators;
import io.sapl.compiler.operators.ComparisonOperators;
import io.sapl.compiler.operators.NumberOperators;
import io.sapl.compiler.operators.StepOperators;
import io.sapl.grammar.sapl.*;
import io.sapl.grammar.sapl.Object;
import io.sapl.grammar.sapl.impl.util.FunctionUtil;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.eclipse.emf.common.util.EList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;

@RequiredArgsConstructor
public class SaplCompiler {

    private final static Value UNIMPLEMENTED = Value.error("unimplemented");

    private final FunctionBroker staticPlugInsServer;

    public CompiledPolicy compileDocument(SAPL document, CompilationContext context) {
        context.resetForNextDocument();
        context.addAllImports(document.getImports());
        compileSchemas(document.getSchemas(), context);
        val policyElement = document.getPolicyElement();
        return switch (policyElement) {
        case Policy policy       -> compilePolicy(policy, context);
        case PolicySet policySet -> compilePolicySet(policySet, context);
        default                  -> throw new SaplCompilerException("Unexpected policy element: " + policyElement);
        };
    }

    private CompiledPolicy compilePolicy(Policy policy, CompilationContext context) {
        val name             = policy.getSaplName();
        val entitlement      = Entitlement.of(policy.getEntitlement());
        val targetExpression = compileExpression(policy.getTargetExpression(), context);
        assertExpressionSuitableForTargetOrBody("Target", name, targetExpression);
        val body = compileBody(policy.getBody(), context);
        assertExpressionSuitableForTargetOrBody("Body", name, body);
        val obligations    = compileListOfExpressions(policy.getObligations(), context);
        val advice         = compileListOfExpressions(policy.getAdvice(), context);
        val transformation = compileExpression(policy.getTransformation(), context);
        return new CompiledPolicy(name, entitlement, targetExpression, body, obligations, advice, transformation);
    }

    private void assertExpressionSuitableForTargetOrBody(String where, String name, CompiledExpression expression) {
        if (expression instanceof StreamExpression) {
            throw new SaplCompilerException(String.format(
                    "Error compiling policy '%s': %s expression must not contain access to any <> attribute finders.",
                    name, where));
        } else if (expression instanceof ErrorValue error) {
            throw new SaplCompilerException(
                    String.format("Error compiling policy '%s': %s expression yielded a compile time error: %s.", name,
                            where, error));
        } else if (expression instanceof Value && !(expression instanceof BooleanValue)) {
            throw new SaplCompilerException(
                    String.format("Error compiling policy '%s': %s expression always returns a non Boolean value: %s.",
                            name, where, expression));
        } else if (expression instanceof BooleanValue booleanValue && booleanValue.equals(Value.FALSE)) {
            throw new SaplCompilerException(String.format(
                    "Error compiling policy '%s': %s expression always returns false. These rules will never be applicable.",
                    name, where));
        }
    }

    private CompiledExpression compileBody(PolicyBody body, CompilationContext context) {
        if (body == null) {
            return Value.TRUE;
        }
        return UNIMPLEMENTED;
    }

    private List<CompiledExpression> compileListOfExpressions(List<Expression> expressions,
            CompilationContext context) {
        return expressions == null ? List.of()
                : expressions.stream().map(expression -> compileExpression(expression, context)).toList();
    }

    private CompiledPolicy compilePolicySet(PolicySet policySet, CompilationContext context) {
        throw new SaplCompilerException("Policy Sets not supported yet.");
    }

    private void compileSchemas(EList<Schema> schemas, CompilationContext context) {
        // TODO: Implement
        // loads current schemas into context
        // Schema expressions must evaluate to constant values !
        // ID must be one of subject, action, resource or environment
    }

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
            BiFunction<Value, Value, Value> operation, CompilationContext context) {
        val left  = compileExpression(operator.getLeft(), context);
        val right = compileExpression(operator.getRight(), context);
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
            // TODO: implement
            return UNIMPLEMENTED;
        }
        if (left instanceof PureExpression subLeft && right instanceof PureExpression subRight) {
            return new PureExpression(ctx -> operation.apply(subLeft.evaluate(ctx), subRight.evaluate(ctx)),
                    subLeft.dependsOnVariables() || subRight.dependsOnVariables(),
                    subLeft.isRelative() || subRight.isRelative());
        }
        if (left instanceof PureExpression subLeft && right instanceof Value valRight) {
            if (operator instanceof Regex) {
                val compiledRegex = ComparisonOperators.compileRegularExpressionOperation(valRight);
                return new PureExpression(ctx -> compiledRegex.apply(subLeft.evaluate(ctx)),
                        subLeft.dependsOnVariables(), subLeft.isRelative());
            }
            return new PureExpression(ctx -> operation.apply(subLeft.evaluate(ctx), valRight),
                    subLeft.dependsOnVariables(), subLeft.isRelative());
        }
        if (left instanceof Value valLeft && right instanceof PureExpression subRight) {
            return new PureExpression(ctx -> operation.apply(valLeft, subRight.evaluate(ctx)),
                    subRight.dependsOnVariables(), subRight.isRelative());
        }
        throw new SaplCompilerException("Unexpected expression types. Should not be possible: "
                + left.getClass().getSimpleName() + " and " + right.getClass().getSimpleName() + ".");
    }

    private CompiledExpression compileUnaryOperation(UnaryOperator operator,
            java.util.function.UnaryOperator<Value> operation, CompilationContext context) {
        val expression = compileExpression(operator.getExpression(), context);
        if (expression instanceof Value value) {
            return operation.apply(value);
        }
        if (expression instanceof StreamExpression) {
            // TODO: implement
            return UNIMPLEMENTED;
        }
        val subExpression = (PureExpression) expression;
        return new PureExpression(ctx -> operation.apply(subExpression.evaluate(ctx)),
                subExpression.dependsOnVariables(), subExpression.isRelative());
    }

    private CompiledExpression compileBasicExpression(BasicExpression expression, CompilationContext context) {
        return switch (expression) {
        case BasicGroup group                               -> compileExpression(group.getExpression(), context);
        case BasicValue value                               -> compileValue(value, context);
        case BasicFunction function                         -> compileFunction(function, context);
        case BasicEnvironmentAttribute envAttribute         -> UNIMPLEMENTED;
        case BasicEnvironmentHeadAttribute envHeadAttribute -> UNIMPLEMENTED;
        case BasicIdentifier identifier                     -> compileIdentifier(identifier, context);
        case BasicRelative relative                         -> UNIMPLEMENTED;
        default                                             ->
            throw new SaplCompilerException("unexpected expression: " + expression + ".");
        };
    }

    private CompiledExpression compileFunction(BasicFunction function, CompilationContext context) {
        if (context.isDynamicLibrariesEnabled()) {
            return UNIMPLEMENTED;
        }
        val arguments = new ArrayList<CompiledExpression>();
        var nature    = Nature.VALUE;
        if (function.getArguments() != null && function.getArguments().getArgs() != null) {
            for (val expression : function.getArguments().getArgs()) {
                val compiled = compileExpression(expression, context);
                arguments.add(compiled);
                if (compiled instanceof PureExpression) {
                    if (nature != Nature.ATTRIBUTE_DEPENDENT) {
                        nature = Nature.SUBSCRIPTION_DEPENDENT;
                    }
                } else if (compiled instanceof StreamExpression) {
                    nature = Nature.ATTRIBUTE_DEPENDENT;
                }
            }
        }
        if (nature == Nature.VALUE) {
            val valueArguments = arguments.stream().map(Value.class::cast).toList();
            val invocation     = new FunctionInvocation(
                    FunctionUtil.resolveFunctionIdentifierByImports(function, function.getIdentifier()),
                    valueArguments);
            return staticPlugInsServer.evaluateFunction(invocation);
        }
        return UNIMPLEMENTED;
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
        }, true, false);
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
        val compiledExpression = compileExpression(expressionStep.getExpression(), context);
        if (compiledExpression instanceof StreamExpression) {
            throw new SaplCompilerException("No attribute finders allowed in expression steps.");
        }
        if (parent instanceof Value value) {
            if (compiledExpression instanceof PureExpression pureExpression) {
                return new PureExpression(ctx -> indexOrKeyStep(value, pureExpression.evaluate(ctx)),
                        pureExpression.dependsOnVariables(), pureExpression.isRelative());
            } else {
                return UNIMPLEMENTED;
            }
        } else if (parent instanceof PureExpression pureParentExpression) {
            if (compiledExpression instanceof PureExpression pureExpression) {
                return new PureExpression(
                        ctx -> indexOrKeyStep(pureParentExpression.evaluate(ctx), pureExpression.evaluate(ctx)),
                        pureParentExpression.dependsOnVariables() || pureExpression.dependsOnVariables(),
                        pureParentExpression.isRelative() || pureExpression.isRelative());
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
                    if (pureExpression.dependsOnVariables()) {

                    }
                    return new PureExpression(ctx -> indexOrKeyStep(value, pureExpression.evaluate(ctx)),
                            pureExpression.dependsOnVariables(), pureExpression.isRelative());
                } else {
                    return UNIMPLEMENTED;
                }
            }
        } else if (parent instanceof PureExpression pureParentExpression) {
            if (compiledConditionExpression instanceof PureExpression pureExpression) {
                return new PureExpression(
                        ctx -> indexOrKeyStep(pureParentExpression.evaluate(ctx), pureExpression.evaluate(ctx)),
                        pureParentExpression.dependsOnVariables() || pureExpression.dependsOnVariables(),
                        pureParentExpression.isRelative() || pureExpression.isRelative());
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
            return Value.error("Expression in expression step must return number of text, but got %s."
                    .formatted(expressionResult));
        }
    }

    private CompiledExpression compileStep(CompiledExpression parent, java.util.function.UnaryOperator<Value> operation,
            CompilationContext context) {
        if (parent instanceof Value value) {
            return operation.apply(value);
        }
        if (parent instanceof StreamExpression) {
            // TODO: implement
            return UNIMPLEMENTED;
        }
        val pureParent = (PureExpression) parent;
        return new PureExpression(ctx -> operation.apply(pureParent.evaluate(ctx)), pureParent.dependsOnVariables(),
                pureParent.isRelative());
    }

    private CompiledExpression composeArray(Array array, CompilationContext context) {
        val items = array.getItems();
        if (items.isEmpty()) {
            return Value.EMPTY_ARRAY;
        }
        val arguments = compileArguments(items, context);
        if (arguments.nature() == Nature.VALUE) {
            val arrayBuilder = ArrayValue.builder();
            for (val argument : arguments.arguments()) {
                arrayBuilder.add((Value) argument);
            }
            return arrayBuilder.build();
        }
        return UNIMPLEMENTED;
    }

    private CompiledExpression composeObject(Object object, CompilationContext context) {
        val members = object.getMembers();
        if (members.isEmpty()) {
            return Value.EMPTY_OBJECT;
        }
        CompiledObjectAttributes attributes = compileAttributes(members, context);
        if (attributes.nature() == Nature.VALUE) {
            val objectBuilder = ObjectValue.builder();
            for (val attribute : attributes.attributes().entrySet()) {
                objectBuilder.put(attribute.getKey(), (Value) attribute.getValue());
            }
            return objectBuilder.build();
        }
        return UNIMPLEMENTED;
    }

    private CompiledObjectAttributes compileAttributes(EList<Pair> members, CompilationContext context) {
        val compiledArguments     = new HashMap<String, CompiledExpression>(members.size());
        var subscriptionDependent = false;
        var attributeDependent    = false;
        for (Pair pair : members) {
            val compiledAttribute = compileExpression(pair.getValue(), context);
            if (compiledAttribute instanceof PureExpression) {
                subscriptionDependent = true;
            } else if (compiledAttribute instanceof StreamExpression) {
                attributeDependent = true;
            }
            compiledArguments.put(pair.getKey(), compiledAttribute);
        }
        var nature = Nature.VALUE;
        if (attributeDependent) {
            nature = Nature.ATTRIBUTE_DEPENDENT;
        } else if (subscriptionDependent) {
            nature = Nature.SUBSCRIPTION_DEPENDENT;
        }
        return new CompiledObjectAttributes(nature, compiledArguments);
    }

    private CompiledArguments compileArguments(EList<Expression> arguments, CompilationContext context) {
        val compiledArguments     = new CompiledExpression[arguments.size()];
        var subscriptionDependent = false;
        var attributeDependent    = false;
        for (int i = 0; i < arguments.size(); i++) {
            val compiledArgument = compileExpression(arguments.get(i), context);
            if (compiledArgument instanceof PureExpression) {
                subscriptionDependent = true;
            } else if (compiledArgument instanceof StreamExpression) {
                attributeDependent = true;
            }
            compiledArguments[i] = compiledArgument;
        }
        var nature = Nature.VALUE;
        if (attributeDependent) {
            nature = Nature.ATTRIBUTE_DEPENDENT;
        } else if (subscriptionDependent) {
            nature = Nature.SUBSCRIPTION_DEPENDENT;
        }
        return new CompiledArguments(nature, compiledArguments);
    }
}
