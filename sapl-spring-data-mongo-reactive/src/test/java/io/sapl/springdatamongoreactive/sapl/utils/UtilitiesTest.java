package io.sapl.springdatamongoreactive.sapl.utils;

import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.util.ReflectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        Assertions.assertFalse(Utilities.isMethodNameValid(methodName));
    }

    @Test
    void when_classIsFlux_then_returnTrue() {
        assertTrue(Utilities.isFlux(returnClassOfFluxMethod));
    }

    @Test
    void when_classIsNoFlux_then_returnFalse() {
        Assertions.assertFalse(Utilities.isFlux(returnClassOfMonoMethod));
    }

    @Test
    void when_classIsMono_then_returnTrue() {
        assertTrue(Utilities.isMono(returnClassOfMonoMethod));
    }

    @Test
    void when_classIsNoMono_then_returnFalse() {
        Assertions.assertFalse(Utilities.isMono(returnClassOfFluxMethod));
    }

    @Test
    void when_classIsList_then_returnTrue() {
        assertTrue(Utilities.isListOrCollection(returnClassOfListMethod));
    }

    @Test
    void when_classIsNoList_then_returnFalse() {
        Assertions.assertFalse(Utilities.isListOrCollection(returnClassOfMonoMethod));
    }

    @Test
    void when_classIsCollection_then_returnTrue() {
        assertTrue(Utilities.isListOrCollection(returnClassOfCollectionMethod));
    }

    @Test
    void when_classIsNoCollection_then_returnFalse() {
        Assertions.assertFalse(Utilities.isListOrCollection(returnClassOfMonoMethod));
    }

    @Test
    void when_objectIsString_then_returnTrue() {
        assertTrue(Utilities.isString("Test"));
    }

    @Test
    void when_objectIsNoString_then_returnFalse() {
        Assertions.assertFalse(Utilities.isString(2));
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
