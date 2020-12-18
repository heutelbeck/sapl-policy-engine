/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.interpreter.EvaluationContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Implements the application of an attribute union step to a previous object
 * value, e.g. 'person["firstName", "lastName"]'.
 *
 * Grammar: Step: '[' Subscript ']' ;
 *
 * Subscript returns Step: {AttributeUnionStep} attributes+=STRING ','
 * attributes+=STRING (',' attributes+=STRING)* ;
 */
@Slf4j
public class AttributeUnionStepImplCustom extends AttributeUnionStepImpl {

	private static final String UNION_TYPE_MISMATCH = "Type mismatch. Attribute union can only be applied to JSON Objects. But had: %s";

	@Override
	public Flux<Val> apply(@NonNull Val parentValue, @NonNull EvaluationContext ctx, @NonNull Val relativeNode) {
		if (parentValue.isError()) {
			return Flux.just(parentValue);
		}
		if (!parentValue.isObject()) {
			return Val.errorFlux(UNION_TYPE_MISMATCH, parentValue);
		}

		var uniqueAttributes = removeDuplicates();
		var parentObject = parentValue.get();
		var result = Val.JSON.arrayNode();
		for (var attribute : uniqueAttributes) {
			addAttributeToResultIfPresent(attribute, parentObject, result);
		}
		return Flux.just(Val.of(result));
	}

	private static void addAttributeToResultIfPresent(String attribute, JsonNode parentObject, ArrayNode result) {
		if (parentObject.has(attribute)) {
			result.add(parentObject.get(attribute));
		}
	}

	private Set<String> removeDuplicates() {
		return new HashSet<>(attributes);
	}

	@Override
	public Flux<Val> applyFilterStatement(@NonNull Val parentValue, @NonNull EvaluationContext ctx,
			@NonNull Val relativeNode, int stepId, @NonNull FilterStatement statement) {
		log.trace("apply key union step '{}' to: {}", attributes, parentValue);
		if (!parentValue.isObject()) {
			// this means the element does not get selected does not get filtered
			return Flux.just(parentValue);
		}
		var uniqueAttributes = removeDuplicates();
		var object = parentValue.getObjectNode();
		var fieldFluxes = new ArrayList<Flux<Tuple2<String, Val>>>(object.size());
		var fields = object.fields();
		while (fields.hasNext()) {
			var field = fields.next();
			log.trace("inspect field {}", field);
			if (uniqueAttributes.contains(field.getKey())) {
				log.trace("field matches '{}'", field.getKey());
				if (stepId == statement.getTarget().getSteps().size() - 1) {
					// this was the final step. apply filter
					log.trace("final step. select and filter!");
					fieldFluxes
							.add(FilterComponentImplCustom
									.applyFilterFunction(Val.of(field.getValue()), statement.getArguments(),
											FunctionUtil.resolveAbsoluteFunctionName(statement.getFsteps(), ctx), ctx,
											parentValue, statement.isEach())
									.map(val -> Tuples.of(field.getKey(), val)));
				} else {
					// there are more steps. descent with them
					log.trace("this step was successful. descent with next step...");
					fieldFluxes.add(statement.getTarget().getSteps().get(stepId + 1)
							.applyFilterStatement(Val.of(field.getValue()), ctx, relativeNode, stepId + 1, statement)
							.map(val -> Tuples.of(field.getKey(), val)));
				}
			} else {
				log.trace("field not matching. just return it as it will not be affected by filtering");
				fieldFluxes.add(Flux.just(Tuples.of(field.getKey(), Val.of(field.getValue()))));
			}
		}
		return Flux.combineLatest(fieldFluxes, RepackageUtil::recombineObject);
	}

}
