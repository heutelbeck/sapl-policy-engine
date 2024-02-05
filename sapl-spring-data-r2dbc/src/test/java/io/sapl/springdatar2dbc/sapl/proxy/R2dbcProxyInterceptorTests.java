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
package io.sapl.springdatar2dbc.sapl.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.springdatacommon.handlers.AuthorizationSubscriptionHandlerProvider;
import io.sapl.springdatacommon.sapl.Enforce;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatacommon.sapl.queries.enforcement.ProceededDataFilterEnforcementPoint;
import io.sapl.springdatar2dbc.database.MethodInvocationForTesting;
import io.sapl.springdatar2dbc.database.MethodInvocationForTestingWithCrudRepository;
import io.sapl.springdatar2dbc.database.Person;
import io.sapl.springdatar2dbc.database.Role;
import io.sapl.springdatar2dbc.sapl.QueryManipulationEnforcementPointFactory;
import io.sapl.springdatar2dbc.sapl.database.repositoryerror.MethodInvocationForRepositoryError;
import io.sapl.springdatar2dbc.sapl.queries.enforcement.R2dbcAnnotationQueryManipulationEnforcementPoint;
import io.sapl.springdatar2dbc.sapl.queries.enforcement.R2dbcMethodNameQueryManipulationEnforcementPoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings("unchecked") // mocking of generic types
class R2dbcProxyInterceptorTests {

    final Person malinda = new Person(1, "Malinda", "Perrot", 53, Role.ADMIN, true);
    final Person emerson = new Person(2, "Emerson", "Rowat", 82, Role.USER, false);
    final Person yul     = new Person(3, "Yul", "Barukh", 79, Role.USER, true);

    final Flux<Person> data = Flux.just(malinda, emerson, yul);

    AuthorizationSubscriptionHandlerProvider                 authSubHandlerMock;
    QueryManipulationEnforcementPointFactory                 factoryMock;
    BeanFactory                                              beanFactoryMock;
    R2dbcAnnotationQueryManipulationEnforcementPoint<Person> r2dbcAnnotationQueryManipulationEnforcementPointMock;
    R2dbcMethodNameQueryManipulationEnforcementPoint<Person> r2dbcMethodNameQueryManipulationEnforcementPointMock;
    ProceededDataFilterEnforcementPoint<Person>              proceededDataFilterEnforcementPointMock;
    EmbeddedPolicyDecisionPoint                              pdpMock;

    @BeforeEach
    void beforeEach() {
        authSubHandlerMock = mock(AuthorizationSubscriptionHandlerProvider.class);
        factoryMock        = mock(QueryManipulationEnforcementPointFactory.class);
        beanFactoryMock    = mock(BeanFactory.class);

        r2dbcAnnotationQueryManipulationEnforcementPointMock = mock(
                R2dbcAnnotationQueryManipulationEnforcementPoint.class);
        r2dbcMethodNameQueryManipulationEnforcementPointMock = mock(
                R2dbcMethodNameQueryManipulationEnforcementPoint.class);
        proceededDataFilterEnforcementPointMock              = mock(ProceededDataFilterEnforcementPoint.class);
        pdpMock                                              = mock(EmbeddedPolicyDecisionPoint.class);
    }

