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

import java.util.function.BiFunction;
import java.util.function.Function;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.BinaryOperator;
import io.sapl.grammar.sapl.UnaryOperator;
import io.sapl.interpreter.EvaluationContext;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class OperatorUtil {

	public static Flux<Val> operator(BinaryOperator operator, Function<Val, Val> leftTypeRequirement,
			Function<Val, Val> rightTypeRequirement, BiFunction<Val, Val, Val> transformation, EvaluationContext ctx,
			Val relativeNode) {
		var left = operator.getLeft().evaluate(ctx, relativeNode).map(leftTypeRequirement);
		var right = operator.getRight().evaluate(ctx, relativeNode).map(rightTypeRequirement);
		return Flux.combineLatest(left, right, errorOrDo(transformation));
	}

	public static Flux<Val> arithmeticOperator(BinaryOperator operator, BiFunction<Val, Val, Val> transformation,
			EvaluationContext ctx, Val relativeNode) {
		return operator(operator, Val::requireBigDecimal, Val::requireBigDecimal, transformation, ctx,
				relativeNode);
	}

	public static Flux<Val> arithmeticOperator(UnaryOperator unarayOperator, Function<Val, Val> transformation,
			EvaluationContext ctx, Val relativeNode) {
		return operator(unarayOperator, Val::requireBigDecimal, transformation, ctx, relativeNode);
	}

	public static Flux<Val> booleanOperator(BinaryOperator operator, BiFunction<Val, Val, Val> transformation,
			EvaluationContext ctx, Val relativeNode) {
		return operator(operator, Val::requireBoolean, Val::requireBoolean, transformation, ctx, relativeNode);
	}

	public static Flux<Val> operator(BinaryOperator operator, BiFunction<Val, Val, Val> transformation,
			EvaluationContext ctx, Val relativeNode) {
		return operator(operator, Function.identity(), Function.identity(), transformation, ctx, relativeNode);
	}

	public static Flux<Val> operator(UnaryOperator unarayOperator, Function<Val, Val> typeRequirement,
			Function<Val, Val> transformation, EvaluationContext ctx, Val relativeNode) {
		return unarayOperator.getExpression().evaluate(ctx, relativeNode).map(typeRequirement)
				.map(errorOrDo(transformation));
	}

	public static BiFunction<Val, Val, Val> errorOrDo(BiFunction<Val, Val, Val> transformation) {
		return (left, right) -> {
			if (left.isError())
				return left;
			if (right.isError())
				return right;
			return transformation.apply(left, right);
		};
	}

	public static Function<Val, Val> errorOrDo(Function<Val, Val> transformation) {
		return value -> {
			if (value.isError())
				return value;
			return transformation.apply(value);
		};
	}
}
