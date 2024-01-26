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
import static org.mockito.Mockito.mock;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.AnnotationUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.expression.Expression;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.springdatacommon.database.R2dbcMethodInvocation;
import io.sapl.springdatacommon.database.R2dbcTestService;
import io.sapl.springdatacommon.handlers.EnforceAnnotationHandler;
import io.sapl.springdatacommon.sapl.Enforce;

@SpringBootTest(classes = { R2dbcTestService.class })
class EnforceAnnotationHandlerTests {

    @Autowired
    R2dbcTestService r2dbcTestService;

    @Autowired
    BeanFactory beanFactory;

    BeanFactory beanFactoryMock = mock(BeanFactory.class);
    Expression  expressionMock  = mock(Expression.class);

    EnforceAnnotationHandler enforceAnnotationHandler = new EnforceAnnotationHandler(beanFactoryMock);

    @Test
    void when_methodHasAnEnforceAnnotationWithStaticValues_then_enforceAnnotation() {
        // GIVEN
        var expectedResult    = AuthorizationSubscription.of("subject", "general_protection_reactive_r2dbc_repository",
                "resource", "environment");
        var methodInvocation  = new R2dbcMethodInvocation("findAllByFirstname", new ArrayList<>(List.of(String.class)),
                null, null);
        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());

        // WHEN
        var result = enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation);

        // THEN
        assertEquals(result, expectedResult);
    }

    @Test
    void when_methodHasNoEnforceAnnotationWithStaticValues_then_returnNull() {
        // GIVEN
        var methodInvocation  = new R2dbcMethodInvocation("findAllByAge", new ArrayList<>(List.of(int.class)), null,
                null);
        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());

        // WHEN
        var result = enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation);

        // THEN
        assertNull(result);
    }

    @Test
    void when_methodHasNoMethodReferenceWithHashButStaticClass_then_returnAuthSub() {
        // GIVEN
        var expectedResult    = AuthorizationSubscription.of("test", "test", "setResource", "test");
        var methodInvocation  = new R2dbcMethodInvocation("findByIdAfterAndFirstname",
                new ArrayList<>(List.of(int.class, String.class)), null, null);
        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());

        // WHEN
        var result = enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation);

        // THEN
        assertEquals(expectedResult, result);
    }

    @Test
    void when_methodHasNoMethodReferenceWithHashAndNoStaticClass_then_returnAuthSub() {
        // GIVEN
        var methodInvocation = new R2dbcMethodInvocation("findByIdBeforeAndFirstname",
                new ArrayList<>(List.of(int.class, String.class)), null, null);

        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());
        // WHEN

        // THEN
        assertThrows(NullPointerException.class,
                () -> enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation));
    }

    @Test
    void when_methodHasAnMethodReferenceButNoStaticClass_then_throwNoSuchMethodException() {
        // GIVEN
        var methodInvocation  = new R2dbcMethodInvocation("findAllByAgeAfterAndId",
                new ArrayList<>(List.of(int.class, int.class)), new ArrayList<>(List.of(18, 123)), null);
        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());

        // WHEN

        // THEN
        assertThrows(NoSuchMethodException.class,
                () -> enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation));
    }

    @Test
    void when_methodHasAnEnforceAnnotationWithStaticClassInEvaluationContext_then_enforceAnnotation() {
        // GIVEN
        var expectedResult           = AuthorizationSubscription.of("test value",
                "general_protection_reactive_r2dbc_repository", "Static class set: field, test value", 56);
        var methodInvocation         = new R2dbcMethodInvocation("findAllByAgeAfterAndFirstname",
                new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(18, "test value")), null);
        var enforceAnnotation        = getEnforceAnnotation(methodInvocation.getMethod());
        var enforceAnnotationHandler = new EnforceAnnotationHandler(beanFactory);

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
        var methodInvocation  = new R2dbcMethodInvocation("findAllByFirstnameAndAgeBefore",
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
        var methodInvocation  = new R2dbcMethodInvocation("findById", new ArrayList<>(List.of(String.class)),
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
        var methodInvocation  = new R2dbcMethodInvocation("findByIdBefore", new ArrayList<>(List.of(String.class)),
                new ArrayList<>(List.of()), null);
        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());

        // WHEN

        // THEN
        assertThrows(NoSuchMethodException.class,
                () -> enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation));
    }

    @Test
    void when_methodHasAnEnforceAnnotationAndMethodOfStaticClassIsNotTheRightClass_then_throwNoSuchMethodException() {
        // GIVEN
        var methodInvocation  = new R2dbcMethodInvocation("findByIdAfter", new ArrayList<>(List.of(String.class)),
                new ArrayList<>(List.of()), null);
        var enforceAnnotation = getEnforceAnnotation(methodInvocation.getMethod());

        // WHEN

        // THEN
        assertThrows(NoSuchMethodException.class,
                () -> enforceAnnotationHandler.enforceAnnotation(methodInvocation, enforceAnnotation));
    }

    @Test
    void when_methodHasAnEnforceAnnotationAndMethodOfStaticClassIsAboutToUseButMethodNotExist_then_throwNoSuchMethodException() {
        // GIVEN
        var methodInvocation  = new R2dbcMethodInvocation("findByIdAndAge",
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
