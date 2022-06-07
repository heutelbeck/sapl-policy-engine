package io.sapl.spring.constraints.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.SaplMethodSecurityConfiguration;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.spring.constraints.it.PostEnforcementIntegrationTests.Application;
import io.sapl.spring.constraints.it.PostEnforcementIntegrationTests.ConstraintHandlerOne;
import io.sapl.spring.constraints.it.PostEnforcementIntegrationTests.ConstraintHandlerTwo;
import io.sapl.spring.constraints.it.PostEnforcementIntegrationTests.FailingConstraintHandler;
import io.sapl.spring.constraints.it.PostEnforcementIntegrationTests.MethodSecurityConfiguration;
import io.sapl.spring.constraints.it.PostEnforcementIntegrationTests.TestService;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import reactor.core.publisher.Flux;

@SpringBootTest(classes = { Application.class, TestService.class, MethodSecurityConfiguration.class,
		ConstraintHandlerOne.class, ConstraintHandlerTwo.class, FailingConstraintHandler.class })
public class PostEnforcementIntegrationTests {
	private static final String UNKNOWN_CONSTRAINT = "unknown constraint";
	private static final String FAILING_CONSTRAINT = "failing constraint";
	private static final String KNOWN_CONSTRAINT   = "known constraint";
	private static final String USER               = "user";

	public static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@MockBean
	PolicyDecisionPoint pdp;

	@SpyBean
	ConstraintHandlerOne constraintHandlerOne;

	@SpyBean
	ConstraintHandlerTwo constraintHandlerTwo;

	@SpyBean
	TestService service;

	@SpringBootApplication
	static class Application {
		public static void main(String... args) {
			SpringApplication.run(Application.class, args);
		}
	}

	@TestConfiguration
	@EnableGlobalMethodSecurity(prePostEnabled = true)
	static class MethodSecurityConfiguration extends SaplMethodSecurityConfiguration {

		public MethodSecurityConfiguration(ObjectFactory<PolicyDecisionPoint> pdpFactory,
				ObjectFactory<ConstraintEnforcementService> constraintHandlerFactory,
				ObjectFactory<ObjectMapper> objectMapperFactory,
				ObjectFactory<AuthorizationSubscriptionBuilderService> subscriptionBuilderFactory) {
			super(pdpFactory, constraintHandlerFactory, objectMapperFactory, subscriptionBuilderFactory);
		}

	}

	@Component
	public static class ConstraintHandlerOne implements RunnableConstraintHandlerProvider {

		@Override
		public int getPriority() {
			return 1;
		}

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint != null && constraint.isTextual() && KNOWN_CONSTRAINT.equals(constraint.textValue());
		}

		@Override
		public Signal getSignal() {
			return Signal.ON_DECISION;
		}

		@Override
		public Runnable getHandler(JsonNode constraint) {
			return this::run;
		}

