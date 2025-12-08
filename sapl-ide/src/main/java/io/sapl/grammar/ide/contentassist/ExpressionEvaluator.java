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
package io.sapl.grammar.ide.contentassist;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.compiler.CompilationContext;
import io.sapl.compiler.ExpressionCompiler;
import io.sapl.compiler.SaplCompilerException;
import io.sapl.grammar.sapl.Expression;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@UtilityClass
public class ExpressionEvaluator {
    public static Optional<JsonNode> evaluateExpressionToJsonNode(Expression expression,
            ContentAssistPDPConfiguration pdpConfiguration) {
        final var          compilationContext = new CompilationContext(pdpConfiguration.functionBroker(),
                pdpConfiguration.attributeBroker());
        CompiledExpression compiledExpression;
        try {
            compiledExpression = ExpressionCompiler.compileExpression(expression, compilationContext);
        } catch (SaplCompilerException e) {
            return Optional.empty();
        }
        if (compiledExpression instanceof StreamExpression) {
            return Optional.empty();
        }

        if (compiledExpression instanceof PureExpression pureExpression) {
            final var evaluationContext = new EvaluationContext(pdpConfiguration.pdpId(),
                    pdpConfiguration.configurationId(), "dummyForContentAssistId",
                    AuthorizationSubscription.of("subject", "action", "resource", "environment"),
                    pdpConfiguration.variables(), pdpConfiguration.functionBroker(), pdpConfiguration.attributeBroker(),
                    () -> "constantTime");
            compiledExpression = pureExpression.evaluate(evaluationContext);
        }
        if (!(compiledExpression instanceof ObjectValue schemaObject)) {
            return Optional.empty();
        }
        try {
            return Optional.of(ValueJsonMarshaller.toJsonNode(schemaObject));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

}
