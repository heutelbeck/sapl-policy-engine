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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.spring.data.database.R2dbcMethodInvocation;
import io.sapl.spring.method.metadata.QueryEnforce;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryEnforceAuthorizationSubscriptionServiceTests {

    private ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider;
    private ObjectProvider<ObjectMapper>                    mapperProvider;
    private ObjectProvider<GrantedAuthorityDefaults>        defaultsProvider;
    private ApplicationContext                              applicationContext;
    private ObjectMapper                                    objectMapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        expressionHandlerProvider = mock(ObjectProvider.class);
        mapperProvider            = mock(ObjectProvider.class);
        defaultsProvider          = mock(ObjectProvider.class);
        applicationContext        = mock(ApplicationContext.class);
        objectMapper              = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        var expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setApplicationContext(applicationContext);

        when(expressionHandlerProvider.getIfAvailable(any())).thenReturn(expressionHandler);
        when(mapperProvider.getIfAvailable(any())).thenReturn(objectMapper);

        // Set up authentication
        var authentication  = new TestingAuthenticationToken("testUser", "password", "ROLE_USER");
        var securityContext = new SecurityContextImpl(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void when_enforceAnnotationIsNull_then_returnNull() {
        // GIVEN
        var methodInvocation = new R2dbcMethodInvocation("findById", new ArrayList<>(List.of(String.class)),
                new ArrayList<>(List.of("20")), null);
        var service          = new QueryEnforceAuthorizationSubscriptionService(expressionHandlerProvider,
                mapperProvider, defaultsProvider, applicationContext);

        // WHEN
        var result = service.getAuthorizationSubscription(methodInvocation, null);

        // THEN
        assertNull(result);
    }

    @Test
    void when_noSubjectExpression_then_useAuthenticationAsDefault() {
        // GIVEN
        var methodInvocation = new R2dbcMethodInvocation("findByNoExpressions", new ArrayList<>(), new ArrayList<>(),
                null);
        var queryEnforce     = AnnotationUtils.findAnnotation(methodInvocation.getMethod(), QueryEnforce.class);
        var service          = new QueryEnforceAuthorizationSubscriptionService(expressionHandlerProvider,
                mapperProvider, defaultsProvider, applicationContext);

        // WHEN
        var result = service.getAuthorizationSubscription(methodInvocation, queryEnforce);

        // THEN
        assertNotNull(result);
        assertNotNull(result.subject());
        // Subject should be an ObjectValue (serialized authentication object)
        assertInstanceOf(ObjectValue.class, result.subject());
    }

    @Test
    void when_noActionExpression_then_useJavaMethodInfo() {
        // GIVEN
        var methodInvocation = new R2dbcMethodInvocation("findByNoExpressions", new ArrayList<>(), new ArrayList<>(),
                null);
        var queryEnforce     = AnnotationUtils.findAnnotation(methodInvocation.getMethod(), QueryEnforce.class);
        var service          = new QueryEnforceAuthorizationSubscriptionService(expressionHandlerProvider,
                mapperProvider, defaultsProvider, applicationContext);

        // WHEN
        var result = service.getAuthorizationSubscription(methodInvocation, queryEnforce);

        // THEN
        assertNotNull(result);
        assertNotNull(result.action());
        // Action should be an ObjectValue with java method info
        assertInstanceOf(ObjectValue.class, result.action());
    }

    @Test
    void when_domainTypeProvided_then_includeEntityTypeInResource() {
        // GIVEN
        var methodInvocation = new R2dbcMethodInvocation("findByNoExpressions", new ArrayList<>(), new ArrayList<>(),
                null);
        var queryEnforce     = AnnotationUtils.findAnnotation(methodInvocation.getMethod(), QueryEnforce.class);
        var service          = new QueryEnforceAuthorizationSubscriptionService(expressionHandlerProvider,
                mapperProvider, defaultsProvider, applicationContext);

        // WHEN
        var result = service.getAuthorizationSubscription(methodInvocation, queryEnforce, String.class);

        // THEN
        assertNotNull(result);
        assertNotNull(result.resource());
        assertInstanceOf(ObjectValue.class, result.resource());
        var resourceValue = (ObjectValue) result.resource();
        // Check entityType is present
        assertTrue(resourceValue.containsKey("entityType"));
    }

    @Test
    void when_noResourceExpression_then_useJavaMethodInfo() {
        // GIVEN
        var methodInvocation = new R2dbcMethodInvocation("findByNoExpressions", new ArrayList<>(), new ArrayList<>(),
                null);
        var queryEnforce     = AnnotationUtils.findAnnotation(methodInvocation.getMethod(), QueryEnforce.class);
        var service          = new QueryEnforceAuthorizationSubscriptionService(expressionHandlerProvider,
                mapperProvider, defaultsProvider, applicationContext);

        // WHEN
        var result = service.getAuthorizationSubscription(methodInvocation, queryEnforce);

        // THEN
        assertNotNull(result);
        assertNotNull(result.resource());
        assertInstanceOf(ObjectValue.class, result.resource());
    }

    @Test
    void when_noEnvironmentExpression_then_returnUndefined() {
        // GIVEN
        var methodInvocation = new R2dbcMethodInvocation("findByNoExpressions", new ArrayList<>(), new ArrayList<>(),
                null);
        var queryEnforce     = AnnotationUtils.findAnnotation(methodInvocation.getMethod(), QueryEnforce.class);
        var service          = new QueryEnforceAuthorizationSubscriptionService(expressionHandlerProvider,
                mapperProvider, defaultsProvider, applicationContext);

        // WHEN
        var result = service.getAuthorizationSubscription(methodInvocation, queryEnforce);

        // THEN
        assertNotNull(result);
        // Environment should be undefined when not specified
        assertInstanceOf(UndefinedValue.class, result.environment());
    }

    @Test
    void when_methodHasArguments_then_includeInAction() {
        // GIVEN
        var methodInvocation = new R2dbcMethodInvocation("findByNoExpressions", new ArrayList<>(), new ArrayList<>(),
                null);
        var queryEnforce     = AnnotationUtils.findAnnotation(methodInvocation.getMethod(), QueryEnforce.class);
        var service          = new QueryEnforceAuthorizationSubscriptionService(expressionHandlerProvider,
                mapperProvider, defaultsProvider, applicationContext);

        // WHEN
        var result = service.getAuthorizationSubscription(methodInvocation, queryEnforce);

        // THEN
        assertNotNull(result);
        assertInstanceOf(ObjectValue.class, result.action());
    }

}
