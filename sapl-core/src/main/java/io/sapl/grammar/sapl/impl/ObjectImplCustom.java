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

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.grammar.sapl.Pair;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

/**
 * Implementation of an object in SAPL.
 *
 * Grammar: Object returns Value: {Object} '{' (members+=Pair (',' members+=Pair)*)? '}' ;
 */
public class ObjectImplCustom extends ObjectImpl {

	/**
	 * The semantics of evaluating an object is as follows:
	 *
	 * An object may contain a list of attribute name-value pairs. To get the values of
	 * the individual attributes, these have to be recursively evaluated.
	 *
	 * Returning a Flux this means to subscribe to all attribute-value expression result
	 * Fluxes and to combineLatest into a new object each time one of the expression
	 * Fluxes emits a new value.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Flux<Optional<JsonNode>> evaluate(EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		// collect all attribute names (keys) and fluxes providing the evaluated values
		final List<String> keys = new ArrayList<>(getMembers().size());
		final List<Flux<Optional<JsonNode>>> valueFluxes = new ArrayList<>(getMembers().size());
		for (Pair member : getMembers()) {
			keys.add(member.getKey());
			valueFluxes.add(member.getValue().evaluate(ctx, isBody, relativeNode));
		}

		// handle the empty object
		if (valueFluxes.isEmpty()) {
			return Flux.just(Optional.of(JsonNodeFactory.instance.objectNode()));
		}

		// the indices of the keys correspond to the indices of the values, because
		// combineLatest() preserves the order of the given list of fluxes in the array
		// of values passed to the combinator function
		return Flux.combineLatest(valueFluxes, values -> {
			final ObjectNode result = JsonNodeFactory.instance.objectNode();
			// omit undefined fields
			IntStream.range(0, values.length).forEach(idx -> ((Optional<JsonNode>) values[idx])
					.ifPresent(val -> result.set(keys.get(idx), (JsonNode) val)));
			return Optional.of(result);
		});
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = 17;
		hash = 37 * hash + Objects.hashCode(getClass().getTypeName());
		for (Pair pair : getMembers()) {
			hash = 37 * hash + (Objects.hashCode(pair.getKey())
					^ ((pair.getValue() == null) ? 0 : pair.getValue().hash(imports)));
		}
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
		final ObjectImplCustom otherImpl = (ObjectImplCustom) other;
		if (getMembers().size() != otherImpl.getMembers().size()) {
			return false;
		}
		ListIterator<Pair> left = getMembers().listIterator();
		ListIterator<Pair> right = otherImpl.getMembers().listIterator();
		while (left.hasNext()) {
			Pair lhs = left.next();
			Pair rhs = right.next();
			if ((lhs == null) != (rhs == null)) {
				return false;
			}
			if (lhs == null) {
				continue;
			}
			if (!Objects.equals(lhs.getKey(), rhs.getKey())) {
				return false;
			}
			if ((lhs.getValue() == null) ? (lhs.getValue() != rhs.getValue())
					: !lhs.getValue().isEqualTo(rhs.getValue(), otherImports, imports)) {
				return false;
			}
		}
		return true;
	}

}
