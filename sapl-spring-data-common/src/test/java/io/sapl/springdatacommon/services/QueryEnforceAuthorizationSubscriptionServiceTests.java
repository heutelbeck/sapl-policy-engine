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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.spring.method.metadata.QueryEnforce;
import io.sapl.springdatacommon.database.R2dbcMethodInvocation;

class QueryEnforceAuthorizationSubscriptionServiceTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BeanFactory               beanFactoryMock           = mock(BeanFactory.class);
    private SecurityExpressionService securityExpressionService = mock(SecurityExpressionService.class);
    private SecurityContext           securityContextMock       = mock(SecurityContext.class);
    private Authentication            authenticationMock        = mock(Authentication.class);
    private Expression                expressionMock            = mock(Expression.class);

    @Test
    void when_getAuthorizationSubscription_then_throwJsonParseException() {
        try (MockedConstruction<SpelExpressionParser> mockedConstructionSpelExpressionParser = mockConstruction(
                SpelExpressionParser.class)) {
            try (MockedConstruction<StandardEvaluationContext> mockedConstructionStandardEvaluationContext = mockConstruction(
                    StandardEvaluationContext.class)) {
                // GIVEN
                final var methodInvocation = new R2dbcMethodInvocation("findById",
                        new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("20")), null);
                final var queryEnforce     = AnnotationUtils.findAnnotation(methodInvocation.getMethod(),
                        QueryEnforce.class);

                final var service = new QueryEnforceAuthorizationSubscriptionService(beanFactoryMock,
                        securityExpressionService);

                // WHEN
                when(securityContextMock.getAuthentication()).thenReturn(null);
                SecurityContextHolder.setContext(securityContextMock);

                // THEN
                assertThrows(JsonParseException.class, () -> {
                    service.getAuthorizationSubscription(methodInvocation, queryEnforce);
                });

            }
        }
    }

    @Test
    void when_getAuthorizationSubscription_then_throwNoSuchMethodException() {
        try (MockedConstruction<SpelExpressionParser> mockedConstructionSpelExpressionParser = mockConstruction(
                SpelExpressionParser.class)) {
            try (MockedConstruction<StandardEvaluationContext> mockedConstructionStandardEvaluationContext = mockConstruction(
                    StandardEvaluationContext.class)) {
                // GIVEN
                final var methodInvocation = new R2dbcMethodInvocation("findByIdBefore",
                        new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("20")), null);
                final var queryEnforce     = AnnotationUtils.findAnnotation(methodInvocation.getMethod(),
                        QueryEnforce.class);

                final var service = new QueryEnforceAuthorizationSubscriptionService(beanFactoryMock,
                        securityExpressionService);

                final var spelExpressionParserMock      = mockedConstructionSpelExpressionParser.constructed().get(0);
                final var standardEvaluationContextMock = mockedConstructionStandardEvaluationContext.constructed()
                        .get(0);

                // WHEN
                doNothing().when(standardEvaluationContextMock).registerFunction(anyString(), any(Method.class));
                when(spelExpressionParserMock.parseExpression(anyString())).thenReturn(expressionMock);
                when(expressionMock.getValue(any(StandardEvaluationContext.class), eq(String.class)))
                        .thenReturn("subject");
                when(expressionMock.getValue(any(StandardEvaluationContext.class), eq(Object.class)))
                        .thenReturn("resource");
                doNothing().when(standardEvaluationContextMock).setVariable(anyString(), any(Object.class));
                doNothing().when(standardEvaluationContextMock).setBeanResolver(any(BeanResolver.class));

                // THEN
                assertThrows(NoSuchMethodException.class, () -> {
                    service.getAuthorizationSubscription(methodInvocation, queryEnforce);
                });
            }
        }
    }

    @Test
    void when_getAuthorizationSubscription_then_returnAuthSub1() throws JsonProcessingException {
        AuthorizationSubscription generalProtectionR2dbcPersonRepository = AuthorizationSubscription.of("environment",
                "protection", "resource", MAPPER.readTree("{\"testNode\":\"testValue\"}"));

        try (MockedConstruction<SpelExpressionParser> mockedConstructionSpelExpressionParser = mockConstruction(
                SpelExpressionParser.class)) {
            try (MockedConstruction<StandardEvaluationContext> mockedConstructionStandardEvaluationContext = mockConstruction(
                    StandardEvaluationContext.class)) {
                // GIVEN
                final var methodInvocation = new R2dbcMethodInvocation("findAllByFirstnameAndAgeBefore",
                        new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("20", 20)), null);
                final var queryEnforce     = AnnotationUtils.findAnnotation(methodInvocation.getMethod(),
                        QueryEnforce.class);

                final var service = new QueryEnforceAuthorizationSubscriptionService(beanFactoryMock,
                        securityExpressionService);

                final var spelExpressionParserMock      = mockedConstructionSpelExpressionParser.constructed().get(0);
                final var standardEvaluationContextMock = mockedConstructionStandardEvaluationContext.constructed()
                        .get(0);

                // WHEN
                when(spelExpressionParserMock.parseExpression(anyString())).thenReturn(expressionMock);
                when(expressionMock.getValue(any(EvaluationContext.class), eq(String.class))).thenReturn("environment");
                doNothing().when(standardEvaluationContextMock).registerFunction(anyString(), any(Method.class));
                doNothing().when(standardEvaluationContextMock).setVariable(anyString(), any(Object.class));
                when(spelExpressionParserMock.parseExpression(anyString())).thenReturn(expressionMock);
                when(expressionMock.getValue(any(StandardEvaluationContext.class), anyString())).thenReturn("subject");
                when(expressionMock.getValue(any(StandardEvaluationContext.class), eq(Object.class)))
                        .thenReturn("resource");
                doNothing().when(standardEvaluationContextMock).setVariable(anyString(), any(Object.class));
                doNothing().when(standardEvaluationContextMock).setBeanResolver(any(BeanResolver.class));

                when(securityContextMock.getAuthentication()).thenReturn(null);
                SecurityContextHolder.setContext(securityContextMock);

                // THEN
                final var authSubResult = service.getAuthorizationSubscription(methodInvocation, queryEnforce);

                compareTwoAuthSubs(generalProtectionR2dbcPersonRepository, authSubResult);
            }
        }
    }

    @Test
    void when_getAuthorizationSubscription_then_returnAuthSub2() {
        AuthorizationSubscription generalProtectionR2dbcPersonRepository = AuthorizationSubscription.of("subject",
                "protection", "resource", "resource");

        try (MockedConstruction<SpelExpressionParser> mockedConstructionSpelExpressionParser = mockConstruction(
                SpelExpressionParser.class)) {
            try (MockedConstruction<StandardEvaluationContext> mockedConstructionStandardEvaluationContext = mockConstruction(
                    StandardEvaluationContext.class)) {
                // GIVEN
                final var methodInvocation = new R2dbcMethodInvocation("findAllByAgeAfterAndFirstname",
                        new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(20, "20")), null);
                final var queryEnforce     = AnnotationUtils.findAnnotation(methodInvocation.getMethod(),
                        QueryEnforce.class);

                final var service = new QueryEnforceAuthorizationSubscriptionService(beanFactoryMock,
                        securityExpressionService);

                final var spelExpressionParserMock      = mockedConstructionSpelExpressionParser.constructed().get(0);
                final var standardEvaluationContextMock = mockedConstructionStandardEvaluationContext.constructed()
                        .get(0);

                // WHEN
                doNothing().when(standardEvaluationContextMock).registerFunction(anyString(), any(Method.class));
                when(spelExpressionParserMock.parseExpression(anyString())).thenReturn(expressionMock);
                when(expressionMock.getValue(any(StandardEvaluationContext.class), eq(String.class)))
                        .thenReturn("subject");
                when(expressionMock.getValue(any(StandardEvaluationContext.class), eq(Object.class)))
                        .thenReturn("resource");
                doNothing().when(standardEvaluationContextMock).setVariable(anyString(), any(Object.class));
                doNothing().when(standardEvaluationContextMock).setVariable(anyString(), any(String.class));
                doNothing().when(standardEvaluationContextMock).setBeanResolver(any(BeanResolver.class));

                when(securityContextMock.getAuthentication()).thenReturn(null);
                SecurityContextHolder.setContext(securityContextMock);

                // THEN
                final var authSubResult = service.getAuthorizationSubscription(methodInvocation, queryEnforce);

                compareTwoAuthSubs(generalProtectionR2dbcPersonRepository, authSubResult);
            }
        }
    }

    @Test
    void when_getAuthorizationSubscription_then_returnAuthSub3() {
        try (MockedConstruction<SpelExpressionParser> mockedConstructionSpelExpressionParser = mockConstruction(
                SpelExpressionParser.class)) {
            try (MockedConstruction<StandardEvaluationContext> mockedConstructionStandardEvaluationContext = mockConstruction(
                    StandardEvaluationContext.class)) {
                // GIVEN
                final var methodInvocation = new R2dbcMethodInvocation("findByIdBeforeAndFirstname",
                        new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(20, "20")), null);
                final var queryEnforce     = AnnotationUtils.findAnnotation(methodInvocation.getMethod(),
                        QueryEnforce.class);

                final var service = new QueryEnforceAuthorizationSubscriptionService(beanFactoryMock,
                        securityExpressionService);

                final var spelExpressionParserMock      = mockedConstructionSpelExpressionParser.constructed().get(0);
                final var standardEvaluationContextMock = mockedConstructionStandardEvaluationContext.constructed()
                        .get(0);

                // WHEN
                doNothing().when(standardEvaluationContextMock).registerFunction(anyString(), any(Method.class));
                when(spelExpressionParserMock.parseExpression(anyString())).thenReturn(expressionMock);
                when(expressionMock.getValue(any(StandardEvaluationContext.class), eq(String.class)))
                        .thenReturn("subject");
                when(expressionMock.getValue(any(StandardEvaluationContext.class), eq(Object.class)))
                        .thenReturn("resource");
                doNothing().when(standardEvaluationContextMock).setVariable(anyString(), any(Object.class));
                doNothing().when(standardEvaluationContextMock).setBeanResolver(any(BeanResolver.class));

                // THEN
                assertThrows(ClassNotFoundException.class, () -> {
                    service.getAuthorizationSubscription(methodInvocation, queryEnforce);
                });
            }
        }
    }

    @Test
    void when_getAuthorizationSubscription_then_returnAuthSub4() {
        try (MockedConstruction<SpelExpressionParser> mockedConstructionSpelExpressionParser = mockConstruction(
                SpelExpressionParser.class)) {
            try (MockedConstruction<StandardEvaluationContext> mockedConstructionStandardEvaluationContext = mockConstruction(
                    StandardEvaluationContext.class)) {
                // GIVEN
                final var methodInvocation = new R2dbcMethodInvocation("findByIdBeforeAndFirstname",
                        new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(20, "20")), null);
                final var queryEnforce     = AnnotationUtils.findAnnotation(methodInvocation.getMethod(),
                        QueryEnforce.class);

                final var service = new QueryEnforceAuthorizationSubscriptionService(beanFactoryMock,
                        securityExpressionService);

                final var spelExpressionParserMock      = mockedConstructionSpelExpressionParser.constructed().get(0);
                final var standardEvaluationContextMock = mockedConstructionStandardEvaluationContext.constructed()
                        .get(0);

                // WHEN
                doNothing().when(standardEvaluationContextMock).registerFunction(anyString(), any(Method.class));
                when(securityExpressionService.evaluateSpelVariables(anyString())).thenReturn("");
                when(securityExpressionService.evaluateSpelMethods(anyString(), any(MethodInvocation.class)))
                        .thenReturn("");
                when(spelExpressionParserMock.parseExpression(anyString())).thenReturn(expressionMock);
                when(expressionMock.getValue(any(StandardEvaluationContext.class), eq(String.class)))
                        .thenReturn("subject");
                when(expressionMock.getValue(any(StandardEvaluationContext.class), eq(Object.class)))
                        .thenReturn("resource");
                doNothing().when(standardEvaluationContextMock).setVariable(anyString(), any(Object.class));
                doNothing().when(standardEvaluationContextMock).setBeanResolver(any(BeanResolver.class));

                when(securityContextMock.getAuthentication()).thenReturn(authenticationMock);
                when(authenticationMock.getName()).thenReturn("TestUser");
                SecurityContextHolder.setContext(securityContextMock);

                // THEN
                assertThrows(ClassNotFoundException.class, () -> {
                    service.getAuthorizationSubscription(methodInvocation, queryEnforce);
                });
            }
        }
    }

    @Test
    void when_getAuthorizationSubscription_then_returnNull() {

        try (MockedConstruction<SpelExpressionParser> mockedConstructionSpelExpressionParser = mockConstruction(
                SpelExpressionParser.class)) {
            try (MockedConstruction<StandardEvaluationContext> mockedConstructionStandardEvaluationContext = mockConstruction(
                    StandardEvaluationContext.class)) {
                // GIVEN
                final var methodInvocation = new R2dbcMethodInvocation("findByIdBeforeAndFirstname",
                        new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(20, "20")), null);

                final var service = new QueryEnforceAuthorizationSubscriptionService(beanFactoryMock,
                        securityExpressionService);

                // THEN
                final var authSubResult = service.getAuthorizationSubscription(methodInvocation, null);

                assertEquals(null, authSubResult);
            }
        }
    }

    private void compareTwoAuthSubs(AuthorizationSubscription first, AuthorizationSubscription second) {
        assertEquals(first.getSubject(), second.getSubject());
        assertEquals(first.getAction(), second.getAction());
        assertEquals(first.getResource(), second.getResource());
        assertEquals(first.getEnvironment(), second.getEnvironment());
    }
}
