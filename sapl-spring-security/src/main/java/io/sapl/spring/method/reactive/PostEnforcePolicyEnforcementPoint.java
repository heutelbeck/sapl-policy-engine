package io.sapl.spring.method.reactive;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import io.sapl.spring.method.metadata.PostEnforceAttribute;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class PostEnforcePolicyEnforcementPoint {

	private final PolicyDecisionPoint pdp;
	private final ReactiveConstraintEnforcementService constraintHandlerService;
	private final ObjectMapper mapper;
	private final AuthorizationSubscriptionBuilderService subscriptionBuilder;

	public Mono<?> postEnforceOneDecisionOnResourceAccessPoint(Mono<?> resourceAccessPoint, MethodInvocation invocation,
			PostEnforceAttribute postEnforceAttribute) {
		return resourceAccessPoint.flatMap(result -> {
			Mono<AuthorizationDecision> dec = postEnforceDecision(invocation, postEnforceAttribute, result);
			return dec.flatMap(decision -> {
				Flux<Object> finalResourceAccessPoint = Flux.just(result);
				if (Decision.PERMIT != decision.getDecision())
					finalResourceAccessPoint = Flux.error(new AccessDeniedException("Access Denied by PDP"));
				else if (decision.getResource().isPresent()) {
					try {
						finalResourceAccessPoint = Flux.just(mapper.treeToValue(decision.getResource().get(),
								postEnforceAttribute.getGenericsType()));
					} catch (JsonProcessingException e) {
						finalResourceAccessPoint = Flux.error(new AccessDeniedException(String.format(
								"Access Denied. Error replacing flux contents by resource from PDPs decision: %s",
								e.getMessage())));
					}
				}
				return constraintHandlerService
						.enforceConstraintsOnResourceAccessPoint(decision, finalResourceAccessPoint).next();
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
