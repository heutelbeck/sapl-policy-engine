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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.ResultNode;
import reactor.core.publisher.Flux;

public class AttributeUnionStepImplCustom extends AttributeUnionStepImpl {

	private static final String UNION_TYPE_MISMATCH = "Type mismatch.";

	private static final int HASH_PRIME_03 = 23;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public Flux<ResultNode> apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		try {
			return Flux.just(apply(previousResult));
		} catch (PolicyEvaluationException e) {
			return Flux.error(e);
		}
	}

	private ResultNode apply(AbstractAnnotatedJsonNode previousResult) throws PolicyEvaluationException {
		final JsonNode previousResultNode = previousResult.getNode()
				.orElseThrow(() -> new PolicyEvaluationException(UNION_TYPE_MISMATCH));
		if (!previousResultNode.isObject()) {
			throw new PolicyEvaluationException(UNION_TYPE_MISMATCH);
		}

		final List<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		final Set<String> attributes = new HashSet<>(getAttributes());

		final Iterator<String> iterator = previousResultNode.fieldNames();
		while (iterator.hasNext()) {
			final String key = iterator.next();
			if (attributes.contains(key)) {
				resultList.add(new JsonNodeWithParentObject(Optional.of(previousResultNode.get(key)),
						previousResult.getNode(), key));
			}
		}
		return new ArrayResultNode(resultList);
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		return Flux.error(new PolicyEvaluationException(UNION_TYPE_MISMATCH));
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_03 * hash + Objects.hashCode(getClass().getTypeName());
		for (String attribute : getAttributes()) {
			hash = HASH_PRIME_03 * hash + Objects.hashCode(attribute);
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
		final AttributeUnionStepImplCustom otherImpl = (AttributeUnionStepImplCustom) other;
		if (getAttributes().size() != otherImpl.getAttributes().size()) {
			return false;
		}
		ListIterator<String> left = getAttributes().listIterator();
		ListIterator<String> right = otherImpl.getAttributes().listIterator();
		while (left.hasNext()) {
			String lhs = left.next();
			String rhs = right.next();
			if (!Objects.equals(lhs, rhs)) {
				return false;
			}
		}
		return true;
	}

}
