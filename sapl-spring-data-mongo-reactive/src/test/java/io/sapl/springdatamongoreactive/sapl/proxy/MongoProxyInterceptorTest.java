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
package io.sapl.springdatamongoreactive.sapl.proxy;

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

import io.sapl.springdatacommon.sapl.Enforce;
import org.aopalliance.intercept.MethodInvocation;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.springdatacommon.handlers.AuthorizationSubscriptionHandlerProvider;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementPoint;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatamongoreactive.sapl.QueryManipulationEnforcementPointFactory;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import io.sapl.springdatamongoreactive.sapl.database.repositoryerror.MethodInvocationForRepositoryError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings("unchecked")
class MongoProxyInterceptorTest {

    final TestUser aaron   = new TestUser(new ObjectId(), "Aaron", 20);
    final TestUser brian   = new TestUser(new ObjectId(), "Brian", 21);
    final TestUser cathrin = new TestUser(new ObjectId(), "Cathrin", 33);

    final Flux<TestUser> data = Flux.just(aaron, brian, cathrin);

    private AuthorizationSubscriptionHandlerProvider    authSubHandlerMock;
    private QueryManipulationEnforcementPointFactory    factoryMock;
    private BeanFactory                                 beanFactoryMock;
    private QueryManipulationEnforcementPoint<TestUser> queryManipulationEnforcementPointMock;
    private EmbeddedPolicyDecisionPoint                 pdpMock;

    @BeforeEach
    void beforeEach() {
        authSubHandlerMock                    = mock(AuthorizationSubscriptionHandlerProvider.class);
        factoryMock                           = mock(QueryManipulationEnforcementPointFactory.class);
        beanFactoryMock                       = mock(BeanFactory.class);
        queryManipulationEnforcementPointMock = mock(QueryManipulationEnforcementPoint.class);
        pdpMock                               = mock(EmbeddedPolicyDecisionPoint.class);
    }

