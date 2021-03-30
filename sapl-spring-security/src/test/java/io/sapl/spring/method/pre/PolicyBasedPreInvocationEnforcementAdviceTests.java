package io.sapl.spring.method.pre;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.util.MethodInvocationUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintHandlerService;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import reactor.core.publisher.Flux;

class PolicyBasedPreInvocationEnforcementAdviceTests {

	public static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	ObjectFactory<PolicyDecisionPoint> pdpFactory;
	ObjectFactory<ConstraintHandlerService> constraintHandlerFactory;
	ObjectFactory<ObjectMapper> objectMapperFactory;

	PolicyDecisionPoint pdp;
	ConstraintHandlerService constraintHandlers;
	Authentication authentication;

	@BeforeEach
	void beforeEach() {
		pdp = mock(PolicyDecisionPoint.class);
		pdpFactory = () -> pdp;
		constraintHandlers = mock(ConstraintHandlerService.class);
		constraintHandlerFactory = () -> constraintHandlers;
		var mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
		mapper.registerModule(module);
		objectMapperFactory = () -> mapper;
		authentication = new UsernamePasswordAuthenticationToken("principal", "credentials");
	}

	@Test
	void whenCreated_thenNotNull() {
		var sut = new PolicyBasedPreInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory);
		assertThat(sut, notNullValue());
	}

	@Test
	void whenBeforeAndDecideDeny_thenReturnFalse() {
		var sut = new PolicyBasedPreInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute((String) null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	@Test
	void whenBeforeAndDecidePermit_thenReturnTrue() {
		var sut = new PolicyBasedPreInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute((String) null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(true));
	}

	@Test
	void whenBeforeAndDecideNotApplicable_thenReturnFalse() {
		var sut = new PolicyBasedPreInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute((String) null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	@Test
	void whenBeforeAndDecideIndeterminate_thenReturnFalse() {
		var sut = new PolicyBasedPreInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute((String) null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	@Test
	void whenBeforeAndDecideEmpty_thenReturnFalse() {
		var sut = new PolicyBasedPreInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute((String) null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.empty());
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	@Test
	void whenBeforeAndDecidePermitWithResource_thenReturnFalse() {
		var sut = new PolicyBasedPreInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute((String) null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.arrayNode())));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	@Test
	void whenBeforeAndDecidePermitAndObligationsFail_thenReturnFalse() {
		var sut = new PolicyBasedPreInvocationEnforcementAdvice(pdpFactory, constraintHandlerFactory,
				objectMapperFactory);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute((String) null, null, null, null);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		doThrow(new AccessDeniedException("FAILED OBLIGATION")).when(constraintHandlers)
				.handleObligations(any(AuthorizationDecision.class));
		assertThat(sut.before(authentication, methodInvocation, attribute), is(false));
	}

	static class TestClass {
		public void doSomething() {
		}
	}

}
