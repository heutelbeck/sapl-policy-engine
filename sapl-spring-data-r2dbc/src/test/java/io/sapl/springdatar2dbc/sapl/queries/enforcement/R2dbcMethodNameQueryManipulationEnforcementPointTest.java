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
package io.sapl.springdatar2dbc.sapl.queries.enforcement;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointFactory;
import io.sapl.springdatacommon.handlers.DataManipulationHandler;
import io.sapl.springdatacommon.handlers.QueryManipulationObligationProvider;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatacommon.sapl.utils.ConstraintHandlerUtils;
import io.sapl.springdatar2dbc.database.MethodInvocationForTesting;
import io.sapl.springdatar2dbc.database.Person;
import io.sapl.springdatar2dbc.database.Role;
import io.sapl.springdatar2dbc.sapl.QueryManipulationExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class R2dbcMethodNameQueryManipulationEnforcementPointTest {

    private static final ObjectMapper          objectMapper               = new ObjectMapper();
    private static ArrayNode                   obligations;
    private static ArrayNode                   obligationWithConjunctionAnd;
    private static ArrayNode                   obligationWithConjunctionOr;
    private static JsonNode                    r2dbcQueryManipulation;
    private static JsonNode                    r2dbcQueryManipulationAnd;
    private static JsonNode                    r2dbcQueryManipulationOr;
    private static JsonNode                    conditions;
    private static JsonNode                    conditionsWithConjunctionAnd;
    private static JsonNode                    conditionsWithConjunctionOr;
    private static EmbeddedPolicyDecisionPoint pdp;
    private static String                      r2dbcQueryManipulationType = "r2dbcQueryManipulation";

    final Person    malinda        = new Person(1, "Malinda", "Perrot", 53, Role.ADMIN, true);
    final ArrayNode emptyArrayNode = objectMapper.createArrayNode();

    EmbeddedPolicyDecisionPoint pdpMock         = mock(EmbeddedPolicyDecisionPoint.class);
    BeanFactory                 beanFactoryMock = mock(BeanFactory.class, Answers.RETURNS_DEEP_STUBS);
    @SuppressWarnings("unchecked")
    Flux<Map<String, Object>>   fluxMap         = mock(Flux.class);

    MockedStatic<ConstraintHandlerUtils>            constraintHandlerUtilsMock;
    MockedStatic<PartTreeToSqlQueryStringConverter> partTreeToSqlQueryStringConverterMock;
    MockedStatic<QueryManipulationExecutor>         queryExecutorMock;

    @BeforeAll
    public static void setUp() throws JsonProcessingException, InitializationException {
        pdp                          = buildPdp();
        obligations                  = (ArrayNode) objectMapper.readTree(
                "[{\"type\":\"r2dbcQueryManipulation\",\"conditions\":\"role IN('USER')\"},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        obligationWithConjunctionAnd = (ArrayNode) objectMapper.readTree(
                "[{\"type\":\"r2dbcQueryManipulation\",\"conditions\":\"AND role IN('USER')\"},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        obligationWithConjunctionOr  = (ArrayNode) objectMapper.readTree(
                "[{\"type\":\"r2dbcQueryManipulation\",\"conditions\":\"OR role IN('USER')\"},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        r2dbcQueryManipulation       = objectMapper
                .readTree("{\"type\":\"r2dbcQueryManipulation\",\"conditions\":\"role IN('USER')\"}");
        conditions                   = r2dbcQueryManipulation.get("conditions");
        r2dbcQueryManipulationAnd    = objectMapper
                .readTree("{\"type\":\"r2dbcQueryManipulation\",\"conditions\":\"AND role IN('USER')\"}");
        conditionsWithConjunctionAnd = r2dbcQueryManipulationAnd.get("conditions");
        r2dbcQueryManipulationOr     = objectMapper
                .readTree("{\"type\":\"r2dbcQueryManipulation\",\"conditions\":\"OR role IN('USER')\"}");
        conditionsWithConjunctionOr  = r2dbcQueryManipulationOr.get("conditions");
    }

    @BeforeEach
    public void initBeforeEach() {
        constraintHandlerUtilsMock            = mockStatic(ConstraintHandlerUtils.class);
        partTreeToSqlQueryStringConverterMock = mockStatic(PartTreeToSqlQueryStringConverter.class);
        queryExecutorMock                     = mockStatic(QueryManipulationExecutor.class);
    }

    @AfterEach
    public void cleanUp() {
        constraintHandlerUtilsMock.close();
        partTreeToSqlQueryStringConverterMock.close();
        queryExecutorMock.close();
    }

    @AfterAll
    public static void disposePdp() {
        pdp.destroy();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void when_thereAreConditionsInTheDecision_then_enforce() {
        try (MockedConstruction<QueryManipulationObligationProvider> QueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(QueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<QueryManipulationExecutor> queryManipulationExecutorMockedConstruction = Mockito
                        .mockConstruction(QueryManipulationExecutor.class)) {

                    // GIVEN
                    var methodInvocationMock = new MethodInvocationForTesting("findAllByFirstname",
                            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                    var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var enforcementData      = new QueryManipulationEnforcementData<>(methodInvocationMock,
                            beanFactoryMock, Person.class, pdpMock, authSub);

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                            .thenReturn(emptyArrayNode);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(obligations);
                    partTreeToSqlQueryStringConverterMock
                            .when(() -> PartTreeToSqlQueryStringConverter.createSqlBaseQuery(enforcementData))
                            .thenReturn("firstname = 'Cathrin'");

                    var r2dbcMethodNameQueryManipulationEnforcementPoint = new R2dbcMethodNameQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    var QueryManipulationObligationProvider = QueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    when(QueryManipulationObligationProvider.isResponsible(obligations, r2dbcQueryManipulationType))
                            .thenReturn(Boolean.TRUE);
                    when(QueryManipulationObligationProvider.getObligation(obligations, r2dbcQueryManipulationType))
                            .thenReturn(r2dbcQueryManipulation);
                    when(QueryManipulationObligationProvider.getConditions(r2dbcQueryManipulation))
                            .thenReturn(conditions);

                    var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                    when(dataManipulationHandler.manipulate(obligations)).thenReturn(obligations -> Flux.just(malinda));
                    when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> Flux.just(malinda));

                    var queryManipulationExecutor = queryManipulationExecutorMockedConstruction.constructed().get(0);
                    when(queryManipulationExecutor.execute(anyString(), eq(Person.class))).thenReturn(fluxMap);

                    // THEN
                    var result = r2dbcMethodNameQueryManipulationEnforcementPoint.enforce();

                    StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                    verify(dataManipulationHandler, times(1)).manipulate(obligations);
                    verify(QueryManipulationObligationProvider, times(1)).isResponsible(obligations,
                            r2dbcQueryManipulationType);
                    verify(QueryManipulationObligationProvider, times(1)).getObligation(obligations,
                            r2dbcQueryManipulationType);
                    verify(QueryManipulationObligationProvider, times(1)).getConditions(r2dbcQueryManipulation);
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
    void when_thereAreConditionsInTheDecisionWithConjunctionAnd_then_enforce() {
        try (MockedConstruction<QueryManipulationObligationProvider> QueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(QueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<QueryManipulationExecutor> queryManipulationExecutorMockedConstruction = Mockito
                        .mockConstruction(QueryManipulationExecutor.class)) {

                    // GIVEN
                    var methodInvocationMock = new MethodInvocationForTesting("findAllByFirstname",
                            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                    var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var enforcementData      = new QueryManipulationEnforcementData<>(methodInvocationMock,
                            beanFactoryMock, Person.class, pdpMock, authSub);

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                            .thenReturn(emptyArrayNode);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(obligationWithConjunctionAnd);
                    partTreeToSqlQueryStringConverterMock
                            .when(() -> PartTreeToSqlQueryStringConverter.createSqlBaseQuery(enforcementData))
                            .thenReturn("firstname = 'Cathrin'");

                    var r2dbcMethodNameQueryManipulationEnforcementPoint = new R2dbcMethodNameQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    var QueryManipulationObligationProvider = QueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    when(QueryManipulationObligationProvider.isResponsible(obligationWithConjunctionAnd,
                            r2dbcQueryManipulationType)).thenReturn(Boolean.TRUE);
                    when(QueryManipulationObligationProvider.getObligation(obligationWithConjunctionAnd,
                            r2dbcQueryManipulationType)).thenReturn(r2dbcQueryManipulationAnd);
                    when(QueryManipulationObligationProvider.getConditions(r2dbcQueryManipulationAnd))
                            .thenReturn(conditionsWithConjunctionAnd);

                    var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                    when(dataManipulationHandler.manipulate(obligationWithConjunctionAnd))
                            .thenReturn(obligations -> Flux.just(malinda));
                    when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> Flux.just(malinda));

                    var queryManipulationExecutor = queryManipulationExecutorMockedConstruction.constructed().get(0);
                    when(queryManipulationExecutor.execute(anyString(), eq(Person.class))).thenReturn(fluxMap);

                    // THEN
                    var result = r2dbcMethodNameQueryManipulationEnforcementPoint.enforce();

                    StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                    verify(dataManipulationHandler, times(1)).manipulate(obligationWithConjunctionAnd);
                    verify(QueryManipulationObligationProvider, times(1)).isResponsible(obligationWithConjunctionAnd,
                            r2dbcQueryManipulationType);
                    verify(QueryManipulationObligationProvider, times(1)).getObligation(obligationWithConjunctionAnd,
                            r2dbcQueryManipulationType);
                    verify(QueryManipulationObligationProvider, times(1)).getConditions(r2dbcQueryManipulationAnd);
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
    void when_thereAreConditionsInTheDecisionWithConjunctionOr_then_enforce() {
        try (MockedConstruction<QueryManipulationObligationProvider> QueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(QueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<QueryManipulationExecutor> queryManipulationExecutorMockedConstruction = Mockito
                        .mockConstruction(QueryManipulationExecutor.class)) {

                    // GIVEN
                    var methodInvocationMock = new MethodInvocationForTesting("findAllByFirstname",
                            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                    var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var enforcementData      = new QueryManipulationEnforcementData<>(methodInvocationMock,
                            beanFactoryMock, Person.class, pdpMock, authSub);

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                            .thenReturn(emptyArrayNode);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(obligationWithConjunctionOr);
                    partTreeToSqlQueryStringConverterMock
                            .when(() -> PartTreeToSqlQueryStringConverter.createSqlBaseQuery(enforcementData))
                            .thenReturn("firstname = 'Cathrin'");

                    var r2dbcMethodNameQueryManipulationEnforcementPoint = new R2dbcMethodNameQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    var QueryManipulationObligationProvider = QueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    when(QueryManipulationObligationProvider.isResponsible(obligationWithConjunctionOr,
                            r2dbcQueryManipulationType)).thenReturn(Boolean.TRUE);
                    when(QueryManipulationObligationProvider.getObligation(obligationWithConjunctionOr,
                            r2dbcQueryManipulationType)).thenReturn(r2dbcQueryManipulationOr);
                    when(QueryManipulationObligationProvider.getConditions(r2dbcQueryManipulationOr))
                            .thenReturn(conditionsWithConjunctionOr);

                    var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                    when(dataManipulationHandler.manipulate(obligationWithConjunctionOr))
                            .thenReturn(obligations -> Flux.just(malinda));
                    when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> Flux.just(malinda));

                    var queryManipulationExecutor = queryManipulationExecutorMockedConstruction.constructed().get(0);
                    when(queryManipulationExecutor.execute(anyString(), eq(Person.class))).thenReturn(fluxMap);

                    // THEN
                    var result = r2dbcMethodNameQueryManipulationEnforcementPoint.enforce();

                    StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                    verify(dataManipulationHandler, times(1)).manipulate(obligationWithConjunctionOr);
                    verify(QueryManipulationObligationProvider, times(1)).isResponsible(obligationWithConjunctionOr,
                            r2dbcQueryManipulationType);
                    verify(QueryManipulationObligationProvider, times(1)).getObligation(obligationWithConjunctionOr,
                            r2dbcQueryManipulationType);
                    verify(QueryManipulationObligationProvider, times(1)).getConditions(r2dbcQueryManipulationOr);
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
        try (MockedConstruction<QueryManipulationObligationProvider> QueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(QueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<QueryManipulationExecutor> queryManipulationExecutorMockedConstruction = Mockito
                        .mockConstruction(QueryManipulationExecutor.class)) {

                    // GIVEN
                    var methodInvocationMock = new MethodInvocationForTesting("findAllByFirstname",
                            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                    var authSub              = AuthorizationSubscription.of("subject", "denyTest", "resource",
                            "environment");
                    var enforcementData      = new QueryManipulationEnforcementData<>(methodInvocationMock,
                            beanFactoryMock, Person.class, pdpMock, authSub);
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.DENY)));

                    // WHEN
                    var r2dbcMethodNameQueryManipulationEnforcementPoint = new R2dbcMethodNameQueryManipulationEnforcementPoint<>(
                            enforcementData);
                    var accessDeniedException                            = r2dbcMethodNameQueryManipulationEnforcementPoint
                            .enforce();

                    // THEN
                    StepVerifier.create(accessDeniedException).expectError(AccessDeniedException.class).verify();

                    assertNotNull(dataManipulationHandlerMockedConstruction.constructed().get(0));
                    assertNotNull(QueryManipulationObligationProviderMockedConstruction.constructed().get(0));
                    constraintHandlerUtilsMock
                            .verify(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(0));
                }
            }
        }
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void when_r2dbcQueryManipulationObligationIsResponsibleIsFalse_then_proceedWithoutQueryManipulation() {
        try (MockedConstruction<QueryManipulationObligationProvider> QueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(QueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<QueryManipulationExecutor> queryManipulationExecutorMockedConstruction = Mockito
                        .mockConstruction(QueryManipulationExecutor.class)) {

                    // GIVEN
                    var methodInvocationMock = new MethodInvocationForTesting("findAllByFirstname",
                            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")),
                            Flux.just(malinda));
                    var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var enforcementData      = new QueryManipulationEnforcementData<>(methodInvocationMock,
                            beanFactoryMock, Person.class, pdpMock, authSub);

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(obligations);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                            .thenReturn(emptyArrayNode);

                    var r2dbcMethodNameQueryManipulationEnforcementPoint = new R2dbcMethodNameQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    var QueryManipulationObligationProviderMock = QueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    when(QueryManipulationObligationProviderMock.isResponsible(obligations, r2dbcQueryManipulationType))
                            .thenReturn(Boolean.FALSE);

                    var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                    when(dataManipulationHandler.manipulate(obligations)).thenReturn(obligations -> Flux.just(malinda));
                    when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> Flux.just(malinda));

                    // THEN
                    var result = r2dbcMethodNameQueryManipulationEnforcementPoint.enforce();

                    StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                    verify(QueryManipulationObligationProviderMock, times(1)).isResponsible(obligations,
                            r2dbcQueryManipulationType);
                    verify(QueryManipulationObligationProviderMock, never()).getObligation(obligations,
                            r2dbcQueryManipulationType);
                    verify(QueryManipulationObligationProviderMock, never()).getConditions(r2dbcQueryManipulation);
                    constraintHandlerUtilsMock
                            .verify(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                    partTreeToSqlQueryStringConverterMock.verify(() -> PartTreeToSqlQueryStringConverter
                            .createSqlBaseQuery(any(QueryManipulationEnforcementData.class)), never());
                }
            }
        }
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void when_r2dbcQueryManipulationObligationIsResponsibleIsFalseAndReturnTypeIsMono_then_proceedWithoutQueryManipulation() {
        try (MockedConstruction<QueryManipulationObligationProvider> QueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(QueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<QueryManipulationExecutor> queryManipulationExecutorMockedConstruction = Mockito
                        .mockConstruction(QueryManipulationExecutor.class)) {

                    // GIVEN
                    var methodInvocationMock = new MethodInvocationForTesting("findByAge",
                            new ArrayList<>(List.of(int.class)), new ArrayList<>(List.of("Cathrin")),
                            Mono.just(malinda));
                    var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var enforcementData      = new QueryManipulationEnforcementData<>(methodInvocationMock,
                            beanFactoryMock, Person.class, pdpMock, authSub);

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(obligations);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                            .thenReturn(emptyArrayNode);

                    var r2dbcMethodNameQueryManipulationEnforcementPoint = new R2dbcMethodNameQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    var QueryManipulationObligationProviderMock = QueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    when(QueryManipulationObligationProviderMock.isResponsible(obligations, r2dbcQueryManipulationType))
                            .thenReturn(Boolean.FALSE);

                    var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                    when(dataManipulationHandler.manipulate(obligations)).thenReturn(obligations -> Flux.just(malinda));
                    when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> Flux.just(malinda));

                    // THEN
                    var result = r2dbcMethodNameQueryManipulationEnforcementPoint.enforce();

                    StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                    verify(QueryManipulationObligationProviderMock, times(1)).isResponsible(obligations,
                            r2dbcQueryManipulationType);
                    verify(QueryManipulationObligationProviderMock, never()).getObligation(obligations,
                            r2dbcQueryManipulationType);
                    verify(QueryManipulationObligationProviderMock, never()).getConditions(r2dbcQueryManipulation);
                    constraintHandlerUtilsMock
                            .verify(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                    partTreeToSqlQueryStringConverterMock.verify(() -> PartTreeToSqlQueryStringConverter
                            .createSqlBaseQuery(any(QueryManipulationEnforcementData.class)), never());
                }
            }
        }
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void when_r2dbcQueryManipulationObligationIsResponsibleIsFalseAndReturnTypeIsMono_then_throwThrowable() {
        try (MockedConstruction<QueryManipulationObligationProvider> QueryManipulationObligationProviders = Mockito
                .mockConstruction(QueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<QueryManipulationExecutor> queryManipulationExecutorMockedConstruction = Mockito
                        .mockConstruction(QueryManipulationExecutor.class)) {

                    // GIVEN
                    var methodInvocationMock = new MethodInvocationForTesting("findByAge",
                            new ArrayList<>(List.of(int.class)), new ArrayList<>(List.of("Cathrin")), new Throwable());
                    var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var enforcementData      = new QueryManipulationEnforcementData<>(methodInvocationMock,
                            beanFactoryMock, Person.class, pdpMock, authSub);

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(obligations);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                            .thenReturn(emptyArrayNode);

                    var r2dbcMethodNameQueryManipulationEnforcementPoint = new R2dbcMethodNameQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    var QueryManipulationObligationProviderMock = QueryManipulationObligationProviders.constructed()
                            .get(0);
                    when(QueryManipulationObligationProviderMock.isResponsible(obligations, r2dbcQueryManipulationType))
                            .thenReturn(Boolean.FALSE);

                    var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                    when(dataManipulationHandler.manipulate(obligations)).thenReturn(obligations -> Flux.just(malinda));
                    when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> Flux.just(malinda));

                    var throwableException = r2dbcMethodNameQueryManipulationEnforcementPoint.enforce();

                    // THEN
                    StepVerifier.create(throwableException).expectError(Throwable.class).verify();
                    verify(QueryManipulationObligationProviderMock, times(1)).isResponsible(obligations,
                            r2dbcQueryManipulationType);
                    verify(QueryManipulationObligationProviderMock, never()).getObligation(obligations,
                            r2dbcQueryManipulationType);
                    verify(QueryManipulationObligationProviderMock, never()).getConditions(r2dbcQueryManipulation);
                    constraintHandlerUtilsMock
                            .verify(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                    partTreeToSqlQueryStringConverterMock.verify(() -> PartTreeToSqlQueryStringConverter
                            .createSqlBaseQuery(any(QueryManipulationEnforcementData.class)), never());
                }
            }
        }
    }

    private static EmbeddedPolicyDecisionPoint buildPdp() throws InitializationException {
        return PolicyDecisionPointFactory.filesystemPolicyDecisionPoint("src/test/resources/policies");
    }
}
