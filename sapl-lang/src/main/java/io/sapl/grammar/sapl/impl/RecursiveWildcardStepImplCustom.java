/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the application of a recursive wildcard step to a previous value,
 * e.g. {@code 'obj..*' or 'arr..[*]'}.
 *
 * Grammar: {@code Step: '..' ({RecursiveWildcardStep} ('*' | '[' '*' ']' )) ;}
 */
public class RecursiveWildcardStepImplCustom extends RecursiveWildcardStepImpl {

	private static final String CANNOT_DESCENT_ON_AN_UNDEFINED_VALUE = "Cannot descent on an undefined value.";

	@Override
	public Flux<Val> apply(@NonNull Val parentValue) {
		if (parentValue.isError()) {
			return Flux.just(parentValue);
		}
		if (parentValue.isUndefined()) {
			return Val.errorFlux(CANNOT_DESCENT_ON_AN_UNDEFINED_VALUE);
		}
		if (!parentValue.isArray() && !parentValue.isObject()) {
			return Flux.just(Val.ofEmptyArray());
		}
		return Flux.just(Val.of(collect(parentValue.get(), Val.JSON.arrayNode())));
	}

	private ArrayNode collect(JsonNode node, ArrayNode results) {
		if (node.isArray()) {
			for (var item : node) {
				if (item.isObject() || item.isArray()) {
					results.add(item);
				}
				collect(item, results);
			}
		} else if (node.isObject()) {
			var iter = node.fields();
			while (iter.hasNext()) {
				var item = iter.next().getValue();
				if (item.isObject() || item.isArray()) {
					results.add(item);
				}
				collect(item, results);
			}
		} else {
			results.add(node);
		}
		return results;
	}

	@Override
	public Flux<Val> applyFilterStatement(
			@NonNull Val parentValue,
			int stepId,
			@NonNull FilterStatement statement) {
		// This type of recursion does not translate well to filtering.
		// Basically just apply filter to top-level matches and do recursion with steps.
		// @.* is basically equivalent to @..* here.
		return WildcardStepImplCustom.doApplyFilterStatement(parentValue, stepId, statement);
	}

}
