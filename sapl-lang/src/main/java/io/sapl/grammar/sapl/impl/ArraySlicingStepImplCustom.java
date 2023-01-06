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

import java.math.BigDecimal;
import java.util.function.BiFunction;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.ArraySlicingStep;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.impl.util.FilterAlgorithmUtil;
import io.sapl.grammar.sapl.impl.util.SelectorUtil;
import io.sapl.grammar.sapl.impl.util.StepAlgorithmUtil;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the application of an array slicing step to a previous array
 * value, e.g. {@code 'arr[4:12:2]'}.
 *
 * Grammar:
 *
 * {@code Step: '[' Subscript ']' ;
 *
 * Subscript returns Step: {ArraySlicingStep} index=JSONNUMBER? ':'
 * to=JSONNUMBER? (':' step=JSONNUMBER)? ;}
 */
public class ArraySlicingStepImplCustom extends ArraySlicingStepImpl {

	private static final String STEP_ZERO = "Step must not be zero.";

	@Override
	public Flux<Val> apply(@NonNull Val parentValue) {
		return StepAlgorithmUtil.applyOnArray(parentValue, SelectorUtil.toArrayElementSelector(isInSlice(parentValue)),
				parameters(), ArraySlicingStep.class);
	}

	@Override
	public Flux<Val> applyFilterStatement(@NonNull Val unfilteredValue, int stepId,
			@NonNull FilterStatement statement) {
		return FilterAlgorithmUtil.applyFilterOnArray(unfilteredValue, stepId,
				SelectorUtil.toArrayElementSelector(isInSlice(unfilteredValue)), statement, parameters(),
				ArraySlicingStep.class);
	}

	private String parameters() {
		return "[" + getIndex() + ":" + getTo() + ":" + getStep() + "]";
	}

	private BiFunction<Integer, Val, Boolean> isInSlice(Val parentValue) {
		return (i, __) -> {
			var arraySize = parentValue.getArrayNode().size();
			// normalize slicing ranges
			var step = getStep() == null ? BigDecimal.ONE.intValue() : getStep().intValue();
			if (step == 0) {
				throw new PolicyEvaluationException(STEP_ZERO);
			}

			var index = getIndex() == null ? 0 : getIndex().intValue();
			if (index < 0) {
				index += arraySize;
			}
			var to = getTo() == null ? arraySize : getTo().intValue();
			if (to < 0) {
				to += arraySize;
			}

			return isInNormalizedSlice(i, index, to, step);
		};
	}

	private boolean isInNormalizedSlice(int i, int from, int to, int step) {
		if (i < from || i >= to) {
			return false;
		}
		if (step > 0) {
			return (i - from) % step == 0;
		}
		return (to - i) % step == 0;
	}

}
