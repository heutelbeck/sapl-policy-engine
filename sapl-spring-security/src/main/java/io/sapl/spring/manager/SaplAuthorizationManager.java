/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.manager;

import java.util.function.Supplier;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SaplAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

	private final PolicyDecisionPoint          pdp;
	private final ConstraintEnforcementService constraintEnforcementService;
	private final ObjectMapper                 mapper;

	@Override
	public AuthorizationDecision check(Supplier<Authentication> authenticationSupplier,
			RequestAuthorizationContext requestAuthorizationContext) {
		var request        = requestAuthorizationContext.getRequest();
		var authentication = authenticationSupplier.get();
		var subscription   = AuthorizationSubscription.of(authentication, request, request, mapper);
		var authzDecision  = pdp.decide(subscription).blockFirst();

		if (authzDecision == null || authzDecision.getResource().isPresent())
			return new AuthorizationDecision(false);

		try {
			constraintEnforcementService.accessManagerBundleFor(authzDecision).handleOnDecisionConstraints();
		} catch (AccessDeniedException e) {
			return new AuthorizationDecision(false);
		}

		if (authzDecision.getDecision() != Decision.PERMIT)
			return new AuthorizationDecision(false);

		return new AuthorizationDecision(true);
	}
}
