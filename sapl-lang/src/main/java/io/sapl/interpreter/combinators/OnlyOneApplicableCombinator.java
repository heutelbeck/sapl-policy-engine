/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.combinators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.variables.VariableContext;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuples;

/**
 * This algorithm is used if policy sets and policies are constructed in a way
 * that multiple policy documents with a matching target are considered an
 * error. A PERMIT or DENY decision will only be returned if there is exactly
 * one policy set or policy with matching target expression and if this policy
 * document evaluates to PERMIT or DENY.
 * 
 * It works as follows:
 * 
 * 1. If any target evaluation results in an error (INDETERMINATE) or if more
 * than one policy documents have a matching target, the decision is
 * INDETERMINATE.
 * 
 * 2. Otherwise:
 * 
 * a) If there is no matching policy document, the decision is NOT_APPLICABLE.
 * 
 * b) Otherwise, i.e., there is exactly one matching policy document, the
 * decision is the result of evaluating this policy document.
 *
 */
public class OnlyOneApplicableCombinator implements DocumentsCombinator, PolicyCombinator {

	@Override
	public Flux<AuthorizationDecision> combineMatchingDocuments(Collection<SAPL> matchingSaplDocuments,
			boolean errorsInTarget, AuthorizationSubscription authzSubscription, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables) {

		if (errorsInTarget || matchingSaplDocuments.size() > 1) {
			return Flux.just(AuthorizationDecision.INDETERMINATE);
		} else if (matchingSaplDocuments.size() == 1) {
			final VariableContext variableCtx;
			try {
				variableCtx = new VariableContext(authzSubscription, systemVariables);
			} catch (PolicyEvaluationException e) {
				return Flux.just(AuthorizationDecision.INDETERMINATE);
			}
			final EvaluationContext evaluationCtx = new EvaluationContext(attributeCtx, functionCtx, variableCtx);

			final SAPL matchingDocument = matchingSaplDocuments.iterator().next();
			return matchingDocument.evaluate(evaluationCtx);
		} else {
			return Flux.just(AuthorizationDecision.NOT_APPLICABLE);
		}
	}

	@Override
	public Flux<AuthorizationDecision> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
		return Flux.fromIterable(policies)
				.concatMap(policy -> policy.matches(ctx).map(match -> Tuples.of(match, policy)))
				.reduce(Tuples.of(Boolean.FALSE, new ArrayList<Policy>(policies.size())), (state, match) -> {
					var newState = new ArrayList<>(state.getT2());
					if (match.getT1().isBoolean() && match.getT1().getBoolean()) {
						newState.add(match.getT2());
					}
					return Tuples.of(state.getT1() || match.getT1().isError(), newState);
				}).flux().concatMap(matching -> doCombine(matching.getT2(), matching.getT1(), ctx));
	}

	private Flux<AuthorizationDecision> doCombine(List<Policy> matches, boolean errorsInTarget, EvaluationContext ctx) {
		if (errorsInTarget || matches.size() > 1) {
			return Flux.just(AuthorizationDecision.INDETERMINATE);
		}
		if (matches.isEmpty()) {
			return Flux.just(AuthorizationDecision.NOT_APPLICABLE);
		}
		return matches.get(0).evaluate(ctx);
	}

}
