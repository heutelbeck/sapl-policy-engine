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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

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
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.FilterPredicateConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MethodInvocationConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RequestHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.spring.constraints.api.SubscriptionHandlerProvider;
import io.sapl.spring.method.metadata.PostEnforceAttribute;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import reactor.core.publisher.Flux;

class PostEnforcePolicyEnforcementPointTests {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final String DO_SOMETHING = "doSomething";

	private static final String ORIGINAL_RETURN_OBJECT = "original return object";

	private static final String CHANGED_RETURN_OBJECT = "changed return object";

	ObjectFactory<PolicyDecisionPoint> pdpFactory;

	ObjectFactory<ConstraintEnforcementService> constraintHandlerFactory;

	ObjectFactory<AuthorizationSubscriptionBuilderService> subscriptionBuilderFactory;

	PolicyDecisionPoint pdp;

	ConstraintEnforcementService constraintHandlers;

	Authentication authentication;

	AuthorizationSubscriptionBuilderService subscriptionBuilder;

	List<RunnableConstraintHandlerProvider> globalRunnableProviders;

	List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders;

	List<SubscriptionHandlerProvider> globalSubscriptionHandlerProviders;

	List<RequestHandlerProvider> globalRequestHandlerProviders;

	List<MappingConstraintHandlerProvider<?>> globalMappingHandlerProviders;

	List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders;

	List<ErrorHandlerProvider> globalErrorHandlerProviders;

	List<FilterPredicateConstraintHandlerProvider> globalFilterPredicateProviders;

	List<MethodInvocationConstraintHandlerProvider> globalInvocationHandlerProviders;

	private ConstraintEnforcementService buildConstraintHandlerService(ObjectMapper mapper) {
		return new ConstraintEnforcementService(globalRunnableProviders, globalConsumerProviders,
				globalSubscriptionHandlerProviders, globalRequestHandlerProviders, globalMappingHandlerProviders,
				globalErrorMappingHandlerProviders, globalErrorHandlerProviders, globalFilterPredicateProviders,
				globalInvocationHandlerProviders, mapper);
	}

	@BeforeEach
	void beforeEach() {
		pdp                                = mock(PolicyDecisionPoint.class);
		pdpFactory                         = () -> pdp;
		globalRunnableProviders            = new LinkedList<>();
		globalConsumerProviders            = new LinkedList<>();
		globalSubscriptionHandlerProviders = new LinkedList<>();
		globalRequestHandlerProviders      = new LinkedList<>();
		globalMappingHandlerProviders      = new LinkedList<>();
		globalErrorMappingHandlerProviders = new LinkedList<>();
		globalErrorHandlerProviders        = new LinkedList<>();
		globalFilterPredicateProviders     = new LinkedList<>();
		globalInvocationHandlerProviders   = new LinkedList<>();

		var          mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
		mapper.registerModule(module);

		constraintHandlers       = buildConstraintHandlerService(mapper);
		constraintHandlerFactory = () -> constraintHandlers;

		authentication             = new UsernamePasswordAuthenticationToken("principal", "credentials");
		subscriptionBuilder        = new AuthorizationSubscriptionBuilderService(
				new DefaultMethodSecurityExpressionHandler(), mapper);
		subscriptionBuilderFactory = () -> subscriptionBuilder;
	}

