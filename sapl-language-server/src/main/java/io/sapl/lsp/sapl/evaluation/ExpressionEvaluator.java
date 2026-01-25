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
package io.sapl.lsp.sapl.evaluation;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.ast.Expression;
import io.sapl.compiler.document.AstTransformer;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.grammar.antlr.SAPLParser.ExpressionContext;
import io.sapl.lsp.configuration.LSPConfiguration;
import lombok.experimental.UtilityClass;

/**
 * Evaluates SAPL expressions at content-assist time to resolve schema values.
 * Used for evaluating schema expressions in policy documents.
 */
@UtilityClass
public class ExpressionEvaluator {

    private static final String CONTENT_ASSIST_ID = "contentAssistEvaluation";

    private static final AstTransformer AST_TRANSFORMER = new AstTransformer();

    /**
     * Evaluates an ANTLR expression parse tree to a JSON node.
     * Only pure expressions (non-streaming) can be evaluated.
     *
     * @param expressionCtx the ANTLR expression context to evaluate
     * @param config the LSP configuration containing brokers and variables
     * @return the evaluated result as JsonNode, or empty if evaluation fails
     */
    public static Optional<JsonNode> evaluateExpressionToJsonNode(ExpressionContext expressionCtx,
            LSPConfiguration config) {
        if (expressionCtx == null || config == null) {
            return Optional.empty();
        }

        // Convert ANTLR context to AST Expression
        var expression = (Expression) AST_TRANSFORMER.visit(expressionCtx);
        if (expression == null) {
            return Optional.empty();
        }

        var compilationContext = new CompilationContext(config.functionBroker(), config.attributeBroker());

        var compiledExpression = ExpressionCompiler.compile(expression, compilationContext);

        if (compiledExpression == null || compiledExpression instanceof StreamOperator) {
            return Optional.empty();
        }

        if (compiledExpression instanceof PureOperator pureOperator) {
            var evaluationContext = createEvaluationContext(config);
            compiledExpression = pureOperator.evaluate(evaluationContext);
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

    private static EvaluationContext createEvaluationContext(LSPConfiguration config) {
        return new EvaluationContext(config.configurationId(), config.configurationId(), CONTENT_ASSIST_ID,
                AuthorizationSubscription.of("subject", "action", "resource", "environment"), config.functionBroker(),
                config.attributeBroker(), Value.UNDEFINED, Value.UNDEFINED);
    }

}
