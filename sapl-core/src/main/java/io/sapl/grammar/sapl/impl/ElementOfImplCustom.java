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
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Implements the evaluation of the array operation. It checks if a value is
 * contained in an array.
 */
public class ElementOfImplCustom extends io.sapl.grammar.sapl.impl.ElementOfImpl {

	private static final int HASH_PRIME_01 = 17;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public Flux<Optional<JsonNode>> evaluate(EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		final Flux<JsonNode> value = getLeft().evaluate(ctx, isBody, relativeNode).flatMap(Value::toJsonNode);
		final Flux<ArrayNode> array = getRight().evaluate(ctx, isBody, relativeNode).flatMap(Value::toArrayNode);
		return Flux.combineLatest(value, array, Tuples::of).map(this::elementOf).distinctUntilChanged();
	}

	/**
	 * Checks if the value is contained in the array 'undefined' is never contained
	 * in any array.
	 * 
	 * @param value an arbritary value
	 * @param array an Array
	 * @return true if value contained in array
	 */
	private Optional<JsonNode> elementOf(Tuple2<JsonNode, ArrayNode> tuple) {
		for (JsonNode arrayItem : tuple.getT2()) {
			// numerically equivalent numbers may be noted differently in JSON.
			// This equality is checked for here as well.
			if (tuple.getT1().equals(arrayItem) || (tuple.getT1().isNumber() && arrayItem.isNumber()
					&& tuple.getT1().decimalValue().compareTo(arrayItem.decimalValue()) == 0)) {
				return Value.ofTrue();
			}
		}
		return Value.ofFalse();
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_01 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_01 * hash + ((getLeft() == null) ? 0 : getLeft().hash(imports));
		hash = HASH_PRIME_01 * hash + ((getRight() == null) ? 0 : getRight().hash(imports));
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
		final ElementOfImplCustom otherImpl = (ElementOfImplCustom) other;
		if ((getLeft() == null) ? (getLeft() != otherImpl.getLeft())
				: !getLeft().isEqualTo(otherImpl.getLeft(), otherImports, imports)) {
			return false;
		}
		return (getRight() == null) ? (getRight() == otherImpl.getRight())
				: getRight().isEqualTo(otherImpl.getRight(), otherImports, imports);
	}

}
