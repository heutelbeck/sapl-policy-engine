/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.eclipse.emf.ecore.EObject;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.BinaryOperator;
import io.sapl.grammar.sapl.UnaryOperator;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class OperatorUtil {
    static final String UNDEFINED_VALUE_ERROR                      = "Undefined value error.";
    static final String OBJECT_OPERATION_TYPE_MISMATCH_S_ERROR     = "Type mismatch. Expected an object, but got %s.";
    static final String ARRAY_OPERATION_TYPE_MISMATCH_S_ERROR      = "Type mismatch. Expected an array, but got %s.";
    static final String BOOLEAN_OPERATION_TYPE_MISMATCH_S_ERROR    = "Type mismatch. Boolean operation expects boolean values, but got: '%s'.";
    static final String NUMBER_OPERATION_TYPE_MISMATCH_S_ERROR     = "Type mismatch. Number operation expects number values, but got: '%s'.";
    static final String TEXT_OPERATION_TYPE_MISMATCH_S_ERROR       = "Type mismatch. Text operation expects text values, but got: '%s'.";
    static final String ARITHMETIC_OPERATION_TYPE_MISMATCH_S_ERROR = "Type mismatch. Number operation expects number values, but got: '%s'.";

    public static Flux<Val> operator(EObject demandingComponent, BinaryOperator operator,
            java.util.function.BiFunction<EObject, Val, Val> leftTypeRequirement,
            java.util.function.BiFunction<EObject, Val, Val> rightTypeRequirement,
            java.util.function.BinaryOperator<Val> transformation) {
        final var left  = operator.getLeft().evaluate().map(v -> leftTypeRequirement.apply(demandingComponent, v));
        final var right = operator.getRight().evaluate().map(v -> rightTypeRequirement.apply(demandingComponent, v));
        return Flux.combineLatest(left, right, errorOrDo(transformation));
    }

    public static Flux<Val> arithmeticOperator(EObject demandingComponent, BinaryOperator operator,
            java.util.function.BinaryOperator<Val> transformation) {
        return operator(demandingComponent, operator, OperatorUtil::requireBigDecimal, OperatorUtil::requireBigDecimal,
                transformation);
    }

    public static Flux<Val> arithmeticOperator(EObject demandingComponent, UnaryOperator unaryOperator,
            java.util.function.UnaryOperator<Val> transformation) {
        return operator(demandingComponent, unaryOperator, OperatorUtil::requireBigDecimal, transformation);
    }

    public static Flux<Val> booleanOperator(EObject demandingComponent, BinaryOperator operator,
            java.util.function.BinaryOperator<Val> transformation) {
        return operator(demandingComponent, operator, OperatorUtil::requireBoolean, OperatorUtil::requireBoolean,
                transformation);
    }

    public static Flux<Val> operator(EObject demandingComponent, BinaryOperator operator,
            java.util.function.BinaryOperator<Val> transformation) {
        return operator(demandingComponent, operator, (d, v) -> v, (d, v) -> v, transformation);
    }

    public static Flux<Val> operator(EObject demandingComponent, UnaryOperator unaryOperator,
            java.util.function.BiFunction<EObject, Val, Val> typeRequirement,
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

    /**
     * Validation method to ensure a Val is a JsonNode, i.e., not undefined or an
     * error.
     * @param demandingComponent the EObject in the document 
     * @param value a Val
     * @return the input Val, or an error, if the input is not a JsonNode.
     */
    public static Val requireJsonNode(EObject demandingComponent, Val value) {
        if (value.isError()) {
            return value;
        }
        if (value.isDefined()) {
            return value;
        }
        return ErrorFactory.error(demandingComponent, UNDEFINED_VALUE_ERROR, Val.typeOf(value)).withTrace(Val.class,
                true, value);
    }

    /**
     * Validation method to ensure a Val is a Boolean.
     *
     * @param demandingComponent the EObject in the document 
     * @param value a Val
     * @return the input Val, or an error, if the input is not Boolean.
     */
    public static Val requireBoolean(EObject demandingComponent, Val value) {
        if (value.isError()) {
            return value;
        }
        if (!value.isBoolean()) {
            return ErrorFactory.error(demandingComponent, BOOLEAN_OPERATION_TYPE_MISMATCH_S_ERROR, Val.typeOf(value))
                    .withTrace(Val.class, true, value);
        }
        return value;
    }

    /**
     * Validation method to ensure a Val is a JSON array.
     *
     * @param demandingComponent the EObject in the document 
     * @param value a Val
     * @return the input Val, or an error, if the input is not an array.
     */
    public static Val requireArrayNode(EObject demandingComponent, Val value) {
        if (value.isError()) {
            return value;
        }
        if (value.isUndefined() || !value.get().isArray()) {
            return ErrorFactory.error(demandingComponent, ARRAY_OPERATION_TYPE_MISMATCH_S_ERROR, Val.typeOf(value))
                    .withTrace(Val.class, true, value);
        }
        return value;
    }

    /**
     * Validation method to ensure a Val is a JSON object.
     *
     * @param demandingComponent the EObject in the document 
     * @param value a Val
     * @return the input Val, or an error, if the input is not an object.
     */
    public static Val requireObjectNode(EObject demandingComponent, Val value) {
        if (value.isError()) {
            return value;
        }
        if (value.isUndefined() || !value.get().isObject()) {
            return ErrorFactory.error(demandingComponent, OBJECT_OPERATION_TYPE_MISMATCH_S_ERROR, Val.typeOf(value))
                    .withTrace(Val.class, true, value);
        }
        return value;
    }

    /**
     * Validation method to ensure a Val is a textual value.
     *
     * @param demandingComponent the EObject in the document 
     * @param value a Val
     * @return the input Val, or an error, if the input is not textual.
     */
    public static Val requireText(EObject demandingComponent, Val value) {
        if (value.isError()) {
            return value;
        }
        if (value.isUndefined() || !value.get().isTextual()) {
            return ErrorFactory.error(demandingComponent, TEXT_OPERATION_TYPE_MISMATCH_S_ERROR, Val.typeOf(value))
                    .withTrace(Val.class, true, value);
        }
        return value;
    }

    /**
     * Validation method to ensure a val is a numerical value.
     *
     * @param demandingComponent the EObject in the document 
     * @param value a Val
     * @return the input Val, or an error, if the input is not a number.
     */
    public static Val requireBigDecimal(EObject demandingComponent, Val value) {
        if (value.isError()) {
            return value;
        }
        if (value.isUndefined() || !value.get().isNumber()) {
            return ErrorFactory.error(demandingComponent, ARITHMETIC_OPERATION_TYPE_MISMATCH_S_ERROR, Val.typeOf(value))
                    .withTrace(Val.class, true, value);
        }
        return value;
    }

    /**
     * Validation method to ensure a val is a numerical value.
     *
     * @param demandingComponent the EObject in the document 
     * @param value a Val
     * @return the input Val, or an error, if the input is not a number.
     */
    public static Val requireNumber(EObject demandingComponent, Val value) {
        return requireBigDecimal(demandingComponent, value);
    }
}
