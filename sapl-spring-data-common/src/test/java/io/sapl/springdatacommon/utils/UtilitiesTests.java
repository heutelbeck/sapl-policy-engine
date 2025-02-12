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
package io.sapl.springdatacommon.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.fasterxml.jackson.core.JsonParseException;

import io.sapl.springdatacommon.database.Person;
import io.sapl.springdatacommon.database.Role;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UtilitiesTests {

    final Person       malinda = new Person("1", "Malinda", "Perrot", 53, Role.ADMIN, true);
    final Person       emerson = new Person("2", "Emerson", "Rowat", 82, Role.USER, false);
    final Person       yul     = new Person("3", "Yul", "Barukh", 79, Role.USER, true);
    final Flux<Person> data    = Flux.just(malinda, emerson, yul);

    static Class<?> returnClassOfMonoMethod;
    static Class<?> returnClassOfFluxMethod;
    static Class<?> returnClassOfListMethod;
    static Class<?> returnClassOfCollectionMethod;

    public Flux<Person> testMethodFlux() {
        return Flux.empty();
    }

    public Mono<Person> testMethodMono() {
        return Mono.empty();
    }

    public List<Person> testMethodList() {
        return List.of();
    }

    public Collection<Person> testMethodCollection() {
        return Collections.emptyList();
    }

    @BeforeAll
    static void beforeAll() {
        Method[] methods = UtilitiesTests.class.getDeclaredMethods();
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
    void when_methodNameIsNotValid_then_returnFalse1(String methodName) {
        assertFalse(Utilities.isMethodNameValid(methodName));
    }

    @ParameterizedTest
    @ValueSource(strings = { "findB", "reedBy", "queryBY", "search", "StreamBy" })
    void when_methodNameIsNotValid_then_returnFalse2(String methodName) {
        assertFalse(Utilities.isMethodNameValid(methodName));
    }

    @Test
    void when_isSpringDataDefaultMethod_then_returnTrue() {
        assertTrue(Utilities.isSpringDataDefaultMethod("findAll"));
    }

    @Test
    void when_isSpringDataDefaultMethod_then_returnFalse() {
        assertFalse(Utilities.isSpringDataDefaultMethod("finddAll"));
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
    void when_classIsString_then_returnFalse() {
        assertFalse(Utilities.isString(returnClassOfMonoMethod));
    }

    @Test
    void when_classIsString_then_returnTrue() {
        assertTrue(Utilities.isString("Test".getClass()));
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
    @SuppressWarnings("unchecked") // cast object to Flux<Person>
    void when_returnTypeIsFlux_then_convertReturnTypeIfNecessary() {
        // GIVEN

        // WHEN
        final var result = (Flux<Person>) Utilities.convertReturnTypeIfNecessary(data, Flux.class);

        // THEN

        StepVerifier.create(result).expectNext(malinda).expectNext(emerson).expectNext(yul).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked") // cast object to Flux<Person>
    void when_returnTypeIsMono_then_convertReturnTypeIfNecessary() {
        // GIVEN

        // WHEN
        final var result = (Mono<Person>) Utilities.convertReturnTypeIfNecessary(data, Mono.class);

        // THEN

        StepVerifier.create(result).expectNext(malinda).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked") // cast object to Flux<Person>
    void when_returnTypeIsList_then_convertReturnTypeIfNecessary() {
        // GIVEN
        final var dataAsList = List.of(malinda, emerson, yul);

        // WHEN
        final var result = (List<Person>) Utilities.convertReturnTypeIfNecessary(data, List.class);

        // THEN
        assertEquals(result, dataAsList);
    }

    @Test
    void when_returnTypeIsNotKnown_then_throwClassNotFoundException() {
        // GIVEN

        // WHEN

        // THEN
        assertThrows(ClassNotFoundException.class, () -> Utilities.convertReturnTypeIfNecessary(data, Person.class));
    }

    @Test
    void when_readTree_then_throwClassNotFoundException() {
        // GIVEN

        // WHEN

        // THEN
        assertThrows(JsonParseException.class, () -> Utilities.readTree("{asd:a}"));
    }

    @Test
    void when_classIsStaticUtilityClass_then_instantiateThisTestForCoverageReasonsOfConstructor() {
        assertThrows(InvocationTargetException.class, () -> {
            final var constructor = Utilities.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            ReflectionUtils.makeAccessible(constructor);
            constructor.newInstance();
        });
    }
}
