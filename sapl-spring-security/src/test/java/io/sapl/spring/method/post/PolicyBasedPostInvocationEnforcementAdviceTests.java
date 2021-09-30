package io.sapl.spring.method.post;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import io.sapl.spring.method.attributes.PostEnforceAttribute;
import io.sapl.spring.method.blocking.PolicyBasedPostInvocationEnforcementAdvice;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import reactor.core.publisher.Flux;

class PolicyBasedPostInvocationEnforcementAdviceTests {

	private static final String DO_SOMETHING = "doSomething";

	private static final String ORIGINAL_RETURN_OBJECT = "original return object";

	private static final String CHANGED_RETURN_OBJECT = "changed return object";

	public static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	ObjectFactory<PolicyDecisionPoint> pdpFactory;
	ObjectFactory<ReactiveConstraintEnforcementService> constraintHandlerFactory;
	ObjectFactory<ObjectMapper> objectMapperFactory;
	ObjectFactory<AuthorizationSubscriptionBuilderService> subscriptionBuilderFactory;

	PolicyDecisionPoint pdp;
	ReactiveConstraintEnforcementService constraintHandlers;
	Authentication authentication;
	AuthorizationSubscriptionBuilderService subscriptionBuilder;

	@BeforeEach
	void beforeEach() {
		pdp = mock(PolicyDecisionPoint.class);
		pdpFactory = () -> pdp;
		constraintHandlers = mock(ReactiveConstraintEnforcementService.class);
		when(constraintHandlers.enforceConstraintsOnResourceAccessPoint(any(), any())).thenCallRealMethod();
		when(constraintHandlers.handleAfterBlockingMethodInvocation(any(), any(), any())).thenCallRealMethod();
		when(constraintHandlers.handleForBlockingMethodInvocationOrAccessDenied(any())).thenCallRealMethod();
		constraintHandlerFactory = () -> constraintHandlers;
		var mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
		mapper.registerModule(module);
		objectMapperFactory = () -> mapper;
		authentication = new UsernamePasswordAuthenticationToken("principal", "credentials");
		subscriptionBuilder = new AuthorizationSubscriptionBuilderService(new DefaultMethodSecurityExpressionHandler(),
				objectMapperFactory);
		subscriptionBuilderFactory = () -> subscriptionBuilder;
	}

	@Test
	void whenCreated_thenNotNull() {
		var sut = new PolicyBasedPostInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		assertThat(sut, notNullValue());
	}

	@Test
	void whenAfterAndDecideIsPermit_thenReturnOriginalReturnObject() {
		var sut = new PolicyBasedPostInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute = new PostEnforceAttribute((String) null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThat(sut.after(authentication, methodInvocation, attribute, originalReturnObject),
				is(originalReturnObject));
	}

	@Test
	void whenAfterAndDecideIsDeny_thenThrowAccessDeniedException() {
		var sut = new PolicyBasedPostInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute = new PostEnforceAttribute((String) null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
		assertThrows(AccessDeniedException.class,
				() -> sut.after(authentication, methodInvocation, attribute, originalReturnObject));
	}

	@Test
	void whenAfterBeforeAndDecideNotApplicable_thenThrowAccessDeniedException() {
		var sut = new PolicyBasedPostInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute = new PostEnforceAttribute((String) null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
		assertThrows(AccessDeniedException.class,
				() -> sut.after(authentication, methodInvocation, attribute, originalReturnObject));
	}

	@Test
	void whenAfterAndDecideIsIndeterminate_thenThrowAccessDeniedException() {
		var sut = new PolicyBasedPostInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute = new PostEnforceAttribute((String) null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
		assertThrows(AccessDeniedException.class,
				() -> sut.after(authentication, methodInvocation, attribute, originalReturnObject));
	}

	@Test
	void whenAfterAndDecideIsEmpty_thenThrowAccessDeniedException() {
		var sut = new PolicyBasedPostInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute = new PostEnforceAttribute((String) null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.empty());
		assertThrows(AccessDeniedException.class,
				() -> sut.after(authentication, methodInvocation, attribute, originalReturnObject));
	}

	@Test
	void whenAfterAndDecideISPermitWithResource_thenReturnTheReplacementObject() {
		var sut = new PolicyBasedPostInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute = new PostEnforceAttribute((String) null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		var expectedReturnObject = CHANGED_RETURN_OBJECT;
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode(expectedReturnObject))));
		assertThat(sut.after(authentication, methodInvocation, attribute, originalReturnObject),
				is(expectedReturnObject));
	}

	@Test
	void whenAfterAndDecideISPermitWithResourceOfBadType_thenThrowAccessDeniedException() {
		var sut = new PolicyBasedPostInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
		var attribute = new PostEnforceAttribute((String) null, null, null, null, null);
		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
		var replacementResource = JSON.arrayNode();
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(replacementResource)));
		assertThrows(AccessDeniedException.class,
				() -> sut.after(authentication, methodInvocation, attribute, originalReturnObject));
	}

//	@Test
//	void whenBeforeAndDecidePermitAndObligationsFail_thenThrowAccessDeniedException() {
//		var sut = new PolicyBasedPostInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
//				objectMapperFactory);
//		var methodInvocation = MethodInvocationUtils.create(new TestClass(), DO_SOMETHING);
//		var attribute = new PostEnforceAttribute((String) null, null, null, null);
//		var originalReturnObject = ORIGINAL_RETURN_OBJECT;
//		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
//		doThrow(new AccessDeniedException("FAILED OBLIGATION")).when(constraintHandlers)
//				.handleObligations(any(AuthorizationDecision.class));
//		assertThrows(AccessDeniedException.class,
//				() -> sut.after(authentication, methodInvocation, attribute, originalReturnObject));
//	}

	@Test
	void whenAfterAndDecideISPermitWithResourceAndMethodReturnsOptional_thenReturnTheReplacementObject() {
		var sut = new PolicyBasedPostInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomethingOptional");
		var attribute = new PostEnforceAttribute((String) null, null, null, null, null);
		var originalReturnObject = Optional.of(ORIGINAL_RETURN_OBJECT);
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
