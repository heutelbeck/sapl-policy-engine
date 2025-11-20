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
import io.sapl.grammar.sapl.*;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.eclipse.emf.common.util.EList;

import java.util.List;

@UtilityClass
public class SaplCompiler {

    private static final String ERROR_POLICY_SETS_NOT_SUPPORTED = "Policy Sets not supported yet.";
    private static final String ERROR_UNEXPECTED_POLICY_ELEMENT = "Unexpected policy element: %s";
    private static final String ERROR_UNIMPLEMENTED             = "unimplemented";

    private static final Value UNIMPLEMENTED = Value.error(ERROR_UNIMPLEMENTED);

    public CompiledPolicy compileDocument(SAPL document, CompilationContext context) {
        context.resetForNextDocument();
        context.addAllImports(document.getImports());
        compileSchemas(document.getSchemas(), context);
        val policyElement = document.getPolicyElement();
        return switch (policyElement) {
        case Policy policy       -> compilePolicy(policy, context);
        case PolicySet policySet -> compilePolicySet(policySet, context);
        default                  ->
            throw new SaplCompilerException(String.format(ERROR_UNEXPECTED_POLICY_ELEMENT, policyElement));
        };
    }

    private CompiledPolicy compilePolicy(Policy policy, CompilationContext context) {
        val name             = policy.getSaplName();
        val entitlement      = Entitlement.of(policy.getEntitlement());
        val targetExpression = ExpressionCompiler.compileExpression(policy.getTargetExpression(), context);
        assertExpressionSuitableForTargetOrBody("Target", name, targetExpression);
        val body = compileBody(policy.getBody(), context);
        assertExpressionSuitableForTargetOrBody("Body", name, body);
        val obligations    = compileListOfExpressions(policy.getObligations(), context);
        val advice         = compileListOfExpressions(policy.getAdvice(), context);
        val transformation = ExpressionCompiler.compileExpression(policy.getTransformation(), context);
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
        } else if (expression instanceof PureExpression pureExpression && !pureExpression.isSubscriptionScoped()) {
            throw new SaplCompilerException(String.format(
                    "Error compiling policy '%s': %s expression contains an unresolved relative reference (e.g., @) and can never be evaluated correctly.",
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
                : expressions.stream().map(expression -> ExpressionCompiler.compileExpression(expression, context))
                        .toList();
    }

    private CompiledPolicy compilePolicySet(PolicySet policySet, CompilationContext context) {
        throw new SaplCompilerException(ERROR_POLICY_SETS_NOT_SUPPORTED);
    }

    private void compileSchemas(EList<Schema> schemas, CompilationContext context) {
        // TODO: Implement
        // loads current schemas into context
        // Schema expressions must evaluate to constant values !
        // ID must be one of subject, action, resource or environment
    }

}
