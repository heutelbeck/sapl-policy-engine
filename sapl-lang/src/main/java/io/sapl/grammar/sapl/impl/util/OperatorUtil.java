/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.grammar.sapl.impl.util;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.BinaryOperator;
import io.sapl.grammar.sapl.UnaryOperator;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class OperatorUtil {

    public static Flux<Val> operator(Object demandingComponent, BinaryOperator operator,
            java.util.function.BiFunction<Object, Val, Val> leftTypeRequirement,
            java.util.function.BiFunction<Object, Val, Val> rightTypeRequirement,
            java.util.function.BinaryOperator<Val> transformation) {
        var left  = operator.getLeft().evaluate().map(v -> leftTypeRequirement.apply(demandingComponent, v));
        var right = operator.getRight().evaluate().map(v -> rightTypeRequirement.apply(demandingComponent, v));
        return Flux.combineLatest(left, right, errorOrDo(transformation));
    }

    public static Flux<Val> arithmeticOperator(Object demandingComponent, BinaryOperator operator,
            java.util.function.BinaryOperator<Val> transformation) {
        return operator(demandingComponent, operator, Val::requireBigDecimal, Val::requireBigDecimal, transformation);
    }

    public static Flux<Val> arithmeticOperator(Object demandingComponent, UnaryOperator unaryOperator,
            java.util.function.UnaryOperator<Val> transformation) {
        return operator(demandingComponent, unaryOperator, Val::requireBigDecimal, transformation);
    }

    public static Flux<Val> booleanOperator(Object demandingComponent, BinaryOperator operator,
            java.util.function.BinaryOperator<Val> transformation) {
        return operator(demandingComponent, operator, Val::requireBoolean, Val::requireBoolean, transformation);
    }

    public static Flux<Val> operator(Object demandingComponent, BinaryOperator operator,
            java.util.function.BinaryOperator<Val> transformation) {
        return operator(demandingComponent, operator, (d, v) -> v, (d, v) -> v, transformation);
    }

    public static Flux<Val> operator(Object demandingComponent, UnaryOperator unaryOperator,
            java.util.function.BiFunction<Object, Val, Val> typeRequirement,
            java.util.function.UnaryOperator<Val> transformation) {
        return unaryOperator.getExpression().evaluate().map(v -> typeRequirement.apply(demandingComponent, v))
                .map(errorOrDo(transformation));
    }

    public static java.util.function.BinaryOperator<Val> errorOrDo(
            java.util.function.BinaryOperator<Val> transformation) {
        return (left, right) -> {
            if (left.isError())
                return left;
            if (right.isError())
                return right;
            return transformation.apply(left, right);
        };
    }

    public static java.util.function.UnaryOperator<Val> errorOrDo(
            java.util.function.UnaryOperator<Val> transformation) {
        return value -> {
            if (value.isError())
                return value;
            return transformation.apply(value);
        };
    }

}
