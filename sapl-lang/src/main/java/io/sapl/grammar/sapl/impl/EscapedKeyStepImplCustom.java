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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Implements the application of a key step to a previous value, e.g
 * 'value.name'.
 * <p>
 * Grammar: {EscapedKeyStep} id=STRING
 */
@Slf4j
public class EscapedKeyStepImplCustom extends EscapedKeyStepImpl {

	@Override
	public Flux<Val> apply(@NonNull Val parentValue) {
		if (parentValue.isError()) {
			return Flux.just(parentValue);
		}

		if (!parentValue.isObject() && !parentValue.isArray()) {
			return Val.fluxOfUndefined();
		}

		if (parentValue.isObject()) {
			if (parentValue.get().has(id)) {
				return Flux.just(Val.of(parentValue.get().get(id)));
			} else {
				return Val.fluxOfUndefined();
			}
		}

		var resultArray = Val.JSON.arrayNode();
		for (var value : parentValue.getArrayNode()) {
			if (value.has(id)) {
				resultArray.add(value.get(id));
			}
		}
		return Flux.just(Val.of(resultArray));
	}

	@Override
	public Flux<Val> applyFilterStatement(
			@NonNull Val parentValue,
			int stepId,
			@NonNull FilterStatement statement) {
		return applyKeyStepFilterStatement(id, parentValue, stepId, statement);
	}

	public static Flux<Val> applyKeyStepFilterStatement(
			String id,
			Val parentValue,
			int stepId,
			FilterStatement statement) {
		log.trace("apply key step '{}' to: {}", id, parentValue);
		if (parentValue.isObject()) {
			return applyFilterStatementToObject(id, parentValue.getObjectNode(), stepId, statement);
		}

		if (parentValue.isArray()) {
			return applyFilterStatementToArray(id, parentValue.getArrayNode(), stepId, statement);
		}

		// this means the element does not get selected does not get filtered
		return Flux.just(parentValue);
	}

	private static Flux<Val> applyFilterStatementToObject(
			String id,
			ObjectNode object,
			int stepId,
			FilterStatement statement) {
		var fieldFluxes = new ArrayList<Flux<Tuple2<String, Val>>>(object.size());
		var fields      = object.fields();
		while (fields.hasNext()) {
			var field = fields.next();
			log.trace("inspect field {}", field);
			if (field.getKey().equals(id)) {
				log.trace("field matches '{}'", id);
				if (stepId == statement.getTarget().getSteps().size() - 1) {
					// this was the final step. apply filter
					log.trace("final step. select and filter!");
					fieldFluxes
							.add(FilterComponentImplCustom
									.applyFilterFunction(Val.of(field.getValue()), statement.getArguments(),
											statement.getFsteps(), statement.isEach())
									.contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, Val.of(object)))
									.map(val -> Tuples.of(field.getKey(), val)));
				} else {
					// there are more steps. descent with them
					log.trace("this step was successful. descent with next step...");
					fieldFluxes.add(statement.getTarget().getSteps().get(stepId + 1)
							.applyFilterStatement(Val.of(field.getValue()), stepId + 1, statement)
							.map(val -> Tuples.of(field.getKey(), val)));
				}
			} else {
				log.trace("field not matching. just return it as it will not be affected by filtering");
				fieldFluxes.add(Flux.just(Tuples.of(field.getKey(), Val.of(field.getValue()))));
			}
		}
		return Flux.combineLatest(fieldFluxes, RepackageUtil::recombineObject);
	}

	private static Flux<Val> applyFilterStatementToArray(
			String id,
			ArrayNode array,
			int stepId,
			FilterStatement statement) {
		if (array.isEmpty()) {
			return Flux.just(Val.ofEmptyArray());
		}
		var elementFluxes = new ArrayList<Flux<Val>>(array.size());
		var elements      = array.elements();
		while (elements.hasNext()) {
			var element = elements.next();
			log.trace("inspect element {}", element);
			if (element.isObject()) {
				log.trace("array element is an object. apply this step to the object.");
				elementFluxes.add(
						applyFilterStatementToObject(id, (ObjectNode) element, stepId, statement)
								.contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, Val.of(array))));
			} else {
				log.trace("array element not an object. just return it as it will not be affected by filtering");
				elementFluxes.add(Flux.just(Val.of(element)));
			}
		}
		return Flux.combineLatest(elementFluxes, RepackageUtil::recombineArray);
	}

}
