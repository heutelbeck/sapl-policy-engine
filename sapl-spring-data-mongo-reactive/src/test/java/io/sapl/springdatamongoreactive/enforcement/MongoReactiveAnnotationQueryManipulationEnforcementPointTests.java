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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.springdatacommon.services.ConstraintQueryEnforcementService;
import io.sapl.springdatacommon.services.QueryManipulationConstraintHandlerBundle;
import io.sapl.springdatamongoreactive.queries.QueryCreation;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MongoReactiveAnnotationQueryManipulationEnforcementPointTests {

	static final ObjectMapper MAPPER = new ObjectMapper();
	static ArrayNode EMPTY_ARRAY_NODE = MAPPER.createArrayNode();

	final TestUser cathrin = new TestUser(new ObjectId(), "Cathrin", 33, true);
	Class<Object> objectClass = (Class<Object>) Object.class;
	BasicQuery baseQuery = new BasicQuery("{'active': {'$eq': true}}");

	@Mock
	ObjectProvider<PolicyDecisionPoint> objectProviderPolicyDecisionPointMock;

	@Mock
	ObjectProvider<BeanFactory> objectProviderBeanFactoryMock;

	@Mock
	ObjectProvider<ConstraintQueryEnforcementService> objectProviderConstraintQueryEnforcementServiceMock;

	PolicyDecisionPoint policyDecisionPointMock = mock(PolicyDecisionPoint.class);
	BeanFactory beanFactoryMock = mock(BeanFactory.class);
	ConstraintQueryEnforcementService constraintQueryEnforcementServiceMock = mock(
			ConstraintQueryEnforcementService.class);
	QueryManipulationConstraintHandlerBundle queryManipulationConstraintHandlerBundle = mock(
			QueryManipulationConstraintHandlerBundle.class);
	AuthorizationSubscription authorizationSubscriptionMock = mock(AuthorizationSubscription.class);
	MethodInvocation methodInvocationMock = mock(MethodInvocation.class);
	ReactiveMongoTemplate reactiveMongoTemplateMock = mock(ReactiveMongoTemplate.class);
	MockedStatic<QueryCreation> queryCreationMock;

	@BeforeEach
	public void beforeEach() {
		lenient().when(objectProviderPolicyDecisionPointMock.getObject()).thenReturn(policyDecisionPointMock);
		lenient().when(objectProviderBeanFactoryMock.getObject()).thenReturn(beanFactoryMock);
		lenient().when(objectProviderConstraintQueryEnforcementServiceMock.getObject())
				.thenReturn(constraintQueryEnforcementServiceMock);
		lenient().when(beanFactoryMock.getBean(ReactiveMongoTemplate.class)).thenReturn(reactiveMongoTemplateMock);
		lenient().when(reactiveMongoTemplateMock.find(any(BasicQuery.class), any())).thenReturn(Flux.just(cathrin));

		when(queryManipulationConstraintHandlerBundle.getConditions()).thenReturn(EMPTY_ARRAY_NODE);
		when(queryManipulationConstraintHandlerBundle.getSelections()).thenReturn(EMPTY_ARRAY_NODE);

		queryCreationMock = mockStatic(QueryCreation.class);

		queryCreationMock.when(() -> QueryCreation.createBaselineQuery(any(MethodInvocation.class)))
				.thenReturn(baseQuery);
		queryCreationMock.when(
				() -> QueryCreation.manipulateQuery(any(ArrayNode.class), any(ArrayNode.class), any(BasicQuery.class), any(MethodInvocation.class)))
				.thenReturn(baseQuery);
	}

	@AfterEach
	public void cleanUp() {
		queryCreationMock.close();
	}

	@Test
	void when_enforce_then_returnFluxDomainObject() {
		// GIVEN
		var enforcementPoint = new MongoReactiveAnnotationQueryManipulationEnforcementPoint<>(
				objectProviderPolicyDecisionPointMock, objectProviderBeanFactoryMock,
				objectProviderConstraintQueryEnforcementServiceMock);

		// WHEN
		when(policyDecisionPointMock.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
		when(constraintQueryEnforcementServiceMock.queryManipulationBundelFor(any(AuthorizationDecision.class),
				any(Boolean.class))).thenReturn(queryManipulationConstraintHandlerBundle);

		var result = enforcementPoint.enforce(authorizationSubscriptionMock, objectClass, methodInvocationMock);

		// THEN
		StepVerifier.create(result).expectNext(cathrin).expectComplete().verify();

		queryCreationMock.verify(() -> QueryCreation.createBaselineQuery(any(MethodInvocation.class)), times(1));
		verify(policyDecisionPointMock, times(1)).decide(any(AuthorizationSubscription.class));
		verify(constraintQueryEnforcementServiceMock, times(1))
				.queryManipulationBundelFor(any(AuthorizationDecision.class), any(Boolean.class));
		verify(queryManipulationConstraintHandlerBundle, times(1)).getConditions();
		verify(queryManipulationConstraintHandlerBundle, times(1)).getSelections();
		queryCreationMock.verify(
				() -> QueryCreation.manipulateQuery(any(ArrayNode.class), any(ArrayNode.class), any(BasicQuery.class), any(MethodInvocation.class)),
				times(1));
		verify(beanFactoryMock, times(1)).getBean(ReactiveMongoTemplate.class);
		verify(reactiveMongoTemplateMock, times(1)).find(any(BasicQuery.class), any());
	}

	@Test
	void when_enforce_then_throwAccessDeniedException() {
		// GIVEN
		var enforcementPoint = new MongoReactiveAnnotationQueryManipulationEnforcementPoint<>(
				objectProviderPolicyDecisionPointMock, objectProviderBeanFactoryMock,
				objectProviderConstraintQueryEnforcementServiceMock);

		// WHEN
		when(policyDecisionPointMock.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(new AuthorizationDecision(Decision.DENY)));

		var result = enforcementPoint.enforce(authorizationSubscriptionMock, objectClass, methodInvocationMock);
		StepVerifier.create(result).expectErrorMatches(
				error -> error instanceof AccessDeniedException && error.getMessage().equals("Access Denied by PDP"))
				.verify();

		// THEN
		queryCreationMock.verify(() -> QueryCreation.createBaselineQuery(any(MethodInvocation.class)), times(1));
		verify(policyDecisionPointMock, times(1)).decide(any(AuthorizationSubscription.class));
		verify(constraintQueryEnforcementServiceMock, times(0))
				.queryManipulationBundelFor(any(AuthorizationDecision.class), any(Boolean.class));
		verify(queryManipulationConstraintHandlerBundle, times(0)).getConditions();
		verify(queryManipulationConstraintHandlerBundle, times(0)).getSelections();
		queryCreationMock.verify(
				() -> QueryCreation.manipulateQuery(any(ArrayNode.class), any(ArrayNode.class), any(BasicQuery.class), any(MethodInvocation.class)),
				times(0));
		verify(beanFactoryMock, times(0)).getBean(ReactiveMongoTemplate.class);
		verify(reactiveMongoTemplateMock, times(0)).find(any(BasicQuery.class), any());
	}

}
