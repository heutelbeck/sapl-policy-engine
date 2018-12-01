/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.grammar.sapl.impl;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import org.eclipse.emf.ecore.EObject;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

public class EagerAndImplCustom extends io.sapl.grammar.sapl.impl.EagerAndImpl {

	private static final int HASH_PRIME_04 = 29;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public JsonNode evaluate(EvaluationContext ctx, boolean isBody, JsonNode relativeNode) throws PolicyEvaluationException {
		final JsonNode leftResult = getLeft().evaluate(ctx, isBody, relativeNode);
		final JsonNode rightResult = getRight().evaluate(ctx, isBody, relativeNode);

		assertBoolean(leftResult);
		assertBoolean(rightResult);

		return JSON.booleanNode(rightResult.asBoolean() && leftResult.asBoolean());
	}

	@Override
	public Flux<JsonNode> reactiveEvaluate(EvaluationContext ctx, boolean isBody, JsonNode relativeNode) {
		final Flux<JsonNode> leftResultFlux = getLeft().reactiveEvaluate(ctx, isBody, relativeNode);
		final Flux<JsonNode> rightResultFlux = getRight().reactiveEvaluate(ctx, isBody, relativeNode);

		return Flux.combineLatest(leftResultFlux, rightResultFlux,
				(leftResult, rightResult) -> {
					try {
						assertBoolean(leftResult);
						assertBoolean(rightResult);
						return (JsonNode) JSON.booleanNode(rightResult.asBoolean() && leftResult.asBoolean());
					}
					catch (PolicyEvaluationException e) {
						throw Exceptions.propagate(e);
					}
				})
				.onErrorResume(e -> Flux.error(Exceptions.unwrap(e)))
				.distinctUntilChanged();
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_04 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_04 * hash + ((getLeft() == null) ? 0 : getLeft().hash(imports));
		hash = HASH_PRIME_04 * hash + ((getRight() == null) ? 0 : getRight().hash(imports));
		return hash;
	}

	@Override
	public boolean isEqualTo(EObject other, Map<String, String> otherImports, Map<String, String> imports) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		final EagerAndImplCustom otherImpl = (EagerAndImplCustom) other;
		if ((getLeft() == null) ? (getLeft() != otherImpl.getLeft())
				: !getLeft().isEqualTo(otherImpl.getLeft(), otherImports, imports)) {
			return false;
		}
		return (getRight() == null) ? (getRight() == otherImpl.getRight())
				: getRight().isEqualTo(otherImpl.getRight(), otherImports, imports);
	}

}
