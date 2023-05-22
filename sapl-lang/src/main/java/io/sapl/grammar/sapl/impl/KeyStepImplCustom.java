/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.util.Map;

import io.sapl.api.interpreter.Traced;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.KeyStep;
import io.sapl.grammar.sapl.impl.util.FilterAlgorithmUtil;
import io.sapl.grammar.sapl.impl.util.RepackageUtil;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Implements the application of a key step to a previous value, e.g
 * 'value.name'.
 * <p>
 * Grammar: Step: '.' ({KeyStep} id=ID) ;
 */
public class KeyStepImplCustom extends KeyStepImpl {

	@Override
	public Flux<Val> apply(@NonNull Val parentValue) {
		return Flux.just(applyToValue(parentValue, id).withTrace(KeyStep.class,
				Map.of("parentValue", parentValue, "id", Val.of(id))));
	}

	@Override
	public Flux<Val> applyFilterStatement(@NonNull Val parentValue, int stepId, @NonNull FilterStatement statement) {
		return applyKeyStepFilterStatement(id, parentValue, stepId, statement);
	}

	public static Val applyToValue(@NonNull Val parentValue, String id) {
		if (parentValue.isError()) {
			return parentValue;
		}

		if (!parentValue.isObject() && !parentValue.isArray()) {
			return Val.UNDEFINED;
		}

		if (parentValue.isObject()) {
			if (parentValue.get().has(id)) {
				return Val.of(parentValue.get().get(id));
			} else {
				return Val.UNDEFINED;
			}
		}

		var resultArray = Val.JSON.arrayNode();
		for (var value : parentValue.getArrayNode()) {
			if (value.has(id)) {
				resultArray.add(value.get(id));
			}
		}
		return Val.of(resultArray);
	}

	public static Flux<Val> applyKeyStepFilterStatement(String id, Val parentValue, int stepId,
			FilterStatement statement) {
		if (parentValue.isObject()) {
			return applyFilterStatementToObject(id, parentValue, stepId, statement);
		}

		if (parentValue.isArray()) {
			return applyFilterStatementToArray(id, parentValue, stepId, statement);
		}

		// this means the element does not get selected does not get filtered
		return Flux.just(parentValue);
	}

	private static Flux<Val> applyFilterStatementToObject(String id, Val unfilteredValue, int stepId,
			FilterStatement statement) {
		var object      = unfilteredValue.getObjectNode();
		var fieldFluxes = new ArrayList<Flux<Tuple2<String, Val>>>(object.size());
		var fields      = object.fields();
		while (fields.hasNext()) {
			var field = fields.next();
			var key   = field.getKey();
			var value = Val.of(field.getValue()).withTrace(KeyStep.class,
					Map.of("unfilteredValue", unfilteredValue, "key", Val.of(key)));
			if (field.getKey().equals(id)) {
				if (stepId == statement.getTarget().getSteps().size() - 1) {
					// this was the final step. apply filter
					fieldFluxes.add(FilterAlgorithmUtil
							.applyFilterFunction(value, statement.getArguments(), statement.getFsteps(),
									statement.isEach())
							.contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, Val.of(object)))
							.map(val -> Tuples.of(field.getKey(), val)));
				} else {
					// there are more steps. descent with them
					fieldFluxes.add(statement.getTarget().getSteps().get(stepId + 1)
							.applyFilterStatement(value, stepId + 1, statement)
							.map(val -> Tuples.of(field.getKey(), val)));
				}
			} else {
				// field not matching. just return it as it will not be affected by filtering
				fieldFluxes.add(Flux.just(Tuples.of(field.getKey(), value)));
			}
		}
		return Flux.combineLatest(fieldFluxes, RepackageUtil::recombineObject);
	}

	private static Flux<Val> applyFilterStatementToArray(String id, Val unfilteredValue, int stepId,
			FilterStatement statement) {
		var array = unfilteredValue.getArrayNode();
		if (array.isEmpty()) {
			return Flux.just(unfilteredValue.withTrace(KeyStep.class, Map.of("unfilteredValue", unfilteredValue)));
		}
		var elementFluxes = new ArrayList<Flux<Val>>(array.size());
		var elements      = array.elements();
		var i             = 0;
		while (elements.hasNext()) {
			var element = Val.of(elements.next()).withTrace(KeyStep.class,
					Map.<String,Traced>of("unfilteredValue", unfilteredValue, "index", Val.of(i++)));
			if (element.isObject()) {
				// array element is an object. apply this step to the object.
				elementFluxes.add(applyFilterStatementToObject(id, element, stepId, statement)
						.contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, Val.of(array))));
			} else {
				// array element not an object. just return it as it will not be affected by
				// filtering
				elementFluxes.add(Flux.just(element));
			}
		}
		return Flux.combineLatest(elementFluxes, RepackageUtil::recombineArray);
	}

}
