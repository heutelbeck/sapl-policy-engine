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
package io.sapl.springdatacommon.queries.enforcement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.springdatacommon.database.MethodInvocationForTesting;
import io.sapl.springdatacommon.database.Person;
import io.sapl.springdatacommon.database.Role;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatacommon.sapl.queries.enforcement.ProceededDataFilterEnforcementPoint;
import io.sapl.springdatacommon.handlers.DataManipulationHandler;
import io.sapl.springdatacommon.sapl.utils.ConstraintHandlerUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ProceededDataFilterEnforcementPointTests {

    final Person malinda = new Person(1, "Malinda", "Perrot", 53, Role.ADMIN, true);
    final Person emerson = new Person(2, "Emerson", "Rowat", 82, Role.USER, false);
    final Person yul     = new Person(3, "Yul", "Barukh", 79, Role.USER, true);

    final Flux<Person> data = Flux.just(malinda, emerson, yul);

    final ObjectMapper objectMapper = new ObjectMapper();

    EmbeddedPolicyDecisionPoint pdpMock;

    @BeforeEach
    void beforeEach() {
        constraintHandlerUtilsMock = mockStatic(ConstraintHandlerUtils.class);
        pdpMock                    = mock(EmbeddedPolicyDecisionPoint.class);
    }

    @AfterEach
    void cleanUp() {
        constraintHandlerUtilsMock.close();
    }

    MockedStatic<ConstraintHandlerUtils> constraintHandlerUtilsMock;

    private final MethodInvocationForTesting r2dbcMethodInvocationTest = new MethodInvocationForTesting(
            "findAllByFirstname", new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);

    @Test
    @SuppressWarnings("rawtypes")
    void when_actionWasFoundInPolicies_then_enforce() throws JsonProcessingException {
        try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                .mockConstruction(DataManipulationHandler.class)) {
            // GIVEN
            var obligations     = objectMapper.readTree(
                    "[{\"type\":\"r2dbcQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
            var authSub         = AuthorizationSubscription.of("", "permitTest", "");
            var enforcementData = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest, null, Person.class,
                    pdpMock, authSub);

            var proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(enforcementData, true);
            var dataManipulationHandler             = dataManipulationHandlerMockedConstruction.constructed().get(0);

            // WHEN
            when(pdpMock.decide(any(AuthorizationSubscription.class)))
                    .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
            when(dataManipulationHandler.manipulate(obligations)).thenReturn((data) -> this.data);
            constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                    .thenReturn(JsonNodeFactory.instance.nullNode());
            constraintHandlerUtilsMock
                    .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                    .thenReturn(obligations);
            var testUserFlux = proceededDataFilterEnforcementPoint.enforce();

            // THEN
            StepVerifier.create(testUserFlux).expectNext(malinda).expectNext(emerson).expectNext(yul).verifyComplete();

            verify(dataManipulationHandler, times(1)).manipulate(obligations);
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    void when_actionWasFoundInPoliciesButProceededDataCantBeConverted_then_throwRuntimeException()
            throws JsonProcessingException {
        try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                .mockConstruction(DataManipulationHandler.class)) {
            // GIVEN
            MethodInvocationForTesting r2dbcMethodInvocationTest           = new MethodInvocationForTesting(
                    "findAllByFirstname", new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")),
                    new Throwable());
            var                        obligations                         = objectMapper.readTree(
                    "[{\"type\":\"r2dbcQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
            var                        authSub                             = AuthorizationSubscription.of("",
                    "permitTest", "");
            var                        enforcementData                     = new QueryManipulationEnforcementData<>(
                    r2dbcMethodInvocationTest, null, Person.class, pdpMock, authSub);
            var                        proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(
                    enforcementData, true);

            var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
            when(dataManipulationHandler.manipulate(obligations)).thenReturn((data) -> this.data);
            constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                    .thenReturn(JsonNodeFactory.instance.nullNode());
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
        var enforcementData                     = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                null, Person.class, pdpMock, authSub);
        var proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(enforcementData, true);

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
        var enforcementData                     = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                null, Person.class, pdpMock, authSub);
        var proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(enforcementData, true);

        // WHEN
        when(pdpMock.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.DENY)));
        var accessDeniedException = proceededDataFilterEnforcementPoint.enforce();

        // THEN
        StepVerifier.create(accessDeniedException).expectError(AccessDeniedException.class).verify();
    }

}
