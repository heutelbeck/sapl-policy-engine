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
package io.sapl.grammar.sapl.impl;

import java.util.HashSet;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.interpreter.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
public class PolicySetImplCustom extends PolicySetImpl {

	/**
	 * Evaluates the body of the policy set within the given evaluation context and
	 * returns a {@link Flux} of {@link AuthorizationDecision} objects.
	 * @param ctx the evaluation context in which the policy set's body is evaluated. It
	 * must contain
	 * <ul>
	 * <li>the attribute context</li>
	 * <li>the function context</li>
	 * <li>the variable context holding the four authorization subscription variables
	 * 'subject', 'action', 'resource' and 'environment' combined with system variables
	 * from the PDP configuration</li>
	 * <li>the import mapping for functions and attribute finders</li>
	 * </ul>
	 * @return A {@link Flux} of {@link AuthorizationDecision} objects.
	 */
	@Override
	public Flux<AuthorizationDecision> evaluate(EvaluationContext ctx) {
		if (!policyNamesAreUnique()) {
			log.debug("  |- INDETERMINATE (Set) '{}'. (Policy names not unique)", saplName);
			return Flux.just(AuthorizationDecision.INDETERMINATE);
		}
		return Flux.just(Tuples.of(Val.TRUE, ctx)).switchMap(evaluateValueDefinitions(0)).switchMap(this::evalPolicies)
				.doOnNext(authzDecision -> log.debug("  |- {} (Set) '{}' {}", authzDecision.getDecision(), saplName,
						authzDecision));
	}

	// TODO: move into validation at parse time, remove from evaluation
	private boolean policyNamesAreUnique() {
		var policyNames = new HashSet<String>(policies.size(), 1.0F);
		for (var policy : policies) {
			if (!policyNames.add(policy.getSaplName())) {
				log.warn("Policy name collision in policy set: \"{}\"", policy.getSaplName());
				return false;
			}
		}
		return true;
	}

	private Flux<AuthorizationDecision> evalPolicies(
			Tuple2<Val, EvaluationContext> valueDefinitionSuccessAndScopedEvaluationContext) {
		if (valueDefinitionSuccessAndScopedEvaluationContext.getT1().isError()) {
			log.debug("| |- Error in the value definitions of the policy set. Policy evaluated INDETERMINATE.: {}",
					valueDefinitionSuccessAndScopedEvaluationContext.getT1().getMessage());
			return Flux.just(AuthorizationDecision.INDETERMINATE);
		}
		return getAlgorithm().combinePolicies(policies, valueDefinitionSuccessAndScopedEvaluationContext.getT2());

	}

	private Function<? super Tuple2<Val, EvaluationContext>, Publisher<? extends Tuple2<Val, EvaluationContext>>> evaluateValueDefinitions(
			int valueDefinitionId) {
		if (valueDefinitions == null || valueDefinitionId == valueDefinitions.size()) {
			return Flux::just;
		}
		return valueDefinitionSuccessAndScopedEvaluationContext -> evaluateValueDefinition(
				valueDefinitionSuccessAndScopedEvaluationContext.getT1(), valueDefinitions.get(valueDefinitionId),
				valueDefinitionSuccessAndScopedEvaluationContext.getT2())
						.switchMap(evaluateValueDefinitions(valueDefinitionId + 1));
	}

	private Flux<Tuple2<Val, EvaluationContext>> evaluateValueDefinition(Val previousResult,
			ValueDefinition valueDefinition, EvaluationContext ctx) {
		if (previousResult.isError()) {
			return Flux.just(Tuples.of(previousResult, ctx));
		}
		return valueDefinition.getEval().evaluate(ctx, Val.UNDEFINED)
				.concatMap(derivePolicySetScopeEvaluationContext(valueDefinition, ctx));
	}

	private Function<? super Val, ? extends Publisher<? extends Tuple2<Val, EvaluationContext>>> derivePolicySetScopeEvaluationContext(
			ValueDefinition valueDefinition, EvaluationContext ctx) {
		return evaluatedValue -> {
			if (evaluatedValue.isError()) {
				return Flux.just(Tuples.of(evaluatedValue, ctx));
			}
			if (evaluatedValue.isDefined()) {
				try {
					var scopedCtx = ctx.withEnvironmentVariable(valueDefinition.getName(), evaluatedValue.get());
					return Flux.just(Tuples.of(Val.TRUE, scopedCtx));
				}
				catch (PolicyEvaluationException e) {
					return Flux.just(Tuples.of(Val.error(e), ctx));
				}
			}
			return Flux.just(Tuples.of(Val.TRUE, ctx));
		};
	}

}
