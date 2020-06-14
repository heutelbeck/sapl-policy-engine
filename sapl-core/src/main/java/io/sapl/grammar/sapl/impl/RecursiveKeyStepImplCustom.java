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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
import reactor.core.publisher.Flux;

/**
 * Implements the application of a recursive key step to a previous value, e.g.
 * 'obj..name' or 'arr..["name"]'.
 *
 * Grammar: Step: '..' ({RecursiveKeyStep} (id=ID | '[' id=STRING ']')) ;
 */
public class RecursiveKeyStepImplCustom extends RecursiveKeyStepImpl {

	private static final String UNDEFINED_ARRAY_ELEMENT = "JSON does not support undefined array elements.";

	@Override
	public Flux<ResultNode> apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			Val relativeNode) {
		return Flux.just(apply(previousResult));
	}

	private ResultNode apply(AbstractAnnotatedJsonNode previousResult) {
		if (previousResult.getNode().isUndefined()
				|| (!previousResult.getNode().get().isArray() && !previousResult.getNode().get().isObject())) {
			return new JsonNodeWithoutParent(Val.undefined());
		}
		return new ArrayResultNode(resolveRecursive(previousResult.getNode().get()));
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			Val relativeNode) {
		try {
			return Flux.just(apply(previousResult));
		} catch (PolicyEvaluationException e) {
			return Flux.error(e);
		}
	}

	private ResultNode apply(ArrayResultNode previousResult) throws PolicyEvaluationException {
		final List<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		for (AbstractAnnotatedJsonNode child : previousResult) {
			if (child.getNode().isDefined()) {
				resultList.addAll(resolveRecursive(child.getNode().get()));
			} else {
				// this case should never happen, because undefined values cannot be added
				// to an array
				throw new PolicyEvaluationException(UNDEFINED_ARRAY_ELEMENT);
			}
		}
		return new ArrayResultNode(resultList);
	}

	private List<AbstractAnnotatedJsonNode> resolveRecursive(JsonNode node) {
		final List<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		if (node.has(id)) {
			resultList.add(new JsonNodeWithParentObject(Val.of(node.get(id)), Val.of(node), id));
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
		hash = 37 * hash + Objects.hashCode(getId());
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
		final RecursiveKeyStepImplCustom otherImpl = (RecursiveKeyStepImplCustom) other;
		return Objects.equals(getId(), otherImpl.getId());
	}

}
