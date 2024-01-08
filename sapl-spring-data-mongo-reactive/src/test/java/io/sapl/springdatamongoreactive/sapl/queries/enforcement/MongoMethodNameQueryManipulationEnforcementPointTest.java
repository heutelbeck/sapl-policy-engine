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
package io.sapl.springdatamongoreactive.sapl.queries.enforcement;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.springdatacommon.handlers.DataManipulationHandler;
import io.sapl.springdatacommon.handlers.QueryManipulationObligationProvider;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatacommon.sapl.utils.ConstraintHandlerUtils;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.MongoDbRepositoryTest;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MongoMethodNameQueryManipulationEnforcementPointTest {

    static final ObjectMapper objectMapper = new ObjectMapper();
    static JsonNode           obligations;
    static JsonNode           mongoQueryManipulation;
    static JsonNode           conditions;

    final TestUser aaron   = new TestUser(new ObjectId(), "Aaron", 20);
    final TestUser brian   = new TestUser(new ObjectId(), "Brian", 21);
    final TestUser cathrin = new TestUser(new ObjectId(), "Cathrin", 33);

    final Flux<TestUser> data                       = Flux.just(aaron, brian, cathrin);
    final String         mongoQueryManipulationType = "mongoQueryManipulation";

    @Autowired
    MongoDbRepositoryTest mongoDbRepositoryTest;

    ReactiveMongoTemplate       reactiveMongoTemplateMock = mock(ReactiveMongoTemplate.class);
    EmbeddedPolicyDecisionPoint pdpMock                   = mock(EmbeddedPolicyDecisionPoint.class);
    BeanFactory                 beanFactoryMock           = mock(BeanFactory.class, Answers.RETURNS_DEEP_STUBS);

    MockedStatic<ConstraintHandlerUtils> constraintHandlerUtilsMock;

    @BeforeAll
    static void setUp() throws JsonProcessingException {
        obligations            = objectMapper.readTree(
                "[{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        mongoQueryManipulation = objectMapper
                .readTree("{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'age':  {'gt': 30 }}\"]}");
        conditions             = mongoQueryManipulation.get("conditions");
    }

    @BeforeEach
    void beforeEach() {
        constraintHandlerUtilsMock = mockStatic(ConstraintHandlerUtils.class);
    }

    @AfterEach
    void cleanUp() {
        constraintHandlerUtilsMock.close();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void when_thereAreConditionsInTheDecision_then_enforce() {
        try (MockedConstruction<QueryManipulationObligationProvider> mongoQueryManipulationObligationProviderMockedConstruction = mockConstruction(
                QueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = mockConstruction(
                    DataManipulationHandler.class)) {
                try (MockedConstruction<SaplPartTreeCriteriaCreator> saplPartTreeCriteriaCreatorMockedConstruction = mockConstruction(
                        SaplPartTreeCriteriaCreator.class)) {

                    // GIVEN
                    var expectedQuery             = new BasicQuery("{'firstname': 'Cathrin', 'age':  {'gt': 30 }}");
                    var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstname",
                            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                    var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var enforcementData           = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                            beanFactoryMock, TestUser.class, pdpMock, authSub);

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    when(beanFactoryMock.getBean(ReactiveMongoTemplate.class)).thenReturn(reactiveMongoTemplateMock);
                    when(reactiveMongoTemplateMock.find(any(BasicQuery.class), any())).thenReturn(Flux.just(cathrin));
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                            .thenReturn(JsonNodeFactory.instance.nullNode());
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(obligations);

                    var mongoMethodNameQueryManipulationEnforcementPoint = new MongoMethodNameQueryManipulationEnforcementPoint<TestUser>(
                            enforcementData);

                    var mongoQueryManipulationObligationProviderMock = mongoQueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    var dataManipulationHandlerMock                  = dataManipulationHandlerMockedConstruction
                            .constructed().get(0);
                    var saplPartTreeCriteriaCreatorMock              = saplPartTreeCriteriaCreatorMockedConstruction
                            .constructed().get(0);

                    when(mongoQueryManipulationObligationProviderMock.isResponsible(obligations,
                            mongoQueryManipulationType)).thenReturn(Boolean.TRUE);
                    when(mongoQueryManipulationObligationProviderMock.getObligation(obligations,
                            mongoQueryManipulationType)).thenReturn(mongoQueryManipulation);
                    when(mongoQueryManipulationObligationProviderMock.getConditions(mongoQueryManipulation))
                            .thenReturn(conditions);
                    when(dataManipulationHandlerMock.manipulate(obligations))
                            .thenReturn(obligations -> Flux.just(cathrin));
                    when(saplPartTreeCriteriaCreatorMock.createManipulatedQuery(conditions)).thenReturn(expectedQuery);

                    // THEN
                    var result = mongoMethodNameQueryManipulationEnforcementPoint.enforce();

                    StepVerifier.create(result).expectNext(cathrin).expectComplete().verify();

                    verify(reactiveMongoTemplateMock, times(1)).find(expectedQuery, TestUser.class);
                    verify(saplPartTreeCriteriaCreatorMock, times(1)).createManipulatedQuery(conditions);
                    verify(dataManipulationHandlerMock, times(1)).manipulate(obligations);
                    verify(mongoQueryManipulationObligationProviderMock, times(1)).isResponsible(obligations,
                            mongoQueryManipulationType);
                    verify(mongoQueryManipulationObligationProviderMock, times(1)).getObligation(obligations,
                            mongoQueryManipulationType);
                    verify(mongoQueryManipulationObligationProviderMock, times(1))
                            .getConditions(mongoQueryManipulation);
                    constraintHandlerUtilsMock
                            .verify(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                }
            }
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    void when_decisionIsNotPermit_then_throwAccessDeniedException() {
        try (MockedConstruction<QueryManipulationObligationProvider> mongoQueryManipulationObligationProviderMockedConstruction = mockConstruction(
                QueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = mockConstruction(
                    DataManipulationHandler.class)) {
                try (MockedConstruction<SaplPartTreeCriteriaCreator> saplPartTreeCriteriaCreatorMockedConstruction = mockConstruction(
                        SaplPartTreeCriteriaCreator.class)) {

                    // GIVEN
                    var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstname",
                            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                    var authSub                   = AuthorizationSubscription.of("subject", "denyTest", "resource",
                            "environment");
                    var enforcementData           = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                            beanFactoryMock, TestUser.class, pdpMock, authSub);

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.DENY)));
                    when(beanFactoryMock.getBean(ReactiveMongoTemplate.class)).thenReturn(reactiveMongoTemplateMock);

                    var mongoMethodNameQueryManipulationEnforcementPoint = new MongoMethodNameQueryManipulationEnforcementPoint<TestUser>(
                            enforcementData);
                    var accessDeniedException                            = mongoMethodNameQueryManipulationEnforcementPoint
                            .enforce();

                    // THEN
                    StepVerifier.create(accessDeniedException).expectError(AccessDeniedException.class).verify();

                    assertNotNull(saplPartTreeCriteriaCreatorMockedConstruction.constructed().get(0));
                    assertNotNull(dataManipulationHandlerMockedConstruction.constructed().get(0));
                    assertNotNull(mongoQueryManipulationObligationProviderMockedConstruction.constructed().get(0));
                    constraintHandlerUtilsMock
                            .verify(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(0));
                }
            }
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    void when_mongoQueryManipulationObligationIsResponsibleIsFalse_then_proceedWithoutQueryManipulation() {
        try (MockedConstruction<QueryManipulationObligationProvider> mongoQueryManipulationObligationProviderMockedConstruction = mockConstruction(
                QueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = mockConstruction(
                    DataManipulationHandler.class)) {
                try (MockedConstruction<SaplPartTreeCriteriaCreator> saplPartTreeCriteriaCreatorMockedConstruction = mockConstruction(
                        SaplPartTreeCriteriaCreator.class)) {

                    // GIVEN
                    var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstname",
                            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                    var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var enforcementData           = new QueryManipulationEnforcementData<TestUser>(
                            mongoMethodInvocationTest, beanFactoryMock, TestUser.class, pdpMock, authSub);

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    when(beanFactoryMock.getBean(ReactiveMongoTemplate.class)).thenReturn(reactiveMongoTemplateMock);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                            .thenReturn(JsonNodeFactory.instance.nullNode());
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(obligations);

                    var mongoMethodNameQueryManipulationEnforcementPoint = new MongoMethodNameQueryManipulationEnforcementPoint<TestUser>(
                            enforcementData);

                    var mongoQueryManipulationObligationProviderMock = mongoQueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    var dataManipulationHandlerMock                  = dataManipulationHandlerMockedConstruction
                            .constructed().get(0);
                    var saplPartTreeCriteriaCreatorMock              = saplPartTreeCriteriaCreatorMockedConstruction
                            .constructed().get(0);

                    when(mongoQueryManipulationObligationProviderMock.isResponsible(obligations,
                            mongoQueryManipulationType)).thenReturn(Boolean.FALSE);
                    when(dataManipulationHandlerMock.manipulate(obligations)).thenReturn(obligations -> data);

                    // THEN
                    var result = mongoMethodNameQueryManipulationEnforcementPoint.enforce();

                    StepVerifier.create(result).expectNext(aaron).expectNext(brian).expectNext(cathrin).expectComplete()
                            .verify();

                    verify(reactiveMongoTemplateMock, never()).find(any(Query.class), eq(TestUser.class));
                    verify(saplPartTreeCriteriaCreatorMock, never()).createManipulatedQuery(conditions);
                    verify(dataManipulationHandlerMock, times(1)).manipulate(obligations);
                    verify(mongoQueryManipulationObligationProviderMock, times(1)).isResponsible(obligations,
                            mongoQueryManipulationType);
                    verify(mongoQueryManipulationObligationProviderMock, never()).getObligation(obligations,
                            mongoQueryManipulationType);
                    verify(mongoQueryManipulationObligationProviderMock, never()).getConditions(mongoQueryManipulation);
                    constraintHandlerUtilsMock
                            .verify(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                }
            }
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    void when_mongoQueryManipulationObligationIsResponsibleIsFalseAndReturnTypeIsMono_then_proceedWithoutQueryManipulation() {
        try (MockedConstruction<QueryManipulationObligationProvider> mongoQueryManipulationObligationProviderMockedConstruction = mockConstruction(
                QueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = mockConstruction(
                    DataManipulationHandler.class)) {
                try (MockedConstruction<SaplPartTreeCriteriaCreator> saplPartTreeCriteriaCreatorMockedConstruction = mockConstruction(
                        SaplPartTreeCriteriaCreator.class)) {

                    // GIVEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    var mongoMethodInvocationTest = new MethodInvocationForTesting("findByAge",
                            new ArrayList<>(List.of(int.class)), new ArrayList<>(List.of(30)), Mono.just(cathrin));
                    var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var enforcementData           = new QueryManipulationEnforcementData<TestUser>(
                            mongoMethodInvocationTest, beanFactoryMock, TestUser.class, pdpMock, authSub);

                    // WHEN
                    when(beanFactoryMock.getBean(ReactiveMongoTemplate.class)).thenReturn(reactiveMongoTemplateMock);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                            .thenReturn(JsonNodeFactory.instance.nullNode());
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(obligations);

                    var mongoMethodNameQueryManipulationEnforcementPoint = new MongoMethodNameQueryManipulationEnforcementPoint<TestUser>(
                            enforcementData);

                    var mongoQueryManipulationObligationProviderMock = mongoQueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    var dataManipulationHandlerMock                  = dataManipulationHandlerMockedConstruction
                            .constructed().get(0);
                    var saplPartTreeCriteriaCreatorMock              = saplPartTreeCriteriaCreatorMockedConstruction
                            .constructed().get(0);

                    when(mongoQueryManipulationObligationProviderMock.isResponsible(obligations,
                            mongoQueryManipulationType)).thenReturn(Boolean.FALSE);
                    when(dataManipulationHandlerMock.manipulate(obligations))
                            .thenReturn(obligations -> Flux.just(cathrin));

                    // THEN
                    var result = mongoMethodNameQueryManipulationEnforcementPoint.enforce();

                    StepVerifier.create(result).expectNext(cathrin).expectComplete().verify();

                    verify(reactiveMongoTemplateMock, never()).find(any(Query.class), eq(TestUser.class));
                    verify(saplPartTreeCriteriaCreatorMock, never()).createManipulatedQuery(conditions);
                    verify(dataManipulationHandlerMock, times(1)).manipulate(obligations);
                    verify(mongoQueryManipulationObligationProviderMock, times(1)).isResponsible(obligations,
                            mongoQueryManipulationType);
                    verify(mongoQueryManipulationObligationProviderMock, never()).getObligation(obligations,
                            mongoQueryManipulationType);
                    verify(mongoQueryManipulationObligationProviderMock, never()).getConditions(mongoQueryManipulation);
                    constraintHandlerUtilsMock
                            .verify(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                }
            }
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    void when_mongoQueryManipulationObligationIsResponsibleIsFalseAndReturnTypeIsMono_then_throwThrowable() {
        try (MockedConstruction<QueryManipulationObligationProvider> mongoQueryManipulationObligationProviderMockedConstruction = mockConstruction(
                QueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = mockConstruction(
                    DataManipulationHandler.class)) {
                try (MockedConstruction<SaplPartTreeCriteriaCreator> saplPartTreeCriteriaCreatorMockedConstruction = mockConstruction(
                        SaplPartTreeCriteriaCreator.class)) {

                    // GIVEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    var mongoMethodInvocationTest = new MethodInvocationForTesting("findByAge",
                            new ArrayList<>(List.of(int.class)), new ArrayList<>(List.of(30)), new Throwable());
                    var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var enforcementData           = new QueryManipulationEnforcementData<TestUser>(
                            mongoMethodInvocationTest, beanFactoryMock, TestUser.class, pdpMock, authSub);

                    // WHEN
                    when(beanFactoryMock.getBean(ReactiveMongoTemplate.class)).thenReturn(reactiveMongoTemplateMock);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                            .thenReturn(JsonNodeFactory.instance.nullNode());
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(obligations);

                    var mongoMethodNameQueryManipulationEnforcementPoint = new MongoMethodNameQueryManipulationEnforcementPoint<TestUser>(
                            enforcementData);

                    var mongoQueryManipulationObligationProviderMock = mongoQueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    var dataManipulationHandlerMock                  = dataManipulationHandlerMockedConstruction
                            .constructed().get(0);
                    var saplPartTreeCriteriaCreatorMock              = saplPartTreeCriteriaCreatorMockedConstruction
                            .constructed().get(0);

                    when(mongoQueryManipulationObligationProviderMock.isResponsible(obligations,
                            mongoQueryManipulationType)).thenReturn(Boolean.FALSE);

                    // THEN
                    var throwableException = mongoMethodNameQueryManipulationEnforcementPoint.enforce();

                    StepVerifier.create(throwableException).expectError(Throwable.class).verify();

                    verify(reactiveMongoTemplateMock, never()).find(any(Query.class), eq(TestUser.class));
                    verify(saplPartTreeCriteriaCreatorMock, never()).createManipulatedQuery(conditions);
                    verify(dataManipulationHandlerMock, never()).manipulate(obligations);
                    verify(mongoQueryManipulationObligationProviderMock, times(1)).isResponsible(obligations,
                            mongoQueryManipulationType);
                    verify(mongoQueryManipulationObligationProviderMock, never()).getObligation(obligations,
                            mongoQueryManipulationType);
                    verify(mongoQueryManipulationObligationProviderMock, never()).getConditions(mongoQueryManipulation);
                    constraintHandlerUtilsMock
                            .verify(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                }
            }
        }
    }
}
