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
package io.sapl.springdatacommon.services;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class CustomMethodSecurityExpressionHandler {

    private ExpressionParser          expressionParser = new SpelExpressionParser();
    private StandardEvaluationContext context          = new StandardEvaluationContext();

    public Object evaluateExpression(String expressionString) {
        final var authentication     = SecurityContextHolder.getContext().getAuthentication();
        final var evaluationContext  = createEvaluationContext(authentication);
        var       expressionWithHash = expressionString;

        if (!expressionString.startsWith("#")) {
            expressionWithHash = "#" + expressionString;
        }

        return expressionParser.parseExpression(expressionWithHash).getValue(evaluationContext);
    }

    private StandardEvaluationContext createEvaluationContext(Authentication authentication) {
        context.setVariable("authentication", authentication);

        return context;
    }
}
