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
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.ResultNode;
import reactor.core.publisher.Flux;

/**
 * Implements the application of a wildcard step to a previous value, e.g
 * 'value.*'.
 *
 * Grammar: Step: '.' ({WildcardStep} '*') ;
 */
public class WildcardStepImplCustom extends WildcardStepImpl {

	private static final String WILDCARD_ACCESS_TYPE_MISMATCH = "Type mismatch. Wildcard access expects object or array, but got: '%s'.";

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
		final Optional<JsonNode> previousResultNode = previousResult.getNode();
		if (!previousResultNode.isPresent()) {
			throw new PolicyEvaluationException(WILDCARD_ACCESS_TYPE_MISMATCH, "undefined");
		}
		if (previousResultNode.get().isArray()) {
			return previousResult;
		} else if (previousResultNode.get().isObject()) {
			final ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
			final Iterator<String> iterator = previousResultNode.get().fieldNames();
			while (iterator.hasNext()) {
				final String key = iterator.next();
				resultList.add(new JsonNodeWithParentObject(Optional.of(previousResultNode.get().get(key)),
						previousResultNode, key));
			}
			return new ArrayResultNode(resultList);
		} else {
			throw new PolicyEvaluationException(WILDCARD_ACCESS_TYPE_MISMATCH, previousResultNode.get().getNodeType());
		}
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		return Flux.just(previousResult);
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
