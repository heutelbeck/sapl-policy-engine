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

import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.springdatacommon.database.MongoReactiveMethodInvocation;
import io.sapl.springdatacommon.database.Person;
import io.sapl.springdatacommon.database.R2dbcMethodInvocation;
import io.sapl.springdatacommon.database.Role;
import io.sapl.springdatacommon.database.User;
import io.sapl.springdatacommon.handlers.DataManipulationHandler;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatacommon.sapl.queries.enforcement.ProceededDataFilterEnforcementPoint;
import io.sapl.springdatacommon.sapl.utils.ConstraintHandlerUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ProceededDataFilterEnforcementPointTests {

    final Person malinda = new Person(1, "Malinda", "Perrot", 53, Role.ADMIN, true);
    final Person emerson = new Person(2, "Emerson", "Rowat", 82, Role.USER, false);
    final Person yul     = new Person(3, "Yul", "Barukh", 79, Role.USER, true);

    final ObjectId malindaUserId = new ObjectId("5399aba6e4b0ae375bfdca88");
    final ObjectId emersonUserId = new ObjectId("5399aba6e4b0ae375bfdca88");
    final ObjectId yulUserId     = new ObjectId("5399aba6e4b0ae375bfdca88");

    final User malindaUser = new User(malindaUserId, "Malinda", 53, Role.ADMIN);
    final User emersonUser = new User(emersonUserId, "Emerson", 82, Role.USER);
    final User yulUser     = new User(yulUserId, "Yul", 79, Role.USER);

    final Flux<Person> data     = Flux.just(malinda, emerson, yul);
    final Flux<User>   dataUser = Flux.just(malindaUser, emersonUser, yulUser);

    static final ObjectMapper MAPPER           = new ObjectMapper();
    static final ArrayNode    EMPTY_ARRAY_NODE = MAPPER.createArrayNode();
    static ArrayNode          OBLIGATIONS_R2DBC;
    static ArrayNode          OBLIGATIONS_MONGO_REACTIVE;

    EmbeddedPolicyDecisionPoint pdpMock;

    @BeforeAll
    public static void beforeAll() throws JsonProcessingException {
        OBLIGATIONS_R2DBC = MAPPER.readValue("""
                    		[
                  {
                    "type": "r2dbcQueryManipulation",
                    "conditions": [
                      "role IN('USER')"
                    ]
                  },
                  {
                    "type": "filterJsonContent",
                    "actions": [
                      {
                        "type": "blacken",
                        "path": "$.firstname",
                        "discloseLeft": 2
                      }
                    ]
                  },
                  {
                    "type": "jsonContentFilterPredicate",
                    "conditions": [
                      {
                        "type": "==",
                        "path": "$.id",
                        "value": "a1"
                      }
                    ]
                  }
                ]
                    		""", ArrayNode.class);

        OBLIGATIONS_MONGO_REACTIVE = MAPPER.readValue("""
                   [
                    {
                      "type": "r2dbcQueryManipulation",
                      "conditions": [
                        "{'role':  {'$in': ['USER']}}"
                      ]
                    },
                    {
                      "type": "filterJsonContent",
                      "actions": [
                        {
                          "type": "blacken",
                          "path": "$.firstname",
                          "discloseLeft": 2
                        }
                      ]
                    },
                    {
                      "type": "jsonContentFilterPredicate",
                      "conditions": [
                        {
                          "type": "==",
                          "path": "$.id",
                          "value": "a1"
                        }
                      ]
                    }
                  ]
                """, ArrayNode.class);
    }

    @BeforeEach
    void beforeEach() {
        pdpMock                    = mock(EmbeddedPolicyDecisionPoint.class);
        constraintHandlerUtilsMock = mockStatic(ConstraintHandlerUtils.class);
    }

    @AfterEach
    void cleanUp() {
        constraintHandlerUtilsMock.close();
    }

    MockedStatic<ConstraintHandlerUtils> constraintHandlerUtilsMock;

    private final R2dbcMethodInvocation r2dbcMethodInvocationTest = new R2dbcMethodInvocation("findAllByFirstname",
            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);

    private final MongoReactiveMethodInvocation mongoReactiveMethodInvocationTest = new MongoReactiveMethodInvocation(
            "findAllByFirstname", new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);

    @Test
    @SuppressWarnings("rawtypes") // mocking of generic types
    void when_actionWasFoundInPolicies_then_enforceR2dbc() throws JsonProcessingException {
        try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                .mockConstruction(DataManipulationHandler.class)) {
            // GIVEN
            var authSub         = AuthorizationSubscription.of("", "permitTest", "");
            var enforcementData = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest, null, Person.class,
                    pdpMock, authSub);

            var proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(enforcementData, true);
            var dataManipulationHandler             = dataManipulationHandlerMockedConstruction.constructed().get(0);

            // WHEN
            when(pdpMock.decide(any(AuthorizationSubscription.class)))
                    .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
            when(dataManipulationHandler.manipulate(OBLIGATIONS_R2DBC)).thenReturn((data) -> this.data);
            constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                    .thenReturn(EMPTY_ARRAY_NODE);
            constraintHandlerUtilsMock
                    .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                    .thenReturn(OBLIGATIONS_R2DBC);
            var testUserFlux = proceededDataFilterEnforcementPoint.enforce();

            // THEN
            StepVerifier.create(testUserFlux).expectNext(malinda).expectNext(emerson).expectNext(yul).verifyComplete();

            verify(dataManipulationHandler, times(1)).manipulate(OBLIGATIONS_R2DBC);
        }
    }

    @Test
    @SuppressWarnings("rawtypes") // mocking of generic types
    void when_actionWasFoundInPolicies_then_enforceMongoReactive() throws JsonProcessingException {
        try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                .mockConstruction(DataManipulationHandler.class)) {
            // GIVEN
            var authSub         = AuthorizationSubscription.of("", "permitTest", "");
            var enforcementData = new QueryManipulationEnforcementData<>(mongoReactiveMethodInvocationTest, null,
                    User.class, pdpMock, authSub);

            var proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(enforcementData, false);
            var dataManipulationHandler             = dataManipulationHandlerMockedConstruction.constructed().get(0);

            // WHEN
            when(pdpMock.decide(any(AuthorizationSubscription.class)))
                    .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
            when(dataManipulationHandler.manipulate(OBLIGATIONS_MONGO_REACTIVE)).thenReturn((data) -> this.dataUser);
            constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                    .thenReturn(EMPTY_ARRAY_NODE);
            constraintHandlerUtilsMock
                    .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                    .thenReturn(OBLIGATIONS_MONGO_REACTIVE);
            var testUserFlux = proceededDataFilterEnforcementPoint.enforce();

            // THEN
            StepVerifier.create(testUserFlux).expectNext(malindaUser).expectNext(emersonUser).expectNext(yulUser)
                    .verifyComplete();

            verify(dataManipulationHandler, times(1)).manipulate(OBLIGATIONS_MONGO_REACTIVE);
        }
    }

    @Test
    @SuppressWarnings("rawtypes") // mocking of generic types
    void when_actionWasFoundInPoliciesButProceededDataCantBeConverted_then_throwRuntimeException()
            throws JsonProcessingException {
        try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                .mockConstruction(DataManipulationHandler.class)) {
            // GIVEN
            R2dbcMethodInvocation r2dbcMethodInvocationTest           = new R2dbcMethodInvocation("findAllByFirstname",
                    new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), new Throwable());
            var                   authSub                             = AuthorizationSubscription.of("", "permitTest",
                    "");
            var                   enforcementData                     = new QueryManipulationEnforcementData<>(
                    r2dbcMethodInvocationTest, null, Person.class, pdpMock, authSub);
            var                   proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(
                    enforcementData, true);

            var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
            when(dataManipulationHandler.manipulate(OBLIGATIONS_R2DBC)).thenReturn((data) -> this.data);
            constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getAdvice(any(AuthorizationDecision.class)))
                    .thenReturn(EMPTY_ARRAY_NODE);
            constraintHandlerUtilsMock
                    .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                    .thenReturn(OBLIGATIONS_R2DBC);
            when(pdpMock.decide(any(AuthorizationSubscription.class)))
                    .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));

            // WHEN
            var accessDeniedException = proceededDataFilterEnforcementPoint.enforce();

            // THEN
            StepVerifier.create(accessDeniedException).expectError(RuntimeException.class).verify();

            verify(dataManipulationHandler, times(0)).manipulate(OBLIGATIONS_R2DBC);
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
