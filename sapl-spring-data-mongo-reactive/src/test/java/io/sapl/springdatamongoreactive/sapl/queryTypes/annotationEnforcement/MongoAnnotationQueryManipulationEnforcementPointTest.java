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
package io.sapl.springdatamongoreactive.sapl.queryTypes.annotationEnforcement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.springdatamongoreactive.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import io.sapl.springdatamongoreactive.sapl.handlers.DataManipulationHandler;
import io.sapl.springdatamongoreactive.sapl.handlers.LoggingConstraintHandlerProvider;
import io.sapl.springdatamongoreactive.sapl.handlers.MongoQueryManipulationObligationProvider;
import io.sapl.springdatamongoreactive.sapl.utils.ConstraintHandlerUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MongoAnnotationQueryManipulationEnforcementPointTest {

    static final ObjectMapper objectMapper = new ObjectMapper();
    static JsonNode           obligations;
    static JsonNode           mongoQueryManipulation;
    static JsonNode           conditions;

    final TestUser aaron   = new TestUser(new ObjectId(), "Aaron", 20);
    final TestUser brian   = new TestUser(new ObjectId(), "Brian", 21);
    final TestUser cathrin = new TestUser(new ObjectId(), "Cathrin", 33);

    final Flux<TestUser> data = Flux.just(aaron, brian, cathrin);

    EmbeddedPolicyDecisionPoint      pdpMock;
    LoggingConstraintHandlerProvider loggingConstraintHandlerProviderMock;
    ReactiveMongoTemplate            reactiveMongoTemplateMock;
    BeanFactory                      beanFactoryMock;

    MockedStatic<ConstraintHandlerUtils>           constraintHandlerUtilsMock;
    MockedStatic<QueryAnnotationParameterResolver> queryAnnotationParameterResolverMockedStatic;

    @BeforeAll
    public static void setUp() throws JsonProcessingException {
        obligations            = objectMapper.readTree(
                "[{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        mongoQueryManipulation = objectMapper
                .readTree("{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'age':  {'gt': 30 }}\"]}");
        conditions             = mongoQueryManipulation.get("conditions");
    }

    @BeforeEach
    public void beforeEach() {
        constraintHandlerUtilsMock                   = mockStatic(ConstraintHandlerUtils.class);
        queryAnnotationParameterResolverMockedStatic = mockStatic(QueryAnnotationParameterResolver.class);
        pdpMock                                      = mock(EmbeddedPolicyDecisionPoint.class);
        loggingConstraintHandlerProviderMock         = mock(LoggingConstraintHandlerProvider.class);
        reactiveMongoTemplateMock                    = mock(ReactiveMongoTemplate.class);
        beanFactoryMock                              = mock(BeanFactory.class);
    }

    @AfterEach
    public void cleanUp() {
        constraintHandlerUtilsMock.close();
        queryAnnotationParameterResolverMockedStatic.close();
    }

    @Test
    void when_thereAreConditionsInTheDecision_then_enforce() {
        try (MockedConstruction<MongoQueryManipulationObligationProvider> mongoQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(MongoQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandler = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                // GIVEN
                var expectedQuery             = new BasicQuery(
                        "{'firstname':  {'$in': [ 'Cathrin' ]}, 'age':  {'gt': 30 }}");
                var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllUsersTest",
                        new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData           = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                        beanFactoryMock, TestUser.class, pdpMock, authSub);

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                when(beanFactoryMock.getBean(eq(ReactiveMongoTemplate.class))).thenReturn(reactiveMongoTemplateMock);
                when(reactiveMongoTemplateMock.find(any(BasicQuery.class), any())).thenReturn(Flux.just(cathrin));
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligations);
                queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)))
                        .thenReturn("{'firstname':  {'$in': [ 'Cathrin' ]}}");

                var mongoAnnotationQueryManipulationEnforcementPoint = new MongoAnnotationQueryManipulationEnforcementPoint<>(
                        enforcementData);

                var mongoQueryManipulationObligationProviderMock = mongoQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(mongoQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                        .thenReturn(Boolean.TRUE);
                when(mongoQueryManipulationObligationProviderMock.getObligation(eq(obligations)))
                        .thenReturn(mongoQueryManipulation);
                when(mongoQueryManipulationObligationProviderMock.getConditions(eq(mongoQueryManipulation)))
                        .thenReturn(conditions);
                when(dataManipulationHandler.constructed().get(0).manipulate(eq(obligations)))
                        .thenReturn(obligations -> Flux.just(cathrin));

                // THEN
                var result = mongoAnnotationQueryManipulationEnforcementPoint.enforce();

                StepVerifier.create(result).expectNext(cathrin).expectComplete().verify();

                verify(reactiveMongoTemplateMock, times(1)).find(eq(expectedQuery), eq(TestUser.class));
                verify(mongoQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                verify(mongoQueryManipulationObligationProviderMock, times(1)).getObligation(eq(obligations));
                verify(mongoQueryManipulationObligationProviderMock, times(1))
                        .getConditions(eq(mongoQueryManipulation));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)),
                        times(1));
            }
        }
    }

    @Test
    void when_decisionIsNotPermit_then_throwAccessDeniedException() {
        // GIVEN
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllUsersTest",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
        var authSub                   = AuthorizationSubscription.of("subject", "denyTest", "resource", "environment");
        var enforcementData           = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                beanFactoryMock, TestUser.class, pdpMock, authSub);

        // WHEN
        when(pdpMock.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.DENY)));
        when(beanFactoryMock.getBean(eq(ReactiveMongoTemplate.class))).thenReturn(reactiveMongoTemplateMock);

        var mongoAnnotationQueryManipulationEnforcementPoint = new MongoAnnotationQueryManipulationEnforcementPoint<>(
                enforcementData);
        var accessDeniedException                            = mongoAnnotationQueryManipulationEnforcementPoint
                .enforce();

        // THEN
        StepVerifier.create(accessDeniedException).expectError(AccessDeniedException.class).verify();

        constraintHandlerUtilsMock.verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)),
                times(1));
        constraintHandlerUtilsMock.verify(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)),
                times(0));
    }

    @Test
    void when_mongoQueryManipulationObligationIsResponsibleIsFalse_then_proceedWithoutQueryManipulation() {
        try (MockedConstruction<MongoQueryManipulationObligationProvider> mongoQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(MongoQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandler = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {

                // GIVEN
                var expectedQuery             = new BasicQuery(
                        "{'firstname':  {'$in': [ 'Cathrin' ]}, 'age':  {'gt': 30 }}");
                var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllUsersTest",
                        new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData           = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                        beanFactoryMock, TestUser.class, pdpMock, authSub);

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                when(beanFactoryMock.getBean(eq(ReactiveMongoTemplate.class))).thenReturn(reactiveMongoTemplateMock);
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligations);
                queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)))
                        .thenReturn("{'firstname':  {'$in': [ 'Cathrin' ]}}");

                var mongoAnnotationQueryManipulationEnforcementPoint = new MongoAnnotationQueryManipulationEnforcementPoint<>(
                        enforcementData);

                var mongoQueryManipulationObligationProviderMock = mongoQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(mongoQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                        .thenReturn(Boolean.FALSE);
                when(dataManipulationHandler.constructed().get(0).manipulate(eq(obligations)))
                        .thenReturn(obligations -> data);

                // THEN
                var result = mongoAnnotationQueryManipulationEnforcementPoint.enforce();

                StepVerifier.create(result).expectNext(aaron).expectNext(brian).expectNext(cathrin).expectComplete()
                        .verify();

                verify(reactiveMongoTemplateMock, times(0)).find(eq(expectedQuery), eq(TestUser.class));
                verify(mongoQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                verify(mongoQueryManipulationObligationProviderMock, times(0)).getObligation(eq(obligations));
                verify(mongoQueryManipulationObligationProviderMock, times(0))
                        .getConditions(eq(mongoQueryManipulation));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)),
                        times(1));
            }
        }
    }

    @Test
    void when_mongoQueryManipulationObligationIsResponsibleIsFalseAndReturnTypeIsMono_then_proceedWithoutQueryManipulation() {
        try (MockedConstruction<MongoQueryManipulationObligationProvider> mongoQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(MongoQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandler = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {

                // GIVEN
                var expectedQuery             = new BasicQuery("{'firstname': 'Cathrin' }");
                var cathrinAsMono             = Mono.just(cathrin);
                var mongoMethodInvocationTest = new MethodInvocationForTesting("findUserTest",
                        new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), cathrinAsMono);
                var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData           = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                        beanFactoryMock, TestUser.class, pdpMock, authSub);

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                when(beanFactoryMock.getBean(eq(ReactiveMongoTemplate.class))).thenReturn(reactiveMongoTemplateMock);
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligations);
                queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)))
                        .thenReturn("{'firstname':  {'$in': [ 'Cathrin' ]}}");

                var mongoAnnotationQueryManipulationEnforcementPoint = new MongoAnnotationQueryManipulationEnforcementPoint<>(
                        enforcementData);

                when(dataManipulationHandler.constructed().get(0).manipulate(eq(obligations)))
                        .thenReturn(obligations -> Flux.just(cathrin));

                var mongoQueryManipulationObligationProviderMock = mongoQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(mongoQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                        .thenReturn(Boolean.FALSE);

                // THEN
                var result = mongoAnnotationQueryManipulationEnforcementPoint.enforce();

                StepVerifier.create(result).expectNext(cathrin).expectComplete().verify();

                verify(reactiveMongoTemplateMock, times(0)).find(eq(expectedQuery), eq(TestUser.class));
                verify(mongoQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                verify(mongoQueryManipulationObligationProviderMock, times(0)).getObligation(eq(obligations));
                verify(mongoQueryManipulationObligationProviderMock, times(0))
                        .getConditions(eq(mongoQueryManipulation));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)),
                        times(1));
            }
        }
    }

    @Test
    void when_mongoQueryManipulationObligationIsResponsibleIsFalseAndReturnTypeIsMono_then_throwThrowable() {
        try (MockedConstruction<MongoQueryManipulationObligationProvider> mongoQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(MongoQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandler = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {

                // GIVEN
                var expectedQuery             = new BasicQuery("{'firstname': 'Cathrin' }");
                var mongoMethodInvocationTest = new MethodInvocationForTesting("findUserTest",
                        new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), new Throwable());
                var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData           = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                        beanFactoryMock, TestUser.class, pdpMock, authSub);

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                when(beanFactoryMock.getBean(eq(ReactiveMongoTemplate.class))).thenReturn(reactiveMongoTemplateMock);
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligations);
                queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)))
                        .thenReturn("{'firstname':  {'$in': [ 'Cathrin' ]}}");

                var mongoAnnotationQueryManipulationEnforcementPoint = new MongoAnnotationQueryManipulationEnforcementPoint<>(
                        enforcementData);
                var mongoQueryManipulationObligationProviderMock     = mongoQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(mongoQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                        .thenReturn(Boolean.FALSE);

                // THEN
                var throwableException = mongoAnnotationQueryManipulationEnforcementPoint.enforce();

                StepVerifier.create(throwableException).expectError(Throwable.class).verify();

                verify(reactiveMongoTemplateMock, never()).find(eq(expectedQuery), eq(TestUser.class));
                verify(mongoQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                verify(mongoQueryManipulationObligationProviderMock, never()).getObligation(eq(obligations));
                verify(mongoQueryManipulationObligationProviderMock, never()).getConditions(eq(mongoQueryManipulation));
                verify(dataManipulationHandler.constructed().get(0), never()).manipulate(eq(obligations));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)),
                        times(1));
            }
        }
    }
}