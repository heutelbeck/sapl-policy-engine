/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

/**
 * Implements the lazy boolean AND operator, written as '&amp;&amp;' in Expressions.
 *
 * Grammar: Multiplication returns Expression: Comparison (({And.left=current} '&amp;&amp;')
 * right=Comparison)* ;
 */
public class AndImplCustom extends AndImpl {

	private static final String LAZY_OPERATOR_IN_TARGET = "Lazy AND operator is not allowed in the target";

	@Override
	public Flux<Optional<JsonNode>> evaluate(EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		if (!isBody) {
			// due to the constraints in indexing policy documents, lazy evaluation is not
			// allowed in target expressions.
			return Flux.error(new PolicyEvaluationException(LAZY_OPERATOR_IN_TARGET));
		}

		final Flux<Boolean> left = getLeft().evaluate(ctx, isBody, relativeNode).flatMap(Value::toBoolean);
		return left.switchMap(leftResult -> {
			// Lazy evaluation of the right expression
			if (Boolean.TRUE.equals(leftResult)) {
				return getRight().evaluate(ctx, isBody, relativeNode).flatMap(Value::toBoolean);
			}
			return Flux.just(Boolean.FALSE);
		}).map(Value::of).distinctUntilChanged();
	}

}
