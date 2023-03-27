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
package io.sapl.spring.method.reactive;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.servlet.http.HttpServletRequest;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.expression.ExpressionParser;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.util.MethodInvocationUtils;

import com.fasterxml.jackson.databind.JsonNode;
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
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.PostEnforceAttribute;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.method.metadata.SaplAttributeFactory;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PostEnforcePolicyEnforcementPointTests {

	private final static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private AuthorizationSubscriptionBuilderService subscriptionBuilderService;

	private MethodInvocation invocation;

	private ObjectMapper mapper;

	private Mono<Integer> resourceAccessPoint;

	private SaplAttributeFactory attributeFactory;

	private PostEnforceAttribute defaultAttribute;

	private PolicyDecisionPoint pdp;

	List<RunnableConstraintHandlerProvider> globalRunnableProviders;

	List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders;

	List<SubscriptionHandlerProvider> globalSubscriptionHandlerProviders;

	List<RequestHandlerProvider> globalRequestHandlerProviders;

	List<MappingConstraintHandlerProvider<?>> globalMappingHandlerProviders;

	List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders;

	List<ErrorHandlerProvider> globalErrorHandlerProviders;

	List<FilterPredicateConstraintHandlerProvider> globalFilterPredicateProviders;

	List<MethodInvocationConstraintHandlerProvider> globalInvocationHandlerProviders;

	@BeforeEach
	void beforeEach() {
		mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
		module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
		mapper.registerModule(module);
		subscriptionBuilderService = new AuthorizationSubscriptionBuilderService(
				new DefaultMethodSecurityExpressionHandler(), mapper);
		var testClass = new TestClass();
		resourceAccessPoint = testClass.publicInteger();
		invocation          = MethodInvocationUtils.createFromClass(testClass, TestClass.class, "publicInteger", null,
				null);

		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser  = mock(ExpressionParser.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		attributeFactory                   = new SaplAttributeFactory(handler);
		defaultAttribute                   = (PostEnforceAttribute) postEnforceAttributeFrom("'the subject'",
				"'the action'", "returnObject", "'the environment'", Integer.class);
		pdp                                = mock(PolicyDecisionPoint.class);
		globalRunnableProviders            = new LinkedList<>();
		globalConsumerProviders            = new LinkedList<>();
		globalSubscriptionHandlerProviders = new LinkedList<>();
		globalRequestHandlerProviders      = new LinkedList<>();
		globalMappingHandlerProviders      = new LinkedList<>();
		globalErrorMappingHandlerProviders = new LinkedList<>();
		globalErrorHandlerProviders        = new LinkedList<>();
		globalFilterPredicateProviders     = new LinkedList<>();
		globalInvocationHandlerProviders   = new LinkedList<>();
	}

	private ConstraintEnforcementService buildConstraintHandlerService() {
		return new ConstraintEnforcementService(globalRunnableProviders, globalConsumerProviders,
				globalSubscriptionHandlerProviders, globalRequestHandlerProviders, globalMappingHandlerProviders,
				globalErrorMappingHandlerProviders, globalErrorHandlerProviders, globalFilterPredicateProviders,
				globalInvocationHandlerProviders, mapper);
	}

	@Test
	void when_Deny_ErrorIsRaisedAndStreamCompleteEvenWithOnErrorContinue() {
		var constraintsService = buildConstraintHandlerService();
		var decisions          = Flux.just(AuthorizationDecision.DENY);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError       = errorConsumer();
		var sut             = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, subscriptionBuilderService)
				.postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
				.expectError(AccessDeniedException.class).verify();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(1)).accept(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_Permit_AccessIsGranted() {
		var constraintsService = buildConstraintHandlerService();
		var decisions          = Flux.just(AuthorizationDecision.PERMIT);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
		var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, subscriptionBuilderService)
				.postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);
		StepVerifier.create((Mono<Integer>) sut).expectNext(420).verifyComplete();
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_PermitWithObligations_and_allObligationsSucceed_then_AccessIsGranted() {
		var handler = spy(new SubscriptionHandlerProvider() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Consumer<Subscription> getHandler(JsonNode constraint) {
				return this::accept;
			}

			public void accept(Subscription s) {
				// NOOP
			}

		});
		this.globalSubscriptionHandlerProviders.add(handler);
		var constraintsService = buildConstraintHandlerService();
		var decisions          = decisionFluxOnePermitWithObligation();
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
		var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, subscriptionBuilderService)
				.postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);

		StepVerifier.create((Mono<Integer>) sut).expectNext(420).verifyComplete();

		verify(handler, times(1)).accept(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_PermitWithObligations_then_ObligationsAreApplied_and_AccessIsGranted() {
		var handler = spy(new MappingConstraintHandlerProvider<Integer>() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Class<Integer> getSupportedType() {
				return Integer.class;
			}

			@Override
			public Function<Integer, Integer> getHandler(JsonNode constraint) {
				return s -> s + constraint.asInt();
			}
		});
		this.globalMappingHandlerProviders.add(handler);
		var constraintsService = buildConstraintHandlerService();
		var decisions          = decisionFluxOnePermitWithObligation();
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
		var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, subscriptionBuilderService)
				.postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);

		StepVerifier.create((Mono<Integer>) sut).expectNext(10420).verifyComplete();

		verify(handler, times(1)).getHandler(any());
	}

	@Test
	void when_PermitWithObligations_and_oneObligationFails_thenAccessIsDeniedOnFailure() {
		var handler = spy(new MappingConstraintHandlerProvider<Integer>() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Class<Integer> getSupportedType() {
				return Integer.class;
			}

			@Override
			public Function<Integer, Integer> getHandler(JsonNode constraint) {
				return s -> {
					throw new IllegalArgumentException("I FAILED TO OBLIGE");
				};
			}
		});
		this.globalMappingHandlerProviders.add(handler);
		var constraintsService = buildConstraintHandlerService();
		var decisions          = decisionFluxOnePermitWithObligation();
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError       = errorConsumer();
		var sut             = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, subscriptionBuilderService)
				.postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
				.expectError(AccessDeniedException.class).verify();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(1)).accept(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_PermitWithResource_thenAccessIsGrantedAndOnlyResourceFromPolicyInStream() {
		var handler = spy(new MappingConstraintHandlerProvider<Integer>() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Class<Integer> getSupportedType() {
				return Integer.class;
			}

			@Override
			public Function<Integer, Integer> getHandler(JsonNode constraint) {
				return s -> s + constraint.asInt();
			}
		});
		this.globalMappingHandlerProviders.add(handler);
		var constraintsService = buildConstraintHandlerService();
		var obligations        = JSON.arrayNode();
		obligations.add(JSON.numberNode(-69));
		var decisions       = Flux
				.just(AuthorizationDecision.PERMIT.withObligations(obligations).withResource(JSON.numberNode(69)));
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError       = errorConsumer();

		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
		var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, subscriptionBuilderService)
				.postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);

		StepVerifier.create((Mono<Integer>) sut).expectNext(0).verifyComplete();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(0)).accept(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_PermitWithResource_and_typeMismatch_thenAccessIsGrantedAndOnlyResourceFromPolicyInStream() {

		var constraintsService = buildConstraintHandlerService();
		var decisions          = Flux
				.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode("I CAUSE A TYPE MISMATCH")));
		var onErrorContinue    = errorAndCauseConsumer();
		var doOnError          = errorConsumer();

		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
		var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, subscriptionBuilderService)
				.postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);

		StepVerifier.create((Mono<Integer>) sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
				.expectError(AccessDeniedException.class).verify();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(1)).accept(any());
	}

	public Flux<AuthorizationDecision> decisionFluxOnePermitWithObligation() {
		var plus10000  = JSON.numberNode(10000L);
		var obligation = JSON.arrayNode();
		obligation.add(plus10000);
		return Flux.just(AuthorizationDecision.PERMIT.withObligations(obligation));
	}

	@SuppressWarnings("unchecked")
	private BiConsumer<Throwable, Object> errorAndCauseConsumer() {
		return (BiConsumer<Throwable, Object>) mock(BiConsumer.class);
	}

	@SuppressWarnings("unchecked")
	private Consumer<Throwable> errorConsumer() {
		return (Consumer<Throwable>) mock(Consumer.class);
	}

	public static class TestClass {

		public Mono<Integer> publicInteger() {
			return Mono.just(420);
		}

	}

	@Getter
	public static class BadForJackson {

		private String bad;

	}

	private SaplAttribute postEnforceAttributeFrom(String subject, String action, String resource, String environment,
			Class<?> genericsType) {
		return attributeFactory.attributeFrom(postEnforceFrom(subject, action, resource, environment, genericsType));
	}

	private PostEnforce postEnforceFrom(String subject, String action, String resource, String environment,
			Class<?> genericsType) {
		return new PostEnforce() {
			@Override
			public Class<? extends Annotation> annotationType() {
				return PostEnforce.class;
			}

			@Override
			public String subject() {
				return subject;
			}

			@Override
			public String action() {
				return action;
			}

			@Override
			public String resource() {
				return resource;
			}

			@Override
			public String environment() {
				return environment;
			}

			@Override
			public Class<?> genericsType() {
				return genericsType;
			}
		};
	}

}
