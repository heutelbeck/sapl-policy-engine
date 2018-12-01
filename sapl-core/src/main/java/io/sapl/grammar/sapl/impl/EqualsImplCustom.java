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
import reactor.core.publisher.Flux;

public class EqualsImplCustom extends io.sapl.grammar.sapl.impl.EqualsImpl {

	private static final int HASH_PRIME_02 = 19;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public JsonNode evaluate(EvaluationContext ctx, boolean isBody, JsonNode relativeNode) throws PolicyEvaluationException {
		final JsonNode left = getLeft().evaluate(ctx, isBody, relativeNode);
		final JsonNode right = getRight().evaluate(ctx, isBody, relativeNode);

		if (left.isNumber() && right.isNumber()) {
			return JSON.booleanNode(left.decimalValue().compareTo(right.decimalValue()) == 0);
		} else {
			return JSON.booleanNode(left.equals(right));
		}
	}

    @Override
    public Flux<JsonNode> reactiveEvaluate(EvaluationContext ctx, boolean isBody, JsonNode relativeNode) {
		final Flux<JsonNode> leftResultFlux = getLeft().reactiveEvaluate(ctx, isBody, relativeNode);
		final Flux<JsonNode> rightResultFlux = getRight().reactiveEvaluate(ctx, isBody, relativeNode);

		return Flux.combineLatest(leftResultFlux, rightResultFlux,
				(leftResult, rightResult) -> {
					if (leftResult.isNumber() && rightResult.isNumber()) {
						return JSON.booleanNode(leftResult.decimalValue().compareTo(rightResult.decimalValue()) == 0);
					} else {
						return (JsonNode) JSON.booleanNode(leftResult.equals(rightResult));
					}
				})
				.distinctUntilChanged();
    }

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_02 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_02 * hash + ((getLeft() == null) ? 0 : getLeft().hash(imports));
		hash = HASH_PRIME_02 * hash + ((getRight() == null) ? 0 : getRight().hash(imports));
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
		final EqualsImplCustom otherImpl = (EqualsImplCustom) other;
		if ((getLeft() == null) ? (getLeft() != otherImpl.getLeft())
				: !getLeft().isEqualTo(otherImpl.getLeft(), otherImports, imports)) {
			return false;
		}
		return (getRight() == null) ? (getRight() == otherImpl.getRight())
				: getRight().isEqualTo(otherImpl.getRight(), otherImports, imports);
	}

}
