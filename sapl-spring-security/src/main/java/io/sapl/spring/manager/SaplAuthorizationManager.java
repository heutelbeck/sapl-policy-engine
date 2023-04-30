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
