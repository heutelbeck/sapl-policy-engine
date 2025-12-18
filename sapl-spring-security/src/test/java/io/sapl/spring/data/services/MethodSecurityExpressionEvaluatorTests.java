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
package io.sapl.spring.data.services;

import io.sapl.spring.data.utils.TestUtils;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MethodSecurityExpressionEvaluatorTests {

    @Mock
    private ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProviderMock;
    private MethodSecurityExpressionHandler                 methodSecurityExpressionHandlerMock = mock(
            MethodSecurityExpressionHandler.class);
    private Expression                                      expressionMock                      = mock(
            Expression.class);
    private SecurityContext                                 securityContextMock                 = mock(
            SecurityContext.class);
    private Authentication                                  authenticationMock                  = mock(
            Authentication.class);
    private MethodInvocation                                methodInvocationMock                = mock(
            MethodInvocation.class);
    private EvaluationContext                               evaluationContextMock               = mock(
            EvaluationContext.class);

    @Test
    void when_evaluate_then_returnEvaluatedValue() {
        try (MockedConstruction<SpelExpressionParser> mockedConstructionSpelExpressionParser = mockConstruction(
                SpelExpressionParser.class)) {

            // GIVEN
            final var expressionEvaluator      = new MethodSecurityExpressionEvaluator(expressionHandlerProviderMock);
            final var mockSpelExpressionParser = mockedConstructionSpelExpressionParser.constructed().getFirst();

            // WHEN
            when(securityContextMock.getAuthentication()).thenReturn(authenticationMock);
            when(authenticationMock.getName()).thenReturn("TestUser");
            SecurityContextHolder.setContext(securityContextMock);

            when(expressionHandlerProviderMock.getObject()).thenReturn(methodSecurityExpressionHandlerMock);
            when(methodSecurityExpressionHandlerMock.createEvaluationContext(authenticationMock, methodInvocationMock))
                    .thenReturn(evaluationContextMock);
            when(mockSpelExpressionParser.parseExpression(anyString())).thenReturn(expressionMock);
            when(expressionMock.getValue(any(EvaluationContext.class), eq(Boolean.class))).thenReturn(true);

            // THEN
            assertTrue(expressionEvaluator.evaluate("Test", methodInvocationMock));

        }
    }

    @Test
    void when_evaluate_then_throwAccessDeniedException() {
        try (MockedConstruction<SpelExpressionParser> mockedConstructionSpelExpressionParser = mockConstruction(
                SpelExpressionParser.class)) {

            // GIVEN
            final var expressionEvaluator      = new MethodSecurityExpressionEvaluator(expressionHandlerProviderMock);
            final var mockSpelExpressionParser = mockedConstructionSpelExpressionParser.constructed().getFirst();

            // WHEN
            when(securityContextMock.getAuthentication()).thenReturn(authenticationMock);
            when(authenticationMock.getName()).thenReturn("TestUser");
            SecurityContextHolder.setContext(securityContextMock);

            when(expressionHandlerProviderMock.getObject()).thenReturn(methodSecurityExpressionHandlerMock);
            when(methodSecurityExpressionHandlerMock.createEvaluationContext(authenticationMock, methodInvocationMock))
                    .thenReturn(evaluationContextMock);
            when(mockSpelExpressionParser.parseExpression(anyString())).thenReturn(expressionMock);
            when(expressionMock.getValue(any(EvaluationContext.class), eq(Boolean.class)))
                    .thenThrow(NullPointerException.class);

            final var errorMessage = """
                    		Expressiondetectedbutcouldnotbeparsed:Test
                    """;

            // THEN
            final var accessDeniedException = assertThrows(AccessDeniedException.class, () -> {
                expressionEvaluator.evaluate("Test", methodInvocationMock);
            });
            assertEquals(TestUtils.removeWhitespace(errorMessage),
                    TestUtils.removeWhitespace(accessDeniedException.getMessage()));
            assertEquals(NullPointerException.class, accessDeniedException.getCause().getClass());
        }
    }

}
