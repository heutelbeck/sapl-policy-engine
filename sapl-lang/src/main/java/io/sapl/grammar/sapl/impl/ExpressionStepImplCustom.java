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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.interpreter.EvaluationContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Implements the expression subscript of an array (or object), written as
 * '[(Expression)]'.
 *
 * Returns the value of an attribute with a key or an array item with an index
 * specified by an expression. Expression must evaluate to a string or a number.
 * If Expression evaluates to a string, the selection can only be applied to an
 * object. If Expression evaluates to a number, the selection can only be
 * applied to an array.
 *
 * Example: The expression step can be used to refer to custom variables
 * (object.array[(anIndex+2)]) or apply custom functions
 * (object.array[(max_value(object.array))].
 *
 * Grammar: Step: ... | '[' Subscript ']' | ... Subscript returns Step: ... |
 * {ExpressionStep} '(' expression=Expression ')' | ...
 */
@Slf4j
public class ExpressionStepImplCustom extends ExpressionStepImpl {

	private static final String OBJECT_ACCESSS_TYPE_MISMATCH_EXPECT_A_STRING_WAS_S = "Object accesss type mismatch. Expect a string, was: %s ";
	private static final String INDEX_OUT_OF_BOUNDS_INDEX_MUST_BE_BETWEEN_0_AND_D_WAS_D = "Index out of bounds. Index must be between 0 and %d, was: %d ";
	private static final String ARRAY_ACCESSS_TYPE_MISMATCH_EXPECT_AN_INTEGER_WAS_S = "Array accesss type mismatch. Expect an integer, was: %s ";
	private static final String EXPRESSIONS_STEP_ONLY_APPLICABLE_TO_ARRAY_OR_OBJECT_WAS_S = "Expressions step only applicable to Array or Object. was: %s";

	@Override
	public Flux<Val> apply(@NonNull Val parentValue, @NonNull EvaluationContext ctx, @NonNull Val relativeNode) {
		if (parentValue.isError()) {
			return Flux.just(parentValue);
		}
		if (parentValue.isArray()) {
			return expression.evaluate(ctx, relativeNode)
					.map(index -> extractValueAt(parentValue.getArrayNode(), index));
		}
		if (parentValue.isObject()) {
			return expression.evaluate(ctx, relativeNode).map(index -> extractKey(parentValue.get(), index));
		}
		return Val.errorFlux(EXPRESSIONS_STEP_ONLY_APPLICABLE_TO_ARRAY_OR_OBJECT_WAS_S, parentValue);
	}

	@Override
	public Flux<Val> applyFilterStatement(@NonNull Val parentValue, @NonNull EvaluationContext ctx,
			@NonNull Val relativeNode, int stepId, @NonNull FilterStatement statement) {
		log.trace("apply expression step to: {}", parentValue);
		if (!parentValue.isArray() && !parentValue.isObject()) {
			// this means the element does not get selected does not get filtered
			return Flux.just(parentValue);
		}
		return expression.evaluate(ctx, relativeNode)
				.concatMap(key -> applyFilterStatement(key, parentValue, ctx, relativeNode, stepId, statement));
	}

	private Flux<Val> applyFilterStatement(Val key, Val parentValue, EvaluationContext ctx, Val relativeNode,
			int stepId, FilterStatement statement) {
		log.trace("apply expression result '{}'to: {}", key, parentValue);
		if (key.isTextual() && parentValue.isObject()) {
			// This is a KeyStep equivalent
			return KeyStepImplCustom.applyKeyStepFilterStatement(key.getText(), parentValue, ctx, relativeNode, stepId,
					statement);
		}
		if (key.isNumber() && parentValue.isArray()) {
			// This is an IndexStep equivalent
			return IndexStepImplCustom.applyFilterStatement(key.decimalValue(), parentValue, ctx, relativeNode, stepId,
					statement);
		}
		return Val.errorFlux("Type mismatch. Tried to access {} with {}", parentValue.getValType(), key.getValType());
	}

	private Val extractValueAt(ArrayNode array, Val index) {
		if (index.isError()) {
			return index;
		}
		if (!index.isNumber()) {
			return Val.error(ARRAY_ACCESSS_TYPE_MISMATCH_EXPECT_AN_INTEGER_WAS_S, index);
		}
		var idx = index.get().asInt();
		if (idx < 0 || idx > array.size()) {
			return Val.error(INDEX_OUT_OF_BOUNDS_INDEX_MUST_BE_BETWEEN_0_AND_D_WAS_D, array.size(), idx);
		}
		return Val.of(array.get(idx));
	}

	private Val extractKey(JsonNode object, Val key) {
		if (key.isError()) {
			return key;
		}
		if (!key.isTextual()) {
			return Val.error(OBJECT_ACCESSS_TYPE_MISMATCH_EXPECT_A_STRING_WAS_S, key);
		}
		var fieldName = key.get().asText();
		if (!object.has(fieldName)) {
			return Val.UNDEFINED;
		}
		return Val.of(object.get(fieldName));
	}

}
