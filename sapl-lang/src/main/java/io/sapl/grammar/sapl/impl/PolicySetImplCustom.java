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

import java.util.function.Function;

import org.reactivestreams.Publisher;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.combinators.DenyOverridesCombinator;
import io.sapl.interpreter.combinators.DenyUnlessPermitCombinator;
import io.sapl.interpreter.combinators.FirstApplicableCombinator;
import io.sapl.interpreter.combinators.OnlyOneApplicableCombinator;
import io.sapl.interpreter.combinators.PermitOverridesCombinator;
import io.sapl.interpreter.combinators.PermitUnlessDenyCombinator;
import io.sapl.interpreter.combinators.PolicyCombinator;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

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
	public Flux<AuthorizationDecision> evaluate(@NonNull EvaluationContext ctx) {
		return Flux.just(Tuples.of(Val.TRUE, ctx.copy())).switchMap(evaluateValueDefinitions(0))
				.switchMap(this::evalPolicies);
	}

	private Flux<AuthorizationDecision> evalPolicies(Tuple2<Val, EvaluationContext> t) {
		if (t.getT1().isError()) {
			log.debug("| |- Error in the value definitions of the policy set. Policy evaluated INDETERMINATE.: {}",
					t.getT1().getMessage());
			return Flux.just(AuthorizationDecision.INDETERMINATE);
		}
		return combinatorFor(getAlgorithm()).combinePolicies(policies, t.getT2());

	}

	private Function<? super Tuple2<Val, EvaluationContext>, Publisher<? extends Tuple2<Val, EvaluationContext>>> evaluateValueDefinitions(
			int valueDefinitionId) {
		if (valueDefinitions == null || valueDefinitionId == valueDefinitions.size()) {
			return Flux::just;
		}
		return previousAndContext -> {
			return evaluateValueDefinition(previousAndContext.getT1(), valueDefinitions.get(valueDefinitionId),
					previousAndContext.getT2()).switchMap(evaluateValueDefinitions(valueDefinitionId + 1));
		};
	}

	private Flux<Tuple2<Val, EvaluationContext>> evaluateValueDefinition(Val previousResult,
			ValueDefinition valueDefinition, EvaluationContext ctx) {
		if (previousResult.isError() || !previousResult.getBoolean()) {
			return Flux.just(Tuples.of(previousResult, ctx));
		}
		return valueDefinition.getEval().evaluate(ctx, Val.UNDEFINED).concatMap(evaluatedValue -> {
			if (evaluatedValue.isDefined()) {
				var newCtx = ctx.copy();
				newCtx.getVariableCtx().put(valueDefinition.getName(), evaluatedValue.get());
				return Flux.just(Tuples.of(Val.TRUE, newCtx));
			} else {
				return Flux.just(Tuples.of(Val.error(CANNOT_ASSIGN_UNDEFINED_TO_A_VAL), ctx));
			}
		});
	}

	private PolicyCombinator combinatorFor(String algorithm) {
		switch (algorithm) {
		case "deny-unless-permit":
			return new DenyUnlessPermitCombinator();
		case "permit-unless-deny":
			return new PermitUnlessDenyCombinator();
		case "deny-overrides":
			return new DenyOverridesCombinator();
		case "permit-overrides":
			return new PermitOverridesCombinator();
		case "only-one-applicable":
			return new OnlyOneApplicableCombinator();
		default: // "first-applicable":
			return new FirstApplicableCombinator();
		}
	}
}
