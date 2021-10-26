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

import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.interpreter.EvaluationContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Implements the application of an array slicing step to a previous array value, e.g.
 * 'arr[4:12:2]'.
 *
 * Grammar: Step: '[' Subscript ']' ;
 *
 * Subscript returns Step: {ArraySlicingStep} index=JSONNUMBER? ':' to=JSONNUMBER? (':'
 * step=JSONNUMBER)? ;
 */
@Slf4j
public class ArraySlicingStepImplCustom extends ArraySlicingStepImpl {

	private static final String STEP_ZERO = "Step must not be zero.";

	private static final String INDEX_ACCESS_TYPE_MISMATCH = "Type mismatch. Accessing an JSON array index [%s] expects array value, but got: '%s'.";

	@Override
	public Flux<Val> apply(@NonNull Val parentValue, @NonNull EvaluationContext ctx, @NonNull Val relativeNode) {
		if (parentValue.isError()) {
			return Flux.just(parentValue);
		}
		if (!parentValue.isArray()) {
			return Val.errorFlux(INDEX_ACCESS_TYPE_MISMATCH, getIndex(), parentValue);
		}

		var array = (ArrayNode) parentValue.get();
		var step = getStep() == null ? BigDecimal.ONE.intValue() : getStep().intValue();
		if (step == 0) {
			return Val.errorFlux(STEP_ZERO);
		}
		var index = getIndex() == null ? 0 : getIndex().intValue();
		if (index < 0) {
			index = index + array.size();
		}
		var to = getTo() == null ? array.size() : getTo().intValue();
		if (to < 0) {
			to = to + array.size();
		}
		log.trace("after normalization [{},{},{}]", index, to, step);

		var resultArray = Val.JSON.arrayNode();
		for (int i = 0; i < array.size(); i++) {
			var element = array.get(i);
			if (isSelected(i, index, to, step)) {
				resultArray.add(element);
			}
		}
		return Flux.just(Val.of(resultArray));
	}

	private boolean isSelected(int i, int from, int to, int step) {
		if (i < from || i >= to) {
			return false;
		}
		if (step > 0) {
			return (i - from) % step == 0;
		}
		return (to - i) % step == 0;
	}

	@Override
	public Flux<Val> applyFilterStatement(@NonNull Val parentValue, @NonNull EvaluationContext ctx,
			@NonNull Val relativeNode, int stepId, @NonNull FilterStatement statement) {
		log.trace("apply array slicing step [{},{},{}] to: {}", getIndex(), getTo(), getStep(), parentValue);
		if (!parentValue.isArray()) {
			return Flux.just(parentValue);
		}
		var array = (ArrayNode) parentValue.get();
		var step = getStep() == null ? BigDecimal.ONE.intValue() : getStep().intValue();
		if (step == 0) {
			return Val.errorFlux(STEP_ZERO);
		}
		var index = getIndex() == null ? 0 : getIndex().intValue();
		if (index < 0) {
			index = index + array.size();
		}
		var to = getTo() == null ? array.size() : getTo().intValue();
		if (to < 0) {
			to = to + array.size();
		}
		log.trace("after normalization [{},{},{}]", index, to, step);
		if (array.isEmpty()) {
			return Flux.just(Val.ofEmptyArray());
		}
		var elementFluxes = new ArrayList<Flux<Val>>(array.size());
		for (int i = 0; i < array.size(); i++) {
			var element = array.get(i);
			if (isSelected(i, index, to, step)) {
				log.trace("array element [{}] selected.", i);
				if (stepId == statement.getTarget().getSteps().size() - 1) {
					// this was the final step. apply filter
					log.trace("final step. do filter...");
					elementFluxes.add(
							FilterComponentImplCustom.applyFilterFunction(Val.of(element), statement.getArguments(),
									FunctionUtil.resolveAbsoluteFunctionName(statement.getFsteps(), ctx), ctx,
									parentValue, statement.isEach()));
				}
				else {
					// there are more steps. descent with them
					log.trace("this step was successful. descent with next step...");
					elementFluxes.add(statement.getTarget().getSteps().get(stepId + 1)
							.applyFilterStatement(Val.of(element), ctx, relativeNode, stepId + 1, statement));
				}
			}
			else {
				log.trace("array element [{}] not selected. return as is", i);
				elementFluxes.add(Flux.just(Val.of(element)));
			}
		}
		return Flux.combineLatest(elementFluxes, RepackageUtil::recombineArray);
	}

}