    @Test
    void when_authorizationSubscriptionIsNull_then_throwIllegalStateException() {
        // GIVEN
        var methodInvocationMock = new MethodInvocationForTesting("findAllBy", new ArrayList<>(), new ArrayList<>(),
                data);

        // WHEN
        when(pdpMock.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any(Enforce.class)))
                .thenReturn(null);
        var proxyR2dbcHandler = new R2dbcProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> proxyR2dbcHandler.invoke(methodInvocationMock));

        assertEquals(
                "The Sapl implementation for the manipulation of the database queries was recognised, but no AuthorizationSubscription was found.",
                thrown.getMessage());

        // THEN
        verify(authSubHandlerMock, times(1)).getAuthSub(any(Class.class), any(MethodInvocation.class), any());
    }

    @Test
    void when_repositoryMethodHasAnnotationQuery_then_callAnnotationQueryEnforcementPoint() {
        // GIVEN
        var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
        var methodInvocationMock = new MethodInvocationForTestingWithCrudRepository("findAllUsersTest",
                new ArrayList<>(List.of(int.class, String.class)), null, null);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any())).thenReturn(authSub);
        when(factoryMock
                .createR2dbcAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class)))
                .thenReturn(r2dbcAnnotationQueryManipulationEnforcementPointMock);
        when(r2dbcAnnotationQueryManipulationEnforcementPointMock.enforce()).thenReturn(data);

        var proxyR2dbcHandler = new R2dbcProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);
        var result            = (Flux<Person>) proxyR2dbcHandler.invoke(methodInvocationMock);

        // THEN
        StepVerifier.create(result).expectNext(malinda).expectNext(emerson).expectNext(yul).verifyComplete();

        verify(authSubHandlerMock, times(1)).getAuthSub(any(Class.class), any(MethodInvocation.class), any());
        verify(factoryMock, times(1))
                .createR2dbcAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, never())
                .createR2dbcMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
    }

    @Test
    void when_repositoryMethodIsQueryMethod_then_callMongoMethodNameQueryManipulationEnforcementPoint() {
        // GIVEN
        var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
        var methodInvocationMock = new MethodInvocationForTesting("findAllByFirstname",
                new ArrayList<>(List.of(String.class)), null, null);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any(Enforce.class)))
                .thenReturn(authSub);
        when(factoryMock
                .createR2dbcMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class)))
                .thenReturn(r2dbcMethodNameQueryManipulationEnforcementPointMock);
        when(r2dbcMethodNameQueryManipulationEnforcementPointMock.enforce()).thenReturn(data);

        var proxyR2dbcHandler = new R2dbcProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);
        var result            = (Flux<Person>) proxyR2dbcHandler.invoke(methodInvocationMock);

        // THEN
        StepVerifier.create(result).expectNext(malinda).expectNext(emerson).expectNext(yul).verifyComplete();

        verify(authSubHandlerMock, times(1)).getAuthSub(any(Class.class), any(MethodInvocation.class),
                any(Enforce.class));
        verify(factoryMock, never())
                .createR2dbcAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, times(1))
                .createR2dbcMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
    }

    @Test
    void when_repositoryMethodIsQueryMethod_then_callProceededDataFilterEnforcementPoint() {
        // GIVEN
        var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
        var methodInvocationMock = new MethodInvocationForTesting("methodTestWithAge",
                new ArrayList<>(List.of(int.class)), null, null);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any())).thenReturn(authSub);
        when(factoryMock.createProceededDataFilterEnforcementPoint(any(QueryManipulationEnforcementData.class)))
                .thenReturn(proceededDataFilterEnforcementPointMock);
        when(proceededDataFilterEnforcementPointMock.enforce()).thenReturn(data);

        var proxyR2dbcHandler = new R2dbcProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);
        var result            = (Flux<Person>) proxyR2dbcHandler.invoke(methodInvocationMock);

        // THEN
        StepVerifier.create(result).expectNext(malinda).expectNext(emerson).expectNext(yul).verifyComplete();

        verify(authSubHandlerMock, times(1)).getAuthSub(any(Class.class), any(MethodInvocation.class), any());
        verify(factoryMock, never())
                .createR2dbcAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, never())
                .createR2dbcMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
    }

    @Test
    void when_repositoryMethodIsQueryMethodAndReturnTypeIsMono_then_callMongoMethodNameQueryManipulationEnforcementPoint() {
        // GIVEN
        var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
        var methodInvocationMock = new MethodInvocationForTesting("findByAge", new ArrayList<>(List.of(int.class)),
                null, null);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any())).thenReturn(authSub);
        when(factoryMock
                .createR2dbcMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class)))
                .thenReturn(r2dbcMethodNameQueryManipulationEnforcementPointMock);
        when(r2dbcMethodNameQueryManipulationEnforcementPointMock.enforce()).thenReturn(Flux.just(malinda));

        var proxyR2dbcHandler = new R2dbcProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);
        var result            = (Mono<Person>) proxyR2dbcHandler.invoke(methodInvocationMock);

        // THEN
        StepVerifier.create(result).expectNext(malinda).verifyComplete();

        verify(authSubHandlerMock, times(1)).getAuthSub(any(Class.class), any(MethodInvocation.class), any());
        verify(factoryMock, never())
                .createR2dbcAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, times(1))
                .createR2dbcMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
    }

    @Test
    void when_repositoryMethodIsQueryMethodAndReturnTypeIsList_then_callMongoMethodNameQueryManipulationEnforcementPoint() {

        // GIVEN
        var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
        var methodInvocationMock = new MethodInvocationForTesting("findAllByAgeGreaterThan",
                new ArrayList<>(List.of(int.class)), null, null);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any())).thenReturn(authSub);
        when(factoryMock
                .createR2dbcMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class)))
                .thenReturn(r2dbcMethodNameQueryManipulationEnforcementPointMock);
        when(r2dbcMethodNameQueryManipulationEnforcementPointMock.enforce()).thenReturn(Flux.just(malinda));

        var proxyR2dbcHandler = new R2dbcProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);
        var result            = (List<Person>) proxyR2dbcHandler.invoke(methodInvocationMock);

        // THEN
        assertEquals(result.get(0), malinda);

        verify(authSubHandlerMock, times(1)).getAuthSub(any(Class.class), any(MethodInvocation.class), any());
        verify(factoryMock, never())
                .createR2dbcAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, times(1))
                .createR2dbcMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
    }

    @Test
    void when_repositoryMethodIsQueryMethodAndReturnTypeIsStream_then_throwNotImplementedError() {

        // GIVEN
        var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
        var methodInvocationMock = new MethodInvocationForTesting("findAllByAgeLessThan",
                new ArrayList<>(List.of(int.class)), null, null);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any())).thenReturn(authSub);
        when(factoryMock
                .createR2dbcMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class)))
                .thenReturn(r2dbcMethodNameQueryManipulationEnforcementPointMock);
        when(r2dbcMethodNameQueryManipulationEnforcementPointMock.enforce()).thenReturn(Flux.just(malinda));

        var proxyR2dbcHandler = new R2dbcProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);

        // THEN
        ClassNotFoundException thrown = assertThrows(ClassNotFoundException.class,
                () -> proxyR2dbcHandler.invoke(methodInvocationMock));

        assertEquals("Return type of method not supported: interface java.util.stream.Stream", thrown.getMessage());

        verify(authSubHandlerMock, times(1)).getAuthSub(any(Class.class), any(MethodInvocation.class), any());
        verify(factoryMock, never())
                .createR2dbcAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, times(1))
                .createR2dbcMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
    }

    @Test
    void when_repositoryMethodHasNoSaplProtectedAnnotation_then_proceedMethodCall() {
        // GIVEN
        var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
        var methodInvocationMock = new MethodInvocationForTesting("findAllByAgeBefore",
                new ArrayList<>(List.of(int.class)), null, Flux.just(emerson));

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any(Enforce.class)))
                .thenReturn(authSub);
        when(factoryMock.createProceededDataFilterEnforcementPoint(any(QueryManipulationEnforcementData.class)))
                .thenReturn(proceededDataFilterEnforcementPointMock);
        when(proceededDataFilterEnforcementPointMock.enforce()).thenReturn(data);

        var proxyR2dbcHandler = new R2dbcProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);
        var result            = (Flux<Person>) proxyR2dbcHandler.invoke(methodInvocationMock);

        // THEN
        StepVerifier.create(result).expectNext(emerson).verifyComplete();

        verify(authSubHandlerMock, never()).getAuthSub(any(Class.class), any(MethodInvocation.class),
                any(Enforce.class));
        verify(factoryMock, never())
                .createR2dbcAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, never())
                .createR2dbcMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
    }

    @Test
    void when_customRepositoryDoesNotExtendSpringDataRepository_then_throwClassCastException() {

        // GIVEN
        var authSub                            = AuthorizationSubscription.of("subject", "permitTest", "resource",
                "environment");
        var methodInvocationForRepositoryError = new MethodInvocationForRepositoryError("findAllByFirstname",
                new ArrayList<>(List.of(String.class)), null, null);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any())).thenReturn(authSub);

        var proxyMongoHandler = new R2dbcProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);

        // THEN
        ClassNotFoundException thrown = assertThrows(ClassNotFoundException.class,
                () -> proxyMongoHandler.invoke(methodInvocationForRepositoryError));

        assertEquals(
                "The interface org.springframework.data.r2dbc.repository.R2dbcRepository or interface org.springframework.data.repository.reactive.ReactiveCrudRepository could not be found as an extension of the interface io.sapl.springdatar2dbc.sapl.database.repositoryerror.RepositoryNotFoundException",
                thrown.getMessage());
    }
}
