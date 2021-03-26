package io.sapl.spring.pep;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintHandlerService;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class PolicyEnforcementPointTests {
	public static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private ObjectMapper mapper;
	private PolicyDecisionPoint pdp;
	private ConstraintHandlerService constraintHandlers;

	@BeforeEach
	void setUpMocks() {
		mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
		mapper.registerModule(module);
		pdp = mock(PolicyDecisionPoint.class);
		constraintHandlers = mock(ConstraintHandlerService.class);
	}

	@Test
	void whenPermitAndNoObligations_thenPermit() {
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		StepVerifier.create(pep.enforce("subject", "action", "resource")).expectNext(Decision.PERMIT).thenCancel()
				.verify();
	}

	@Test
	void whenDeny_thenDeny() {
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.DENY));
		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		StepVerifier.create(pep.enforce("subject", "action", "resource")).expectNext(Decision.DENY).thenCancel()
				.verify();
	}

	@Test
	void whenNotApplicable_thenDeny() {
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		StepVerifier.create(pep.enforce("subject", "action", "resource")).expectNext(Decision.DENY).thenCancel()
				.verify();
	}

	@Test
	void whenIndeterminate_thenIndeterminate() {
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		StepVerifier.create(pep.enforce("subject", "action", "resource")).expectNext(Decision.DENY).thenCancel()
				.verify();
	}

	@Test
	void whenPermitAndAndObligationsFulfilled_thenPermit() {
		var obligations = JSON.arrayNode();
		obligations.add("obligation1");
		obligations.add("obligation2");
		var decision = AuthorizationDecision.PERMIT.withObligations(obligations);

		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(decision));
		doThrow(new AccessDeniedException("forced failure")).when(constraintHandlers)
				.handleObligations((AuthorizationDecision) any());

		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		StepVerifier.create(pep.enforce("subject", "action", "resource")).expectNext(Decision.DENY).thenCancel()
				.verify();
	}

	@Test
	void whenDecisionHasResourceAndPEPDoesNotSupportTransform_thenDeny() {
		var decision = AuthorizationDecision.PERMIT.withResource(JSON.textNode("transformed resource"));
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(decision));
		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		StepVerifier.create(pep.enforce("subject", "action", "resource")).expectNext(Decision.DENY).thenCancel()
				.verify();
	}

	@Test
	void whenDecisionHasResourceAndPEPDoesSupportTransform_thenPermit() {
		var decision = AuthorizationDecision.PERMIT.withResource(JSON.textNode("transformed resource"));
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(decision));
		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		StepVerifier.create(pep.filterEnforce("subject", "action", "resource")).expectNext(decision).thenCancel()
				.verify();
	}

	@Test
	void whenFilterEnforcingAll_thenPermitsWithFulfilledObligationsStayAndWithFailedObligationsTurnToDeny() {
		var subscription = new MultiAuthorizationSubscription();
		subscription.addAuthorizationSubscription("id1", "subject", "action1", "resource", "environment");
		subscription.addAuthorizationSubscription("id2", "subject", "action1", "resource", "environment");
		var obligations1 = JSON.arrayNode();
		obligations1.add("obligation1");
		var decision1 = AuthorizationDecision.PERMIT.withObligations(obligations1);
		var obligations2 = JSON.arrayNode();
		obligations2.add("obligation2");
		var decision2 = AuthorizationDecision.PERMIT.withObligations(obligations2);

		var pdpMultiDecision = new MultiAuthorizationDecision();
		pdpMultiDecision.setAuthorizationDecisionForSubscriptionWithId("id1", decision1);
		pdpMultiDecision.setAuthorizationDecisionForSubscriptionWithId("id2", decision2);
		when(pdp.decideAll((MultiAuthorizationSubscription) any())).thenReturn(Flux.just(pdpMultiDecision));
		doAnswer(i -> {
			var decision = (AuthorizationDecision) i.getArgument(0);
			if (decision.getObligations().isPresent()
					&& decision.getObligations().get().get(0).asText().equals("obligation2"))
				throw new AccessDeniedException("forced failure on obligation2");
			return null;
		}).when(constraintHandlers).handleObligations(any(AuthorizationDecision.class));

		var alteredDecision2 = AuthorizationDecision.DENY.withObligations(obligations2);
		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		StepVerifier.create(pep.filterEnforceAll(subscription)).assertNext(actual -> {
			assertThat(actual.getAuthorizationDecisionForSubscriptionWithId("id1"), is(decision1));
			assertThat(actual.getAuthorizationDecisionForSubscriptionWithId("id2"), is(alteredDecision2));
		}).thenCancel().verify();
	}

	@Test
	void whenFilterEnforcingAll_thenNonPermitsAreDeny() {
		var subscription = new MultiAuthorizationSubscription();
		subscription.addAuthorizationSubscription("id1", "subject", "action1", "resource", "environment");
		subscription.addAuthorizationSubscription("id2", "subject", "action1", "resource", "environment");
		var decision1 = AuthorizationDecision.INDETERMINATE;
		var decision2 = AuthorizationDecision.NOT_APPLICABLE;

		var pdpMultiDecision = new MultiAuthorizationDecision();
		pdpMultiDecision.setAuthorizationDecisionForSubscriptionWithId("id1", decision1);
		pdpMultiDecision.setAuthorizationDecisionForSubscriptionWithId("id2", decision2);
		when(pdp.decideAll((MultiAuthorizationSubscription) any())).thenReturn(Flux.just(pdpMultiDecision));

		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		StepVerifier.create(pep.filterEnforceAll(subscription)).assertNext(actual -> {
			assertThat(actual.getAuthorizationDecisionForSubscriptionWithId("id1"), is(AuthorizationDecision.DENY));
			assertThat(actual.getAuthorizationDecisionForSubscriptionWithId("id2"), is(AuthorizationDecision.DENY));
		}).thenCancel().verify();
	}

	@Test
	void whenFilterEnforcing_thenNonPermitsAreDenyAndPermitStays() {
		var subscription = new MultiAuthorizationSubscription();
		subscription.addAuthorizationSubscription("id1", "subject", "action1", "resource", "environment");
		subscription.addAuthorizationSubscription("id2", "subject", "action1", "resource", "environment");
		var decision1 = new IdentifiableAuthorizationDecision("id1", AuthorizationDecision.PERMIT);
		var decision2 = new IdentifiableAuthorizationDecision("id2", AuthorizationDecision.NOT_APPLICABLE);

		var expectedDecision1 = decision1;
		var expectedDecision2 = new IdentifiableAuthorizationDecision("id2", AuthorizationDecision.DENY);

		when(pdp.decide((MultiAuthorizationSubscription) any())).thenReturn(Flux.just(decision1, decision2));

		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		assertThat(pep.filterEnforce(subscription).take(2).collectList().block(),
				containsInAnyOrder(expectedDecision2, expectedDecision1));
	}

}
