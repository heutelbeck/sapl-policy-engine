package io.sapl.spring.method.reactive;

import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class PreEnforcePolicyEnforcementPoint {

	private final ReactiveConstraintEnforcementService constraintHandlerService;
	private final ObjectMapper mapper;

	public Flux<Object> enforce(Flux<AuthorizationDecision> authorizationDecisions, Flux<Object> resourceAccessPoint,
			Class<?> clazz) {
		return authorizationDecisions.take(1, true).switchMap(enforceDecision(resourceAccessPoint, clazz));
	}

	private Function<? super AuthorizationDecision, Publisher<? extends Object>> enforceDecision(
			Flux<Object> resourceAccessPoint, Class<?> clazz) {
		return decision -> {
			Flux<Object> finalResourceAccessPoint = resourceAccessPoint;
			if (Decision.PERMIT != decision.getDecision())
				finalResourceAccessPoint = Flux.error(new AccessDeniedException("Access Denied by PDP"));
			else if (decision.getResource().isPresent()) {
				try {
					finalResourceAccessPoint = Flux.just(mapper.treeToValue(decision.getResource().get(), clazz));
				} catch (JsonProcessingException e) {
					finalResourceAccessPoint = Flux.error(new AccessDeniedException(String.format(
							"Access Denied. Error replacing Flux contents by resource from PDPs decision: %s",
							e.getMessage())));
				}
			}
			// onErrorStop is required to counter an onErrorContinue attack on the PEP/RAP.
			return constraintHandlerService.enforceConstraintsOnResourceAccessPoint(decision, finalResourceAccessPoint)
					.onErrorStop();
		};
	}

}
