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
package io.sapl.springdatar2dbc.sapl.queryTypes.methodNameEnforcement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.springdatar2dbc.database.MethodInvocationForTesting;
import io.sapl.springdatar2dbc.database.Person;
import io.sapl.springdatar2dbc.database.Role;
import io.sapl.springdatar2dbc.sapl.utils.ConstraintHandlerUtils;
import io.sapl.springdatar2dbc.sapl.QueryManipulationExecutor;
import io.sapl.springdatar2dbc.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatar2dbc.sapl.handlers.DataManipulationHandler;
import io.sapl.springdatar2dbc.sapl.handlers.LoggingConstraintHandlerProvider;
import io.sapl.springdatar2dbc.sapl.handlers.R2dbcQueryManipulationObligationProvider;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class R2dbcMethodNameQueryManipulationEnforcementPointTest {

    final static ObjectMapper          objectMapper = new ObjectMapper();
    static JsonNode                    obligations;
    static JsonNode                    obligationsWithConjunctionAnd;
    static JsonNode                    obligationsWithConjunctionOr;
    static JsonNode                    r2dbcQueryManipulation;
    static JsonNode                    r2dbcQueryManipulationWithConjunctionAnd;
    static JsonNode                    r2dbcQueryManipulationWithConjunctionOr;
    static JsonNode                    conditions;
    static JsonNode                    conditionsWithConjunctionAnd;
    static JsonNode                    conditionsWithConjunctionOr;
    static EmbeddedPolicyDecisionPoint pdp;

    final Person malinda = new Person(1, "Malinda", "Perrot", 53, Role.ADMIN, true);

    @Mock
    EmbeddedPolicyDecisionPoint pdpMock;

    @MockBean
    LoggingConstraintHandlerProvider loggingConstraintHandlerProviderMock;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    BeanFactory beanFactoryMock;

    @Mock
    Flux<Map<String, Object>> fluxMap;

    MockedStatic<ConstraintHandlerUtils>            constraintHandlerUtilsMock;
    MockedStatic<PartTreeToSqlQueryStringConverter> partTreeToSqlQueryStringConverterMock;
    MockedStatic<QueryManipulationExecutor>         queryExecutorMock;

    @BeforeAll
    public static void setUp() throws JsonProcessingException, InitializationException {
        pdp                                      = buildPdp();
        obligations                              = objectMapper.readTree(
                "[{\"type\":\"r2dbcQueryManipulation\",\"conditions\":\"role IN('USER')\"},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        obligationsWithConjunctionAnd            = objectMapper.readTree(
                "[{\"type\":\"r2dbcQueryManipulation\",\"conditions\":\"AND role IN('USER')\"},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        obligationsWithConjunctionOr             = objectMapper.readTree(
                "[{\"type\":\"r2dbcQueryManipulation\",\"conditions\":\"OR role IN('USER')\"},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        r2dbcQueryManipulation                   = objectMapper
                .readTree("{\"type\":\"r2dbcQueryManipulation\",\"conditions\":\"role IN('USER')\"}");
        conditions                               = r2dbcQueryManipulation.get("conditions");
        r2dbcQueryManipulationWithConjunctionAnd = objectMapper
                .readTree("{\"type\":\"r2dbcQueryManipulation\",\"conditions\":\"AND role IN('USER')\"}");
        conditionsWithConjunctionAnd             = r2dbcQueryManipulationWithConjunctionAnd.get("conditions");
        r2dbcQueryManipulationWithConjunctionOr  = objectMapper
                .readTree("{\"type\":\"r2dbcQueryManipulation\",\"conditions\":\"OR role IN('USER')\"}");
        conditionsWithConjunctionOr              = r2dbcQueryManipulationWithConjunctionOr.get("conditions");
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
    void when_thereAreConditionsInTheDecision_then_enforce(CapturedOutput output) {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {

                // GIVEN
                var methodInvocationMock  = new MethodInvocationForTesting("findAllByFirstname",
                        new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                var expectedLoggerMessage = "Manipulated condition: [firstname = 'Cathrin' AND role IN('USER')]";
                var authSub               = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData       = new QueryManipulationEnforcementData<>(methodInvocationMock,
                        beanFactoryMock, Person.class, pdpMock, authSub);

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligations);
                queryExecutorMock.when(
                        () -> QueryManipulationExecutor.execute(anyString(), any(BeanFactory.class), eq(Person.class)))
                        .thenReturn(fluxMap);
                partTreeToSqlQueryStringConverterMock
                        .when(() -> PartTreeToSqlQueryStringConverter.createSqlBaseQuery(eq(enforcementData)))
                        .thenReturn("firstname = 'Cathrin'");

                var r2dbcMethodNameQueryManipulationEnforcementPoint = new R2dbcMethodNameQueryManipulationEnforcementPoint<>(
                        enforcementData);

                var r2dbcQueryManipulationObligationProvider = r2dbcQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(r2dbcQueryManipulationObligationProvider.isResponsible(eq(obligations))).thenReturn(Boolean.TRUE);
                when(r2dbcQueryManipulationObligationProvider.getObligation(eq(obligations)))
                        .thenReturn(r2dbcQueryManipulation);
                when(r2dbcQueryManipulationObligationProvider.getCondition(eq(r2dbcQueryManipulation)))
                        .thenReturn(conditions);

                var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                when(dataManipulationHandler.manipulate(eq(obligations))).thenReturn(obligations -> Flux.just(malinda));
                when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> Flux.just(malinda));

                // THEN
                var result = r2dbcMethodNameQueryManipulationEnforcementPoint.enforce();

                StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                Assertions.assertTrue(output.getOut().contains(expectedLoggerMessage));
                verify(dataManipulationHandler, times(1)).manipulate(eq(obligations));
                verify(r2dbcQueryManipulationObligationProvider, times(1)).isResponsible(eq(obligations));
                verify(r2dbcQueryManipulationObligationProvider, times(1)).getObligation(eq(obligations));
                verify(r2dbcQueryManipulationObligationProvider, times(1)).getCondition(eq(r2dbcQueryManipulation));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
            }
        }
    }

    @Test
    void when_thereAreConditionsInTheDecisionWithConjunctionAnd_then_enforce(CapturedOutput output) {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {

                // GIVEN
                var methodInvocationMock  = new MethodInvocationForTesting("findAllByFirstname",
                        new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                var expectedLoggerMessage = "Manipulated condition: [firstname = 'Cathrin' AND role IN('USER')]";
                var authSub               = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData       = new QueryManipulationEnforcementData<>(methodInvocationMock,
                        beanFactoryMock, Person.class, pdpMock, authSub);

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligationsWithConjunctionAnd);
                queryExecutorMock.when(
                        () -> QueryManipulationExecutor.execute(anyString(), any(BeanFactory.class), eq(Person.class)))
                        .thenReturn(fluxMap);
                partTreeToSqlQueryStringConverterMock
                        .when(() -> PartTreeToSqlQueryStringConverter.createSqlBaseQuery(eq(enforcementData)))
                        .thenReturn("firstname = 'Cathrin'");

                var r2dbcMethodNameQueryManipulationEnforcementPoint = new R2dbcMethodNameQueryManipulationEnforcementPoint<>(
                        enforcementData);

                var r2dbcQueryManipulationObligationProvider = r2dbcQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(r2dbcQueryManipulationObligationProvider.isResponsible(eq(obligationsWithConjunctionAnd)))
                        .thenReturn(Boolean.TRUE);
                when(r2dbcQueryManipulationObligationProvider.getObligation(eq(obligationsWithConjunctionAnd)))
                        .thenReturn(r2dbcQueryManipulationWithConjunctionAnd);
                when(r2dbcQueryManipulationObligationProvider
                        .getCondition(eq(r2dbcQueryManipulationWithConjunctionAnd)))
                        .thenReturn(conditionsWithConjunctionAnd);

                var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                when(dataManipulationHandler.manipulate(eq(obligationsWithConjunctionAnd)))
                        .thenReturn(obligations -> Flux.just(malinda));
                when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> Flux.just(malinda));

                // THEN
                var result = r2dbcMethodNameQueryManipulationEnforcementPoint.enforce();

                StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                Assertions.assertTrue(output.getOut().contains(expectedLoggerMessage));
                verify(dataManipulationHandler, times(1)).manipulate(eq(obligationsWithConjunctionAnd));
                verify(r2dbcQueryManipulationObligationProvider, times(1))
                        .isResponsible(eq(obligationsWithConjunctionAnd));
                verify(r2dbcQueryManipulationObligationProvider, times(1))
                        .getObligation(eq(obligationsWithConjunctionAnd));
                verify(r2dbcQueryManipulationObligationProvider, times(1))
                        .getCondition(eq(r2dbcQueryManipulationWithConjunctionAnd));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
            }
        }
    }

    @Test
    void when_thereAreConditionsInTheDecisionWithConjunctionOr_then_enforce(CapturedOutput output) {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {

                // GIVEN
                var methodInvocationMock  = new MethodInvocationForTesting("findAllByFirstname",
                        new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                var expectedLoggerMessage = "Manipulated condition: [firstname = 'Cathrin' OR role IN('USER')]";
                var authSub               = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData       = new QueryManipulationEnforcementData<>(methodInvocationMock,
                        beanFactoryMock, Person.class, pdpMock, authSub);

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligationsWithConjunctionOr);
                queryExecutorMock.when(
                        () -> QueryManipulationExecutor.execute(anyString(), any(BeanFactory.class), eq(Person.class)))
                        .thenReturn(fluxMap);
                partTreeToSqlQueryStringConverterMock
                        .when(() -> PartTreeToSqlQueryStringConverter.createSqlBaseQuery(eq(enforcementData)))
                        .thenReturn("firstname = 'Cathrin'");

                var r2dbcMethodNameQueryManipulationEnforcementPoint = new R2dbcMethodNameQueryManipulationEnforcementPoint<>(
                        enforcementData);

                var r2dbcQueryManipulationObligationProvider = r2dbcQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(r2dbcQueryManipulationObligationProvider.isResponsible(eq(obligationsWithConjunctionOr)))
                        .thenReturn(Boolean.TRUE);
                when(r2dbcQueryManipulationObligationProvider.getObligation(eq(obligationsWithConjunctionOr)))
                        .thenReturn(r2dbcQueryManipulationWithConjunctionOr);
                when(r2dbcQueryManipulationObligationProvider.getCondition(eq(r2dbcQueryManipulationWithConjunctionOr)))
                        .thenReturn(conditionsWithConjunctionOr);

                var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                when(dataManipulationHandler.manipulate(eq(obligationsWithConjunctionOr)))
                        .thenReturn(obligations -> Flux.just(malinda));
                when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> Flux.just(malinda));

                // THEN
                var result = r2dbcMethodNameQueryManipulationEnforcementPoint.enforce();

                StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                Assertions.assertTrue(output.getOut().contains(expectedLoggerMessage));
                verify(dataManipulationHandler, times(1)).manipulate(eq(obligationsWithConjunctionOr));
                verify(r2dbcQueryManipulationObligationProvider, times(1))
                        .isResponsible(eq(obligationsWithConjunctionOr));
                verify(r2dbcQueryManipulationObligationProvider, times(1))
                        .getObligation(eq(obligationsWithConjunctionOr));
                verify(r2dbcQueryManipulationObligationProvider, times(1))
                        .getCondition(eq(r2dbcQueryManipulationWithConjunctionOr));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
            }
        }
    }

    @Test
    void when_decisionIsNotPermit_then_throwAccessDeniedException() {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {

                // GIVEN
                var methodInvocationMock = new MethodInvocationForTesting("findAllByFirstname",
                        new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                var authSub              = AuthorizationSubscription.of("subject", "denyTest", "resource",
                        "environment");
                var enforcementData      = new QueryManipulationEnforcementData<>(methodInvocationMock, beanFactoryMock,
                        Person.class, pdpMock, authSub);
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.DENY)));

                // WHEN
                var r2dbcMethodNameQueryManipulationEnforcementPoint = new R2dbcMethodNameQueryManipulationEnforcementPoint<>(
                        enforcementData);
                var accessDeniedException                            = r2dbcMethodNameQueryManipulationEnforcementPoint
                        .enforce();

                // THEN
                StepVerifier.create(accessDeniedException).expectError(AccessDeniedException.class).verify();

                Assertions.assertNotNull(dataManipulationHandlerMockedConstruction.constructed().get(0));
                Assertions
                        .assertNotNull(r2dbcQueryManipulationObligationProviderMockedConstruction.constructed().get(0));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(0));
            }
        }
    }

    @Test
    void when_r2dbcQueryManipulationObligationIsResponsibleIsFalse_then_proceedWithoutQueryManipulation() {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {

                // GIVEN
                var methodInvocationMock = new MethodInvocationForTesting("findAllByFirstname",
                        new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")),
                        Flux.just(malinda));
                var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData      = new QueryManipulationEnforcementData<>(methodInvocationMock, beanFactoryMock,
                        Person.class, pdpMock, authSub);

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligations);
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());

                var r2dbcMethodNameQueryManipulationEnforcementPoint = new R2dbcMethodNameQueryManipulationEnforcementPoint<>(
                        enforcementData);

                var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(r2dbcQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                        .thenReturn(Boolean.FALSE);

                var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                when(dataManipulationHandler.manipulate(eq(obligations))).thenReturn(obligations -> Flux.just(malinda));
                when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> Flux.just(malinda));

                // THEN
                var result = r2dbcMethodNameQueryManipulationEnforcementPoint.enforce();

                StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, never()).getObligation(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, never()).getCondition(eq(r2dbcQueryManipulation));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                partTreeToSqlQueryStringConverterMock.verify(() -> PartTreeToSqlQueryStringConverter
                        .createSqlBaseQuery(any(QueryManipulationEnforcementData.class)), never());
            }
        }
    }

    @Test
    void when_r2dbcQueryManipulationObligationIsResponsibleIsFalseAndReturnTypeIsMono_then_proceedWithoutQueryManipulation() {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {

                // GIVEN
                var methodInvocationMock = new MethodInvocationForTesting("findByAge",
                        new ArrayList<>(List.of(int.class)), new ArrayList<>(List.of("Cathrin")), Mono.just(malinda));
                var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData      = new QueryManipulationEnforcementData<>(methodInvocationMock, beanFactoryMock,
                        Person.class, pdpMock, authSub);

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligations);
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());

                var r2dbcMethodNameQueryManipulationEnforcementPoint = new R2dbcMethodNameQueryManipulationEnforcementPoint<>(
                        enforcementData);

                var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(r2dbcQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                        .thenReturn(Boolean.FALSE);

                var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                when(dataManipulationHandler.manipulate(eq(obligations))).thenReturn(obligations -> Flux.just(malinda));
                when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> Flux.just(malinda));

                // THEN
                var result = r2dbcMethodNameQueryManipulationEnforcementPoint.enforce();

                StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, never()).getObligation(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, never()).getCondition(eq(r2dbcQueryManipulation));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                partTreeToSqlQueryStringConverterMock.verify(() -> PartTreeToSqlQueryStringConverter
                        .createSqlBaseQuery(any(QueryManipulationEnforcementData.class)), never());
            }
        }
    }

    @Test
    void when_r2dbcQueryManipulationObligationIsResponsibleIsFalseAndReturnTypeIsMono_then_throwThrowable() {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviders = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {

                // GIVEN
                var methodInvocationMock = new MethodInvocationForTesting("findByAge",
                        new ArrayList<>(List.of(int.class)), new ArrayList<>(List.of("Cathrin")), new Throwable());
                var authSub              = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData      = new QueryManipulationEnforcementData<>(methodInvocationMock, beanFactoryMock,
                        Person.class, pdpMock, authSub);

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligations);
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());

                var r2dbcMethodNameQueryManipulationEnforcementPoint = new R2dbcMethodNameQueryManipulationEnforcementPoint<>(
                        enforcementData);

                var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviders
                        .constructed().get(0);
                when(r2dbcQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                        .thenReturn(Boolean.FALSE);

                var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                when(dataManipulationHandler.manipulate(eq(obligations))).thenReturn(obligations -> Flux.just(malinda));
                when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> Flux.just(malinda));

                var throwableException = r2dbcMethodNameQueryManipulationEnforcementPoint.enforce();

                // THEN
                StepVerifier.create(throwableException).expectError(Throwable.class).verify();

                verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, never()).getObligation(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, never()).getCondition(eq(r2dbcQueryManipulation));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                partTreeToSqlQueryStringConverterMock.verify(() -> PartTreeToSqlQueryStringConverter
                        .createSqlBaseQuery(any(QueryManipulationEnforcementData.class)), never());
            }
        }
    }

    private static EmbeddedPolicyDecisionPoint buildPdp() throws InitializationException {
        return PolicyDecisionPointFactory.filesystemPolicyDecisionPoint("src/test/resources/policies");
    }
}
