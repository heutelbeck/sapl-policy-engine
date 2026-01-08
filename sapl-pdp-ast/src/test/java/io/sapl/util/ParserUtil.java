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

import io.sapl.compiler.SAPLCompiler;
import io.sapl.grammar.antlr.SAPLParser.ConditionStatementContext;
import io.sapl.grammar.antlr.SAPLParser.ExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.StatementContext;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Utility class for parsing SAPL expressions and fragments using ANTLR parser.
 */
@UtilityClass
public class ParserUtil {

    private static final SAPLCompiler PARSER = new SAPLCompiler();

    /**
     * Parses an expression from SAPL source.
     *
     * @param sapl the expression source
     * @return the parsed ExpressionContext
     */
    public static ExpressionContext expression(String sapl) {
        // Wrap in policy condition to parse the expression
        val policySource  = """
                policy "temp"
                permit
                where
                    %s;
                """.formatted(sapl);
        val document      = PARSER.parse(policySource);
        val policyElement = document.policyElement();
        if (policyElement instanceof PolicyOnlyElementContext policyOnly) {
            val policy = policyOnly.policy();
            if (policy.policyBody() != null && !policy.policyBody().statements.isEmpty()) {
                val statement = policy.policyBody().statements.getFirst();
                if (statement instanceof ConditionStatementContext condition) {
                    return condition.expression();
                }
            }
        }
        throw new IllegalArgumentException("Could not parse expression: " + sapl);
    }

    /**
     * Parses a statement from SAPL source.
     *
     * @param sapl the statement source
     * @return the parsed StatementContext
     */
    public static StatementContext statement(String sapl) {
        // Wrap in policy body to parse the statement
        val policySource  = """
                policy "temp"
                permit
                where
                    %s
                """.formatted(sapl);
        val document      = PARSER.parse(policySource);
        val policyElement = document.policyElement();
        if (policyElement instanceof PolicyOnlyElementContext policyOnly) {
            val policy = policyOnly.policy();
            if (policy.policyBody() != null && !policy.policyBody().statements.isEmpty()) {
                return policy.policyBody().statements.getFirst();
            }
        }
        throw new IllegalArgumentException("Could not parse statement: " + sapl);
    }
}
