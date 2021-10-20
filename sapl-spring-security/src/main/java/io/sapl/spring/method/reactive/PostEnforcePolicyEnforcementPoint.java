package io.sapl.spring.method.reactive;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints2.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.PostEnforceAttribute;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class PostEnforcePolicyEnforcementPoint {

	private final PolicyDecisionPoint pdp;
	private final ConstraintEnforcementService constraintHandlerService;
	private final AuthorizationSubscriptionBuilderService subscriptionBuilder;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Mono<?> postEnforceOneDecisionOnResourceAccessPoint(Mono<?> resourceAccessPoint, MethodInvocation invocation,
			PostEnforceAttribute postEnforceAttribute) {
		return resourceAccessPoint.flatMap(result -> {
			Mono<AuthorizationDecision> dec = postEnforceDecision(invocation, postEnforceAttribute, result);
			return dec.flatMap(decision -> {
				var finalResourceAccessPoint = Flux.just(result);
				if (Decision.PERMIT != decision.getDecision())
					finalResourceAccessPoint = Flux.error(new AccessDeniedException("Access Denied by PDP"));

				return constraintHandlerService.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
						(Flux) finalResourceAccessPoint, postEnforceAttribute.getGenericsType()).onErrorStop().next();
			});
		});
	}

	private Mono<AuthorizationDecision> postEnforceDecision(MethodInvocation invocation,
			PostEnforceAttribute postEnforceAttribute, Object returnedObject) {
		return subscriptionBuilder
				.reactiveConstructAuthorizationSubscription(invocation, postEnforceAttribute, returnedObject)
				.flatMapMany(authzSubscription -> pdp.decide(authzSubscription)).next();
	}

}
