/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.expression.method.ExpressionBasedAnnotationAttributeFactory;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.method.DelegatingMethodSecurityMetadataSource;
import org.springframework.security.access.method.MethodSecurityMetadataSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreInvocationAttribute;
import org.springframework.security.access.prepost.PrePostAnnotationSecurityMetadataSource;
import org.springframework.security.util.MethodInvocationUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.EnforceDropWhileDenied;
import io.sapl.spring.method.metadata.EnforceRecoverableIfDenied;
import io.sapl.spring.method.metadata.EnforceTillDenied;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.metadata.PreEnforceAttribute;
import io.sapl.spring.method.metadata.SaplAttributeFactory;
import io.sapl.spring.method.metadata.SaplMethodSecurityMetadataSource;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class ReactiveSaplMethodInterceptorTests {

	private MethodInterceptor springSecurityMethodInterceptor;
	private MethodSecurityMetadataSource metadataSource;
	private MethodSecurityExpressionHandler handler;
	private PolicyDecisionPoint pdp;
	private ConstraintEnforcementService constraintHandlerService;
	private ObjectMapper mapper;
	private AuthorizationSubscriptionBuilderService subscriptionBuilder;
	private PreEnforcePolicyEnforcementPoint preEnforcePolicyEnforcementPoint;
	private PostEnforcePolicyEnforcementPoint postEnforcePolicyEnforcementPoint;
	private ReactiveSaplMethodInterceptor defaultSut;

	@BeforeEach
	void beforeEach() {
		springSecurityMethodInterceptor = mock(MethodInterceptor.class);
		handler = mock(MethodSecurityExpressionHandler.class);

		PrePostAnnotationSecurityMetadataSource prePostSource = new PrePostAnnotationSecurityMetadataSource(
				new ExpressionBasedAnnotationAttributeFactory(handler));
		SaplMethodSecurityMetadataSource sapl = new SaplMethodSecurityMetadataSource(new SaplAttributeFactory(handler));
		metadataSource = new DelegatingMethodSecurityMetadataSource(Arrays.asList(sapl, prePostSource));

		pdp = mock(PolicyDecisionPoint.class);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		constraintHandlerService = mock(ConstraintEnforcementService.class);
		mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
		module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
		mapper.registerModule(module);
		subscriptionBuilder = mock(AuthorizationSubscriptionBuilderService.class);
		when(subscriptionBuilder.reactiveConstructAuthorizationSubscription(any(MethodInvocation.class), any()))
				.thenReturn(Mono.just(AuthorizationSubscription.of("the subject", "the action", "the resource")));
		preEnforcePolicyEnforcementPoint = mock(PreEnforcePolicyEnforcementPoint.class);
		postEnforcePolicyEnforcementPoint = mock(PostEnforcePolicyEnforcementPoint.class);
		defaultSut = new ReactiveSaplMethodInterceptor(springSecurityMethodInterceptor, metadataSource, handler, pdp,
				constraintHandlerService, mapper, subscriptionBuilder, preEnforcePolicyEnforcementPoint,
				postEnforcePolicyEnforcementPoint);
	}

	@Test
	void when_noSaplAnnotations_then_delegatedToSpringImplementation() throws Throwable {
		class TestClass {
			@SuppressWarnings("unused")
			public Mono<Integer> monoInteger() {
				return Mono.just(1);
			}
		}
		var invocation = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class, "monoInteger", null,
				null);
		assertThat(defaultSut.invoke(invocation), is(nullValue()));
		verify(springSecurityMethodInterceptor, times(1)).invoke(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_onlyPreEnforce_then_preEnforcePEPIsCalled() throws Throwable {
		class TestClass {
			@PreEnforce
			public Mono<Integer> monoInteger() {
				return Mono.just(1);
			}
		}
		var testInstance = new TestClass();
		var invocation = MockMethodInvocation.of(testInstance, TestClass.class, "monoInteger",
				() -> testInstance.monoInteger(), null, null);
		when(preEnforcePolicyEnforcementPoint.enforce(any(), any(), any())).thenReturn(Flux.just(2));
		var actual = (Mono<Integer>) defaultSut.invoke(invocation);
		StepVerifier.create(actual).expectNext(2).verifyComplete();
		verify(preEnforcePolicyEnforcementPoint, times(1)).enforce(any(), any(), any());
	}

	@Test
	void when_nonReactiveType_then_fail() throws Throwable {
		class TestClass {
			@PreEnforce
			public int integer() {
				return 1;
			}
		}
		var testInstance = new TestClass();
		var invocation = MockMethodInvocation.of(testInstance, TestClass.class, "integer", () -> testInstance.integer(),
				null, null);
		assertThrows(IllegalStateException.class, () -> defaultSut.invoke(invocation));
		verify(preEnforcePolicyEnforcementPoint, times(0)).enforce(any(), any(), any());
	}

	@Test
	void when_postEnforceOnFlux_then_fail() throws Throwable {
		class TestClass {
			@PostEnforce
			public Flux<Integer> fluxIntegrer() {
				return Flux.just(1);
			}
		}
		var testInstance = new TestClass();
		var invocation = MockMethodInvocation.of(testInstance, TestClass.class, "fluxIntegrer",
				() -> testInstance.fluxIntegrer(), null, null);
		assertThrows(IllegalStateException.class, () -> defaultSut.invoke(invocation));
		verify(preEnforcePolicyEnforcementPoint, times(0)).enforce(any(), any(), any());
	}

	@Test
	void when_saplAndSpringAnnotationsPresent_then_thisDoesNotFailBecauseDelegationgSourceDoesOnlyReturnOne()
			throws Throwable {

		// This test should fail if at one point in time the delegating source changes
		// behavior and returns attributes from more than one source

		class TestClass {
			@PreAuthorize(value = "")
			@PreEnforce
			public Flux<Integer> fluxInteger() {
				return Flux.just(1);
			}
		}
		var testInstance = new TestClass();
		MethodInvocation invocation = MockMethodInvocation.of(testInstance, TestClass.class, "fluxInteger",
				() -> testInstance.fluxInteger(), null, null);

		Flux<Object> expected = Flux.just(2);
		when(preEnforcePolicyEnforcementPoint.enforce(any(), any(), any())).thenReturn(expected);
		assertThat(defaultSut.invoke(invocation), is(expected));
		verify(preEnforcePolicyEnforcementPoint, times(1)).enforce(any(), any(), any());
	}

	@Test
	void when_prePostIsCombinedWithContiniousEnforce_then_fail() throws Throwable {

		class TestClass {
			@PreEnforce
			@EnforceTillDenied
			public Flux<Integer> fluxInteger() {
				return Flux.just(1);
			}
		}

		var testInstance = new TestClass();
		MethodInvocation invocation = MockMethodInvocation.of(testInstance, TestClass.class, "fluxInteger",
				() -> testInstance.fluxInteger(), null, null);

		Flux<Object> expected = Flux.just(2);
		when(preEnforcePolicyEnforcementPoint.enforce(any(), any(), any())).thenReturn(expected);
		assertThrows(IllegalStateException.class, () -> defaultSut.invoke(invocation));
		verify(preEnforcePolicyEnforcementPoint, times(0)).enforce(any(), any(), any());
	}

	@Test
	void when_moreThanOneContiniousAnnotation_then_fail() throws Throwable {

		class TestClass {
			@EnforceTillDenied
			@EnforceDropWhileDenied
			@EnforceRecoverableIfDenied
			public Flux<Integer> fluxInteger() {
				return Flux.just(1);
			}
		}

		var testInstance = new TestClass();
		MethodInvocation invocation = MockMethodInvocation.of(testInstance, TestClass.class, "fluxInteger",
				() -> testInstance.fluxInteger(), null, null);

		Flux<Object> expected = Flux.just(2);
		when(preEnforcePolicyEnforcementPoint.enforce(any(), any(), any())).thenReturn(expected);
		assertThrows(IllegalStateException.class, () -> defaultSut.invoke(invocation));
		verify(preEnforcePolicyEnforcementPoint, times(0)).enforce(any(), any(), any());
	}

	@Test
	void when_saplAndSpringAnnotationsPresent_then_failsIfBothAreReturnedByMetadataSource() throws Throwable {

		var conflictingAttributes = List.of(mock(PreEnforceAttribute.class), mock(PreInvocationAttribute.class));
		var mockMetadataSource = mock(MethodSecurityMetadataSource.class);
		when(mockMetadataSource.getAttributes(any(), any())).thenReturn(conflictingAttributes);

		var sut = new ReactiveSaplMethodInterceptor(springSecurityMethodInterceptor, mockMetadataSource, handler, pdp,
				constraintHandlerService, mapper, subscriptionBuilder, preEnforcePolicyEnforcementPoint,
				postEnforcePolicyEnforcementPoint);

		class TestClass {
			@PreAuthorize(value = "")
			@PreEnforce
			public Flux<Integer> fluxInteger() {
				return Flux.just(1);
			}
		}
		var testInstance = new TestClass();
		var invocation = MockMethodInvocation.of(testInstance, TestClass.class, "fluxInteger",
				() -> testInstance.fluxInteger(), null, null);
		assertThrows(IllegalStateException.class, () -> sut.invoke(invocation));
		verify(preEnforcePolicyEnforcementPoint, times(0)).enforce(any(), any(), any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_onlyPostEnforce_then_postEnforcePepIsCalled() throws Throwable {
		class TestClass {
			@PostEnforce
			public Mono<Integer> monoInteger() {
				return Mono.just(1);
			}
		}
		var testInstance = new TestClass();
		var invocation = MockMethodInvocation.of(testInstance, TestClass.class, "monoInteger",
				() -> testInstance.monoInteger(), null, null);
		Mono<?> expected = Mono.just(4);
		when(postEnforcePolicyEnforcementPoint.postEnforceOneDecisionOnResourceAccessPoint(any(), any(), any()))
				.thenAnswer(__ -> expected);
		var actual = defaultSut.invoke(invocation);
		assertThat(actual, is(expected));
		StepVerifier.create((Mono<Integer>) actual).expectNext(4).verifyComplete();
		verify(postEnforcePolicyEnforcementPoint, times(1)).postEnforceOneDecisionOnResourceAccessPoint(any(), any(),
				any());
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void when_onlyEnforceTillDenied_then_enforceTillDeniedPEPIsCalled() throws Throwable {
		class TestClass {
			@EnforceTillDenied
			public Flux<Integer> fluxInteger() {
				return Flux.just(1);
			}
		}
		var testInstance = new TestClass();
		var invocation = MockMethodInvocation.of(testInstance, TestClass.class, "fluxInteger",
				() -> testInstance.fluxInteger(), null, null);
		Flux<Object> expected = Flux.just(2);

		try (MockedStatic<EnforceTillDeniedPolicyEnforcementPoint> mockPEP = Mockito
				.mockStatic(EnforceTillDeniedPolicyEnforcementPoint.class)) {
			mockPEP.when(() -> EnforceTillDeniedPolicyEnforcementPoint.of(any(), any(), any(), any()))
					.thenReturn(expected);
			var actual = defaultSut.invoke(invocation);
			assertThat(actual, is(expected));
			StepVerifier.create((Flux<Integer>) actual).expectNext(2).verifyComplete();
			mockPEP.verify(() -> EnforceTillDeniedPolicyEnforcementPoint.of(any(), any(), any(), any()), times(1));
		}
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void when_onlyEnforceDropWhileDenied_then_enforceDropWhileDeniedPEPIsCalled() throws Throwable {
		class TestClass {
			@EnforceDropWhileDenied
			public Flux<Integer> fluxInteger() {
				return Flux.just(1);
			}
		}
		var testInstance = new TestClass();
		var invocation = MockMethodInvocation.of(testInstance, TestClass.class, "fluxInteger",
				() -> testInstance.fluxInteger(), null, null);
		Flux<Object> expected = Flux.just(2);

		try (MockedStatic<EnforceDropWhileDeniedPolicyEnforcementPoint> mockPEP = Mockito
				.mockStatic(EnforceDropWhileDeniedPolicyEnforcementPoint.class)) {
			mockPEP.when(() -> EnforceDropWhileDeniedPolicyEnforcementPoint.of(any(), any(), any(), any()))
					.thenReturn(expected);
			var actual = defaultSut.invoke(invocation);
			assertThat(actual, is(expected));
			StepVerifier.create((Flux<Integer>) actual).expectNext(2).verifyComplete();
			mockPEP.verify(() -> EnforceDropWhileDeniedPolicyEnforcementPoint.of(any(), any(), any(), any()), times(1));
		}
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void when_onlyEnforceRevocerableIfDenied_then_enforceRecoverableIfDeniedPEPIsCalled() throws Throwable {
		class TestClass {
			@EnforceRecoverableIfDenied
			public Flux<Integer> fluxInteger() {
				return Flux.just(1);
			}
		}
		var testInstance = new TestClass();
		var invocation = MockMethodInvocation.of(testInstance, TestClass.class, "fluxInteger",
				() -> testInstance.fluxInteger(), null, null);
		Flux<Object> expected = Flux.just(2);

		try (MockedStatic<EnforceRecoverableIfDeniedPolicyEnforcementPoint> mockPEP = Mockito
				.mockStatic(EnforceRecoverableIfDeniedPolicyEnforcementPoint.class)) {
			mockPEP.when(() -> EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(any(), any(), any(), any()))
					.thenReturn(expected);
			var actual = defaultSut.invoke(invocation);
			assertThat(actual, is(expected));
			StepVerifier.create((Flux<Integer>) actual).expectNext(2).verifyComplete();
			mockPEP.verify(() -> EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(any(), any(), any(), any()),
					times(1));
		}
	}

}
