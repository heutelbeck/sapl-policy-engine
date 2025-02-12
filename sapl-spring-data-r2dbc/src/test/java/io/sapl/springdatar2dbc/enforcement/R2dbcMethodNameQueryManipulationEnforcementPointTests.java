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
package io.sapl.springdatar2dbc.enforcement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointFactory;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.ReactiveConstraintHandlerBundle;
import io.sapl.springdatacommon.services.ConstraintQueryEnforcementService;
import io.sapl.springdatacommon.services.QueryManipulationConstraintHandlerService;
import io.sapl.springdatar2dbc.database.Person;
import io.sapl.springdatar2dbc.queries.PartTreeToSqlQueryStringConverter;
import io.sapl.springdatar2dbc.queries.QueryCreation;
import io.sapl.springdatar2dbc.queries.QueryManipulationExecutor;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class R2dbcMethodNameQueryManipulationEnforcementPointTests {

    private static final String       ACCESS_DENIED_BY_PDP = "Access Denied by PDP";
    private static final ObjectMapper MAPPER               = new ObjectMapper();
    private static final ArrayNode    EMPTY_ARRAY_NODE     = MAPPER.createArrayNode();
    private static final Person       CATHRIN              = new Person(1, "Cathrin", 33, true);
    private static final String       BASE_QUERY           = "SELECT * FROM PERSON WHERE age < 90";

    @Mock
    ObjectProvider<PolicyDecisionPoint> objectProviderPolicyDecisionPointMock;

    @Mock
    ObjectProvider<QueryManipulationExecutor> objectProviderQueryManipulationExecutorMock;

    @Mock
    ObjectProvider<ConstraintQueryEnforcementService> objectProviderConstraintQueryEnforcementServiceMock;

    @Mock
    ReactiveConstraintHandlerBundle<Person> reactiveConstraintHandlerBundleMock;

    QueryManipulationExecutor                       queryManipulationExecutorMock                = mock(
            QueryManipulationExecutor.class);
    ConstraintQueryEnforcementService               constraintQueryEnforcementServiceMock        = mock(
            ConstraintQueryEnforcementService.class);
    QueryManipulationConstraintHandlerService       queryManipulationConstraintHandlerBundleMock = mock(
            QueryManipulationConstraintHandlerService.class);
    MethodInvocation                                methodInvocationMock                         = mock(
            MethodInvocation.class, RETURNS_DEEP_STUBS);
    ConstraintEnforcementService                    constraintEnforcementServiceMock             = mock(
            ConstraintEnforcementService.class);
    MockedStatic<QueryCreation>                     queryCreationMock;
    MockedStatic<PartTreeToSqlQueryStringConverter> partTreeToSqlQueryStringConverterMock;
    PolicyDecisionPoint                             pdp;

    @BeforeEach
    public void beforeEach() throws InitializationException {
        pdp = buildPdp();
        lenient().when(objectProviderPolicyDecisionPointMock.getObject()).thenReturn(pdp);
        lenient().when(objectProviderQueryManipulationExecutorMock.getObject())
                .thenReturn(queryManipulationExecutorMock);
        lenient().when(objectProviderConstraintQueryEnforcementServiceMock.getObject())
                .thenReturn(constraintQueryEnforcementServiceMock);
        lenient().when(queryManipulationExecutorMock.execute(anyString(), eq(Person.class)))
                .thenReturn(Flux.just(CATHRIN));

        when(queryManipulationConstraintHandlerBundleMock.getQueryManipulationObligations())
                .thenReturn(new JsonNode[0]);
        when(queryManipulationConstraintHandlerBundleMock.getConditions()).thenReturn(EMPTY_ARRAY_NODE);
        when(queryManipulationConstraintHandlerBundleMock.getSelections()).thenReturn(EMPTY_ARRAY_NODE);
        when(queryManipulationConstraintHandlerBundleMock.getTransformations()).thenReturn(EMPTY_ARRAY_NODE);

        queryCreationMock                     = mockStatic(QueryCreation.class);
        partTreeToSqlQueryStringConverterMock = mockStatic(PartTreeToSqlQueryStringConverter.class);

        queryCreationMock.when(() -> QueryCreation.createSqlQuery(any(ArrayNode.class), any(ArrayNode.class),
                any(ArrayNode.class), eq(Person.class), anyString())).thenReturn(BASE_QUERY);
        partTreeToSqlQueryStringConverterMock.when(() -> PartTreeToSqlQueryStringConverter
                .createSqlBaseQuery(any(MethodInvocation.class), eq(Person.class))).thenReturn(BASE_QUERY);
    }

    @AfterEach
    public void cleanUp() {
        queryCreationMock.close();
        partTreeToSqlQueryStringConverterMock.close();
    }

    @Test
    void when_enforce_then_returnFluxDomainObject() {
        // GIVEN
        final var authorizationSubscriptionMock = AuthorizationSubscription.of("", "permitTest", "", "");
        final var enforcementPoint              = new R2dbcMethodNameQueryManipulationEnforcementPoint<Person>(
                objectProviderPolicyDecisionPointMock, objectProviderQueryManipulationExecutorMock,
                objectProviderConstraintQueryEnforcementServiceMock, constraintEnforcementServiceMock);

        // WHEN
        when(constraintQueryEnforcementServiceMock.queryManipulationForR2dbc(any(AuthorizationDecision.class)))
                .thenReturn(queryManipulationConstraintHandlerBundleMock);
        when(constraintEnforcementServiceMock.reactiveTypeBundleFor(any(AuthorizationDecision.class), eq(Person.class),
                any(JsonNode[].class))).thenReturn(reactiveConstraintHandlerBundleMock);
        doNothing().when(reactiveConstraintHandlerBundleMock)
                .handleMethodInvocationHandlers(any(MethodInvocation.class));
        when(constraintEnforcementServiceMock.replaceIfResourcePresent(any(), any(), eq(Person.class)))
                .thenReturn(Flux.just(CATHRIN));

        final var result = enforcementPoint.enforce(authorizationSubscriptionMock, Person.class, methodInvocationMock);

        // THEN
        StepVerifier.create(result).expectNext(CATHRIN).expectComplete().verify();

        partTreeToSqlQueryStringConverterMock.verify(() -> PartTreeToSqlQueryStringConverter
                .createSqlBaseQuery(any(MethodInvocation.class), eq(Person.class)), times(1));
        queryCreationMock.verify(() -> QueryCreation.createSqlQuery(any(ArrayNode.class), any(ArrayNode.class),
                any(ArrayNode.class), eq(Person.class), anyString()), times(1));
        verify(constraintQueryEnforcementServiceMock, times(1))
                .queryManipulationForR2dbc(any(AuthorizationDecision.class));
        verify(queryManipulationConstraintHandlerBundleMock, times(1)).getConditions();
        verify(queryManipulationConstraintHandlerBundleMock, times(1)).getSelections();
        verify(queryManipulationExecutorMock, times(1)).execute(anyString(), eq(Person.class));
    }

    @Test
    void when_enforce_then_throwAccessDeniedException() {
        // GIVEN
        final var authorizationSubscriptionMock = AuthorizationSubscription.of("", "denyTest", "", "");
        final var enforcementPoint              = new R2dbcMethodNameQueryManipulationEnforcementPoint<Person>(
                objectProviderPolicyDecisionPointMock, objectProviderQueryManipulationExecutorMock,
                objectProviderConstraintQueryEnforcementServiceMock, constraintEnforcementServiceMock);

        // WHEN
        final var result = enforcementPoint.enforce(authorizationSubscriptionMock, Person.class, methodInvocationMock);
        StepVerifier.create(result).expectErrorMatches(
                error -> error instanceof AccessDeniedException && ACCESS_DENIED_BY_PDP.equals(error.getMessage()))
                .verify();

        // THEN
        partTreeToSqlQueryStringConverterMock.verify(() -> PartTreeToSqlQueryStringConverter
                .createSqlBaseQuery(any(MethodInvocation.class), eq(Person.class)), times(1));
        verify(constraintQueryEnforcementServiceMock, times(0))
                .queryManipulationForR2dbc(any(AuthorizationDecision.class));
        verify(queryManipulationConstraintHandlerBundleMock, times(0)).getConditions();
        verify(queryManipulationConstraintHandlerBundleMock, times(0)).getSelections();
        queryCreationMock.verify(() -> QueryCreation.createSqlQuery(any(ArrayNode.class), any(ArrayNode.class),
                any(ArrayNode.class), eq(Person.class), anyString()), times(0));
        verify(queryManipulationExecutorMock, times(0)).execute(anyString(), eq(Person.class));
    }

    private static EmbeddedPolicyDecisionPoint buildPdp() throws InitializationException {
        return PolicyDecisionPointFactory.filesystemPolicyDecisionPoint("src/test/resources/policies");
    }

}
