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
package io.sapl.springdatacommon.queries.enforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

import io.sapl.springdatacommon.database.MongoReactiveMethodInvocation;
import io.sapl.springdatacommon.database.R2dbcMethodInvocation;
import io.sapl.springdatacommon.sapl.queries.enforcement.QueryAnnotationParameterResolver;

class QueryAnnotationParameterResolverTests {

    @Test
    void resolveBoundedMethodParametersAndAnnotationParameters() {
        // GIVEN
        var r2dbcMethodInvocationTest = new R2dbcMethodInvocation("findAllUsersTest",
                new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(30, "2")), null);
        var expected                  = "SELECT * FROM testUser WHERE age = 30 AND id = '2'";

        // WHEN
        var actual = QueryAnnotationParameterResolver.resolveBoundedMethodParametersAndAnnotationParameters(
                r2dbcMethodInvocationTest.getMethod(), r2dbcMethodInvocationTest.getArguments(), true);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    void when_resolveBoundedMethodParametersAndAnnotationParametersAreStrings_then_resolveQuery() {
        // GIVEN
        var expectedResult   = "{'firstname':  {'$in': [ 'Aaron' ]}}";
        var methodInvocation = new MongoReactiveMethodInvocation("findAllUsersTest",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Aaron")), null);
        var method           = methodInvocation.getMethod();
        var args             = methodInvocation.getArguments();

        // WHEN
        var result = QueryAnnotationParameterResolver.resolveBoundedMethodParametersAndAnnotationParameters(method,
                args, false);

        // THEN
        assertEquals(expectedResult, result);
    }

    @Test
    void when_resolveBoundedMethodParametersAndAnnotationParametersAreNoStrings_then_resolveQuery() {
        // GIVEN
        var expectedResult   = "{'age':  {'$in': [ 22 ]}}";
        var methodInvocation = new MongoReactiveMethodInvocation("findAllByAge", new ArrayList<>(List.of(int.class)),
                new ArrayList<>(List.of(22)), null);
        var method           = methodInvocation.getMethod();
        var args             = methodInvocation.getArguments();

        // WHEN
        var result = QueryAnnotationParameterResolver.resolveBoundedMethodParametersAndAnnotationParameters(method,
                args, false);

        // THEN
        assertEquals(expectedResult, result);
    }

    @Test
    void when_classIsStaticUtilityClass_then_instantiateThisTestForCoverageReasonsOfConstructor() {
        assertThrows(InvocationTargetException.class, () -> {
            var constructor = QueryAnnotationParameterResolver.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            ReflectionUtils.makeAccessible(constructor);
            constructor.newInstance();
        });
    }
}
