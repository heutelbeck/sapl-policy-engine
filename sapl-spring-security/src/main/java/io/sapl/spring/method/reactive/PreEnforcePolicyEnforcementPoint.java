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
package io.sapl.spring.method.reactive;

import java.util.function.Function;

import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.ConstraintEnforcementService;
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

			// onErrorStop is required to counter an onErrorContinue attack on the
			// PEP/RAP.
			return constraintEnforcementService
					.enforceConstraintsOfDecisionOnResourceAccessPoint(decision, finalResourceAccessPoint, clazz)
					.onErrorStop();
		};
	}

}
