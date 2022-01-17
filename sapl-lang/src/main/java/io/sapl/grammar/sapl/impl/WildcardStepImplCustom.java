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
 * Implements the application of a wildcard step to a previous value, e.g
 * 'value.*'.
 *
 * Grammar: Step: '.' ({WildcardStep} '*') ;
 */
@Slf4j
public class WildcardStepImplCustom extends WildcardStepImpl {

	private static final String WILDCARD_ACCESS_TYPE_MISMATCH = "Type mismatch. Wildcard access expects object or array, but got: '%s'.";

	@Override
	public Flux<Val> apply(@NonNull Val parentValue) {
		if (parentValue.isError() || parentValue.isArray()) {
			return Flux.just(parentValue);
		}
		if (!parentValue.isObject()) {
			return Val.errorFlux(WILDCARD_ACCESS_TYPE_MISMATCH, parentValue);
		}
		var object      = parentValue.get();
		var resultArray = Val.JSON.arrayNode();
		var iter        = object.fields();
		while (iter.hasNext()) {
			var entry = iter.next();
			resultArray.add(entry.getValue());
		}
		return Flux.just(Val.of(resultArray));
	}

	@Override
	public Flux<Val> applyFilterStatement(
			@NonNull Val parentValue,
			int stepId,
			@NonNull FilterStatement statement) {
		return doApplyFilterStatement(parentValue, stepId, statement);
	}

	public static Flux<Val> doApplyFilterStatement(
			Val parentValue,
			int stepId,
			FilterStatement statement) {
		log.trace("apply wildcard step to: {}", parentValue);
		if (parentValue.isObject()) {
			return applyFilterStatementToObject(parentValue.getObjectNode(), stepId, statement);
		}

		if (parentValue.isArray()) {
			return applyFilterStatementToArray(parentValue.getArrayNode(), stepId, statement);
		}

		// this means the element does not get selected does not get filtered
		return Flux.just(parentValue);
	}

	private static Flux<Val> applyFilterStatementToObject(
			ObjectNode object,
			int stepId,
			FilterStatement statement) {
		var fieldFluxes = new ArrayList<Flux<Tuple2<String, Val>>>(object.size());
		var fields      = object.fields();
		while (fields.hasNext()) {
			var field = fields.next();
			log.trace("field matches .*: {}", field);
			if (stepId == statement.getTarget().getSteps().size() - 1) {
				// this was the final step. apply filter
				log.trace("final step. select and filter!");
				fieldFluxes.add(FilterComponentImplCustom.applyFilterFunction(Val.of(field.getValue()),
						statement.getArguments(), statement.getFsteps(), statement.isEach())
						.contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, Val.of(object)))
						.map(val -> Tuples.of(field.getKey(), val)));
			} else {
				// there are more steps. descent with them
				log.trace("this step was successful. descent with next step...");
				fieldFluxes.add(statement.getTarget().getSteps().get(stepId + 1)
						.applyFilterStatement(Val.of(field.getValue()), stepId + 1, statement)
						.map(val -> Tuples.of(field.getKey(), val)));
			}
		}
		return Flux.combineLatest(fieldFluxes, RepackageUtil::recombineObject);
	}

	private static Flux<Val> applyFilterStatementToArray(
			ArrayNode array,
			int stepId,
			FilterStatement statement) {
		var elementFluxes = new ArrayList<Flux<Val>>(array.size());
		var elements      = array.elements();
		while (elements.hasNext()) {
			var element = elements.next();
			log.trace("element matches .*: {}", element);
			if (stepId == statement.getTarget().getSteps().size() - 1) {
				// this was the final step. apply filter
				log.trace("final step. select and filter!");
				elementFluxes.add(FilterComponentImplCustom.applyFilterFunction(Val.of(element),
						statement.getArguments(), statement.getFsteps(), statement.isEach())
						.contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, Val.of(array))));
			} else {
				// there are more steps. descent with them
				log.trace("this step was successful. descent with next step...");
				elementFluxes.add(statement.getTarget().getSteps().get(stepId + 1).applyFilterStatement(Val.of(element),
						stepId + 1, statement));
			}
		}
		return Flux.combineLatest(elementFluxes, RepackageUtil::recombineArray);
	}

}
