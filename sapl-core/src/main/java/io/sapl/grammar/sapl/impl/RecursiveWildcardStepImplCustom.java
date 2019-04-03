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
import java.util.Iterator;
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
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.ResultNode;
import reactor.core.publisher.Flux;

public class RecursiveWildcardStepImplCustom extends RecursiveWildcardStepImpl {

	private static final String CANNOT_DESCENT_ON_AN_UNDEFINED_VALUE = "Cannot descent on an undefined value.";

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
		return new ArrayResultNode(resolveRecursive(previousResult.getNode().get()));
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		try {
			return Flux.just(apply(previousResult));
		} catch (PolicyEvaluationException e) {
			return Flux.error(e);
		}
	}

	private ResultNode apply(ArrayResultNode previousResult) throws PolicyEvaluationException {
		final List<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		for (AbstractAnnotatedJsonNode child : previousResult) {
			if (child.getNode().isPresent()) {
				resultList.add(child);
				resultList.addAll(resolveRecursive(child.getNode().get()));
			} else {
				throw new PolicyEvaluationException(CANNOT_DESCENT_ON_AN_UNDEFINED_VALUE);
			}
		}
		return new ArrayResultNode(resultList);
	}

	private static List<AbstractAnnotatedJsonNode> resolveRecursive(JsonNode node) {
		final List<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		if (node.isArray()) {
			for (int i = 0; i < node.size(); i++) {
				resultList.add(new JsonNodeWithParentArray(Optional.of(node.get(i)), Optional.of(node), i));
				resultList.addAll(resolveRecursive(node.get(i)));
			}
		} else {
			final Iterator<String> it = node.fieldNames();
			while (it.hasNext()) {
				final String key = it.next();
				resultList.add(new JsonNodeWithParentObject(Optional.of(node.get(key)), Optional.of(node), key));
				resultList.addAll(resolveRecursive(node.get(key)));
			}
		}
		return resultList;
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = 17;
		hash = 37 * hash + Objects.hashCode(getClass().getTypeName());
		return hash;
	}

	@Override
	public boolean isEqualTo(EObject other, Map<String, String> otherImports, Map<String, String> imports) {
		if (this == other) {
			return true;
		}
		return !(other == null || getClass() != other.getClass());
	}

}
