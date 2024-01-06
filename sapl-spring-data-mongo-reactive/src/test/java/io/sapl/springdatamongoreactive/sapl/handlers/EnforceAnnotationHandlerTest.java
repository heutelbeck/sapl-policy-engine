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
package io.sapl.springdatamongoreactive.sapl.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.MongoTestService;

@SpringBootTest(classes = { EnforceAnnotationHandler.class, MongoTestService.class })
class EnforceAnnotationHandlerTest {

    @Autowired
    EnforceAnnotationHandler enforceAnnotationHandler;

    @Test
    void when_methodHasAnEnforceAnnotationWithStaticValues_then_enforceAnnotation() {
        // GIVEN
        var expectedResult   = AuthorizationSubscription.of("subject", "general_protection_reactive_mongo_repository",
                "resource", "environment");
        var methodInvocation = new MethodInvocationForTesting("findAllByFirstname",
                new ArrayList<>(List.of(String.class)), null, null);

        // WHEN
        var result = enforceAnnotationHandler.enforceAnnotation(methodInvocation);

        // THEN
        assertEquals(result, expectedResult);
    }

    @Test
    void when_methodHasNoEnforceAnnotationWithStaticValues_then_returnNull() {
        // GIVEN
        var methodInvocation = new MethodInvocationForTesting("findAllByAge", new ArrayList<>(List.of(int.class)), null,
                null);

        // WHEN
        var result = enforceAnnotationHandler.enforceAnnotation(methodInvocation);

        // THEN
        assertNull(result);
    }

    @Test
    void when_methodHasAnEnforceAnnotationWithStaticClassInEvaluationContext_then_enforceAnnotation() {
        // GIVEN
        var expectedResult   = AuthorizationSubscription.of("test value",
                "general_protection_reactive_mongo_repository", "Static class set: field, test value", 56);
        var methodInvocation = new MethodInvocationForTesting("findAllByAgeAfterAndFirstname",
                new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(18, "test value")), null);

        // WHEN
        var result = enforceAnnotationHandler.enforceAnnotation(methodInvocation);

        // THEN
        assertEquals(result, expectedResult);
    }

    @Test
    void when_methodHasAnEnforceAnnotationWithStaticClassInEvaluationContextPart2_then_enforceAnnotation() {
        // GIVEN
        var environment = JsonNodeFactory.instance.objectNode().put("testNode", "testValue");

        var expectedResult   = AuthorizationSubscription.of("firstname", "general_protection_reactive_mongo_repository",
                "Static class set: firstname, test value", environment);
        var methodInvocation = new MethodInvocationForTesting("findAllByFirstnameAndAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("firstname", 4)), null);

        // WHEN
        var result = enforceAnnotationHandler.enforceAnnotation(methodInvocation);

        // THEN
        assertEquals(result, expectedResult);
    }

    @Test
    void when_methodHasAnEnforceAnnotationAndJsonStringIsNotValid_then_throwParseException() {
        // GIVEN
        var methodInvocation = new MethodInvocationForTesting("findById", new ArrayList<>(List.of(ObjectId.class)),
                new ArrayList<>(List.of()), null);

        // WHEN

        // THEN
        assertThrows(JsonParseException.class, () -> enforceAnnotationHandler.enforceAnnotation(methodInvocation));
    }

    @Test
    void when_methodHasAnEnforceAnnotationAndMethodOfStaticClassIsAboutToUseButNoStaticClassInAnnotationAttached_then_throwNoSuchMethodException() {
        // GIVEN
        var methodInvocation = new MethodInvocationForTesting("findByIdBefore",
                new ArrayList<>(List.of(ObjectId.class)), new ArrayList<>(List.of()), null);

        // WHEN

        // THEN
        assertThrows(NoSuchMethodException.class, () -> enforceAnnotationHandler.enforceAnnotation(methodInvocation));
    }

    @Test
    void when_methodHasAnEnforceAnnotationAndMethodOfStaticClassIsNotTheRightClass_then_throwNoSuchMethodException() {
        // GIVEN
        var methodInvocation = new MethodInvocationForTesting("findByIdAfter", new ArrayList<>(List.of(ObjectId.class)),
                new ArrayList<>(List.of()), null);

        // WHEN

        // THEN
        assertThrows(NoSuchMethodException.class, () -> enforceAnnotationHandler.enforceAnnotation(methodInvocation));
    }

    @Test
    void when_methodHasAnEnforceAnnotationAndMethodOfStaticClassIsAboutToUseButMethodNotExist_then_throwNoSuchMethodException() {
        // GIVEN
        var methodInvocation = new MethodInvocationForTesting("findByIdAndAge",
                new ArrayList<>(List.of(ObjectId.class, int.class)), new ArrayList<>(List.of()), null);

        // WHEN

        // THEN
        assertThrows(NoSuchMethodException.class, () -> enforceAnnotationHandler.enforceAnnotation(methodInvocation));
    }
}
