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

import io.sapl.api.model.*;
import io.sapl.api.model.Value;
import io.sapl.compiler.operators.BooleanOperators;
import io.sapl.functions.libraries.SchemaValidationLibrary;
import io.sapl.grammar.sapl.*;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.eclipse.emf.common.util.EList;

import java.util.List;

@UtilityClass
public class SaplCompiler {

    private static final String ERROR_POLICY_ALWAYS_FALSE                 = "Error compiling policy '%s': %s expression always returns false. These rules will never be applicable.";
    private static final String ERROR_POLICY_ALWAYS_NON_BOOLEAN           = "Error compiling policy '%s': %s expression always returns a non Boolean value: %s.";
    private static final String ERROR_POLICY_COMPILE_TIME_ERROR           = "Error compiling policy '%s': %s expression yielded a compile time error: %s.";
    private static final String ERROR_POLICY_CONTAINS_ATTRIBUTE_FINDERS   = "Error compiling policy '%s': %s expression must not contain access to any <> attribute finders.";
    private static final String ERROR_POLICY_SETS_NOT_SUPPORTED           = "Policy Sets not supported yet.";
    private static final String ERROR_POLICY_UNRESOLVED_RELATIVE          = "Error compiling policy '%s': %s expression contains an unresolved relative reference (e.g., @) and can never be evaluated correctly.";
    private static final String ERROR_SCHEMA_INVALID_SUBSCRIPTION_ELEMENT = "Schema must reference one of the four subscription identifiers: subject, action, resource, environment, but was: %s.";
    private static final String ERROR_SCHEMA_NOT_OBJECT_VALUE             = "Schema must evaluate to an ObjectValue, but was: %s";
    private static final String ERROR_UNEXPECTED_POLICY_ELEMENT           = "Unexpected policy element: %s";
    private static final String ERROR_VARIABLE_ALREADY_EXISTS             = "Variable already exists: %s";

    private static final String ENVIRONMENT_VARIABLE_WITH_SCHEMAS = "SCHEMAS";

    public CompiledPolicy compileDocument(SAPL document, CompilationContext context) {
        context.resetForNextDocument();
        context.addAllImports(document.getImports());
        val schemaCheckingExpression = compileSchemas(document.getSchemas(), context);
        val policyElement            = document.getPolicyElement();
        return switch (policyElement) {
        case Policy policy       -> compilePolicy(policy, schemaCheckingExpression, context);
        case PolicySet policySet -> compilePolicySet(policySet, schemaCheckingExpression, context);
        default                  ->
            throw new SaplCompilerException(ERROR_UNEXPECTED_POLICY_ELEMENT.formatted(policyElement));
        };
    }

    private CompiledPolicy compilePolicy(Policy policy, CompiledExpression schemaCheckingExpression,
            CompilationContext context) {
        val name             = policy.getSaplName();
        val entitlement      = Entitlement.of(policy.getEntitlement());
        val targetExpression = ExpressionCompiler.compileLazyAnd(schemaCheckingExpression, policy.getTargetExpression(),
                context);
        assertExpressionSuitableForTarget(name, targetExpression);
        val body           = compileBody(policy.getBody(), context);
        val obligations    = compileListOfExpressions(policy.getObligations(), context);
        val advice         = compileListOfExpressions(policy.getAdvice(), context);
        val transformation = ExpressionCompiler.compileExpression(policy.getTransformation(), context);
        return new CompiledPolicy(name, entitlement, targetExpression, body, obligations, advice, transformation);
    }

