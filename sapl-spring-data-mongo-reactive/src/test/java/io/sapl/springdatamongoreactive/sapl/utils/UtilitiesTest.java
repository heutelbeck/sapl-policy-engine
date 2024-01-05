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
package io.sapl.springdatamongoreactive.sapl.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.util.ReflectionUtils;

import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class UtilitiesTest {

    static Class<?> returnClassOfMonoMethod;
    static Class<?> returnClassOfFluxMethod;
    static Class<?> returnClassOfListMethod;
    static Class<?> returnClassOfCollectionMethod;

    public Flux<TestUser> testMethodFlux() {
        return Flux.empty();
    }

    public Mono<TestUser> testMethodMono() {
        return Mono.empty();
    }

    public List<TestUser> testMethodList() {
        return List.of();
    }

    public Collection<TestUser> testMethodCollection() {
        return Collections.emptyList();
    }

    @BeforeAll
    static void beforeAll() {
        Method[] methods = UtilitiesTest.class.getDeclaredMethods();
        returnClassOfFluxMethod       = Objects.requireNonNull(Arrays.stream(methods)
                .filter(method -> "testMethodFlux".equals(method.getName())).findAny().orElse(null)).getReturnType();
        returnClassOfMonoMethod       = Objects.requireNonNull(Arrays.stream(methods)
                .filter(method -> "testMethodMono".equals(method.getName())).findAny().orElse(null)).getReturnType();
        returnClassOfListMethod       = Objects.requireNonNull(Arrays.stream(methods)
                .filter(method -> "testMethodList".equals(method.getName())).findAny().orElse(null)).getReturnType();
        returnClassOfCollectionMethod = Objects.requireNonNull(Arrays.stream(methods)
                .filter(method -> "testMethodCollection".equals(method.getName())).findAny().orElse(null))
                .getReturnType();
    }

    @ParameterizedTest
    @ValueSource(strings = { "findBy", "readBy", "queryBy", "searchBy", "streamBy" })
    void when_methodNameIsValid_then_returnTrue(String methodName) {
        assertTrue(Utilities.isMethodNameValid(methodName));
    }

    @ParameterizedTest
    @ValueSource(strings = { "findB", "reedBy", "queryBY", "search", "StreamBy" })
    void when_methodNameIsNotValid_then_returnFalse(String methodName) {
        assertFalse(Utilities.isMethodNameValid(methodName));
    }

    @Test
    void when_classIsFlux_then_returnTrue() {
        assertTrue(Utilities.isFlux(returnClassOfFluxMethod));
    }

    @Test
    void when_classIsNoFlux_then_returnFalse() {
        assertFalse(Utilities.isFlux(returnClassOfMonoMethod));
    }

    @Test
    void when_classIsMono_then_returnTrue() {
        assertTrue(Utilities.isMono(returnClassOfMonoMethod));
    }

    @Test
    void when_classIsNoMono_then_returnFalse() {
        assertFalse(Utilities.isMono(returnClassOfFluxMethod));
    }

    @Test
    void when_classIsList_then_returnTrue() {
        assertTrue(Utilities.isListOrCollection(returnClassOfListMethod));
    }

    @Test
    void when_classIsNoList_then_returnFalse() {
        assertFalse(Utilities.isListOrCollection(returnClassOfMonoMethod));
    }

    @Test
    void when_classIsCollection_then_returnTrue() {
        assertTrue(Utilities.isListOrCollection(returnClassOfCollectionMethod));
    }

    @Test
    void when_classIsNoCollection_then_returnFalse() {
        assertFalse(Utilities.isListOrCollection(returnClassOfMonoMethod));
    }

    @Test
    void when_objectIsString_then_returnTrue() {
        assertTrue(Utilities.isString("Test"));
    }

    @Test
    void when_objectIsNoString_then_returnFalse() {
        assertFalse(Utilities.isString(2));
    }

    @Test
    void when_classIsStaticUtilityClass_then_instantiateThisTestForCoverageReasonsOfConstructor() {
        assertThrows(InvocationTargetException.class, () -> {
            var constructor = Utilities.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            ReflectionUtils.makeAccessible(constructor);
            constructor.newInstance();
        });
    }
}
