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

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.ResultNode;
import org.eclipse.emf.ecore.EObject;
import reactor.core.publisher.Flux;

public class KeyStepImplCustom extends io.sapl.grammar.sapl.impl.KeyStepImpl {

	private static final String KEY_ACCESS_TYPE_MISMATCH = "Type mismatch. Accessing a JSON key '%s' expects object value, but got: '%s'.";
	private static final String KEY_ACCESS_NOT_FOUND = "Key not found. Failed to access JSON key '%s'.";

	private static final int HASH_PRIME_05 = 31;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public ResultNode apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody, JsonNode relativeNode) throws PolicyEvaluationException {
		final JsonNode previousResultNode = previousResult.getNode();

		if (previousResultNode.isObject()) {
			if (!previousResultNode.has(id)) {
				throw new PolicyEvaluationException(String.format(KEY_ACCESS_NOT_FOUND, id));
			}
			return new JsonNodeWithParentObject(previousResultNode.get(id), previousResultNode, id);
		} else if (previousResultNode.isArray()) {
			return applyToJsonArray(previousResultNode);
		} else {
			throw new PolicyEvaluationException(String.format(KEY_ACCESS_TYPE_MISMATCH, id, previousResultNode.getNodeType()));
		}
	}

	@Override
	public ResultNode apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody, JsonNode relativeNode) throws PolicyEvaluationException {
		return applyToJsonArray(previousResult.asJsonWithoutAnnotations());
	}

	@Override
	public Flux<ResultNode> reactiveApply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody, JsonNode relativeNode) {
		try {
			return Flux.just(apply(previousResult, ctx, isBody, relativeNode));
		}
		catch (PolicyEvaluationException e) {
			return Flux.error(e);
		}
	}

	@Override
	public Flux<ResultNode> reactiveApply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody, JsonNode relativeNode) {
		try {
			return Flux.just(apply(previousResult, ctx, isBody, relativeNode));
		}
		catch (PolicyEvaluationException e) {
			return Flux.error(e);
		}
	}

	private ArrayResultNode applyToJsonArray(Iterable<JsonNode> array) {
		final ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();

		for (JsonNode item : array) {
			if (item.isObject() && item.has(id)) {
				resultList.add(new JsonNodeWithParentObject(item.get(id), item, id));
			}
		}
		return new ArrayResultNode(resultList);
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_05 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_05 * hash + Objects.hashCode(getId());
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
