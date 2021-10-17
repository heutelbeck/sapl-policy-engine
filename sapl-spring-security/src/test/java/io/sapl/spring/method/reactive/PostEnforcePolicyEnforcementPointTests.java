package io.sapl.spring.method.reactive;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
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
import io.sapl.spring.constraints.AbstractConstraintHandler;
import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.PostEnforceAttribute;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.method.metadata.SaplAttributeFactory;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class PostEnforcePolicyEnforcementPointTests {
	private final static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private AuthorizationSubscriptionBuilderService subscriptionBuilderService;
	private MethodInvocation invocation;
	private ObjectMapper mapper;
	private Mono<Integer> resourceAccessPoint;
	private SaplAttributeFactory attributeFactory;
	private PostEnforceAttribute defaultAttribute;
	private PolicyDecisionPoint pdp;

	@BeforeEach
	void beforeEach() {
		mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
		module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
		mapper.registerModule(module);
		subscriptionBuilderService = new AuthorizationSubscriptionBuilderService(
				new DefaultMethodSecurityExpressionHandler(), () -> mapper);
		var testClass = new TestClass();
		resourceAccessPoint = testClass.publicInteger();
		invocation = MethodInvocationUtils.createFromClass(testClass, TestClass.class, "publicInteger", null, null);

		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser = mock(ExpressionParser.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		attributeFactory = new SaplAttributeFactory(handler);
		defaultAttribute = (PostEnforceAttribute) postEnforceAttributeFrom("'the subject'", "'the action'",
				"returnObject", "'the envirionment'", Integer.class);
		pdp = mock(PolicyDecisionPoint.class);
	}

	@Test
	void when_Deny_ErrorIsRaisedAndStreamCompleteEvenWithOnErrorContinue() {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = Flux.just(AuthorizationDecision.DENY);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, mapper, subscriptionBuilderService)
				.postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
				.expectError(AccessDeniedException.class).verify();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(1)).accept(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_Permit_AccessIsGranted() {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
		var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, mapper, subscriptionBuilderService)
				.postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);
		StepVerifier.create((Mono<Integer>) sut).expectNext(420).verifyComplete();
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_PermitWithObligations_and_allObligationsSucceed_then_AccessIsGranted() {
		var handler = spy(new AbstractConstraintHandler(1) {
			@Override
			public Consumer<Subscription> onSubscribe(JsonNode constraint) {
				return s -> {
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var decisions = decisionFluxOnePermitWithObligation();
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
		var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, mapper, subscriptionBuilderService)
				.postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);

		StepVerifier.create((Mono<Integer>) sut).expectNext(420).verifyComplete();

		verify(handler, times(1)).onSubscribe(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_PermitWithObligations_then_ObligationsAreApplied_and_AccessIsGranted() {
		var handler = spy(new AbstractConstraintHandler(1) {
			@Override
			public Function<Integer, Integer> onNextMap(JsonNode constraint) {
				return s -> s + constraint.asInt();
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var decisions = decisionFluxOnePermitWithObligation();
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
		var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, mapper, subscriptionBuilderService)
				.postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);

		StepVerifier.create((Mono<Integer>) sut).expectNext(10420).verifyComplete();

		verify(handler, times(1)).onSubscribe(any());
	}

	@Test
	void when_PermitWithObligations_and_oneObligationFails_thenAccessIsDeniedOnFailure() {
		var handler = spy(new AbstractConstraintHandler(1) {
			@Override
			@SuppressWarnings("unchecked")
			public Function<Integer, Integer> onNextMap(JsonNode constraint) {
				return s -> {
					throw new IllegalArgumentException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}
		});
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));

		var decisions = decisionFluxOnePermitWithObligation();
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, mapper, subscriptionBuilderService)
				.postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
				.expectError(AccessDeniedException.class).verify();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(1)).accept(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_PermitWithResource_thenAccessIsGrantedAndOnlyResourceFromPolicyInStream() {
		var handler = spy(new AbstractConstraintHandler(1) {
			@Override
			public Function<Integer, Integer> onNextMap(JsonNode constraint) {
				return s -> s + constraint.asInt();
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}
		});
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var mapper = new ObjectMapper();
		var obligations = JSON.arrayNode();
		obligations.add(JSON.numberNode(-69));
		var decisions = Flux
				.just(AuthorizationDecision.PERMIT.withObligations(obligations).withResource(JSON.numberNode(69)));
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();

		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
		var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, mapper, subscriptionBuilderService)
				.postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);

		StepVerifier.create((Mono<Integer>) sut).expectNext(0).verifyComplete();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(0)).accept(any());
		verify(handler, times(1)).onNextMap(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_PermitWithResource_and_typeMismatch_thenAccessIsGrantedAndOnlyResourceFromPolicyInStream() {

		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode("I CAUSE A TYPE MISMATCH")));
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();

		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
		var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, mapper, subscriptionBuilderService)
				.postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);

		StepVerifier.create((Mono<Integer>) sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
				.expectError(AccessDeniedException.class).verify();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(1)).accept(any());
	}

	public Flux<AuthorizationDecision> decisionFluxOnePermitWithObligation() {
		var plus10000 = JSON.numberNode(10000L);
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

	public static class BadForJackson {
		@SuppressWarnings("unused")
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
