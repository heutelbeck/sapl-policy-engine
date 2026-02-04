/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.data.utils;

import io.sapl.spring.data.database.Person;
import io.sapl.spring.data.database.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.util.ReflectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.core.exc.StreamReadException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    public List<Person> testMethodCollection() {
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
        assertThat(Utilities.isMethodNameValid(methodName)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "findB", "reedBy", "queryBY", "search", "StreamBy" })
    void when_methodNameIsNotValid_then_returnFalse2(String methodName) {
        assertThat(Utilities.isMethodNameValid(methodName)).isFalse();
    }

    @Test
    void when_isSpringDataDefaultMethod_then_returnTrue() {
        assertThat(Utilities.isSpringDataDefaultMethod("findAll")).isTrue();
    }

    @Test
    void when_isSpringDataDefaultMethod_then_returnFalse() {
        assertThat(Utilities.isSpringDataDefaultMethod("finddAll")).isFalse();
    }

    @Test
    void when_classIsFlux_then_returnTrue() {
        assertThat(Utilities.isFlux(returnClassOfFluxMethod)).isTrue();
    }

    @Test
    void when_classIsNoFlux_then_returnFalse() {
        assertThat(Utilities.isFlux(returnClassOfMonoMethod)).isFalse();
    }

    @Test
    void when_classIsMono_then_returnTrue() {
        assertThat(Utilities.isMono(returnClassOfMonoMethod)).isTrue();
    }

    @Test
    void when_classIsNoMono_then_returnFalse() {
        assertThat(Utilities.isMono(returnClassOfFluxMethod)).isFalse();
    }

    @Test
    void when_classIsList_then_returnTrue() {
        assertThat(Utilities.isListOrCollection(returnClassOfListMethod)).isTrue();
    }

    @Test
    void when_classIsNoList_then_returnFalse() {
        assertThat(Utilities.isListOrCollection(returnClassOfMonoMethod)).isFalse();
    }

    @Test
    void when_classIsCollection_then_returnTrue() {
        assertThat(Utilities.isListOrCollection(returnClassOfCollectionMethod)).isTrue();
    }

    @Test
    void when_classIsNoCollection_then_returnFalse() {
        assertThat(Utilities.isListOrCollection(returnClassOfMonoMethod)).isFalse();
    }

    @Test
    void when_classIsString_then_returnFalse() {
        assertThat(Utilities.isString(returnClassOfMonoMethod)).isFalse();
    }

    @Test
    void when_classIsString_then_returnTrue() {
        assertThat(Utilities.isString("Test".getClass())).isTrue();
    }

    @Test
    void when_objectIsString_then_returnTrue() {
        assertThat(Utilities.isString("Test")).isTrue();
    }

    @Test
    void when_objectIsNoString_then_returnFalse() {
        assertThat(Utilities.isString(2)).isFalse();
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
        assertThat(result).isEqualTo(dataAsList);
    }

    @Test
    void when_returnTypeIsNotKnown_then_throwClassNotFoundException() {
        // GIVEN

        // WHEN

        // THEN
        assertThatThrownBy(() -> Utilities.convertReturnTypeIfNecessary(data, Person.class))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void when_readTree_then_throwClassNotFoundException() {
        // GIVEN

        // WHEN

        // THEN
        assertThatThrownBy(() -> Utilities.readTree("{asd:a}")).isInstanceOf(StreamReadException.class);
    }

    @Test
    void when_classIsStaticUtilityClass_then_instantiateThisTestForCoverageReasonsOfConstructor() {
        assertThatThrownBy(() -> {
            final var constructor = Utilities.class.getDeclaredConstructor();
            assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
            ReflectionUtils.makeAccessible(constructor);
            constructor.newInstance();
        }).isInstanceOf(InvocationTargetException.class);
    }
}
