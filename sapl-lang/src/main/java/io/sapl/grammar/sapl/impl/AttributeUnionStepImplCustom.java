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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.ResultNode;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the application of an attribute union step to a previous object
 * value, e.g. 'person["firstName", "lastName"]'.
 *
 * Grammar: Step: '[' Subscript ']' ;
 *
 * Subscript returns Step: {AttributeUnionStep} attributes+=STRING ','
 * attributes+=STRING (',' attributes+=STRING)* ;
 */
public class AttributeUnionStepImplCustom extends AttributeUnionStepImpl {

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
				resultList.add(new JsonNodeWithParentObject(Val.of(previousResultNode.get(key)),
						previousResult.getNode(), key));
			}
		}
		return new ArrayResultNode(resultList);
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, @NonNull Val relativeNode) {
		return Flux.error(new PolicyEvaluationException(UNION_TYPE_MISMATCH));
	}

}
