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
import java.util.Optional;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

public class PlusImplCustom extends io.sapl.grammar.sapl.impl.PlusImpl {

	private static final String STRING_CONCATENATION_TYPE_MISMATCH = "String concatenation requires the right side to evaluate to a string, but got %s.";

	private static final int HASH_PRIME_05 = 31;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public Flux<Optional<JsonNode>> evaluate(EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		final Flux<Optional<JsonNode>> left = getLeft().evaluate(ctx, isBody, relativeNode);
		final Flux<Optional<JsonNode>> right = getRight().evaluate(ctx, isBody, relativeNode);
		return Flux.combineLatest(left, right, this::plus).distinctUntilChanged();
	}

	private Optional<JsonNode> plus(Optional<JsonNode> left, Optional<JsonNode> right) {
		assertDefined(left, right);
		if (left.get().isTextual()) {
			if (!right.get().isTextual()) {
				throw Exceptions.propagate(new PolicyEvaluationException(
						String.format(STRING_CONCATENATION_TYPE_MISMATCH, right.get().getNodeType())));
			}
			return Value.text(left.get().asText().concat(right.get().asText()));
		} else {
			assertNumber(left, right);
			return Value.num(left.get().decimalValue().add(right.get().decimalValue()));
		}
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_05 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_05 * hash + ((getLeft() == null) ? 0 : getLeft().hash(imports));
		hash = HASH_PRIME_05 * hash + ((getRight() == null) ? 0 : getRight().hash(imports));
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
		final PlusImplCustom otherImpl = (PlusImplCustom) other;
		if ((getLeft() == null) ? (getLeft() != otherImpl.getLeft())
				: !getLeft().isEqualTo(otherImpl.getLeft(), otherImports, imports)) {
			return false;
		}
		return (getRight() == null) ? (getRight() == otherImpl.getRight())
				: getRight().isEqualTo(otherImpl.getRight(), otherImports, imports);
	}

}
