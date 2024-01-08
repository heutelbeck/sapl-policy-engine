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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.springdatacommon.handlers.DataManipulationHandler;
import io.sapl.springdatacommon.handlers.LoggingConstraintHandlerProvider;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatacommon.sapl.queries.enforcement.ProceededDataFilterEnforcementPoint;
import io.sapl.springdatacommon.sapl.utils.ConstraintHandlerUtils;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ProceededDataFilterEnforcementPointTest {

    final TestUser aaron   = new TestUser(new ObjectId(), "Aaron", 20);
    final TestUser brian   = new TestUser(new ObjectId(), "Brian", 21);
    final TestUser cathrin = new TestUser(new ObjectId(), "Cathrin", 33);

    final Flux<TestUser> data = Flux.just(aaron, brian, cathrin);

    LoggingConstraintHandlerProvider loggingConstraintHandlerProviderMock;
    EmbeddedPolicyDecisionPoint      pdpMock;

    @BeforeEach
    void beforeEach() {
        constraintHandlerUtilsMock           = mockStatic(ConstraintHandlerUtils.class);
        loggingConstraintHandlerProviderMock = mock(LoggingConstraintHandlerProvider.class);
        pdpMock                              = mock(EmbeddedPolicyDecisionPoint.class);
    }

    @AfterEach
    public void cleanUp() {
        constraintHandlerUtilsMock.close();
    }

    MockedStatic<ConstraintHandlerUtils> constraintHandlerUtilsMock;
    final MethodInvocationForTesting     mongoMethodInvocationTest = new MethodInvocationForTesting(
            "findAllByFirstname", new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);

    @Test
    void when_actionWasFoundInPolicies_then_enforce() throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        try (@SuppressWarnings("rawtypes")
        MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = mockConstruction(
                DataManipulationHandler.class)) {
            // GIVEN
            var obligations     = (ArrayNode) objectMapper.readTree(
                    "[{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
            var authSub         = AuthorizationSubscription.of("", "permitTest", "");
            var enforcementData = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest, null,
                    TestUser.class, pdpMock, authSub);

            var proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(enforcementData, false);
            var dataManipulationHandler             = dataManipulationHandlerMockedConstruction.constructed().get(0);

            // WHEN
            when(pdpMock.decide(any(AuthorizationSubscription.class)))
                    .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
            when(dataManipulationHandler.manipulate(obligations)).thenReturn((data) -> this.data);
            constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                    .thenReturn(objectMapper.createArrayNode());
            constraintHandlerUtilsMock
                    .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                    .thenReturn(obligations);
            var testUserFlux = proceededDataFilterEnforcementPoint.enforce();

            // THEN
            StepVerifier.create(testUserFlux).expectNext(aaron).expectNext(brian).expectNext(cathrin).verifyComplete();

            verify(dataManipulationHandler, times(1)).manipulate(obligations);
        }
    }

    @Test
    void when_actionWasFoundInPoliciesButProceededDataCantBeConverted_then_throwRuntimeException()
            throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        try (@SuppressWarnings("rawtypes")
        MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = mockConstruction(
                DataManipulationHandler.class)) {
            // GIVEN
            var mongoMethodInvocationTest           = new MethodInvocationForTesting("findAllByFirstname",
                    new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), new Throwable());
            var obligations                         = (ArrayNode) objectMapper.readTree(
                    "[{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
            var authSub                             = AuthorizationSubscription.of("", "permitTest", "");
            var enforcementData                     = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                    null, TestUser.class, pdpMock, authSub);
            var proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(enforcementData, false);

            var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
            when(dataManipulationHandler.manipulate(obligations)).thenReturn((data) -> this.data);
            constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                    .thenReturn(objectMapper.createArrayNode());
            constraintHandlerUtilsMock
                    .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                    .thenReturn(obligations);
            when(pdpMock.decide(any(AuthorizationSubscription.class)))
                    .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));

            // WHEN
            var accessDeniedException = proceededDataFilterEnforcementPoint.enforce();

            // THEN
            StepVerifier.create(accessDeniedException).expectError(RuntimeException.class).verify();

            verify(dataManipulationHandler, times(0)).manipulate(obligations);
        }
    }

    @Test
    void when_actionWasFoundInPoliciesButDecisionIsDeny_then_throwAccessDeniedException() {
        // GIVEN
        var authSub                             = AuthorizationSubscription.of("", "denyTest", "");
        var enforcementData                     = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                null, TestUser.class, pdpMock, authSub);
        var proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(enforcementData, false);

        // WHEN
        when(pdpMock.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.DENY)));
        var accessDeniedException = proceededDataFilterEnforcementPoint.enforce();

        // THEN
        StepVerifier.create(accessDeniedException).expectError(AccessDeniedException.class).verify();
    }

    @Test
    void when_actionWasNotFoundInPolicies_then_throwAccessDeniedException() {
        // GIVEN
        var authSub                             = AuthorizationSubscription.of("", "noCorrectAction", "");
        var enforcementData                     = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                null, TestUser.class, pdpMock, authSub);
        var proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(enforcementData, false);

        // WHEN
        when(pdpMock.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.DENY)));
        var accessDeniedException = proceededDataFilterEnforcementPoint.enforce();

        // THEN
        StepVerifier.create(accessDeniedException).expectError(AccessDeniedException.class).verify();
    }

}
