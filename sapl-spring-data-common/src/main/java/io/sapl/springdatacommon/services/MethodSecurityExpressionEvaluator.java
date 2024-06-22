/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.core.context.SecurityContextHolder;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MethodSecurityExpressionEvaluator {

    private final ObjectProvider<MethodSecurityExpressionHandler> handler;
    private final ExpressionParser                                parser = new SpelExpressionParser();

    public boolean evaluate(String expression, MethodInvocation invocation) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            return parser.parseExpression(expression)
                    .getValue(handler.getObject().createEvaluationContext(authentication, invocation), Boolean.class);
        } catch (NullPointerException | EvaluationException | ParseException e) {
            throw new AccessDeniedException("Expression detected but could not be parsed: " + expression, e);
        }
    }
}
