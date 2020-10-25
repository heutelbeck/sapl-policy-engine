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
package io.sapl.grammar.sapl.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.interpreter.DependentStreamsUtil;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.FluxProvider;
import io.sapl.interpreter.Void;
import io.sapl.interpreter.combinators.DenyOverridesCombinator;
import io.sapl.interpreter.combinators.DenyUnlessPermitCombinator;
import io.sapl.interpreter.combinators.FirstApplicableCombinator;
import io.sapl.interpreter.combinators.OnlyOneApplicableCombinator;
import io.sapl.interpreter.combinators.PermitOverridesCombinator;
import io.sapl.interpreter.combinators.PermitUnlessDenyCombinator;
import io.sapl.interpreter.combinators.PolicyCombinator;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class PolicySetImplCustom extends PolicySetImpl {

	/**
	 * Evaluates the body of the policy set within the given evaluation context and
	 * returns a {@link Flux} of {@link AuthorizationDecision} objects.
	 * 
	 * @param ctx the evaluation context in which the policy set's body is
	 *            evaluated. It must contain
	 *            <ul>
	 *            <li>the attribute context</li>
	 *            <li>the function context</li>
	 *            <li>the variable context holding the four authorization
	 *            subscription variables 'subject', 'action', 'resource' and
	 *            'environment' combined with system variables from the PDP
	 *            configuration</li>
	 *            <li>the import mapping for functions and attribute finders</li>
	 *            </ul>
	 * @return A {@link Flux} of {@link AuthorizationDecision} objects.
	 */
	@Override
	public Flux<AuthorizationDecision> evaluate(EvaluationContext ctx) {
		final Map<String, JsonNode> variables = new HashMap<>();
		final List<FluxProvider<Void>> fluxProviders = new ArrayList<>(getValueDefinitions().size());
		for (ValueDefinition valueDefinition : getValueDefinitions()) {
			fluxProviders.add(voiD -> evaluateValueDefinition(valueDefinition, ctx, variables));
		}
		final Flux<Void> variablesFlux = DependentStreamsUtil.nestedSwitchMap(Void.INSTANCE, fluxProviders);

		final PolicyCombinator combinator;
		switch (getAlgorithm()) {
		case "deny-unless-permit":
			combinator = new DenyUnlessPermitCombinator();
			break;
		case "permit-unless-deny":
			combinator = new PermitUnlessDenyCombinator();
			break;
		case "deny-overrides":
			combinator = new DenyOverridesCombinator();
			break;
		case "permit-overrides":
			combinator = new PermitOverridesCombinator();
			break;
		case "only-one-applicable":
			combinator = new OnlyOneApplicableCombinator();
			break;
		default: // "first-applicable":
			combinator = new FirstApplicableCombinator();
			break;
		}

		return variablesFlux.switchMap(voiD -> combinator.combinePolicies(getPolicies(), ctx))
				.onErrorReturn(INDETERMINATE);
	}

	private Flux<Void> evaluateValueDefinition(ValueDefinition valueDefinition, EvaluationContext evaluationCtx,
			Map<String, JsonNode> variables) {
		return valueDefinition.getEval().evaluate(evaluationCtx, true, Val.undefined()).flatMap(evaluatedValue -> {
			try {
				if (evaluatedValue.isDefined()) {
					evaluationCtx.getVariableCtx().put(valueDefinition.getName(), evaluatedValue.get());
					variables.put(valueDefinition.getName(), evaluatedValue.get());
					return Flux.just(Void.INSTANCE);
				} else {
					return Flux.error(new PolicyEvaluationException(CANNOT_ASSIGN_UNDEFINED_TO_A_VAL));
				}
			} catch (PolicyEvaluationException e) {
				log.debug("Value definition evaluation failed: {}", e.getMessage());
				return Flux.error(e);
			}
		});
	}

}
