/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl;

import java.util.List;
import java.util.function.BiFunction;

import org.eclipse.emf.common.util.EList;

import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.Policy;
import io.sapl.interpreter.PolicyDecision;
import io.sapl.interpreter.SAPLDecision;
import reactor.core.publisher.Flux;

public class PolicyImplCustom extends PolicyImpl {

	@Override
	public Flux<SAPLDecision> evaluate() {
		var whereResult     = body == null ? Flux.just(Val.TRUE.withTrace(Policy.class)) : body.evaluate();
		var afterWhere      = whereResult
				.map(where -> PolicyDecision.of(saplName, conditionsResultToDecision(where), where));
		var withObligations = afterWhere
				.switchMap(decision -> addConstraints(decision, obligations, 0, (d, val) -> d.withObligation(val)));
		var withAdvice      = withObligations
				.switchMap(decision -> addConstraints(decision, advice, 0, (d, val) -> d.withAdvice(val)));
		var withResource    = withAdvice.switchMap(decision -> addResource(decision));
		return withResource.map(this::addAuthorizationDecision);
	}

	private Flux<PolicyDecision> addResource(PolicyDecision decision) {
		if (transformation == null || decisionMustNotCarryConstraints(decision.getEntitlement()))
			return Flux.just(decision);
		return transformation.evaluate().map(decision::withResource).defaultIfEmpty(decision);
	}

	private Flux<PolicyDecision> addConstraints(PolicyDecision policyDecision, EList<Expression> constraints,
			int constraintIndex, BiFunction<PolicyDecision, Val, PolicyDecision> merge) {
		if (constraints == null || constraints.size() == constraintIndex
				|| decisionMustNotCarryConstraints(policyDecision.getEntitlement())) {
			return Flux.just(policyDecision);
		}
		var constraint               = constraints.get(constraintIndex).evaluate();
		var decisionWithConstraint   = constraint.map(val -> merge.apply(policyDecision, val));
		var withRemainingConstraints = decisionWithConstraint
				.switchMap(decision -> addConstraints(decision, constraints, constraintIndex + 1, merge));
		return withRemainingConstraints;
	}

	private Decision conditionsResultToDecision(Val where) {
		if (where.isError())
			return Decision.INDETERMINATE;
		if (where.getBoolean())
			return entitlement.getDecision();
		return Decision.NOT_APPLICABLE;
	}

	private PolicyDecision addAuthorizationDecision(PolicyDecision policyDecision) {
		var newDecision = policyDecision.withDecision(policyDecisionToAuthorizationDecision(policyDecision));
		return newDecision;
	}

	private AuthorizationDecision policyDecisionToAuthorizationDecision(PolicyDecision policyDecision) {
		if ((policyDecision.getWhereResult().isPresent() && policyDecision.getWhereResult().get().isError())
				|| containsError(policyDecision.getObligations()) || containsError(policyDecision.getAdvice())
				|| (policyDecision.getResource().isPresent() && policyDecision.getResource().get().isError())) {
			return AuthorizationDecision.INDETERMINATE;
		}

		var authzDecision = new AuthorizationDecision(policyDecision.getEntitlement());
		if (decisionMustNotCarryConstraints(policyDecision.getEntitlement()))
			return authzDecision;
		authzDecision = authzDecision.withObligations(collectConstraints(policyDecision.getObligations()));
		authzDecision = authzDecision.withAdvice(collectConstraints(policyDecision.getAdvice()));
		if (policyDecision.getResource().isPresent())
			authzDecision = authzDecision.withResource(policyDecision.getResource().get().get());
		return authzDecision;
	}

	private ArrayNode collectConstraints(List<Val> constraints) {
		var array = Val.JSON.arrayNode();
		for (var constraint : constraints) {
			array.add(constraint.get());
		}
		return array;
	}

	private boolean containsError(List<Val> values) {
		return values.stream().filter(Val::isError).findAny().isPresent();
	}

	private boolean decisionMustNotCarryConstraints(Decision authzDecision) {
		return authzDecision == Decision.INDETERMINATE || authzDecision == Decision.NOT_APPLICABLE;
	}

}
