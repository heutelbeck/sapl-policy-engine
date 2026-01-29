/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.data.mongo.enforcement;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.ReactiveConstraintHandlerBundle;
import io.sapl.spring.data.services.ConstraintQueryEnforcementService;
import io.sapl.spring.data.services.QueryManipulationConstraintHandlerService;
import io.sapl.spring.data.mongo.queries.QueryCreation;
import io.sapl.spring.data.mongo.sapl.database.TestUser;
import lombok.val;
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
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

    MockedStatic<QueryCreation>              queryCreationMock;
    PolicyDecisionPoint                      pdp;
    PolicyDecisionPointBuilder.PDPComponents components;

    @BeforeEach
    void beforeEach() throws URISyntaxException {
        components = buildPdp();
        pdp        = components.pdp();
        lenient().when(objectProviderPolicyDecisionPointMock.getObject()).thenReturn(pdp);
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
    void cleanUp() {
        queryCreationMock.close();
    }

    @Test
    void when_enforce_then_returnFluxDomainObject() {
        // GIVEN
        final var authorizationSubscriptionMock = AuthorizationSubscription.of("", "permitTest", "", "");
        final var enforcementPoint              = new MongoReactiveAnnotationQueryManipulationEnforcementPoint<TestUser>(
                objectProviderPolicyDecisionPointMock, objectProviderBeanFactoryMock,
                objectProviderConstraintQueryEnforcementServiceMock, constraintEnforcementServiceMock);

        // WHEN
        when(constraintQueryEnforcementServiceMock.queryManipulationForMongoReactive(any(AuthorizationDecision.class)))
                .thenReturn(queryManipulationConstraintHandlerBundle);
        when(constraintEnforcementServiceMock.reactiveTypeBundleFor(any(AuthorizationDecision.class),
                eq(TestUser.class), any(Value[].class))).thenReturn(reactiveConstraintHandlerBundleMock);
        doNothing().when(reactiveConstraintHandlerBundleMock)
                .handleMethodInvocationHandlers(any(MethodInvocation.class));
        when(constraintEnforcementServiceMock.replaceIfResourcePresent(any(), any(), eq(TestUser.class)))
                .thenReturn(Flux.just(cathrin));

        final var result = enforcementPoint.enforce(authorizationSubscriptionMock, TestUser.class,
                methodInvocationMock);

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
        final var authorizationSubscriptionMock = AuthorizationSubscription.of("", "denyTest", "", "");
        final var enforcementPoint              = new MongoReactiveAnnotationQueryManipulationEnforcementPoint<TestUser>(
                objectProviderPolicyDecisionPointMock, objectProviderBeanFactoryMock,
                objectProviderConstraintQueryEnforcementServiceMock, constraintEnforcementServiceMock);

        // WHEN
        final var result = enforcementPoint.enforce(authorizationSubscriptionMock, TestUser.class,
                methodInvocationMock);
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

    private static PolicyDecisionPointBuilder.PDPComponents buildPdp() throws URISyntaxException {
        val policiesPath = Paths.get(Objects
                .requireNonNull(MongoReactiveAnnotationQueryManipulationEnforcementPointTests.class.getClassLoader()
                        .getResource("policies-mongo"), "Classpath resource 'policies-mongo' not found")
                .toURI());
        return PolicyDecisionPointBuilder.withDefaults().withDirectorySource(policiesPath).build();
    }
}
