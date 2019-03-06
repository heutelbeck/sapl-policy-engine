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

public class RecursiveIndexStepImplCustom extends io.sapl.grammar.sapl.impl.RecursiveIndexStepImpl {

	private static final String WRONG_TYPE = "Recursive descent step can only be applied to an object or an array.";

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
		if (!previousResult.getNode().isPresent()
				|| (!previousResult.getNode().get().isArray() && !previousResult.getNode().get().isObject())) {
			throw new PolicyEvaluationException(WRONG_TYPE);
		}
		final ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		resultList.addAll(resolveRecursive(previousResult.getNode().get()));
		return new ArrayResultNode(resultList);
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		return Flux.just(apply(previousResult));
	}

	private ResultNode apply(ArrayResultNode previousResult) {
		final ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		for (AbstractAnnotatedJsonNode target : previousResult) {
			resultList.addAll(resolveRecursive(target.getNode().get()));
		}
		return new ArrayResultNode(resultList);
	}

	private ArrayList<AbstractAnnotatedJsonNode> resolveRecursive(JsonNode node) {
		final ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		int intIndex = index.intValue();

		if (node.isArray() && node.has(intIndex)) {
			resultList.add(new JsonNodeWithParentArray(Optional.of(node.get(intIndex)), Optional.of(node), intIndex));
		}
		for (JsonNode child : node) {
			resultList.addAll(resolveRecursive(child));
		}
		return resultList;
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
		final RecursiveIndexStepImplCustom otherImpl = (RecursiveIndexStepImplCustom) other;
		return Objects.equals(getIndex(), otherImpl.getIndex());
	}

}
