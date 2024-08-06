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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.aopalliance.intercept.MethodInvocation;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
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
import io.sapl.springdatamongoreactive.queries.QueryCreation;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MongoReactiveAnnotationQueryManipulationEnforcementPointTests {

    private static final String       ACCESS_DENIED_BY_PDP = "Access Denied by PDP";
    private static final ObjectMapper MAPPER               = new ObjectMapper();
    private static final ArrayNode    EMPTY_ARRAY_NODE     = MAPPER.createArrayNode();

    private final TestUser   cathrin   = new TestUser(new ObjectId(), "Cathrin", 33, true);
    private final BasicQuery baseQuery = new BasicQuery("{'active': {'$eq': true}}");

    @Mock
    ObjectProvider<PolicyDecisionPoint> objectProviderPolicyDecisionPointMock;

    @Mock
    ObjectProvider<BeanFactory> objectProviderBeanFactoryMock;

    @Mock
    ObjectProvider<ConstraintQueryEnforcementService> objectProviderConstraintQueryEnforcementServiceMock;

    @Mock
    ReactiveConstraintHandlerBundle<TestUser> reactiveConstraintHandlerBundleMock;

    BeanFactory                               beanFactoryMock                          = mock(BeanFactory.class);
    ConstraintQueryEnforcementService         constraintQueryEnforcementServiceMock    = mock(
            ConstraintQueryEnforcementService.class);
    QueryManipulationConstraintHandlerService queryManipulationConstraintHandlerBundle = mock(
            QueryManipulationConstraintHandlerService.class);
    MethodInvocation                          methodInvocationMock                     = mock(MethodInvocation.class);
    ReactiveMongoTemplate                     reactiveMongoTemplateMock                = mock(
            ReactiveMongoTemplate.class);
    ConstraintEnforcementService              constraintEnforcementServiceMock         = mock(
            ConstraintEnforcementService.class);

    MockedStatic<QueryCreation> queryCreationMock;
    PolicyDecisionPoint         PDP;

    @BeforeEach
    public void beforeEach() throws InitializationException {
        PDP = buildPdp();
        lenient().when(objectProviderPolicyDecisionPointMock.getObject()).thenReturn(PDP);
        lenient().when(objectProviderBeanFactoryMock.getObject()).thenReturn(beanFactoryMock);
        lenient().when(objectProviderConstraintQueryEnforcementServiceMock.getObject())
                .thenReturn(constraintQueryEnforcementServiceMock);
        lenient().when(beanFactoryMock.getBean(ReactiveMongoTemplate.class)).thenReturn(reactiveMongoTemplateMock);
        lenient().when(reactiveMongoTemplateMock.find(any(BasicQuery.class), any())).thenReturn(Flux.just(cathrin));

        when(queryManipulationConstraintHandlerBundle.getConditions()).thenReturn(EMPTY_ARRAY_NODE);
        when(queryManipulationConstraintHandlerBundle.getSelections()).thenReturn(EMPTY_ARRAY_NODE);
        when(queryManipulationConstraintHandlerBundle.getQueryManipulationObligations()).thenReturn(new JsonNode[0]);

        queryCreationMock = mockStatic(QueryCreation.class);

        queryCreationMock.when(() -> QueryCreation.createBaselineQuery(any(MethodInvocation.class)))
                .thenReturn(baseQuery);
        queryCreationMock.when(() -> QueryCreation.manipulateQuery(any(ArrayNode.class), any(ArrayNode.class),
                any(BasicQuery.class), any(MethodInvocation.class))).thenReturn(baseQuery);
    }

    @AfterEach
    public void cleanUp() {
        queryCreationMock.close();
    }

    @Test
    void when_enforce_then_returnFluxDomainObject() {
        // GIVEN
        var authorizationSubscriptionMock = AuthorizationSubscription.of("", "permitTest", "", "");
        var enforcementPoint              = new MongoReactiveAnnotationQueryManipulationEnforcementPoint<TestUser>(
                objectProviderPolicyDecisionPointMock, objectProviderBeanFactoryMock,
                objectProviderConstraintQueryEnforcementServiceMock, constraintEnforcementServiceMock);

        // WHEN
        when(constraintQueryEnforcementServiceMock.queryManipulationForMongoReactive(any(AuthorizationDecision.class)))
                .thenReturn(queryManipulationConstraintHandlerBundle);
        when(constraintEnforcementServiceMock.reactiveTypeBundleFor(any(AuthorizationDecision.class),
                eq(TestUser.class), any(JsonNode[].class))).thenReturn(reactiveConstraintHandlerBundleMock);
        doNothing().when(reactiveConstraintHandlerBundleMock)
                .handleMethodInvocationHandlers(any(MethodInvocation.class));
        when(constraintEnforcementServiceMock.replaceIfResourcePresent(any(), any(), eq(TestUser.class)))
                .thenReturn(Flux.just(cathrin));

        var result = enforcementPoint.enforce(authorizationSubscriptionMock, TestUser.class, methodInvocationMock);

        // THEN
        StepVerifier.create(result).expectNext(cathrin).expectComplete().verify();

        queryCreationMock.verify(() -> QueryCreation.createBaselineQuery(any(MethodInvocation.class)), times(1));
        verify(constraintQueryEnforcementServiceMock, times(1))
                .queryManipulationForMongoReactive(any(AuthorizationDecision.class));
        verify(queryManipulationConstraintHandlerBundle, times(1)).getConditions();
        verify(queryManipulationConstraintHandlerBundle, times(1)).getSelections();
        queryCreationMock.verify(() -> QueryCreation.manipulateQuery(any(ArrayNode.class), any(ArrayNode.class),
                any(BasicQuery.class), any(MethodInvocation.class)), times(1));
        verify(beanFactoryMock, times(1)).getBean(ReactiveMongoTemplate.class);
        verify(reactiveMongoTemplateMock, times(1)).find(any(BasicQuery.class), any());
    }

    @Test
    void when_enforce_then_throwAccessDeniedException() {
        // GIVEN
        var authorizationSubscriptionMock = AuthorizationSubscription.of("", "denyTest", "", "");
        var enforcementPoint              = new MongoReactiveAnnotationQueryManipulationEnforcementPoint<TestUser>(
                objectProviderPolicyDecisionPointMock, objectProviderBeanFactoryMock,
                objectProviderConstraintQueryEnforcementServiceMock, constraintEnforcementServiceMock);

        // WHEN
        var result = enforcementPoint.enforce(authorizationSubscriptionMock, TestUser.class, methodInvocationMock);
        StepVerifier.create(result).expectErrorMatches(
                error -> error instanceof AccessDeniedException && ACCESS_DENIED_BY_PDP.equals(error.getMessage()))
                .verify();

        // THEN
        queryCreationMock.verify(() -> QueryCreation.createBaselineQuery(any(MethodInvocation.class)), times(1));
        verify(constraintQueryEnforcementServiceMock, times(0))
                .queryManipulationForMongoReactive(any(AuthorizationDecision.class));
        verify(queryManipulationConstraintHandlerBundle, times(0)).getConditions();
        verify(queryManipulationConstraintHandlerBundle, times(0)).getSelections();
        queryCreationMock.verify(() -> QueryCreation.manipulateQuery(any(ArrayNode.class), any(ArrayNode.class),
                any(BasicQuery.class), any(MethodInvocation.class)), times(0));
        verify(beanFactoryMock, times(0)).getBean(ReactiveMongoTemplate.class);
        verify(reactiveMongoTemplateMock, times(0)).find(any(BasicQuery.class), any());
    }

    private static EmbeddedPolicyDecisionPoint buildPdp() throws InitializationException {
        return PolicyDecisionPointFactory.filesystemPolicyDecisionPoint("src/test/resources/policies");
    }
}
