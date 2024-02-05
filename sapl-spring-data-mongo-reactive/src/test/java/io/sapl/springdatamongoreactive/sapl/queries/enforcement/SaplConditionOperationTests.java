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
package io.sapl.springdatamongoreactive.sapl.queries.enforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ReflectionUtils;

import io.sapl.springdatamongoreactive.sapl.OperatorMongoDB;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import io.sapl.springdatamongoreactive.sapl.utils.SaplCondition;
import io.sapl.springdatamongoreactive.sapl.utils.SaplConditionOperation;

class SaplConditionOperationTests {

    @Test
    void when_methodIsQueryMethodButNoPartsCanBeCreatedByMethodName_then_returnEmptySaplConditionList() {
        // GIVEN
        var methodInvocation = new MethodInvocationForTesting("findAllBy", new ArrayList<>(List.of()),
                new ArrayList<>(List.of()), null);
        var method           = methodInvocation.getMethod();
        var args             = methodInvocation.getArguments();

        var expectedResult = new ArrayList<>();

        // WHEN
        var actualResult = SaplConditionOperation.methodToSaplConditions(args, method, TestUser.class);

        // THEN
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void when_methodIsQueryMethodButParametersDontFit_then_throwArrayIndexOutOfBoundsException() {
        // GIVEN
        var methodInvocation = new MethodInvocationForTesting("findAllByFirstnameAndAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("Aaron")), null);
        var method           = methodInvocation.getMethod();
        var args             = methodInvocation.getArguments();

        // WHEN

        // THEN
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> SaplConditionOperation.methodToSaplConditions(args, method, TestUser.class));
    }

    @Test
    void when_methodIsQueryMethod_then_convertToSaplConditions() {
        // GIVEN
        var methodInvocation = new MethodInvocationForTesting("findAllByFirstnameAndAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("Aaron", 22)), null);
        var method           = methodInvocation.getMethod();
        var args             = methodInvocation.getArguments();
        var expectedResult   = List.of(new SaplCondition("firstname", "Aaron", OperatorMongoDB.SIMPLE_PROPERTY, "And"),
                new SaplCondition("age", 22, OperatorMongoDB.BEFORE, "And"));

        // WHEN
        var actualResult = SaplConditionOperation.methodToSaplConditions(args, method, TestUser.class);

        // THEN
        for (int i = 0; i < actualResult.size(); i++) {
            assertTwoSaplConditions(expectedResult.get(i), actualResult.get(i));
        }
    }

    @Test
    void when_methodIsQueryMethodAndNameIsFindAll_then_convertToSaplConditions() {
        // GIVEN
        var methodInvocation = new MethodInvocationForTesting("findAll", new ArrayList<>(List.of()),
                new ArrayList<>(List.of()), null);
        var method           = methodInvocation.getMethod();
        var saplConditions   = List.of(new SaplCondition("firstname", "Aaron", OperatorMongoDB.SIMPLE_PROPERTY, "And"),
                new SaplCondition("age", 22, OperatorMongoDB.BEFORE, "And"));

        // WHEN
        var actualResult = SaplConditionOperation.toModifiedMethodName(method.getName(), saplConditions);

        // THEN
        assertEquals("findAllByFirstnameIsAndAgeIsBefore", actualResult);
    }

    @Test
    void when_classIsStaticUtilityClass_then_instantiateThisTestForCoverageReasonsOfConstructor() {
        assertThrows(InvocationTargetException.class, () -> {
            var constructor = SaplConditionOperation.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            ReflectionUtils.makeAccessible(constructor);
            constructor.newInstance();
        });
    }

    private void assertTwoSaplConditions(SaplCondition first, SaplCondition second) {
        assertEquals(first.field(), second.field());
        assertEquals(first.value(), second.value());
        assertEquals(first.operator(), second.operator());
        assertEquals(first.conjunction(), second.conjunction());
    }
}
