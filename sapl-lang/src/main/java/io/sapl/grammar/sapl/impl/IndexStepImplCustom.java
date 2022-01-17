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

import java.math.BigDecimal;
import java.util.ArrayList;

import com.fasterxml.jackson.core.TreeNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Implements the application of an index step to a previous array value, e.g.
 * 'arr[2]'.
 *
 * Grammar: {@code Step: '[' Subscript ']' ;
 *
 * Subscript returns Step: {IndexStep} index=JSONNUMBER ;}
 */
@Slf4j
public class IndexStepImplCustom extends IndexStepImpl {

	private static final String TYPE_MISMATCH_CAN_ONLY_ACCESS_ARRAYS_BY_INDEX_GOT_S = "Type mismatch. Can only access arrays by index, got: %s";

	private static final String INDEX_OUT_OF_BOUNDS_INDEX_MUST_BE_BETWEEN_0_AND_D_WAS_D = "Index out of bounds. Index must be between 0 and %d, was: %d ";

	@Override
	public Flux<Val> apply(@NonNull Val parentValue) {
		if (parentValue.isError()) {
			return Flux.just(parentValue);
		}
		if (!parentValue.isArray()) {
			return Val.errorFlux(TYPE_MISMATCH_CAN_ONLY_ACCESS_ARRAYS_BY_INDEX_GOT_S, parentValue);
		}
		var array = parentValue.getArrayNode();
		var idx   = normalizeIndex(index, array);
		if (idx < 0 || idx >= array.size()) {
			return Val.errorFlux(INDEX_OUT_OF_BOUNDS_INDEX_MUST_BE_BETWEEN_0_AND_D_WAS_D, array.size(), idx);
		}
		return Flux.just(Val.of(array.get(idx)));
	}

	private static int normalizeIndex(BigDecimal index, TreeNode array) {
		// handle negative index values
		var idx = index.intValue();
		if (idx < 0) {
			idx = array.size() + idx;
		}
		return idx;
	}

	@Override
	public Flux<Val> applyFilterStatement(
			@NonNull Val parentValue,
			int stepId,
			@NonNull FilterStatement statement) {
		return doApplyFilterStatement(index, parentValue, stepId, statement);
	}

	public static Flux<Val> doApplyFilterStatement(
			BigDecimal index,
			Val parentValue,
			int stepId,
			FilterStatement statement) {
		log.trace("apply index step [{}] to: {}", index, parentValue);
		if (!parentValue.isArray()) {
			// this means the element does not get selected does not get filtered
			return Flux.just(parentValue);
		}
		var array = parentValue.getArrayNode();
		var idx   = normalizeIndex(index, array);
		if (idx < 0 || idx >= array.size()) {
			// this means the element does not get selected does not get filtered
			return Flux.just(parentValue);
		}
		var elementFluxes = new ArrayList<Flux<Val>>(array.size());
		for (var i = 0; i < array.size(); i++) {
			var element = array.get(i);
			log.trace("inspect element [{}]={}", i, element);
			if (i == idx) {
				log.trace("selected. [{}]={}", i, element);
				if (stepId == statement.getTarget().getSteps().size() - 1) {
					// this was the final step. apply filter
					log.trace("final step. apply filter!");
					elementFluxes.add(
							FilterComponentImplCustom.applyFilterFunction(Val.of(element), statement.getArguments(),
									statement.getFsteps(), statement.isEach())
									.contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, parentValue)));
				} else {
					// there are more steps. descent with them
					log.trace("this step was successful. descent with next step...");
					elementFluxes.add(statement.getTarget().getSteps().get(stepId + 1)
							.applyFilterStatement(Val.of(element), stepId + 1, statement));
				}
			} else {
				log.trace("[{}] not selected. Just return as is. Not affected by filtering.", i);
				elementFluxes.add(Flux.just(Val.of(element)));
			}
		}
		return Flux.combineLatest(elementFluxes, RepackageUtil::recombineArray);
	}

}
