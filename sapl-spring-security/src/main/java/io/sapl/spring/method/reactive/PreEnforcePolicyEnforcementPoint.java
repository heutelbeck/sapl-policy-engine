package io.sapl.spring.method.reactive;

import java.util.function.Function;

import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints2.ConstraintEnforcementService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class PreEnforcePolicyEnforcementPoint {

	private final ConstraintEnforcementService constraintEnforcementService;

	public <T> Flux<T> enforce(Flux<AuthorizationDecision> authorizationDecisions, Flux<T> resourceAccessPoint,
			Class<T> clazz) {
		return authorizationDecisions.next().flatMapMany(enforceDecision(resourceAccessPoint, clazz));
	}

	private <T> Function<AuthorizationDecision, Flux<T>> enforceDecision(Flux<T> resourceAccessPoint, Class<T> clazz) {
		return decision -> {
			Flux<T> finalResourceAccessPoint = resourceAccessPoint;
			if (Decision.PERMIT != decision.getDecision())
				finalResourceAccessPoint = Flux.error(new AccessDeniedException("Access Denied by PDP"));

			// onErrorStop is required to counter an onErrorContinue attack on the PEP/RAP.
			return constraintEnforcementService
					.enforceConstraintsOfDecisionOnResourceAccessPoint(decision, finalResourceAccessPoint, clazz)
					.onErrorStop();
		};
	}

}
