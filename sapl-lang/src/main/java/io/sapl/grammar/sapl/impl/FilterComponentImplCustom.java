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
import java.util.Arrays;

import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.interpreter.EvaluationContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class FilterComponentImplCustom extends FilterComponentImpl {

	protected static final String FILTERS_CANNOT_BE_APPLIED_TO_UNDEFINED_VALUES = "Filters cannot be applied to undefined values.";
	private static final String TYPE_MISMATCH = "Type mismatch error. Cannot use 'each' keyword with non-array values. Value type was: ";

	public static Flux<Val> applyFilterFunction(Val unfilteredValue, Arguments arguments, String functionName,
			EvaluationContext ctx, Val relativeNode, boolean each) {
		log.trace("apply filter '{}' to {}", functionName, unfilteredValue);
		if (unfilteredValue.isError()) {
			return Flux.just(unfilteredValue);
		}
		if (unfilteredValue.isUndefined()) {
			return Val.errorFlux(FILTERS_CANNOT_BE_APPLIED_TO_UNDEFINED_VALUES);
		}

		if (!each) {
			return FunctionUtil.combineArgumentFluxes(arguments, ctx, relativeNode).concatMap(parameters -> FunctionUtil
					.evaluateFunctionWithLeftHandArgumentMono(functionName, ctx, unfilteredValue, parameters));
		}

		// "|- each" may only applied to arrays
		if (!unfilteredValue.isArray()) {
			return Val.errorFlux(TYPE_MISMATCH + unfilteredValue.getValType());
		}

		var rootArray = (ArrayNode) unfilteredValue.get();
		var argumentFluxes = FunctionUtil.combineArgumentFluxes(arguments, ctx, relativeNode);
		return argumentFluxes.concatMap(parameters -> {
			var elementsEvaluations = new ArrayList<Mono<Val>>(rootArray.size());
			for (var element : rootArray) {
				elementsEvaluations.add(FunctionUtil.evaluateFunctionWithLeftHandArgumentMono(functionName, ctx,
						Val.of(element), parameters));
			}
			return Flux.combineLatest(elementsEvaluations, e -> Arrays.copyOf(e, e.length, Val[].class))
					.map(RepackageUtil::recombineArray);
		});
	}

}
