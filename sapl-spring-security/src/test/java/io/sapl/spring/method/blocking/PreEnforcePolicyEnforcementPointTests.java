/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.method.blocking;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.util.MethodInvocationUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.BlockingPreEnforceConstraintHandlerBundle;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.PreEnforceAttribute;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import reactor.core.publisher.Flux;

class PreEnforcePolicyEnforcementPointTests {

	static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	ObjectFactory<PolicyDecisionPoint>                     pdpFactory;
	ObjectFactory<ConstraintEnforcementService>            constraintHandlerFactory;
	ObjectFactory<AuthorizationSubscriptionBuilderService> subscriptionBuilderFactory;
	PolicyDecisionPoint                                    pdp;
	ConstraintEnforcementService                           constraintEnforcementService;
	Authentication                                         authentication;
	AuthorizationSubscriptionBuilderService                subscriptionBuilder;

	@BeforeEach
	void beforeEach() {
		pdp                          = mock(PolicyDecisionPoint.class);
		pdpFactory                   = () -> pdp;
		constraintEnforcementService = mock(ConstraintEnforcementService.class);
		constraintHandlerFactory     = () -> constraintEnforcementService;
		authentication               = new UsernamePasswordAuthenticationToken("principal", "credentials");
		subscriptionBuilder          = new AuthorizationSubscriptionBuilderService(
				new DefaultMethodSecurityExpressionHandler(), configureObjectMapper());
		subscriptionBuilderFactory   = () -> subscriptionBuilder;
	}

	private ObjectMapper configureObjectMapper() {
		var mapper = new ObjectMapper();
		var module = new SimpleModule();
		module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
		mapper.registerModule(module);
		return mapper;
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_triggeredTwice_factoriesOnlyCalledOnce() {
		var mockBundle = mock(BlockingPreEnforceConstraintHandlerBundle.class);
		when(constraintEnforcementService.blockingPreEnforceBundleFor(any())).thenReturn(mockBundle);
		var mock = (ObjectFactory<ConstraintEnforcementService>) mock(ObjectFactory.class);
		when(mock.getObject()).thenReturn(constraintEnforcementService);
		var sut              = new PreEnforcePolicyEnforcementPoint(pdpFactory, mock, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute        = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
		sut.before(authentication, methodInvocation, attribute);
		sut.before(authentication, methodInvocation, attribute);
		verify(mock, times(1)).getObject();
	}

	@Test
	void whenCreated_thenNotNull() {
		var sut = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		assertThat(sut, notNullValue());
	}

	@Test
	void whenBeforeAndDecideDeny_thenReturnFalse() {
		var mockBundle = mock(BlockingPreEnforceConstraintHandlerBundle.class);
		when(constraintEnforcementService.blockingPreEnforceBundleFor(any())).thenReturn(mockBundle);
		var sut              = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute        = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}
	
	@Test
	void whenBeforeAndDecideNull_thenReturnFalse() {
		var mockBundle = mock(BlockingPreEnforceConstraintHandlerBundle.class);
		when(constraintEnforcementService.blockingPreEnforceBundleFor(any())).thenReturn(mockBundle);
		var sut              = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute        = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(null);
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	@Test
	void whenBeforeAndDecidePermitButBundleNull_thenReturnFalse() {
		when(constraintEnforcementService.blockingPreEnforceBundleFor(any())).thenReturn(null);
		var sut              = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute        = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}
	
	@Test
	void whenBeforeAndDecidePermit_thenReturnTrue() {
		var mockBundle = mock(BlockingPreEnforceConstraintHandlerBundle.class);
		when(constraintEnforcementService.blockingPreEnforceBundleFor(any())).thenReturn(mockBundle);
		var sut              = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute        = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(true));
	}

	@Test
	void when_BeforeAndDecidePermit_and_obligationsFail_then_ReturnFalse() {
		var mockBundle = mock(BlockingPreEnforceConstraintHandlerBundle.class);
		doThrow(new AccessDeniedException("FAILURE IN CONSTRAINTS")).when(mockBundle).handleOnDecisionConstraints();
		when(constraintEnforcementService.blockingPreEnforceBundleFor(any())).thenReturn(mockBundle);
		var sut              = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute        = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	@Test
	void whenBeforeAndDecideNotApplicable_thenReturnFalse() {
		var mockBundle = mock(BlockingPreEnforceConstraintHandlerBundle.class);
		when(constraintEnforcementService.blockingPreEnforceBundleFor(any())).thenReturn(mockBundle);
		var sut              = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute        = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	@Test
	void whenBeforeAndDecideIndeterminate_thenReturnFalse() {
		var mockBundle = mock(BlockingPreEnforceConstraintHandlerBundle.class);
		when(constraintEnforcementService.blockingPreEnforceBundleFor(any())).thenReturn(mockBundle);
		var sut              = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute        = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	@Test
	void whenBeforeAndDecideEmpty_thenReturnFalse() {
		var sut              = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute        = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.empty());
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	@Test
	void whenBeforeAndDecidePermitWithResource_thenReturnFalse() {
		var sut              = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute        = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.arrayNode())));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	static class TestClass {

		public void doSomething() {
		}

	}

}
