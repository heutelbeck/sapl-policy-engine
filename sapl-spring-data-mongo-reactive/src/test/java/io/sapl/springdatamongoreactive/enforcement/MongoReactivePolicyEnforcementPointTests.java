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
package io.sapl.springdatamongoreactive.enforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.intercept.MethodInvocation;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.repository.core.RepositoryInformation;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.spring.method.metadata.QueryEnforce;
import io.sapl.springdatacommon.services.QueryEnforceAuthorizationSubscriptionService;
import io.sapl.springdatacommon.services.RepositoryInformationCollectorService;
import io.sapl.springdatacommon.utils.AnnotationUtilities;
import io.sapl.springdatacommon.utils.Utilities;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class MongoReactivePolicyEnforcementPointTests {

    @Mock
    ObjectProvider<MongoReactiveAnnotationQueryManipulationEnforcementPoint<TestUser>> objectProviderMongoReactiveAnnotationQueryManipulationEnforcementPointMock;

    @Mock
    ObjectProvider<MongoReactiveMethodNameQueryManipulationEnforcementPoint<TestUser>> objectProviderMongoReactiveMethodNameQueryManipulationEnforcementPointMock;

    @Mock
    ObjectProvider<QueryEnforceAuthorizationSubscriptionService> objectProviderQueryEnforceAuthorizationSubscriptionServiceMock;

    @Mock
    MongoReactiveAnnotationQueryManipulationEnforcementPoint<TestUser> mongoReactiveAnnotationQueryManipulationEnforcementPointMock;

    @Mock
    MongoReactiveMethodNameQueryManipulationEnforcementPoint<TestUser> mongoReactiveMethodNameQueryManipulationEnforcementPointMock;
    QueryEnforceAuthorizationSubscriptionService                       queryEnforceAuthorizationSubscriptionServiceMock = mock(
            QueryEnforceAuthorizationSubscriptionService.class);
    RepositoryInformationCollectorService                              repositoryInformationCollectorServiceMock        = mock(
            RepositoryInformationCollectorService.class);

    RepositoryInformation      repositoryInformationMock = mock(RepositoryInformation.class, RETURNS_DEEP_STUBS);
    final TestUser             cathrin                   = new TestUser(new ObjectId(), "Cathrin", 33, true);
    MethodInvocationForTesting methodInvocation          = new MethodInvocationForTesting("findAllByFirstname",
            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Aaron")), Flux.just(cathrin));
    AuthorizationSubscription  authSub                   = AuthorizationSubscription.of("", "", "", "");

    MockedStatic<AnnotationUtilities> annotationUtilitiesMock;
    MockedStatic<Utilities>           utilitiesMock;

    @BeforeEach
    public void beforeEach() {
        lenient().when(objectProviderMongoReactiveAnnotationQueryManipulationEnforcementPointMock.getObject())
                .thenReturn(mongoReactiveAnnotationQueryManipulationEnforcementPointMock);
        lenient().when(objectProviderMongoReactiveMethodNameQueryManipulationEnforcementPointMock.getObject())
                .thenReturn(mongoReactiveMethodNameQueryManipulationEnforcementPointMock);
        lenient().when(objectProviderQueryEnforceAuthorizationSubscriptionServiceMock.getObject())
                .thenReturn(queryEnforceAuthorizationSubscriptionServiceMock);

        annotationUtilitiesMock = mockStatic(AnnotationUtilities.class);
        utilitiesMock           = mockStatic(Utilities.class);
    }

    @AfterEach
    public void cleanUp() {
        annotationUtilitiesMock.close();
        utilitiesMock.close();
    }

    @Test
    void when_invoke_then_hasAnnotationQueryEnforce() {
        // GIVEN
        final var enforcementPoint = new MongoReactivePolicyEnforcementPoint<TestUser>(
                objectProviderMongoReactiveAnnotationQueryManipulationEnforcementPointMock,
                objectProviderMongoReactiveMethodNameQueryManipulationEnforcementPointMock,
                objectProviderQueryEnforceAuthorizationSubscriptionServiceMock,
                repositoryInformationCollectorServiceMock);

        // WHEN
        annotationUtilitiesMock.when(() -> AnnotationUtilities.hasAnnotationQueryEnforce(any(Method.class)))
                .thenReturn(true);
        annotationUtilitiesMock.when(() -> AnnotationUtilities.hasAnnotationQueryReactiveMongo(any(Method.class)))
                .thenReturn(true);

        when(repositoryInformationCollectorServiceMock.getRepositoryByName(anyString()))
                .thenReturn(repositoryInformationMock);
        when(repositoryInformationMock.isCustomMethod(any(Method.class))).thenReturn(false);
        when(queryEnforceAuthorizationSubscriptionServiceMock.getAuthorizationSubscription(any(MethodInvocation.class),
                any(QueryEnforce.class))).thenReturn(authSub);
        when(mongoReactiveAnnotationQueryManipulationEnforcementPointMock.enforce(eq(authSub), any(),
                eq(methodInvocation))).thenReturn(Flux.just(cathrin));
        utilitiesMock.when(() -> Utilities.convertReturnTypeIfNecessary(any(Flux.class), any()))
                .thenReturn(Flux.just(cathrin));

        final var result = (Flux<TestUser>) enforcementPoint.invoke(methodInvocation);

        // THEN
        StepVerifier.create(result).expectNext(cathrin).expectComplete().verify();

        annotationUtilitiesMock.verify(() -> AnnotationUtilities.hasAnnotationQueryEnforce(any(Method.class)),
                times(1));
        annotationUtilitiesMock.verify(() -> AnnotationUtilities.hasAnnotationQueryReactiveMongo(any(Method.class)),
                times(1));
        verify(repositoryInformationCollectorServiceMock, times(1)).getRepositoryByName(anyString());
        verify(repositoryInformationMock, times(1)).isCustomMethod(any(Method.class));
        verify(queryEnforceAuthorizationSubscriptionServiceMock, times(1))
                .getAuthorizationSubscription(any(MethodInvocation.class), any(QueryEnforce.class));
        verify(mongoReactiveAnnotationQueryManipulationEnforcementPointMock, times(1)).enforce(eq(authSub), any(),
                eq(methodInvocation));
        verify(mongoReactiveMethodNameQueryManipulationEnforcementPointMock, times(0)).enforce(eq(authSub), any(),
                eq(methodInvocation));
        utilitiesMock.verify(() -> Utilities.convertReturnTypeIfNecessary(any(Flux.class), any()), times(1));
    }

    @Test
    void when_invoke_then_hasNoAnnotationQueryEnforce() {
        // GIVEN
        final var enforcementPoint = new MongoReactivePolicyEnforcementPoint<TestUser>(
                objectProviderMongoReactiveAnnotationQueryManipulationEnforcementPointMock,
                objectProviderMongoReactiveMethodNameQueryManipulationEnforcementPointMock,
                objectProviderQueryEnforceAuthorizationSubscriptionServiceMock,
                repositoryInformationCollectorServiceMock);

        // WHEN
        annotationUtilitiesMock.when(() -> AnnotationUtilities.hasAnnotationQueryEnforce(any(Method.class)))
                .thenReturn(false);

        final var result = (Flux<TestUser>) enforcementPoint.invoke(methodInvocation);

        // THEN
        StepVerifier.create(result).expectNext(cathrin).expectComplete().verify();

        annotationUtilitiesMock.verify(() -> AnnotationUtilities.hasAnnotationQueryEnforce(any(Method.class)),
                times(1));
        annotationUtilitiesMock.verify(() -> AnnotationUtilities.hasAnnotationQueryReactiveMongo(any(Method.class)),
                times(0));
        verify(repositoryInformationCollectorServiceMock, times(0)).getRepositoryByName(anyString());
        verify(repositoryInformationMock, times(0)).isCustomMethod(any(Method.class));
        verify(queryEnforceAuthorizationSubscriptionServiceMock, times(0))
                .getAuthorizationSubscription(any(MethodInvocation.class), any(QueryEnforce.class));
        verify(mongoReactiveAnnotationQueryManipulationEnforcementPointMock, times(0)).enforce(eq(authSub), any(),
                eq(methodInvocation));
        verify(mongoReactiveMethodNameQueryManipulationEnforcementPointMock, times(0)).enforce(eq(authSub), any(),
                eq(methodInvocation));
        utilitiesMock.verify(() -> Utilities.convertReturnTypeIfNecessary(any(Flux.class), any()), times(0));
    }

    @Test
    void when_invoke_then_isSpringDataDefaultMethod() {
        // GIVEN
        final var enforcementPoint = new MongoReactivePolicyEnforcementPoint<TestUser>(
                objectProviderMongoReactiveAnnotationQueryManipulationEnforcementPointMock,
                objectProviderMongoReactiveMethodNameQueryManipulationEnforcementPointMock,
                objectProviderQueryEnforceAuthorizationSubscriptionServiceMock,
                repositoryInformationCollectorServiceMock);

        // WHEN
        annotationUtilitiesMock.when(() -> AnnotationUtilities.hasAnnotationQueryEnforce(any(Method.class)))
                .thenReturn(true);
        annotationUtilitiesMock.when(() -> AnnotationUtilities.hasAnnotationQueryReactiveMongo(any(Method.class)))
                .thenReturn(false);
        utilitiesMock.when(() -> Utilities.isSpringDataDefaultMethod(anyString())).thenReturn(true);
        utilitiesMock.when(() -> Utilities.isMethodNameValid(anyString())).thenReturn(false);

        when(repositoryInformationCollectorServiceMock.getRepositoryByName(anyString()))
                .thenReturn(repositoryInformationMock);
        when(repositoryInformationMock.isCustomMethod(any(Method.class))).thenReturn(false);
        when(queryEnforceAuthorizationSubscriptionServiceMock.getAuthorizationSubscription(any(MethodInvocation.class),
                any(QueryEnforce.class))).thenReturn(authSub);
        when(mongoReactiveMethodNameQueryManipulationEnforcementPointMock.enforce(eq(authSub), any(),
                eq(methodInvocation))).thenReturn(Flux.just(cathrin));
        utilitiesMock.when(() -> Utilities.convertReturnTypeIfNecessary(any(Flux.class), any()))
                .thenReturn(Flux.just(cathrin));

        final var result = (Flux<TestUser>) enforcementPoint.invoke(methodInvocation);

        // THEN
        StepVerifier.create(result).expectNext(cathrin).expectComplete().verify();

        annotationUtilitiesMock.verify(() -> AnnotationUtilities.hasAnnotationQueryEnforce(any(Method.class)),
                times(1));
        annotationUtilitiesMock.verify(() -> AnnotationUtilities.hasAnnotationQueryReactiveMongo(any(Method.class)),
                times(1));
        verify(repositoryInformationCollectorServiceMock, times(1)).getRepositoryByName(anyString());
        verify(repositoryInformationMock, times(1)).isCustomMethod(any(Method.class));
        verify(queryEnforceAuthorizationSubscriptionServiceMock, times(1))
                .getAuthorizationSubscription(any(MethodInvocation.class), any(QueryEnforce.class));
        verify(mongoReactiveAnnotationQueryManipulationEnforcementPointMock, times(0)).enforce(eq(authSub), any(),
                eq(methodInvocation));
        verify(mongoReactiveMethodNameQueryManipulationEnforcementPointMock, times(1)).enforce(eq(authSub), any(),
                eq(methodInvocation));
        utilitiesMock.verify(() -> Utilities.isSpringDataDefaultMethod(anyString()), times(1));
        utilitiesMock.verify(() -> Utilities.isMethodNameValid(anyString()), times(0));
        utilitiesMock.verify(() -> Utilities.convertReturnTypeIfNecessary(any(Flux.class), any()), times(1));
    }

    @Test
    void when_invoke_then_isMethodNameValid() {
        // GIVEN
        final var enforcementPoint = new MongoReactivePolicyEnforcementPoint<TestUser>(
                objectProviderMongoReactiveAnnotationQueryManipulationEnforcementPointMock,
                objectProviderMongoReactiveMethodNameQueryManipulationEnforcementPointMock,
                objectProviderQueryEnforceAuthorizationSubscriptionServiceMock,
                repositoryInformationCollectorServiceMock);

        // WHEN
        annotationUtilitiesMock.when(() -> AnnotationUtilities.hasAnnotationQueryEnforce(any(Method.class)))
                .thenReturn(true);
        annotationUtilitiesMock.when(() -> AnnotationUtilities.hasAnnotationQueryReactiveMongo(any(Method.class)))
                .thenReturn(false);
        utilitiesMock.when(() -> Utilities.isSpringDataDefaultMethod(anyString())).thenReturn(false);
        utilitiesMock.when(() -> Utilities.isMethodNameValid(anyString())).thenReturn(true);

        when(repositoryInformationCollectorServiceMock.getRepositoryByName(anyString()))
                .thenReturn(repositoryInformationMock);
        when(repositoryInformationMock.isCustomMethod(any(Method.class))).thenReturn(false);
        when(queryEnforceAuthorizationSubscriptionServiceMock.getAuthorizationSubscription(any(MethodInvocation.class),
                any(QueryEnforce.class))).thenReturn(authSub);
        when(mongoReactiveMethodNameQueryManipulationEnforcementPointMock.enforce(eq(authSub), any(),
                eq(methodInvocation))).thenReturn(Flux.just(cathrin));
        utilitiesMock.when(() -> Utilities.convertReturnTypeIfNecessary(any(Flux.class), any()))
                .thenReturn(Flux.just(cathrin));

        final var result = (Flux<TestUser>) enforcementPoint.invoke(methodInvocation);

        // THEN
        StepVerifier.create(result).expectNext(cathrin).expectComplete().verify();

        annotationUtilitiesMock.verify(() -> AnnotationUtilities.hasAnnotationQueryEnforce(any(Method.class)),
                times(1));
        annotationUtilitiesMock.verify(() -> AnnotationUtilities.hasAnnotationQueryReactiveMongo(any(Method.class)),
                times(1));
        verify(repositoryInformationCollectorServiceMock, times(1)).getRepositoryByName(anyString());
        verify(repositoryInformationMock, times(1)).isCustomMethod(any(Method.class));
        verify(queryEnforceAuthorizationSubscriptionServiceMock, times(1))
                .getAuthorizationSubscription(any(MethodInvocation.class), any(QueryEnforce.class));
        verify(mongoReactiveAnnotationQueryManipulationEnforcementPointMock, times(0)).enforce(eq(authSub), any(),
                eq(methodInvocation));
        verify(mongoReactiveMethodNameQueryManipulationEnforcementPointMock, times(1)).enforce(eq(authSub), any(),
                eq(methodInvocation));
        utilitiesMock.verify(() -> Utilities.isSpringDataDefaultMethod(anyString()), times(1));
        utilitiesMock.verify(() -> Utilities.isMethodNameValid(anyString()), times(1));
        utilitiesMock.verify(() -> Utilities.convertReturnTypeIfNecessary(any(Flux.class), any()), times(1));
    }

    @Test
    void when_invoke_then_invocationProceed() {
        // GIVEN
        final var enforcementPoint = new MongoReactivePolicyEnforcementPoint<TestUser>(
                objectProviderMongoReactiveAnnotationQueryManipulationEnforcementPointMock,
                objectProviderMongoReactiveMethodNameQueryManipulationEnforcementPointMock,
                objectProviderQueryEnforceAuthorizationSubscriptionServiceMock,
                repositoryInformationCollectorServiceMock);

        // WHEN
        annotationUtilitiesMock.when(() -> AnnotationUtilities.hasAnnotationQueryEnforce(any(Method.class)))
                .thenReturn(true);
        annotationUtilitiesMock.when(() -> AnnotationUtilities.hasAnnotationQueryReactiveMongo(any(Method.class)))
                .thenReturn(false);
        utilitiesMock.when(() -> Utilities.isSpringDataDefaultMethod(anyString())).thenReturn(false);
        utilitiesMock.when(() -> Utilities.isMethodNameValid(anyString())).thenReturn(false);

        when(repositoryInformationCollectorServiceMock.getRepositoryByName(anyString()))
                .thenReturn(repositoryInformationMock);
        when(repositoryInformationMock.isCustomMethod(any(Method.class))).thenReturn(false);
        when(queryEnforceAuthorizationSubscriptionServiceMock.getAuthorizationSubscription(any(MethodInvocation.class),
                any(QueryEnforce.class))).thenReturn(authSub);
        utilitiesMock.when(() -> Utilities.convertReturnTypeIfNecessary(any(Flux.class), any()))
                .thenReturn(Flux.just(cathrin));

        final var result = (Flux<TestUser>) enforcementPoint.invoke(methodInvocation);

        // THEN
        StepVerifier.create(result).expectNext(cathrin).expectComplete().verify();

        annotationUtilitiesMock.verify(() -> AnnotationUtilities.hasAnnotationQueryEnforce(any(Method.class)),
                times(1));
        annotationUtilitiesMock.verify(() -> AnnotationUtilities.hasAnnotationQueryReactiveMongo(any(Method.class)),
                times(1));
        verify(repositoryInformationCollectorServiceMock, times(1)).getRepositoryByName(anyString());
        verify(repositoryInformationMock, times(1)).isCustomMethod(any(Method.class));
        verify(queryEnforceAuthorizationSubscriptionServiceMock, times(1))
                .getAuthorizationSubscription(any(MethodInvocation.class), any(QueryEnforce.class));
        verify(mongoReactiveAnnotationQueryManipulationEnforcementPointMock, times(0)).enforce(eq(authSub), any(),
                eq(methodInvocation));
        verify(mongoReactiveMethodNameQueryManipulationEnforcementPointMock, times(0)).enforce(eq(authSub), any(),
                eq(methodInvocation));
        utilitiesMock.verify(() -> Utilities.isSpringDataDefaultMethod(anyString()), times(1));
        utilitiesMock.verify(() -> Utilities.isMethodNameValid(anyString()), times(1));
        utilitiesMock.verify(() -> Utilities.convertReturnTypeIfNecessary(any(Flux.class), any()), times(0));
    }

    @Test
    void when_invoke_then_throwIllegalStateException() {
        // GIVEN
        final var enforcementPoint = new MongoReactivePolicyEnforcementPoint<TestUser>(
                objectProviderMongoReactiveAnnotationQueryManipulationEnforcementPointMock,
                objectProviderMongoReactiveMethodNameQueryManipulationEnforcementPointMock,
                objectProviderQueryEnforceAuthorizationSubscriptionServiceMock,
                repositoryInformationCollectorServiceMock);

        // WHEN
        annotationUtilitiesMock.when(() -> AnnotationUtilities.hasAnnotationQueryEnforce(any(Method.class)))
                .thenReturn(true);

        when(repositoryInformationCollectorServiceMock.getRepositoryByName(anyString()))
                .thenReturn(repositoryInformationMock);
        when(repositoryInformationMock.isCustomMethod(any(Method.class))).thenReturn(false);
        when(queryEnforceAuthorizationSubscriptionServiceMock.getAuthorizationSubscription(any(MethodInvocation.class),
                any(QueryEnforce.class))).thenReturn(null);

        // THEN
        final var errorMessage = "The Sapl implementation for the manipulation of the database queries was recognised, but no AuthorizationSubscription was found.";

        final var illegalStateException = assertThrows(IllegalStateException.class, () -> {
            enforcementPoint.invoke(methodInvocation);
        });
        assertEquals(errorMessage, illegalStateException.getMessage());

        annotationUtilitiesMock.verify(() -> AnnotationUtilities.hasAnnotationQueryEnforce(any(Method.class)),
                times(1));
        annotationUtilitiesMock.verify(() -> AnnotationUtilities.hasAnnotationQueryReactiveMongo(any(Method.class)),
                times(0));
        verify(repositoryInformationCollectorServiceMock, times(1)).getRepositoryByName(anyString());
        verify(repositoryInformationMock, times(1)).isCustomMethod(any(Method.class));
        verify(queryEnforceAuthorizationSubscriptionServiceMock, times(1))
                .getAuthorizationSubscription(any(MethodInvocation.class), any(QueryEnforce.class));
        verify(mongoReactiveAnnotationQueryManipulationEnforcementPointMock, times(0)).enforce(eq(authSub), any(),
                eq(methodInvocation));
        verify(mongoReactiveMethodNameQueryManipulationEnforcementPointMock, times(0)).enforce(eq(authSub), any(),
                eq(methodInvocation));
        utilitiesMock.verify(() -> Utilities.isSpringDataDefaultMethod(anyString()), times(0));
        utilitiesMock.verify(() -> Utilities.isMethodNameValid(anyString()), times(0));
        utilitiesMock.verify(() -> Utilities.convertReturnTypeIfNecessary(any(Flux.class), any()), times(0));
    }

    @Test
    void when_invoke_then_throwIllegalStateException2() {
        // GIVEN
        final var enforcementPoint = new MongoReactivePolicyEnforcementPoint<TestUser>(
                objectProviderMongoReactiveAnnotationQueryManipulationEnforcementPointMock,
                objectProviderMongoReactiveMethodNameQueryManipulationEnforcementPointMock,
                objectProviderQueryEnforceAuthorizationSubscriptionServiceMock,
                repositoryInformationCollectorServiceMock);

        // WHEN
        annotationUtilitiesMock.when(() -> AnnotationUtilities.hasAnnotationQueryEnforce(any(Method.class)))
                .thenReturn(true);

        when(repositoryInformationCollectorServiceMock.getRepositoryByName(anyString()))
                .thenReturn(repositoryInformationMock);
        when(repositoryInformationMock.isCustomMethod(any(Method.class))).thenReturn(true);

        // THEN
        final var errorMessage = "The QueryEnforce annotation cannot be applied to custom repository methods. ";

        final var illegalStateException = assertThrows(IllegalStateException.class, () -> {
            enforcementPoint.invoke(methodInvocation);
        });
        assertEquals(errorMessage, illegalStateException.getMessage());

        annotationUtilitiesMock.verify(() -> AnnotationUtilities.hasAnnotationQueryEnforce(any(Method.class)),
                times(1));
        annotationUtilitiesMock.verify(() -> AnnotationUtilities.hasAnnotationQueryReactiveMongo(any(Method.class)),
                times(0));
        verify(repositoryInformationCollectorServiceMock, times(1)).getRepositoryByName(anyString());
        verify(repositoryInformationMock, times(1)).isCustomMethod(any(Method.class));
        verify(queryEnforceAuthorizationSubscriptionServiceMock, times(0))
                .getAuthorizationSubscription(any(MethodInvocation.class), any(QueryEnforce.class));
        verify(mongoReactiveAnnotationQueryManipulationEnforcementPointMock, times(0)).enforce(eq(authSub), any(),
                eq(methodInvocation));
        verify(mongoReactiveMethodNameQueryManipulationEnforcementPointMock, times(0)).enforce(eq(authSub), any(),
                eq(methodInvocation));
        utilitiesMock.verify(() -> Utilities.isSpringDataDefaultMethod(anyString()), times(0));
        utilitiesMock.verify(() -> Utilities.isMethodNameValid(anyString()), times(0));
        utilitiesMock.verify(() -> Utilities.convertReturnTypeIfNecessary(any(Flux.class), any()), times(0));
    }

}
