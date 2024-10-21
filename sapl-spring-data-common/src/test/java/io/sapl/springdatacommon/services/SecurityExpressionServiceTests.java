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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class SecurityExpressionServiceTests {

    @Test
    void when_evaluateSpelMethods_then_returnEvaluatedValue() {
        try (MockedConstruction<CustomMethodSecurityExpressionHandler> mockedConstructionStandardEvaluationContext = mockConstruction(
                CustomMethodSecurityExpressionHandler.class)) {
            // GIVEN
            final var expression                            = "test string permitAll() method";
            final var expressionPart                        = "permitAll()";
            final var resultExpression                      = "test string true method";
            final var methodSecurityExpressionEvaluatorMock = mock(MethodSecurityExpressionEvaluator.class);
            final var methodInvocationMock                  = mock(MethodInvocation.class);

            // WHEN
            final var expressionService = new SecurityExpressionService(methodSecurityExpressionEvaluatorMock);

            when(methodSecurityExpressionEvaluatorMock.evaluate(eq(expressionPart), any(MethodInvocation.class)))
                    .thenReturn(true);

            final var result = expressionService.evaluateSpelMethods(expression, methodInvocationMock);

            // THEN
            assertEquals(result, resultExpression);
            verify(methodSecurityExpressionEvaluatorMock, times(1)).evaluate(eq(expressionPart),
                    any(MethodInvocation.class));
        }
    }

    @Test
    void when_evaluateSpelMethods_then_returnEvaluatedOfTwoValues() {
        try (MockedConstruction<CustomMethodSecurityExpressionHandler> mockedConstructionStandardEvaluationContext = mockConstruction(
                CustomMethodSecurityExpressionHandler.class)) {
            // GIVEN
            final var expression      = "test string hasAuthority() method, and has hasRole()";
            final var expressionPart1 = "hasAuthority()";
            final var expressionPart2 = "hasRole()";

            final var resultExpression                      = "test string true method, and has false";
            final var methodSecurityExpressionEvaluatorMock = mock(MethodSecurityExpressionEvaluator.class);
            final var methodInvocationMock                  = mock(MethodInvocation.class);

            // WHEN
            final var expressionService = new SecurityExpressionService(methodSecurityExpressionEvaluatorMock);

            when(methodSecurityExpressionEvaluatorMock.evaluate(eq(expressionPart1), any(MethodInvocation.class)))
                    .thenReturn(true);
            when(methodSecurityExpressionEvaluatorMock.evaluate(eq(expressionPart2), any(MethodInvocation.class)))
                    .thenReturn(false);

            final var result = expressionService.evaluateSpelMethods(expression, methodInvocationMock);

            // THEN
            assertEquals(result, resultExpression);
            verify(methodSecurityExpressionEvaluatorMock, times(2)).evaluate(anyString(), any(MethodInvocation.class));
        }
    }

    @Test
    void when_evaluateSpelMethods_then_returnOriginalExpressionBecauseNoMethodsWereFoundInInput() {
        try (MockedConstruction<CustomMethodSecurityExpressionHandler> mockedConstructionStandardEvaluationContext = mockConstruction(
                CustomMethodSecurityExpressionHandler.class)) {
            // GIVEN
            final var expression                            = "test string method";
            final var resultExpression                      = "test string method";
            final var methodSecurityExpressionEvaluatorMock = mock(MethodSecurityExpressionEvaluator.class);
            final var methodInvocationMock                  = mock(MethodInvocation.class);

            // WHEN
            final var expressionService = new SecurityExpressionService(methodSecurityExpressionEvaluatorMock);
            final var result            = expressionService.evaluateSpelMethods(expression, methodInvocationMock);

            // THEN
            assertEquals(result, resultExpression);
            verify(methodSecurityExpressionEvaluatorMock, times(0)).evaluate(anyString(), any(MethodInvocation.class));
        }
    }

    @Test
    void when_evaluateSpelMethods_then_returnOriginalExpressionButEndIndexIsNoBracket() {
        try (MockedConstruction<CustomMethodSecurityExpressionHandler> mockedConstructionStandardEvaluationContext = mockConstruction(
                CustomMethodSecurityExpressionHandler.class)) {
            // GIVEN
            final var expression                            = "test string isAuthenticated( method";
            final var resultExpression                      = "test string isAuthenticated( method";
            final var methodSecurityExpressionEvaluatorMock = mock(MethodSecurityExpressionEvaluator.class);
            final var methodInvocationMock                  = mock(MethodInvocation.class);

            // WHEN
            final var expressionService = new SecurityExpressionService(methodSecurityExpressionEvaluatorMock);
            final var result            = expressionService.evaluateSpelMethods(expression, methodInvocationMock);

            // THEN
            assertEquals(result, resultExpression);
            verify(methodSecurityExpressionEvaluatorMock, times(0)).evaluate(anyString(), any(MethodInvocation.class));
        }
    }

    @Test
    void when_evaluateSpelMethods_then_returnInputIfInputIsEmpty() {
        try (MockedConstruction<CustomMethodSecurityExpressionHandler> mockedConstructionStandardEvaluationContext = mockConstruction(
                CustomMethodSecurityExpressionHandler.class)) {
            // GIVEN
            final var expression                            = "";
            final var resultExpression                      = "";
            final var methodSecurityExpressionEvaluatorMock = mock(MethodSecurityExpressionEvaluator.class);
            final var methodInvocationMock                  = mock(MethodInvocation.class);

            // WHEN
            final var expressionService = new SecurityExpressionService(methodSecurityExpressionEvaluatorMock);
            final var result            = expressionService.evaluateSpelMethods(expression, methodInvocationMock);

            // THEN
            assertEquals(result, resultExpression);
            verify(methodSecurityExpressionEvaluatorMock, times(0)).evaluate(anyString(), any(MethodInvocation.class));
        }
    }

    @Test
    void when_evaluateSpelVariables_then_returnEvaluatedValue() {
        try (MockedConstruction<CustomMethodSecurityExpressionHandler> mockedConstructionStandardEvaluationContext = mockConstruction(
                CustomMethodSecurityExpressionHandler.class)) {
            // GIVEN
            final var expression                  = "test string authentication.getName() method";
            final var expressionPart              = "authentication.getName()";
            final var returnValueOfExpressionPart = "USER";
            final var resultExpression            = "test string USER method";

            final var methodSecurityExpressionEvaluatorMock = mock(MethodSecurityExpressionEvaluator.class);

            // WHEN
            final var expressionService                         = new SecurityExpressionService(
                    methodSecurityExpressionEvaluatorMock);
            final var customMethodSecurityExpressionHandlerMock = mockedConstructionStandardEvaluationContext
                    .constructed().get(0);

            when(customMethodSecurityExpressionHandlerMock.evaluateExpression(expressionPart))
                    .thenReturn(returnValueOfExpressionPart);

            final var result = expressionService.evaluateSpelVariables(expression);

            // THEN
            assertEquals(result, resultExpression);
            verify(customMethodSecurityExpressionHandlerMock, times(1)).evaluateExpression(expressionPart);
        }
    }

    @Test
    void when_evaluateSpelVariables_then_returnEvaluatedOfTwoValues() {
        try (MockedConstruction<CustomMethodSecurityExpressionHandler> mockedConstructionStandardEvaluationContext = mockConstruction(
                CustomMethodSecurityExpressionHandler.class)) {
            // GIVEN
            final var expression                   = "test string authentication.getName() method and target";
            final var expressionPart1              = "authentication.getName()";
            final var expressionPart2              = "target";
            final var returnValueOfExpressionPart1 = "USER";
            final var returnValueOfExpressionPart2 = "TargetValue";
            final var resultExpression             = "test string USER method and TargetValue";

            final var methodSecurityExpressionEvaluatorMock = mock(MethodSecurityExpressionEvaluator.class);

            // WHEN
            final var expressionService = new SecurityExpressionService(methodSecurityExpressionEvaluatorMock);

            final var customMethodSecurityExpressionHandlerMock = mockedConstructionStandardEvaluationContext
                    .constructed().get(0);

            when(customMethodSecurityExpressionHandlerMock.evaluateExpression(expressionPart1))
                    .thenReturn(returnValueOfExpressionPart1);
            when(customMethodSecurityExpressionHandlerMock.evaluateExpression(expressionPart2))
                    .thenReturn(returnValueOfExpressionPart2);

            final var result = expressionService.evaluateSpelVariables(expression);

            // THEN
            assertEquals(result, resultExpression);
            verify(customMethodSecurityExpressionHandlerMock, times(2)).evaluateExpression(anyString());
        }
    }

    @Test
    void when_evaluateSpelVariables_then_returnOriginalExpressionBecauseNoVariablesWereFoundInInput() {
        try (MockedConstruction<CustomMethodSecurityExpressionHandler> mockedConstructionStandardEvaluationContext = mockConstruction(
                CustomMethodSecurityExpressionHandler.class)) {
            // GIVEN
            final var expression                            = "test string method";
            final var resultExpression                      = "test string method";
            final var methodSecurityExpressionEvaluatorMock = mock(MethodSecurityExpressionEvaluator.class);
            final var expressionService                     = new SecurityExpressionService(
                    methodSecurityExpressionEvaluatorMock);

            // WHEN
            final var result                                    = expressionService.evaluateSpelVariables(expression);
            final var customMethodSecurityExpressionHandlerMock = mockedConstructionStandardEvaluationContext
                    .constructed().get(0);

            // THEN
            assertEquals(result, resultExpression);
            verify(customMethodSecurityExpressionHandlerMock, times(0)).evaluateExpression(anyString());
        }
    }

    @Test
    void when_evaluateSpelVariables_then_returnOriginalExpressionButEndIndexIsNoBracketOrSpace() {
        try (MockedConstruction<CustomMethodSecurityExpressionHandler> mockedConstructionStandardEvaluationContext = mockConstruction(
                CustomMethodSecurityExpressionHandler.class)) {
            // GIVEN
            final var expression                            = "test string isAuthenticated( method";
            final var resultExpression                      = "test string isAuthenticated( method";
            final var methodSecurityExpressionEvaluatorMock = mock(MethodSecurityExpressionEvaluator.class);
            final var expressionService                     = new SecurityExpressionService(
                    methodSecurityExpressionEvaluatorMock);

            // WHEN
            final var result                                    = expressionService.evaluateSpelVariables(expression);
            final var customMethodSecurityExpressionHandlerMock = mockedConstructionStandardEvaluationContext
                    .constructed().get(0);

            // THEN
            assertEquals(result, resultExpression);
            verify(customMethodSecurityExpressionHandlerMock, times(0)).evaluateExpression(anyString());
        }
    }

    @Test
    void when_evaluateSpelVariables_then_returnInputIfInputIsEmpty() {
        try (MockedConstruction<CustomMethodSecurityExpressionHandler> mockedConstructionStandardEvaluationContext = mockConstruction(
                CustomMethodSecurityExpressionHandler.class)) {
            // GIVEN
            final var expression                            = "";
            final var resultExpression                      = "";
            final var methodSecurityExpressionEvaluatorMock = mock(MethodSecurityExpressionEvaluator.class);
            final var expressionService                     = new SecurityExpressionService(
                    methodSecurityExpressionEvaluatorMock);

            // WHEN
            final var result                                    = expressionService.evaluateSpelVariables(expression);
            final var customMethodSecurityExpressionHandlerMock = mockedConstructionStandardEvaluationContext
                    .constructed().get(0);

            // THEN
            assertEquals(result, resultExpression);
            verify(customMethodSecurityExpressionHandlerMock, times(0)).evaluateExpression(anyString());
        }
    }
}
