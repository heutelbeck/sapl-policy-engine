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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.ResultNode;
import reactor.core.publisher.Flux;

/**
 * Implements the application of an index step to a previous array value, e.g. 'arr[2]'.
 *
 * Grammar: Step: '[' Subscript ']' ;
 *
 * Subscript returns Step: {IndexStep} index=JSONNUMBER ;
 */
public class IndexStepImplCustom extends IndexStepImpl {

	private static final String INDEX_ACCESS_TYPE_MISMATCH = "Type mismatch. Accessing a JSON array index [%s] expects array value, but got: '%s'.";

	private static final String INDEX_ACCESS_NOT_FOUND = "Index not found. Failed to access index [%s].";

	@Override
	public Flux<ResultNode> apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		try {
			return Flux.just(apply(previousResult));
		}
		catch (PolicyEvaluationException e) {
			return Flux.error(e);
		}
	}

	private ResultNode apply(AbstractAnnotatedJsonNode previousResult) throws PolicyEvaluationException {
		final JsonNode previousResultNode = previousResult.getNode().get();
		if (!previousResultNode.isArray()) {
			throw new PolicyEvaluationException(
					String.format(INDEX_ACCESS_TYPE_MISMATCH, getIndex(), previousResultNode.getNodeType()));
		}

		final int arrayLength = previousResultNode.size();
		int index = computeAndValidateIndex(arrayLength);
		return new JsonNodeWithParentArray(Optional.of(previousResultNode.get(index)), previousResult.getNode(), index);
	}

	private int computeAndValidateIndex(int arrayLength) throws PolicyEvaluationException {
		int index = getIndex().intValue();
		if (index < 0) {
			index += arrayLength;
		}
		if (index < 0 || index >= arrayLength) {
			throw new PolicyEvaluationException(String.format(INDEX_ACCESS_NOT_FOUND, index));
		}
		return index;
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		try {
			return Flux.just(apply(previousResult));
		}
		catch (PolicyEvaluationException e) {
			return Flux.error(e);
		}
	}

	private ResultNode apply(ArrayResultNode previousResult) throws PolicyEvaluationException {
		final List<AbstractAnnotatedJsonNode> previousResultNodes = previousResult.getNodes();

		final int arrayLength = previousResultNodes.size();
		int index = computeAndValidateIndex(arrayLength);
		return previousResultNodes.get(index);
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = 17;
		hash = 37 * hash + Objects.hashCode(getClass().getTypeName());
		hash = 37 * hash + Objects.hashCode(getIndex());
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
		final IndexStepImplCustom otherImpl = (IndexStepImplCustom) other;
		return Objects.equals(getIndex(), otherImpl.getIndex());
	}

}
