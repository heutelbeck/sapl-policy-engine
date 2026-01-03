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
package io.sapl.util;

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ExpressionResult;
import io.sapl.api.model.Value;
import io.sapl.ast.Expression;
import io.sapl.compiler.ExpressionEvaluator;
import io.sapl.parser.AstTransformer;
import lombok.experimental.UtilityClass;

import java.util.Map;

/**
 * Test utilities for parsing and evaluating SAPL expressions.
 */
@UtilityClass
public class ExpressionTestUtil {

    private static final AstTransformer TRANSFORMER = new AstTransformer();

    /**
     * Parses a SAPL expression string into an AST Expression.
     *
     * @param source the expression source code
     * @return the parsed Expression AST node
     * @throws IllegalArgumentException if parsing fails
     */
    public static Expression parseExpression(String source) {
        var ctx = ParserUtil.expression(source);
        return (Expression) TRANSFORMER.visit(ctx);
    }

    /**
     * Evaluates a SAPL expression string with an empty context.
     *
     * @param source the expression source code
     * @return the evaluation result
     */
    public static ExpressionResult evaluateExpression(String source) {
        return evaluateExpression(source, emptyContext());
    }

    /**
     * Evaluates a SAPL expression string with the given context.
     *
     * @param source the expression source code
     * @param ctx the evaluation context
     * @return the evaluation result
     */
    public static ExpressionResult evaluateExpression(String source, EvaluationContext ctx) {
        var expression = parseExpression(source);
        return ExpressionEvaluator.evaluate(expression, ctx);
    }

    /**
     * Creates an empty evaluation context for testing.
     * No variables, no brokers.
     */
    public static EvaluationContext emptyContext() {
        return new EvaluationContext(null, null, null, null, Map.of(), null, null, () -> "test-timestamp");
    }

    /**
     * Creates an evaluation context with the given variables.
     *
     * @param variables the variables to include
     * @return the evaluation context
     */
    public static EvaluationContext withVariables(Map<String, Value> variables) {
        return new EvaluationContext(null, null, null, null, variables, null, null, () -> "test-timestamp");
    }

}
