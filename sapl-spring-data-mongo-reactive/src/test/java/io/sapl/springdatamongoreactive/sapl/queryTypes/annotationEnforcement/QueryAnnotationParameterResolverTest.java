/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatamongoreactive.sapl.querytypes.annotationenforcement;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;

class QueryAnnotationParameterResolverTest {

    @Test
    void when_resolveBoundedMethodParametersAndAnnotationParametersAreStrings_then_resolveQuery() {
        // GIVEN
        var expectedResult   = "{'firstname':  {'$in': [ 'Aaron' ]}}";
        var methodInvocation = new MethodInvocationForTesting("findAllUsersTest",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Aaron")), null);
        var method           = methodInvocation.getMethod();
        var args             = methodInvocation.getArguments();

        // WHEN
        var result = QueryAnnotationParameterResolver.resolveBoundedMethodParametersAndAnnotationParameters(method,
                args);

        // THEN
        Assertions.assertEquals(expectedResult, result);
    }

    @Test
    void when_resolveBoundedMethodParametersAndAnnotationParametersAreNoStrings_then_resolveQuery() {
        // GIVEN
        var expectedResult   = "{'age':  {'$in': [ 22 ]}}";
        var methodInvocation = new MethodInvocationForTesting("findAllByAge", new ArrayList<>(List.of(int.class)),
                new ArrayList<>(List.of(22)), null);
        var method           = methodInvocation.getMethod();
        var args             = methodInvocation.getArguments();

        // WHEN
        var result = QueryAnnotationParameterResolver.resolveBoundedMethodParametersAndAnnotationParameters(method,
                args);

        // THEN
        Assertions.assertEquals(expectedResult, result);
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
