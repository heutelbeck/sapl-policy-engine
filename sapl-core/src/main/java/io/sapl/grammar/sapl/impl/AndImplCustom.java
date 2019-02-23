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

/**
 * Implements the lazy boolean AND '&&' operator.
 */
public class AndImplCustom extends io.sapl.grammar.sapl.impl.AndImpl {

	private static final String LAZY_OPERATOR_IN_TARGET = "Lazy AND operator is not allowed in the target";

	private static final int HASH_PRIME_04 = 29;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public Flux<Optional<JsonNode>> evaluate(EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		if (!isBody) {
			// due to the constraints in indexing policy documents, lazy evaluation is not
			// allowed in target expressions.
			return Flux.error(new PolicyEvaluationException(LAZY_OPERATOR_IN_TARGET));
		}
		final Flux<Optional<JsonNode>> left = getLeft().evaluate(ctx, isBody, relativeNode);
		final Flux<Optional<JsonNode>> right = getRight().evaluate(ctx, isBody, relativeNode);
		return Flux.combineLatest(left, right, this::and).distinctUntilChanged();
	}

	private Optional<JsonNode> and(Optional<JsonNode> left, Optional<JsonNode> right) {
		try {
			assertBoolean(left);
			if (!left.get().asBoolean()) {
				return Optional.of((JsonNode) JSON.booleanNode(false));
			}
			assertBoolean(right);
			return Optional.of((JsonNode) JSON.booleanNode(right.get().asBoolean()));
		} catch (PolicyEvaluationException e) {
			throw Exceptions.propagate(e);
		}
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
		final AndImplCustom otherImpl = (AndImplCustom) other;
		if ((getLeft() == null) ? (getLeft() != otherImpl.getLeft())
				: !getLeft().isEqualTo(otherImpl.getLeft(), otherImports, imports)) {
			return false;
		}
		return (getRight() == null) ? (getRight() == otherImpl.getRight())
				: getRight().isEqualTo(otherImpl.getRight(), otherImports, imports);
	}

}
