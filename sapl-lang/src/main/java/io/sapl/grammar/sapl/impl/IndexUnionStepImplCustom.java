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
import java.util.Set;

import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.interpreter.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Implements the application of an index union step to a previous array value,
 * e.g. 'arr[4, 7, 11]'.
 *
 * Grammar: Step: '[' Subscript ']' ;
 *
 * Subscript returns Step: {IndexUnionStep} indices+=JSONNUMBER ','
 * indices+=JSONNUMBER (',' indices+=JSONNUMBER)* ;
 */
@Slf4j
public class IndexUnionStepImplCustom extends IndexUnionStepImpl {

	private static final String TYPE_MISMATCH_CAN_ONLY_ACCESS_ARRAYS_BY_INDEX_GOT_S = "Type mismatch. Can only access arrays by index, got: %s";

	@Override
	public Flux<Val> apply(Val parentValue, EvaluationContext ctx, Val relativeNode) {
		if (parentValue.isError()) {
			return Flux.just(parentValue);
		}
		if (!parentValue.isArray()) {
			return Val.errorFlux(TYPE_MISMATCH_CAN_ONLY_ACCESS_ARRAYS_BY_INDEX_GOT_S, parentValue);
		}

		var array = parentValue.getArrayNode();
		// remove duplicates
		var uniqueIndices = uniqueIndices(array);
		var resultArray = Val.JSON.arrayNode();
		for (var index : uniqueIndices) {
			if (index >= 0 && index < array.size())
				resultArray.add(array.get(index));
		}
		return Flux.just(Val.of(resultArray));
	}

	private Set<Integer> uniqueIndices(ArrayNode array) {
		// remove duplicates
		var uniqueIndices = new HashSet<Integer>();
		for (var index : indices) {
			var idx = index.intValue();
			if (idx < 0) {
				uniqueIndices.add(array.size() + idx);
			} else {
				uniqueIndices.add(idx);
			}
		}
		return uniqueIndices;
	}

	@Override
	public Flux<Val> applyFilterStatement(Val parentValue, EvaluationContext ctx, Val relativeNode, int stepId,
			FilterStatement statement) {
		log.trace("apply index union step [{}] to: {}", indices, parentValue);
		if (!parentValue.isArray()) {
			// this means the element does not get selected does not get filtered
			return Flux.just(parentValue);
		}
		var array = parentValue.getArrayNode();
		var uniqueIndices = uniqueIndices(array);
		var elementFluxes = new ArrayList<Flux<Val>>(array.size());
		for (var i = 0; i < array.size(); i++) {
			var element = array.get(i);
			log.trace("inspect element [{}]={}", i, element);
			if (uniqueIndices.contains(i)) {
				log.trace("selected. [{}]={}", i, element);
				if (stepId == statement.getTarget().getSteps().size() - 1) {
					// this was the final step. apply filter
					log.trace("final step. apply filter!");
					elementFluxes.add(
							FilterComponentImplCustom.applyFilterFunction(Val.of(element), statement.getArguments(),
									FunctionUtil.resolveAbsoluteFunctionName(statement.getFsteps(), ctx), ctx,
									parentValue, statement.isEach()));
				} else {
					// there are more steps. descent with them
					log.trace("this step was successful. descent with next step...");
					elementFluxes.add(statement.getTarget().getSteps().get(stepId + 1)
							.applyFilterStatement(Val.of(element), ctx, relativeNode, stepId + 1, statement));
				}
			} else {
				log.trace("[{}] not selected. Just return as is. Not affected by filtering.", i);
				elementFluxes.add(Flux.just(Val.of(element)));
			}
		}
		return Flux.combineLatest(elementFluxes, RepackageUtil::recombineArray);
	}
}