	@Test
	void when_errorDuringBundleConmstruction_then_AccessDenied() {
		var constraintEnforcementService = mock(ConstraintEnforcementService.class);
		when(constraintEnforcementService.blockingPostEnforceBundleFor(any(), any()))
				.thenThrow(new IllegalStateException("TEST FAILURE"));
		var sut                  = new PostEnforcePolicyEnforcementPoint(pdpFactory, () -> constraintEnforcementService,
				subscriptionBuilderFactory);
		var methodInvocation     = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute            = new PostEnforceAttribute(null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThrows(AccessDeniedException.class,
				() -> sut.after(authentication, methodInvocation, attribute, originalReturnObject));
	}

	@Test
	void when_bundleIsNull_then_AccessDenied() {
		var constraintEnforcementService = mock(ConstraintEnforcementService.class);
		var sut                          = new PostEnforcePolicyEnforcementPoint(pdpFactory,
				() -> constraintEnforcementService, subscriptionBuilderFactory);
		var methodInvocation             = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute                    = new PostEnforceAttribute(null, null, null, null, null);
		var originalReturnObject         = ORIGINAL_RETURN_OBJECT;
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThrows(AccessDeniedException.class,
				() -> sut.after(authentication, methodInvocation, attribute, originalReturnObject));
	}

	@Test
	void when_AfterAndDecideIsPermit_then_ReturnOriginalReturnObject() {
		var sut                  = new PostEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation     = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute            = new PostEnforceAttribute(null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThat(sut.after(authentication, methodInvocation, attribute, originalReturnObject),
				is(originalReturnObject));
	}

	@Test
	void when_AfterAndDecideIsDeny_then_ThrowAccessDeniedException() {
		var sut                  = new PostEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation     = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute            = new PostEnforceAttribute(null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
		assertThrows(AccessDeniedException.class,
				() -> sut.after(authentication, methodInvocation, attribute, originalReturnObject));
	}

	@Test
	void when_AfterBeforeAndDecideNotApplicable_then_ThrowAccessDeniedException() {
		var sut                  = new PostEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation     = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute            = new PostEnforceAttribute(null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
		assertThrows(AccessDeniedException.class,
				() -> sut.after(authentication, methodInvocation, attribute, originalReturnObject));
	}

	@Test
	void when_AfterAndDecideIsIndeterminate_then_ThrowAccessDeniedException() {
		var sut                  = new PostEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation     = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute            = new PostEnforceAttribute(null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
		assertThrows(AccessDeniedException.class,
				() -> sut.after(authentication, methodInvocation, attribute, originalReturnObject));
	}

	@Test
	void when_AfterAndDecideIsEmpty_then_ThrowAccessDeniedException() {
		var sut                  = new PostEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation     = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute            = new PostEnforceAttribute(null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.empty());
		assertThrows(AccessDeniedException.class,
				() -> sut.after(authentication, methodInvocation, attribute, originalReturnObject));
	}

	@Test
	void when_AfterAndDecideIsPermitWithResource_then_ReturnTheReplacementObject() {
		var sut                  = new PostEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation     = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute            = new PostEnforceAttribute(null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		var expectedReturnObject = CHANGED_RETURN_OBJECT;
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode(expectedReturnObject))));
		assertThat(sut.after(authentication, methodInvocation, attribute, originalReturnObject),
				is(expectedReturnObject));
	}

	@Test
	void when_AfterAndDecideISPermitWithResourceOfBadType_then_ThrowAccessDeniedException() {
		var sut                  = new PostEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation     = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute            = new PostEnforceAttribute(null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		var replacementResource  = JSON.arrayNode();
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(replacementResource)));
		assertThrows(AccessDeniedException.class,
				() -> sut.after(authentication, methodInvocation, attribute, originalReturnObject));
	}

	@Test
	void when_AfterAndDecideISPermitWithResourceAndMethodReturnsOptional_then_ReturnTheReplacementObject() {
		var sut                  = new PostEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation     = MethodInvocationUtils.create(new TestClass(), "doSomethingOptional");
		var attribute            = new PostEnforceAttribute(null, null, null, null, null);
		var originalReturnObject = Optional.of(ORIGINAL_RETURN_OBJECT);
		var expectedReturnObject = Optional.of(CHANGED_RETURN_OBJECT);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode(CHANGED_RETURN_OBJECT))));
		assertThat(sut.after(authentication, methodInvocation, attribute, originalReturnObject),
				is(expectedReturnObject));
	}

	@Test
	void when_AfterAndDecideISPermitWithResourceAndMethodReturnsEmptyOptional_then_ReturnEmpty() {
		var sut                  = new PostEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation     = MethodInvocationUtils.create(new TestClass(), "doSomethingOptional");
		var attribute            = new PostEnforceAttribute(null, null, null, null, Object.class);
		var originalReturnObject = Optional.empty();
		var expectedReturnObject = Optional.empty();
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThat(sut.after(authentication, methodInvocation, attribute, originalReturnObject),
				is(expectedReturnObject));
	}

	@Test
	void when_AfterAndDecideIsPermitWithResourceAndMethodReturnsEmptyOptionalAndResourcePresent_then_ReturnResourceOptional() {
		var sut                  = new PostEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var methodInvocation     = MethodInvocationUtils.create(new TestClass(), "doSomethingOptional");
		var attribute            = new PostEnforceAttribute(null, null, null, null, Object.class);
		var originalReturnObject = Optional.empty();
		var expectedReturnObject = Optional.of(CHANGED_RETURN_OBJECT);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode(CHANGED_RETURN_OBJECT))));
		assertThat(sut.after(authentication, methodInvocation, attribute, originalReturnObject),
				is(expectedReturnObject));
	}

	static class TestClass {

		public String doSomething() {
			return "I did something!";
		}

		public Optional<String> doSomethingOptional() {
			return Optional.of("I did something!");
		}

	}

}
