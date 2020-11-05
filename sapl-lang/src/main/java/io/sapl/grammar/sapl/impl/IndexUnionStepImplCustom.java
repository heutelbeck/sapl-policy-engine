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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.ResultNode;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the application of an index union step to a previous array value,
 * e.g. 'arr[4, 7, 11]'.
 *
 * Grammar: Step: '[' Subscript ']' ;
 *
 * Subscript returns Step: {IndexUnionStep} indices+=JSONNUMBER ','
 * indices+=JSONNUMBER (',' indices+=JSONNUMBER)* ;
 */
public class IndexUnionStepImplCustom extends IndexUnionStepImpl {

	private static final String UNION_TYPE_MISMATCH = "Type mismatch.";

	@Override
	public Flux<ResultNode> apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx,
			@NonNull Val relativeNode) {
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
				resultList.add(new JsonNodeWithParentArray(Val.of(previousResultNode.get(index)),
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
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, @NonNull Val relativeNode) {
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

}
