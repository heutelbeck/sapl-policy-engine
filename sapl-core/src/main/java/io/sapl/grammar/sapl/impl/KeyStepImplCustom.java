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
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
import reactor.core.publisher.Flux;

/**
 * Implements the application of a key step to a previous value, e.g
 * 'value.name'.
 *
 * Grammar: Step: '.' ({KeyStep} id=ID) ;
 */
public class KeyStepImplCustom extends KeyStepImpl {

	private static final String KEY_ACCESS_TYPE_MISMATCH = "Type mismatch. Accessing a JSON key '%s' is not possible on a null node.";

	@Override
	public Flux<ResultNode> apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			Val relativeNode) {
		try {
			return Flux.just(apply(previousResult));
		} catch (PolicyEvaluationException e) {
			return Flux.error(e);
		}
	}

	private ResultNode apply(AbstractAnnotatedJsonNode previousResult) throws PolicyEvaluationException {
		if (previousResult.getNode().isUndefined()) {
			return new JsonNodeWithParentObject(Val.undefined(), previousResult.getNode(), id);
		}
		final JsonNode previousResultNode = previousResult.getNode().get();
		if (previousResultNode.isObject()) {
			if (!previousResultNode.has(id)) {
				return new JsonNodeWithParentObject(Val.undefined(), previousResult.getNode(), id);
			}
			return new JsonNodeWithParentObject(Val.of(previousResultNode.get(id)), previousResult.getNode(), id);
		} else if (previousResultNode.isArray()) {
			return applyToJsonArray(previousResultNode);
		} else if (previousResultNode.isTextual()) {
			return new JsonNodeWithParentObject(Val.undefined(), previousResult.getNode(), id);
		} else if (previousResultNode.isNull()) {
			throw new PolicyEvaluationException(KEY_ACCESS_TYPE_MISMATCH, id);
		} else {
			return new JsonNodeWithoutParent(Val.undefined());
		}
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			Val relativeNode) {
		return Flux.just(apply(previousResult));
	}

	private ResultNode apply(ArrayResultNode previousResult) {
		return applyToJsonArray(previousResult.asJsonWithoutAnnotations().get());
	}

	private ArrayResultNode applyToJsonArray(Iterable<JsonNode> array) {
		final ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();

		for (JsonNode item : array) {
			if (item.isObject() && item.has(id)) {
				resultList.add(new JsonNodeWithParentObject(Val.of(item.get(id)), Val.of(item), id));
			}
		}
		return new ArrayResultNode(resultList);
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
		final KeyStepImplCustom otherImpl = (KeyStepImplCustom) other;
		return Objects.equals(getId(), otherImpl.getId());
	}

}