    private void assertExpressionSuitableForTarget(String name, CompiledExpression expression) {
        if (expression instanceof StreamExpression) {
            throw new SaplCompilerException(ERROR_POLICY_CONTAINS_ATTRIBUTE_FINDERS.formatted(name, "Target"));
        } else if (expression instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_POLICY_COMPILE_TIME_ERROR.formatted(name, "Target", error));
        } else if (expression instanceof Value && !(expression instanceof BooleanValue)) {
            throw new SaplCompilerException(ERROR_POLICY_ALWAYS_NON_BOOLEAN.formatted(name, "Target", expression));
        } else if (expression instanceof BooleanValue booleanValue && booleanValue.equals(Value.FALSE)) {
            throw new SaplCompilerException(ERROR_POLICY_ALWAYS_FALSE.formatted(name, "Target"));
        } else if (expression instanceof PureExpression pureExpression && !pureExpression.isSubscriptionScoped()) {
            throw new SaplCompilerException(ERROR_POLICY_UNRESOLVED_RELATIVE.formatted(name, "Target"));
        }
    }

    private CompiledExpression compileBody(PolicyBody body, CompilationContext context) {
        if (body == null) {
            return Value.TRUE;
        }
        val statements = body.getStatements();
        if (statements == null || statements.isEmpty()) {
            return Value.TRUE;
        }
        CompiledExpression compiledBody = null;
        for (val statement : statements) {
            if (statement instanceof Condition condition) {
                if (compiledBody == null) {
                    compiledBody = ExpressionCompiler.compileExpression(condition.getExpression(), context);
                } else {
                    compiledBody = ExpressionCompiler.compileLazyAnd(compiledBody, condition.getExpression(), context);
                }
            } else if (statement instanceof ValueDefinition valueDefinition) {
                val variableName = valueDefinition.getName();
                if (context.localVariablesInScope.containsKey(variableName)) {
                    throw new SaplCompilerException(ERROR_VARIABLE_ALREADY_EXISTS.formatted(variableName));
                }
                var compiledValueDefinition = ExpressionCompiler.compileExpression(valueDefinition.getEval(), context);
                if (compiledValueDefinition instanceof StreamExpression streamExpression) {
                    compiledValueDefinition = makeExpressionMulticast(streamExpression);
                }
                context.localVariablesInScope.put(variableName, compiledValueDefinition);
            }
        }
        if (compiledBody == null) {
            return Value.TRUE;
        }
        return compiledBody;
    }

    /**
     * Converts a StreamExpression into a multicast stream that caches the last
     * value and allows multiple subscribers.
     * <p>
     * Uses replay(1).refCount() to create a hot source that:
     * <ul>
     * <li>Caches the last emitted value</li>
     * <li>Shares the cached value with new subscribers immediately</li>
     * <li>Automatically connects when the first subscriber subscribes</li>
     * <li>Automatically disconnects when all subscribers unsubscribe</li>
     * </ul>
     *
     * @param streamExpression the stream expression to multicast
     * @return a new StreamExpression with multicast behavior
     */
    private static StreamExpression makeExpressionMulticast(StreamExpression streamExpression) {
        return new StreamExpression(streamExpression.stream().replay(1).refCount());
    }

    private List<CompiledExpression> compileListOfExpressions(List<Expression> expressions,
            CompilationContext context) {
        return expressions == null ? List.of()
                : expressions.stream().map(expression -> ExpressionCompiler.compileExpression(expression, context))
                        .toList();
    }

    private CompiledPolicy compilePolicySet(PolicySet policySet, CompiledExpression schemaCheckingExpression,
            CompilationContext context) {
        throw new SaplCompilerException(ERROR_POLICY_SETS_NOT_SUPPORTED);
    }

    private CompiledExpression compileSchemas(EList<Schema> schemas, CompilationContext context) {
        if (schemas == null || schemas.isEmpty()) {
            return Value.TRUE;
        }
        PureExpression schemaValidationExpression = null;
        for (val schema : schemas) {
            if (!schema.isEnforced()) {
                // Only actually verify enforced schemata. THe others are just sugar for the
                // editor helping with code completion hints.
                continue;
            }
            val schemaValidation = compileSchema(schema, context);
            if (schemaValidationExpression == null) {
                schemaValidationExpression = schemaValidation;
            } else {
                val finalSchemaValidationExpression = schemaValidationExpression;
                schemaValidationExpression = new PureExpression(ctx -> BooleanOperators
                        .and(finalSchemaValidationExpression.evaluate(ctx), schemaValidation.evaluate(ctx)), true);
            }
        }
        return schemaValidationExpression == null ? Value.TRUE : schemaValidationExpression;
    }

    private PureExpression compileSchema(Schema schema, CompilationContext context) {
        val schemaValue = ExpressionCompiler.compileExpression(schema.getSchemaExpression(), context);
        if (!(schemaValue instanceof ObjectValue schemaObjectValue)) {
            throw new SaplCompilerException(ERROR_SCHEMA_NOT_OBJECT_VALUE.formatted(schemaValue));
        }
        val subscriptionElement = schema.getSubscriptionElement();
        if (!ReservedIdentifiers.SUBSCRIPTION_IDENTIFIERS.contains(subscriptionElement)) {
            throw new SaplCompilerException(ERROR_SCHEMA_INVALID_SUBSCRIPTION_ELEMENT.formatted(subscriptionElement));
        }

        return new PureExpression(ctx -> {
            val externalSchemas = ctx.get(ENVIRONMENT_VARIABLE_WITH_SCHEMAS);
            if (!(externalSchemas instanceof ArrayValue)) {
                return Value.TRUE;
            }
            val subscriptionElementValue = ctx.get(subscriptionElement);
            return SchemaValidationLibrary.isCompliantWithExternalSchemas(subscriptionElementValue, schemaObjectValue,
                    externalSchemas);
        }, true);
    }

}
