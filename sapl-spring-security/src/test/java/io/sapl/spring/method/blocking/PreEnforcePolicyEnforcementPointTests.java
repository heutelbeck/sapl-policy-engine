package io.sapl.spring.method.blocking;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
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
import io.sapl.spring.method.metadata.PreEnforceAttribute;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import reactor.core.publisher.Flux;

class PreEnforcePolicyEnforcementPointTests {

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
		var sut = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		assertThat(sut, notNullValue());
	}

	@Test
	void whenBeforeAndDecideDeny_thenReturnFalse() {
		var sut = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	@Test
	void whenBeforeAndDecidePermit_thenReturnTrue() {
		var sut = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(constraintHandlers.handleForBlockingMethodInvocationOrAccessDenied(any(AuthorizationDecision.class)))
				.thenReturn(true);
		assertThat(sut.before(authentication, methodInvocation, attribute), is(true));
	}

	@Test
	void whenBeforeAndDecideNotApplicable_thenReturnFalse() {
		var sut = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	@Test
	void whenBeforeAndDecideIndeterminate_thenReturnFalse() {
		var sut = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	@Test
	void whenBeforeAndDecideEmpty_thenReturnFalse() {
		var sut = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.empty());
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	@Test
	void whenBeforeAndDecidePermitWithResource_thenReturnFalse() {
		var sut = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				objectMapperFactory, subscriptionBuilderFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute = new PreEnforceAttribute(null, null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.arrayNode())));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	static class TestClass {
		public void doSomething() {
		}
	}

}