		public void run() {
		}

	}

	@Component
	public static class ConstraintHandlerTwo implements RunnableConstraintHandlerProvider {

		@Override
		public int getPriority() {
			return 2;
		}

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint != null && constraint.isTextual() && KNOWN_CONSTRAINT.equals(constraint.textValue());
		}

		@Override
		public Signal getSignal() {
			return Signal.ON_DECISION;
		}

		@Override
		public Runnable getHandler(JsonNode constraint) {
			return this::run;
		}

		public void run() {
		}

	}

	@Component
	public static class FailingConstraintHandler implements RunnableConstraintHandlerProvider {

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint != null && constraint.isTextual() && FAILING_CONSTRAINT.equals(constraint.textValue());
		}

		@Override
		public Signal getSignal() {
			return Signal.ON_DECISION;
		}

		@Override
		public Runnable getHandler(JsonNode constraint) {
			return this::run;
		}

		public void run() {
			throw new IllegalArgumentException("I fail because I must test!");
		}
	}

	@Service
	static class TestService {
		@PostEnforce
		public String execute(String argument) {
			return "Argument: " + argument;
		}

		@PostEnforce
		public Optional<String> executeOptional(String argument) {
			return Optional.of("Argument: " + argument);
		}

		@PostEnforce
		public Optional<String> executeOptionalEmpty() {
			return Optional.empty();
		}
	}

	@Test
	void contextLoads() {
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalled_then_pdpDecideIsInvoked() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		service.execute("test");
		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledAndPdpPermits_then_pdpMethodReturnsNormally() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertEquals("Argument: test", service.execute("test"));
		verify(service, times(1)).execute(any());
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledAndPdpDenies_then_pdpMethodThrowsAccessDeniedButWasInvoked() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
		assertThrows(AccessDeniedException.class, () -> service.execute("test"));
		verify(service, times(1)).execute(any());
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledAndPdpIndeterminate_then_pdpMethodThrowsAccessDeniedButWasInvoked() {
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
		assertThrows(AccessDeniedException.class, () -> service.execute("test"));
		verify(service, times(1)).execute(any());
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledAndPdpNotApplicable_then_pdpMethodThrowsAccessDeniedButWasInvoked() {
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
		assertThrows(AccessDeniedException.class, () -> service.execute("test"));
		verify(service, times(1)).execute(any());
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledAndPdpReturnsEmptyStream_then_pdpMethodThrowsAccessDeniedButWasInvoked() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.empty());
		assertThrows(AccessDeniedException.class, () -> service.execute("test"));
		verify(service, times(1)).execute(any());
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledAndPdpReturnsNull_then_pdpMethodThrowsAccessDeniedButWasInvoked() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(null);
		assertThrows(AccessDeniedException.class, () -> service.execute("test"));
		verify(service, times(1)).execute(any());
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledAndDecisionContainsUnenforcableObligation_then_pdpMethodThrowsAccessDeniedButWasInvoked() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(UNKNOWN_CONSTRAINT));
		var decision = AuthorizationDecision.PERMIT.withObligations(obligations);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
		assertThrows(AccessDeniedException.class, () -> service.execute("test"));
		verify(service, times(1)).execute(any());
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledAndDecisionContainsFailingObligation_then_pdpMethodThrowsAccessDeniedButWasInvoked() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(FAILING_CONSTRAINT));
		obligations.add(JSON.textNode(KNOWN_CONSTRAINT));
		var decision = AuthorizationDecision.PERMIT.withObligations(obligations);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
		assertThrows(AccessDeniedException.class, () -> service.execute("test"));
		verify(service, times(1)).execute(any());
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledAndDecisionContainsUnenforcableAdvice_then_accessGranted() {
		var advice = JSON.arrayNode();
		advice.add(JSON.textNode(UNKNOWN_CONSTRAINT));
		var decision = AuthorizationDecision.PERMIT.withAdvice(advice);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
		assertEquals("Argument: test", service.execute("test"));
		verify(service, times(1)).execute(any());
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledAndDecisionContainsFailingAdvice_then_normalAccessGranted() {
		var advice = JSON.arrayNode();
		advice.add(JSON.textNode(FAILING_CONSTRAINT));
		var decision = AuthorizationDecision.PERMIT.withAdvice(advice);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
		assertEquals("Argument: test", service.execute("test"));
		verify(service, times(1)).execute(any());
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledAndDecisionContainsEnforcableObligation_then_pdpMethodReturnsNormallyAndHandlersAreInvoked() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(KNOWN_CONSTRAINT));
		var decision = AuthorizationDecision.PERMIT.withObligations(obligations);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
		InOrder inOrder = inOrder(constraintHandlerOne, constraintHandlerTwo);
		assertEquals("Argument: test", service.execute("test"));
		inOrder.verify(constraintHandlerTwo).run();
		inOrder.verify(constraintHandlerOne).run();
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledAndDecisionDenyContainsEnforcableObligation_then_acceddDeniedButConstraintsHandled() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(KNOWN_CONSTRAINT));
		var decision = AuthorizationDecision.DENY.withObligations(obligations);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
		InOrder inOrder = inOrder(constraintHandlerOne, constraintHandlerTwo);
		assertThrows(AccessDeniedException.class, () -> service.execute("test"));
		inOrder.verify(constraintHandlerTwo).run();
		inOrder.verify(constraintHandlerOne).run();
		verify(service, times(1)).execute(any());
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledAndDecisionContainsEnforcableAdvice_then_pdpMethodReturnsNormallyAndHandlersAreInvoked() {
		var advice = JSON.arrayNode();
		advice.add(JSON.textNode(KNOWN_CONSTRAINT));
		var decision = AuthorizationDecision.PERMIT.withAdvice(advice);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
		InOrder inOrder = inOrder(constraintHandlerOne, constraintHandlerTwo);
		assertEquals("Argument: test", service.execute("test"));
		inOrder.verify(constraintHandlerTwo).run();
		inOrder.verify(constraintHandlerOne).run();
		verify(service, times(1)).execute(any());
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledAndDecisionContainsEnforcableObligationsAndAdvice_then_pdpMethodReturnsNormallyAndHandlersAreInvoked() {
		var advice = JSON.arrayNode();
		advice.add(JSON.textNode(KNOWN_CONSTRAINT));
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(KNOWN_CONSTRAINT));
		var decision = AuthorizationDecision.PERMIT.withObligations(obligations).withAdvice(advice);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
		InOrder inOrder = inOrder(constraintHandlerOne, constraintHandlerTwo);
		assertEquals("Argument: test", service.execute("test"));
		inOrder.verify(constraintHandlerTwo).run();
		inOrder.verify(constraintHandlerOne).run();
		inOrder.verify(constraintHandlerTwo).run();
		inOrder.verify(constraintHandlerOne).run();
		verify(service, times(1)).execute(any());
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledOptionalReturnValueAndPermit_then_returnsNormally() {
		var decision = AuthorizationDecision.PERMIT;
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
		assertEquals(Optional.of("Argument: test"), service.executeOptional("test"));
		verify(service, times(1)).executeOptional(any());
	}

	@Test
	@WithMockUser(USER)
	void when_testServiceCalledOptionalEmptyReturnValueAndPermit_then_returnsNormally() {
		var decision = AuthorizationDecision.PERMIT;
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
		assertEquals(Optional.empty(), service.executeOptionalEmpty());
		verify(service, times(1)).executeOptionalEmpty();
	}

}
