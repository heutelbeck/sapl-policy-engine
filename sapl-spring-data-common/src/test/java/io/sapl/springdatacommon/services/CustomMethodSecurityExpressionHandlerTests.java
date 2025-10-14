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

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CustomMethodSecurityExpressionHandlerTests {

    private Expression      expressionMock      = mock(Expression.class);
    private SecurityContext securityContextMock = mock(SecurityContext.class);
    private Authentication  authenticationMock  = mock(Authentication.class);

    @Test
    void when_evaluateExpression_then_evaluateExpression() {
        try (MockedConstruction<SpelExpressionParser> mockedConstructionSpelExpressionParser = mockConstruction(
                SpelExpressionParser.class)) {
            try (MockedConstruction<StandardEvaluationContext> mockedConstructionStandardEvaluationContext = mockConstruction(
                    StandardEvaluationContext.class)) {

                final var expressionHandler = new CustomMethodSecurityExpressionHandler();

                when(securityContextMock.getAuthentication()).thenReturn(authenticationMock);
                when(authenticationMock.getName()).thenReturn("TestUser");
                SecurityContextHolder.setContext(securityContextMock);

                final var mockSpelExpressionParser      = mockedConstructionSpelExpressionParser.constructed().get(0);
                final var mockStandardEvaluationContext = mockedConstructionStandardEvaluationContext.constructed()
                        .get(0);

                when(mockSpelExpressionParser.parseExpression(anyString())).thenReturn(expressionMock);
                when(expressionMock.getValue(any(StandardEvaluationContext.class))).thenReturn("TestValue");

                doNothing().when(mockStandardEvaluationContext).setVariable(anyString(), any(Authentication.class));

                final var result = expressionHandler.evaluateExpression("testExpression");

                assertEquals("TestValue", result);

            }
        }

    }

}
