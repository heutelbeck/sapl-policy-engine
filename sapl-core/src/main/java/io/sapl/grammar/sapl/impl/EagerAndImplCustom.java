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

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

/**
 * Implements the eager boolean AND operator, written as '&amp;' in Expressions.
 *
 * Grammar: Multiplication returns Expression: Comparison (('&amp;' {EagerAnd.left=current})
 * right=Comparison)* ;
 */
public class EagerAndImplCustom extends EagerAndImpl {

	@Override
	public Flux<Optional<JsonNode>> evaluate(EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		final Flux<Boolean> left = getLeft().evaluate(ctx, isBody, relativeNode).flatMap(Value::toBoolean);
		final Flux<Boolean> right = getRight().evaluate(ctx, isBody, relativeNode).flatMap(Value::toBoolean);
		return Flux.combineLatest(left, right, Boolean::logicalAnd).map(Value::of).distinctUntilChanged();
	}

}
