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

import org.eclipse.xtext.EcoreUtil2;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

/**
 * Implements the lazy boolean OR operator, written as '||' in Expressions.
 *
 * Grammar: Addition returns Expression: Multiplication (({Or.left=current}
 * '||') right=Multiplication)* ;
 */
public class OrImplCustom extends OrImpl {

	private static final String LAZY_OPERATOR_IN_TARGET = "Lazy OR operator is not allowed in the target";

	@Override
	public Flux<Val> evaluate(EvaluationContext ctx, boolean isBody, Val relativeNode) {
		if (EcoreUtil2.getContainerOfType(this, PolicyBody.class) == null) {
			// due to the constraints in indexing policy documents, lazy evaluation is not
			// allowed in target expressions.
			return Flux.error(new PolicyEvaluationException(LAZY_OPERATOR_IN_TARGET));
		}
		final Flux<Boolean> left = getLeft().evaluate(ctx, isBody, relativeNode).flatMap(Val::toBoolean);
		return left.switchMap(leftResult -> {
			if (Boolean.FALSE.equals(leftResult)) {
				return getRight().evaluate(ctx, isBody, relativeNode).flatMap(Val::toBoolean);
			}
			return Flux.just(Boolean.TRUE);
		}).map(Val::of).distinctUntilChanged();
	}

}
