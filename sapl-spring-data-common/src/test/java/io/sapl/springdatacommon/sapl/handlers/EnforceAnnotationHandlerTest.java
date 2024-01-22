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
package io.sapl.springdatacommon.sapl.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.sapl.springdatacommon.sapl.Enforce;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.AnnotationUtils;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.springdatacommon.database.R2dbcMethodInvocationForTesting;
import io.sapl.springdatacommon.database.R2dbcTestService;
import io.sapl.springdatacommon.handlers.EnforceAnnotationHandler;

@SpringBootTest(classes = { EnforceAnnotationHandler.class, R2dbcTestService.class })
class EnforceAnnotationHandlerTest {

    @Autowired
    EnforceAnnotationHandler enforceAnnotationHandler;

    @Autowired
    R2dbcTestService r2dbcTestService;

    @MockBean
    BeanFactory beanFactoryMock;

    @Test
    void when_methodHasAnEnforceAnnotationWithStaticValues_then_enforceAnnotation() {
        // GIVEN
        var expectedResult    = AuthorizationSubscription.of("subject", "general_protection_reactive_r2dbc_repository",
                "resource", "environment");
        var methodInvocation  = new R2dbcMethodInvocationForTesting("findAllByFirstname",
                new ArrayList<>(List.of(String.class)), null, null);
        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());

        // WHEN
        var result = enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation);

        // THEN
        assertEquals(result, expectedResult);
    }

    @Test
    void when_methodHasNoEnforceAnnotationWithStaticValues_then_returnNull() {
        // GIVEN
        var methodInvocation  = new R2dbcMethodInvocationForTesting("findAllByAge", new ArrayList<>(List.of(int.class)),
                null, null);
        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());

        // WHEN
        var result = enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation);

        // THEN
        assertNull(result);
    }

    @Test
    void when_methodHasAnEnforceAnnotationWithStaticClassInEvaluationContext_then_enforceAnnotation() {
        // GIVEN
        when(beanFactoryMock.getBean(R2dbcTestService.class)).thenReturn(r2dbcTestService);
        MockitoAnnotations.openMocks(beanFactoryMock);
        var expectedResult   = AuthorizationSubscription.of("test value",
                "general_protection_reactive_r2dbc_repository", "Static class set: field, test value", 56);
        var methodInvocation = new R2dbcMethodInvocationForTesting("findAllByAgeAfterAndFirstname",
                new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(18, "test value")), null);
        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());

        // WHEN
        var result = enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation);

        // THEN
        assertEquals(result, expectedResult);
    }

    @Test
    void when_methodHasAnEnforceAnnotationWithStaticClassInEvaluationContextPart2_then_enforceAnnotation() {
        // GIVEN
        var environment = JsonNodeFactory.instance.objectNode().put("testNode", "testValue");

        var expectedResult    = AuthorizationSubscription.of("firstname",
                "general_protection_reactive_r2dbc_repository", "Static class set: firstname, test value", environment);
        var methodInvocation  = new R2dbcMethodInvocationForTesting("findAllByFirstnameAndAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("firstname", 4)), null);
        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());

        // WHEN
        var result = enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation);

        // THEN
        assertEquals(result, expectedResult);
    }

    @Test
    void when_methodHasAnEnforceAnnotationAndJsonStringIsNotValid_then_throwParseException() {
        // GIVEN
        var methodInvocation  = new R2dbcMethodInvocationForTesting("findById", new ArrayList<>(List.of(String.class)),
                new ArrayList<>(List.of()), null);
        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());

        // WHEN

        // THEN
        assertThrows(JsonParseException.class,
                () -> enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation));
    }

    @Test
    void when_methodHasAnEnforceAnnotationAndMethodOfStaticClassIsAboutToUseButNoStaticClassInAnnotationAttached_then_throwNoSuchMethodException() {
        // GIVEN
        var methodInvocation  = new R2dbcMethodInvocationForTesting("findByIdBefore",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of()), null);
        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());

        // WHEN

        // THEN
        assertThrows(NoSuchMethodException.class,
                () -> enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation));
    }

    @Test
    void when_methodHasAnEnforceAnnotationAndMethodOfStaticClassIsNotTheRightClass_then_throwNoSuchMethodException() {
        // GIVEN
        var methodInvocation  = new R2dbcMethodInvocationForTesting("findByIdAfter",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of()), null);
        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());

        // WHEN

        // THEN
        assertThrows(NoSuchMethodException.class,
                () -> enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation));
    }

    @Test
    void when_methodHasAnEnforceAnnotationAndMethodOfStaticClassIsAboutToUseButMethodNotExist_then_throwNoSuchMethodException() {
        // GIVEN
        var methodInvocation  = new R2dbcMethodInvocationForTesting("findByIdAndAge",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of()), null);
        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());

        // WHEN

        // THEN
        assertThrows(NoSuchMethodException.class,
                () -> enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation));
    }

    private Enforce getEnforceAnnotation(Method method) {
        var enforceAnnotation = AnnotationUtils.findAnnotation(method, Enforce.class);

        return enforceAnnotation.orElse(null);
    }
}
