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
package io.sapl.spring.pep;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import reactor.core.publisher.Flux;

class PolicyEnforcementPointTests {

	public static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private PolicyDecisionPoint pdp;

	private ConstraintEnforcementService constraintHandlers;

	@BeforeEach
	void setUpMocks() {
		pdp = mock(PolicyDecisionPoint.class);
		constraintHandlers = mock(ConstraintEnforcementService.class);
	}

	@Test
	void whenPermitAndNoObligations_thenPermit() {
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(constraintHandlers.enforceConstraintsOfDecisionOnResourceAccessPoint(any(), any(), any()))
				.thenReturn(Flux.empty());
		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers);
		var actual = pep.isPermitted(AuthorizationSubscription.of("subject", "action", "resource")).block();
		assertThat(actual, is(true));
	}

	@Test
	void whenDenyAndNoObligations_thenDeny() {
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.DENY));
		when(constraintHandlers.enforceConstraintsOfDecisionOnResourceAccessPoint(any(), any(), any()))
				.thenReturn(Flux.empty());
		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers);
		var actual = pep.isPermitted(AuthorizationSubscription.of("subject", "action", "resource")).block();
		assertThat(actual, is(false));
	}

	@Test
	void whenNoDecision_thenDeny() {
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.empty());
		when(constraintHandlers.enforceConstraintsOfDecisionOnResourceAccessPoint(any(), any(), any()))
				.thenReturn(Flux.empty());
		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers);
		var actual = pep.isPermitted(AuthorizationSubscription.of("subject", "action", "resource")).block();
		assertThat(actual, is(false));
	}

	@Test
	void whenPermitAndObligationsSucceed_thenPermit() {
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisionFluxOnePermitWithObligation());
		when(constraintHandlers.enforceConstraintsOfDecisionOnResourceAccessPoint(any(), any(), any()))
				.thenReturn(Flux.empty());
		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers);
		var actual = pep.isPermitted(AuthorizationSubscription.of("subject", "action", "resource")).block();
		assertThat(actual, is(true));
	}

	@Test
	void whenPermitAndObligationsFail_thenDeny() {
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisionFluxOnePermitWithObligation());
		when(constraintHandlers.enforceConstraintsOfDecisionOnResourceAccessPoint(any(), any(), any()))
				.thenReturn(Flux.error(new AccessDeniedException("FAILED OBLIGATION")));
		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers);
		var actual = pep.isPermitted(AuthorizationSubscription.of("subject", "action", "resource")).block();
		assertThat(actual, is(false));
	}

	@Test
	void whenPermitAndResource_thenDeny() {
		when(pdp.decide((AuthorizationSubscription) any()))
				.thenReturn(Flux.just(AuthorizationDecision.DENY.withResource(JSON.textNode("CAUSES FAIL"))));
		when(constraintHandlers.enforceConstraintsOfDecisionOnResourceAccessPoint(any(), any(), any()))
				.thenReturn(Flux.error(new AccessDeniedException("FAILED OBLIGATION")));
		var pep = new PolicyEnforcementPoint(pdp, constraintHandlers);
		var actual = pep.isPermitted(AuthorizationSubscription.of("subject", "action", "resource")).block();
		assertThat(actual, is(false));
	}

	private Flux<AuthorizationDecision> decisionFluxOnePermitWithObligation() {
		var json = JsonNodeFactory.instance;
		var plus10000 = json.numberNode(10000L);
		var obligation = json.arrayNode();
		obligation.add(plus10000);
		return Flux.just(AuthorizationDecision.PERMIT.withObligations(obligation));
	}

}