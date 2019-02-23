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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
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
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.ResultNode;
import reactor.core.publisher.Flux;

public class IndexUnionStepImplCustom extends IndexUnionStepImpl {

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
		final JsonNode previousResultNode = previousResult.getNode().get();
		if (!previousResultNode.isArray()) {
			throw new PolicyEvaluationException(UNION_TYPE_MISMATCH);
		}

		final int arrayLength = previousResultNode.size();
		final Set<Integer> indices = collectIndices(arrayLength);

		final ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		for (int index : indices) {
			if (previousResultNode.has(index)) {
				resultList.add(new JsonNodeWithParentArray(Optional.of(previousResultNode.get(index)),
						previousResult.getNode(), index));
			}
		}
		return new ArrayResultNode(resultList);
	}

	private Set<Integer> collectIndices(int arrayLength) {
		final Set<Integer> indices = new HashSet<>();
		for (BigDecimal index : getIndices()) {
			if (index.intValue() < 0) {
				indices.add(arrayLength + index.intValue());
			} else {
				indices.add(index.intValue());
			}
		}
		return indices;
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		return Flux.just(apply(previousResult));
	}

	private ResultNode apply(ArrayResultNode previousResult) {
		final List<AbstractAnnotatedJsonNode> nodes = previousResult.getNodes();
		final int arrayLength = nodes.size();

		final Set<Integer> indices = collectIndices(arrayLength);

		final ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		for (int index : indices) {
			if (index >= 0 && index < arrayLength) {
				resultList.add(nodes.get(index));
			}
		}
		return new ArrayResultNode(resultList);
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_03 * hash + Objects.hashCode(getClass().getTypeName());
		for (BigDecimal indice : getIndices()) {
			hash = HASH_PRIME_03 * hash + Objects.hashCode(indice);
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
		final IndexUnionStepImplCustom otherImpl = (IndexUnionStepImplCustom) other;
		if (getIndices().size() != otherImpl.getIndices().size()) {
			return false;
		}
		ListIterator<BigDecimal> left = getIndices().listIterator();
		ListIterator<BigDecimal> right = otherImpl.getIndices().listIterator();
		while (left.hasNext()) {
			BigDecimal lhs = left.next();
			BigDecimal rhs = right.next();
			if (!Objects.equals(lhs, rhs)) {
				return false;
			}
		}
		return true;
	}

}
