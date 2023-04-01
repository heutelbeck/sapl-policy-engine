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

import java.util.function.BiFunction;

import org.eclipse.emf.common.util.EList;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.impl.util.ImportsUtil;
import io.sapl.interpreter.DocumentEvaluationResult;
import io.sapl.interpreter.PolicyDecision;
import reactor.core.publisher.Flux;

public class PolicyImplCustom extends PolicyImpl {

	@Override
	public Flux<DocumentEvaluationResult> evaluate() {
		var whereResult     = body == null ? Flux.just(Val.TRUE.withTrace(Policy.class))
				: body.evaluate();
		var afterWhere      = whereResult
				.map(where -> PolicyDecision.fromWhereResult(getSaplName(), entitlement.getDecision(), where));
		var withObligations = afterWhere
				.switchMap(decision -> addConstraints(decision, obligations, 0, PolicyDecision::withObligation));
		var withAdvice      = withObligations
				.switchMap(decision -> addConstraints(decision, advice, 0, PolicyDecision::withAdvice));

		Flux<DocumentEvaluationResult> withResource = withAdvice.switchMap(this::addResource);
		
		return withResource.contextWrite(ctx -> ImportsUtil.loadImportsIntoContext(this, ctx))
				.onErrorResume(this::importFailure);
	}

	private Flux<DocumentEvaluationResult> importFailure(Throwable error) {
		return Flux.just(importError(error.getMessage()));
	}

	@Override
	public DocumentEvaluationResult targetResult(Val targetValue) {
		return PolicyDecision.ofTargetExpressionEvaluation(getSaplName(), targetValue, getEntitlement().getDecision());

	}

	@Override
	public DocumentEvaluationResult importError(String errorMessage) {
		return PolicyDecision.ofImportError(getSaplName(), getEntitlement().getDecision(), errorMessage);
	}

	private Flux<PolicyDecision> addResource(PolicyDecision policyDecision) {
		if (transformation == null || decisionMustNotCarryConstraints(policyDecision))
			return Flux.just(policyDecision);
		return transformation.evaluate().map(policyDecision::withResource).defaultIfEmpty(policyDecision);
	}

	private Flux<PolicyDecision> addConstraints(PolicyDecision policyDecision, EList<Expression> constraints,
			int constraintIndex, BiFunction<PolicyDecision, Val, PolicyDecision> merge) {
		if (constraints == null || constraints.size() == constraintIndex
				|| decisionMustNotCarryConstraints(policyDecision)) {
			return Flux.just(policyDecision);
		}
		var constraint             = constraints.get(constraintIndex).evaluate();
		var decisionWithConstraint = constraint.map(val -> merge.apply(policyDecision, val));
		return decisionWithConstraint
				.switchMap(decision -> addConstraints(decision, constraints, constraintIndex + 1, merge));
	}

	private boolean decisionMustNotCarryConstraints(DocumentEvaluationResult policyDecision) {
		var decision = policyDecision.getAuthorizationDecision().getDecision();
		return decision == Decision.INDETERMINATE || decision == Decision.NOT_APPLICABLE;
	}

}