    @Test
    void when_authorizationSubscriptionIsNull_then_throwIllegalStateException() {
        // GIVEN
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByAge",
                new ArrayList<>(List.of(int.class)), new ArrayList<>(), data);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any())).thenReturn(null);
        var proxyMongoHandler = new MongoProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> proxyMongoHandler.invoke(mongoMethodInvocationTest));

        assertEquals(
                "The Sapl implementation for the manipulation of the database queries was recognised, but no AuthorizationSubscription was found.",
                thrown.getMessage());

        // THEN
        verify(authSubHandlerMock, times(1)).getAuthSub(any(Class.class), any(MethodInvocation.class), any());
    }

    @Test
    void when_repositoryMethodHasAnnotationQuery_then_callAnnotationQueryEnforcementPoint() {

        // GIVEN
        var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                "environment");
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllUsersTest",
                new ArrayList<>(List.of(String.class)), null, null);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any())).thenReturn(authSub);
        when(factoryMock
                .createMongoAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class)))
                .thenReturn(queryManipulationEnforcementPointMock);
        when(queryManipulationEnforcementPointMock.enforce()).thenReturn(data);

        var proxyMongoHandler = new MongoProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);
        var result            = (Flux<TestUser>) proxyMongoHandler.invoke(mongoMethodInvocationTest);

        // THEN
        StepVerifier.create(result).expectNext(aaron).expectNext(brian).expectNext(cathrin).verifyComplete();

        verify(authSubHandlerMock, times(1)).getAuthSub(any(Class.class), any(MethodInvocation.class), any());
        verify(factoryMock, times(1))
                .createMongoAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, never())
                .createMongoMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, never())
                .createProceededDataFilterEnforcementPoint(any(QueryManipulationEnforcementData.class));
    }

    @Test
    void when_repositoryMethodIsQueryMethod_then_callMongoMethodNameQueryManipulationEnforcementPoint() {

        // GIVEN
        var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                "environment");
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstname",
                new ArrayList<>(List.of(String.class)), null, null);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any(Enforce.class)))
                .thenReturn(authSub);
        when(factoryMock
                .createMongoMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class)))
                .thenReturn(queryManipulationEnforcementPointMock);
        when(queryManipulationEnforcementPointMock.enforce()).thenReturn(data);

        var proxyMongoHandler = new MongoProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);
        var result            = (Flux<TestUser>) proxyMongoHandler.invoke(mongoMethodInvocationTest);

        // THEN
        StepVerifier.create(result).expectNext(aaron).expectNext(brian).expectNext(cathrin).verifyComplete();

        verify(authSubHandlerMock, times(1)).getAuthSub(any(Class.class), any(MethodInvocation.class),
                any(Enforce.class));
        verify(factoryMock, never())
                .createMongoAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, times(1))
                .createMongoMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, never())
                .createProceededDataFilterEnforcementPoint(any(QueryManipulationEnforcementData.class));
    }

    @Test
    void when_repositoryMethodHasNoAnnotationAtAll_then_proceedMethodInvocation() {

        // GIVEN
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByAgeBefore",
                new ArrayList<>(List.of(int.class)), null, data);

        // WHEN
        var proxyMongoHandler = new MongoProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);
        var result            = (Flux<TestUser>) proxyMongoHandler.invoke(mongoMethodInvocationTest);

        // THEN
        StepVerifier.create(result).expectNext(aaron).expectNext(brian).expectNext(cathrin).verifyComplete();

        verify(authSubHandlerMock, never()).getAuthSub(any(Class.class), any(MethodInvocation.class),
                any(Enforce.class));
        verify(factoryMock, never())
                .createMongoAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, never())
                .createMongoMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, never())
                .createProceededDataFilterEnforcementPoint(any(QueryManipulationEnforcementData.class));
    }

    @Test
    void when_repositoryMethodIsCustomMethod_then_callProceededDataFilterEnforcementPoint() {

        // GIVEN
        var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                "environment");
        var mongoMethodInvocationTest = new MethodInvocationForTesting("methodTestWithAge",
                new ArrayList<>(List.of(int.class)), null, data);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any())).thenReturn(authSub);
        when(factoryMock.createProceededDataFilterEnforcementPoint(any(QueryManipulationEnforcementData.class)))
                .thenReturn(queryManipulationEnforcementPointMock);
        when(queryManipulationEnforcementPointMock.enforce()).thenReturn(data);

        var proxyMongoHandler = new MongoProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);
        var result            = (Flux<TestUser>) proxyMongoHandler.invoke(mongoMethodInvocationTest);

        // THEN
        StepVerifier.create(result).expectNext(aaron).expectNext(brian).expectNext(cathrin).verifyComplete();

        verify(authSubHandlerMock, times(1)).getAuthSub(any(Class.class), any(MethodInvocation.class), any());
        verify(factoryMock, never())
                .createMongoAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, never())
                .createMongoMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, times(1))
                .createProceededDataFilterEnforcementPoint(any(QueryManipulationEnforcementData.class));
    }

    @Test
    void when_repositoryMethodIsQueryMethodAndReturnTypeIsMono_then_callMongoMethodNameQueryManipulationEnforcementPoint() {

        // GIVEN
        var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                "environment");
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findByAge", new ArrayList<>(List.of(int.class)),
                null, null);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any())).thenReturn(authSub);
        when(factoryMock
                .createMongoMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class)))
                .thenReturn(queryManipulationEnforcementPointMock);
        when(queryManipulationEnforcementPointMock.enforce()).thenReturn(Flux.just(aaron));

        var proxyMongoHandler = new MongoProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);
        var result            = (Mono<TestUser>) proxyMongoHandler.invoke(mongoMethodInvocationTest);

        // THEN
        StepVerifier.create(result).expectNext(aaron).verifyComplete();

        verify(authSubHandlerMock, times(1)).getAuthSub(any(Class.class), any(MethodInvocation.class), any());
        verify(factoryMock, never())
                .createMongoAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, times(1))
                .createMongoMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, never())
                .createProceededDataFilterEnforcementPoint(any(QueryManipulationEnforcementData.class));
    }

    @Test
    void when_repositoryMethodIsQueryMethodAndReturnTypeIsList_then_callMongoMethodNameQueryManipulationEnforcementPoint() {

        // GIVEN
        var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                "environment");
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByAgeGreaterThan",
                new ArrayList<>(List.of(int.class)), null, null);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any())).thenReturn(authSub);
        when(factoryMock
                .createMongoMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class)))
                .thenReturn(queryManipulationEnforcementPointMock);
        when(queryManipulationEnforcementPointMock.enforce()).thenReturn(Flux.just(aaron));

        var proxyMongoHandler = new MongoProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);
        var result            = (List<TestUser>) proxyMongoHandler.invoke(mongoMethodInvocationTest);

        // THEN
        assertEquals(result.get(0), aaron);

        verify(authSubHandlerMock, times(1)).getAuthSub(any(Class.class), any(MethodInvocation.class), any());
        verify(factoryMock, never())
                .createMongoAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, times(1))
                .createMongoMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, never())
                .createProceededDataFilterEnforcementPoint(any(QueryManipulationEnforcementData.class));
    }

    @Test
    void when_repositoryMethodIsQueryMethodAndReturnTypeIsStream_then_throwNotImplementedError() {

        // GIVEN
        var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                "environment");
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByAgeLessThan",
                new ArrayList<>(List.of(int.class)), null, null);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any())).thenReturn(authSub);
        when(factoryMock
                .createMongoMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class)))
                .thenReturn(queryManipulationEnforcementPointMock);
        when(queryManipulationEnforcementPointMock.enforce()).thenReturn(Flux.just(aaron));

        var proxyMongoHandler = new MongoProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);

        // THEN
        ClassNotFoundException thrown = assertThrows(ClassNotFoundException.class,
                () -> proxyMongoHandler.invoke(mongoMethodInvocationTest));

        assertEquals("Return type of method not supported: interface java.util.stream.Stream", thrown.getMessage());

        verify(authSubHandlerMock, times(1)).getAuthSub(any(Class.class), any(MethodInvocation.class), any());
        verify(factoryMock, never())
                .createMongoAnnotationQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, times(1))
                .createMongoMethodNameQueryManipulationEnforcementPoint(any(QueryManipulationEnforcementData.class));
        verify(factoryMock, never())
                .createProceededDataFilterEnforcementPoint(any(QueryManipulationEnforcementData.class));
    }

    @Test
    void when_extendedInterfacesOfRepositoryHaveWrongOrder_then_throwClassCastException() {

        // GIVEN
        var authSub                            = AuthorizationSubscription.of("subject", "permitTest", "resource",
                "environment");
        var methodInvocationForRepositoryError = new MethodInvocationForRepositoryError("findAllByFirstname",
                new ArrayList<>(List.of(String.class)), null, null);

        // WHEN
        when(authSubHandlerMock.getAuthSub(any(Class.class), any(MethodInvocation.class), any())).thenReturn(authSub);

        var proxyMongoHandler = new MongoProxyInterceptor<>(authSubHandlerMock, beanFactoryMock, pdpMock, factoryMock);

        // THEN
        ClassNotFoundException thrown = assertThrows(ClassNotFoundException.class,
                () -> proxyMongoHandler.invoke(methodInvocationForRepositoryError));

        assertEquals("The interface org.springframework.data.mongodb.repository.ReactiveMongoRepository or "
                + "interface org.springframework.data.repository.reactive.ReactiveCrudRepository could not be found as "
                + "an extension of the interface io.sapl.springdatamongoreactive.sapl.database.repositoryerror.RepositoryNotFoundExceptionTest",
                thrown.getMessage());
    }

}
