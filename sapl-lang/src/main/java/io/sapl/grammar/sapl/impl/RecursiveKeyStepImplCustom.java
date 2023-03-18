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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Traced;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.RecursiveKeyStep;
import io.sapl.grammar.sapl.impl.util.FilterAlgorithmUtil;
import io.sapl.grammar.sapl.impl.util.RepackageUtil;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Implements the application of a recursive key step to a previous value, e.g.
 * {@code 'obj..name' or 'arr..["name"]'}.
 *
 * Grammar: {@code Step: '..' ({RecursiveKeyStep} (id=ID | '[' id=STRING ']'))
 * ;}
 */
public class RecursiveKeyStepImplCustom extends RecursiveKeyStepImpl {

	public Flux<Val> apply(@NonNull Val parentValue) {
		return Flux.just(applyToValue(parentValue).withTrace(RecursiveKeyStep.class,
				Map.of("parentValue", parentValue, "key", Val.of(id))));
	}

	public Val applyToValue(@NonNull Val parentValue) {
		if (parentValue.isError()) {
			return parentValue;
		}
		if (parentValue.isUndefined()) {
			return Val.ofEmptyArray();
		}
		return Val.of(collect(parentValue.get(), Val.JSON.arrayNode()));
	}

	private ArrayNode collect(JsonNode node, ArrayNode results) {
		if (node.isArray()) {
			for (var item : node) {
				collect(item, results);
			}
		} else if (node.isObject()) {
			if (node.has(id)) {
				results.add(node.get(id));
			}
			var iter = node.fields();
			while (iter.hasNext()) {
				var item = iter.next().getValue();
				collect(item, results);
			}
		}
		return results;
	}

	@Override
	public Flux<Val> applyFilterStatement(@NonNull Val unfilteredValue, int stepId,
			@NonNull FilterStatement statement) {
		return applyKeyStepFilterStatement(id, unfilteredValue, stepId, statement);
	}

	private static Flux<Val> applyKeyStepFilterStatement(String id, Val unfilteredValue, int stepId,
			FilterStatement statement) {
		if (unfilteredValue.isObject()) {
			return applyFilterStatementToObject(id, unfilteredValue, stepId, statement);
		}

		if (unfilteredValue.isArray()) {
			return applyFilterStatementToArray(id, unfilteredValue, stepId, statement);
		}

		// this means the element does not get selected does not get filtered
		return Flux.just(unfilteredValue.withTrace(RecursiveKeyStep.class,
				Map.of("unfilteredValue", unfilteredValue, "key", Val.of(id))));
	}

	private static Flux<Val> applyFilterStatementToObject(String id, Val unfilteredValue, int stepId,
			FilterStatement statement) {
		var object      = unfilteredValue.getObjectNode();
		var fieldFluxes = new ArrayList<Flux<Tuple2<String, Val>>>(object.size());
		var fields      = object.fields();

		while (fields.hasNext()) {
			var field = fields.next();
			var key   = field.getKey();
			var trace = new HashMap<String, Traced>();
			trace.put("unfilteredValue", unfilteredValue);
			trace.put("key", Val.of(id));
			trace.put("[\"+key+\"]", Val.of(key));
			var value = Val.of(field.getValue()).withTrace(RecursiveKeyStep.class, trace);
			if (field.getKey().equals(id)) {
				if (stepId == statement.getTarget().getSteps().size() - 1) {
					// this was the final step. apply filter
					fieldFluxes.add(FilterAlgorithmUtil
							.applyFilterFunction(value, statement.getArguments(), statement.getFsteps(),
									statement.isEach())
							.map(val -> Tuples.of(field.getKey(), val))
							.contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, unfilteredValue)));
				} else {
					// there are more steps. descent with them
					fieldFluxes.add(statement.getTarget().getSteps().get(stepId + 1)
							.applyFilterStatement(value, stepId + 1, statement)
							.map(val -> Tuples.of(field.getKey(), val)));
				}
			} else {
				// field not matching. Do recursive search for first match.
				fieldFluxes.add(applyKeyStepFilterStatement(id, value, stepId, statement)
						.map(val -> Tuples.of(field.getKey(), val)));
			}
		}
		return Flux.combineLatest(fieldFluxes, RepackageUtil::recombineObject);
	}

	private static Flux<Val> applyFilterStatementToArray(String id, Val unfilteredValue, int stepId,
			FilterStatement statement) {
		var array = unfilteredValue.getArrayNode();

		if (array.isEmpty()) {
			return Flux.just(unfilteredValue.withTrace(RecursiveKeyStep.class,
					Map.<String,Traced>of("unfilteredValue", unfilteredValue, "key", Val.of(id))));
		}
		var elementFluxes = new ArrayList<Flux<Val>>(array.size());
		var elements      = array.elements();
		var index         = 0;
		while (elements.hasNext()) {
			var trace = new HashMap<String, Traced>();
			trace.put("unfilteredValue", unfilteredValue);
			trace.put("key", Val.of(id));
			trace.put("index", Val.of(index++));
			var element = Val.of(elements.next()).withTrace(RecursiveKeyStep.class, trace);
			if (element.isObject()) {
				// array element is an object. apply this step to the object.
				elementFluxes.add(applyFilterStatementToObject(id, element, stepId, statement));
			} else {
				// array element not an object. Do recursive search for first match.
				elementFluxes.add(applyKeyStepFilterStatement(id, element, stepId, statement));
			}
		}
		return Flux.combineLatest(elementFluxes, RepackageUtil::recombineArray);
	}

}
