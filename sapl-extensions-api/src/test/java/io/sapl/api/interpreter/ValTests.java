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
package io.sapl.api.interpreter;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import reactor.test.StepVerifier;

class ValTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private static final String ERROR_MESSAGE = "Error Message";

    @Test
    void createError() {
        var error = Val.error(ERROR_MESSAGE);

        var sa = new SoftAssertions();
        sa.assertThat(error.getMessage()).isEqualTo(ERROR_MESSAGE);
        sa.assertThat(error.isError()).isTrue();
        sa.assertThat(error.isArray()).isFalse();
        sa.assertThat(error.isBigDecimal()).isFalse();
        sa.assertThat(error.isBoolean()).isFalse();
        sa.assertThat(error.isDefined()).isFalse();
        sa.assertThat(error.isDouble()).isFalse();
        sa.assertThat(error.isEmpty()).isFalse();
        sa.assertThat(error.isFloat()).isFalse();
        sa.assertThat(error.isLong()).isFalse();
        sa.assertThat(error.isFloatingPointNumber()).isFalse();
        sa.assertThat(error.isNull()).isFalse();
        sa.assertThat(error.isInt()).isFalse();
        sa.assertThat(error.isNumber()).isFalse();
        sa.assertThat(error.isUndefined()).isFalse();
        sa.assertThat(error.isObject()).isFalse();
        sa.assertThat(error.isValueNode()).isFalse();
        sa.assertThat(error.isTextual()).isFalse();
        sa.assertThat(error.isArray()).isFalse();
        sa.assertThat(error.noError()).isFalse();
        sa.assertAll();
    }

    @Test
    void notEqualComparisonTest() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.notEqual(Val.of("A"), Val.of("A"))).isEqualTo(Val.FALSE);
        sa.assertThat(Val.notEqual(Val.of("A"), Val.of("B"))).isEqualTo(Val.TRUE);
        sa.assertThat(Val.notEqual(Val.of(1.0D), Val.of(1))).isEqualTo(Val.FALSE);
        sa.assertThat(Val.notEqual(Val.of(1.0D), Val.of(1.1D))).isEqualTo(Val.TRUE);
        sa.assertThat(Val.notEqual(Val.of("X"), Val.of(1))).isEqualTo(Val.TRUE);
        sa.assertThat(Val.notEqual(Val.of(1.0D), Val.of("X"))).isEqualTo(Val.TRUE);
        sa.assertThat(Val.notEqual(Val.UNDEFINED, Val.error())).isEqualTo(Val.TRUE);
        sa.assertThat(Val.notEqual(Val.error(), Val.UNDEFINED)).isEqualTo(Val.TRUE);
        sa.assertThat(Val.notEqual(Val.UNDEFINED, Val.UNDEFINED)).isEqualTo(Val.FALSE);
        sa.assertAll();
    }

    @Test
    void equalComparisonTest() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.areEqual(Val.of("A"), Val.of("A"))).isEqualTo(Val.TRUE);
        sa.assertThat(Val.areEqual(Val.of("A"), Val.of("B"))).isEqualTo(Val.FALSE);
        sa.assertAll();
    }

    @Test
    void createErrorWithFormattedMessage() {
        var error = Val.error("MESSAGE STRING %d", 1);
        assertThat(error.getMessage()).isEqualTo("MESSAGE STRING 1");
    }

    @Test
    void createErrorWithNullMessage() {
        var error = Val.error((String) null);
        assertThat(error.getMessage()).isEqualTo(Val.UNKNOWN_ERROR);
    }

    @Test
    void createErrorWithNullCause() {
        var error = Val.error((Throwable) null);
        assertThat(error.getMessage()).isEqualTo(Val.UNKNOWN_ERROR);
    }

    @Test
    void createUnknownError() {
        var error = Val.error();
        assertThat(error.getMessage()).isEqualTo(Val.UNKNOWN_ERROR);
    }

    @Test
    void noError() {
        assertThat(Val.UNDEFINED.noError()).isTrue();
    }

    @Test
    void errorMessageFromNonError() {
        assertThatThrownBy(Val.UNDEFINED::getMessage).isInstanceOf(NoSuchElementException.class)
                .hasMessage(Val.VALUE_NOT_AN_ERROR);
    }

    @Test
    void gerValueFromError() {
        var value = Val.error(ERROR_MESSAGE);
        assertThatThrownBy(value::get).isInstanceOf(NoSuchElementException.class)
                .hasMessage(String.format(Val.VALUE_IS_AN_ERROR_S_ERROR, ERROR_MESSAGE));
    }

    @Test
    void gerValueFromUndefined() {
        assertThatThrownBy(Val.UNDEFINED::get).isInstanceOf(NoSuchElementException.class)
                .hasMessage(Val.VALUE_UNDEFINED_ERROR);
    }

    @Test
    void getValueFromValue() {
        var value = Val.TRUE;
        assertThatJson(value.get()).isBoolean().isEqualTo(true);
    }

    @Test
    void valOfNullJson() {
        var val = Val.of((JsonNode) null);
        assertThat(val.isUndefined()).isTrue();
    }

    @Test
    void valOfNull() {
        assertThat(Val.NULL.isNull()).isTrue();
    }

    @Test
    void valOfJsonValue() {
        var value = Val.of(JSON.booleanNode(true));
        assertThatJson(value.get()).isBoolean().isEqualTo(true);
    }

    @Test
    void valOfEmptyObject() {
        var value = Val.ofEmptyObject();
        assertThatJson(value.get()).isObject().isEmpty();
    }

    @Test
    void valOfEmptyArray() {
        var value = Val.ofEmptyArray();
        assertThatJson(value.get()).isArray().isEmpty();
    }

    @Test
    void errorOfThrowableNoMessage() {
        var error = Val.error(new RuntimeException());
        assertThat(error.getMessage()).isEqualTo("RuntimeException");
    }

    @Test
    void errorOfThrowableBlankMessage() {
        var error = Val.error(new RuntimeException(""));
        assertThat(error.getMessage()).isEqualTo("RuntimeException");
    }

    @Test
    void errorOfThrowableWithMessage() {
        var error = Val.error(new RuntimeException(ERROR_MESSAGE));
        assertThat(error.getMessage()).isEqualTo(ERROR_MESSAGE);
    }

    @Test
    void isUndefined() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.isUndefined()).isFalse();
        sa.assertThat(Val.UNDEFINED.isUndefined()).isTrue();
        sa.assertThat(Val.error().isUndefined()).isFalse();
        sa.assertAll();
    }

    @Test
    void isArray() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.isArray()).isFalse();
        sa.assertThat(Val.ofEmptyArray().isArray()).isTrue();
        sa.assertThat(Val.error().isArray()).isFalse();
        sa.assertAll();
    }

    @Test
    void isBigDecimal() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.isBigDecimal()).isFalse();
        sa.assertThat(Val.of(BigDecimal.ONE).isBigDecimal()).isTrue();
        sa.assertThat(Val.error().isBigDecimal()).isFalse();
        sa.assertAll();
    }

    @Test
    void isBigInteger() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.isBigInteger()).isFalse();
        sa.assertThat(Val.of(BigInteger.ONE).isBigInteger()).isTrue();
        sa.assertThat(Val.error().isBigInteger()).isFalse();
        sa.assertAll();
    }

    @Test
    void isBoolean() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.of(1).isBoolean()).isFalse();
        sa.assertThat(Val.TRUE.isBoolean()).isTrue();
        sa.assertThat(Val.error().isBoolean()).isFalse();
        sa.assertAll();
    }

    @Test
    void isDouble() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.of(1).isDouble()).isFalse();
        sa.assertThat(Val.of(1D).isDouble()).isTrue();
        sa.assertThat(Val.error().isDouble()).isFalse();
        sa.assertAll();
    }

    @Test
    void isFloatingPointNumber() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.of(1).isFloatingPointNumber()).isFalse();
        sa.assertThat(Val.of(1F).isFloatingPointNumber()).isTrue();
        sa.assertThat(Val.of(1F).isFloatingPointNumber()).isTrue();
        sa.assertThat(Val.of(new BigDecimal("2.2")).isFloatingPointNumber()).isTrue();
        sa.assertThat(Val.of(BigInteger.valueOf(10L)).isFloatingPointNumber()).isFalse();
        sa.assertThat(Val.error().isFloatingPointNumber()).isFalse();
        sa.assertAll();
    }

    @Test
    void isInt() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.of(1L).isInt()).isFalse();
        sa.assertThat(Val.of(1).isInt()).isTrue();
        sa.assertThat(Val.error().isInt()).isFalse();
        sa.assertAll();
    }

    @Test
    void isLong() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.of(1).isLong()).isFalse();
        sa.assertThat(Val.of(1L).isLong()).isTrue();
        sa.assertThat(Val.error().isLong()).isFalse();
        sa.assertAll();
    }

    @Test
    void isFloat() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.of(1).isFloat()).isFalse();
        sa.assertThat(Val.of(1F).isFloat()).isTrue();
        sa.assertThat(Val.error().isFloat()).isFalse();
        sa.assertAll();
    }

    @Test
    void isNull() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.of(1).isNull()).isFalse();
        sa.assertThat(Val.NULL.isNull()).isTrue();
        sa.assertThat(Val.error().isNull()).isFalse();
        sa.assertAll();
    }

    @Test
    void isNumber() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.of("").isNumber()).isFalse();
        sa.assertThat(Val.of(1).isNumber()).isTrue();
        sa.assertThat(Val.error().isNumber()).isFalse();
        sa.assertAll();
    }

    @Test
    void isObject() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.of("").isObject()).isFalse();
        sa.assertThat(Val.ofEmptyObject().isObject()).isTrue();
        sa.assertThat(Val.error().isObject()).isFalse();
        sa.assertAll();
    }

    @Test
    void isTextual() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.isTextual()).isFalse();
        sa.assertThat(Val.of("A").isTextual()).isTrue();
        sa.assertThat(Val.error().isTextual()).isFalse();
        sa.assertAll();
    }

    @Test
    void isValueNode() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.ofEmptyArray().isValueNode()).isFalse();
        sa.assertThat(Val.TRUE.isValueNode()).isTrue();
        sa.assertThat(Val.error().isValueNode()).isFalse();
        sa.assertAll();
    }

    @Test
    void isEmpty() {
        var array = JSON.arrayNode();
        array.add(false);
        var sa = new SoftAssertions();
        sa.assertThat(Val.UNDEFINED.isEmpty()).isFalse();
        sa.assertThat(Val.ofEmptyArray().isEmpty()).isTrue();
        sa.assertThat(Val.of(array).isEmpty()).isFalse();
        sa.assertThat(Val.error().isEmpty()).isFalse();
        sa.assertAll();
    }

    @Test
    void toStringTest() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.toString()).isEqualTo("true");
        sa.assertThat(Val.UNDEFINED.toString()).isEqualTo("undefined");
        sa.assertThat(Val.error().toString()).isEqualTo("ERROR[" + Val.UNKNOWN_ERROR + "]");
        sa.assertAll();
    }

    @Test
    void errorFlux() {
        StepVerifier.create(Val.errorFlux(ERROR_MESSAGE)).expectNext(Val.error(ERROR_MESSAGE)).verifyComplete();
    }

    @Test
    void errorMono() {
        StepVerifier.create(Val.errorMono(ERROR_MESSAGE)).expectNext(Val.error(ERROR_MESSAGE)).verifyComplete();
    }

    @Test
    void fluxOfBigDecimal() {
        StepVerifier.create(Val.fluxOf(BigDecimal.ONE)).expectNext(Val.of(BigDecimal.ONE)).verifyComplete();
    }

    @Test
    void fluxOfBigInteger() {
        StepVerifier.create(Val.fluxOf(BigInteger.ONE)).expectNext(Val.of(BigInteger.ONE)).verifyComplete();
    }

    @Test
    void fluxOfLong() {
        StepVerifier.create(Val.fluxOf(123L)).expectNext(Val.of(123L)).verifyComplete();
    }

    @Test
    void fluxOfText() {
        StepVerifier.create(Val.fluxOf("")).expectNext(Val.of("")).verifyComplete();
    }

    @Test
    void fluxOfTrue() {
        StepVerifier.create(Val.fluxOfTrue()).expectNext(Val.TRUE).verifyComplete();
    }

    @Test
    void fluxOfFalse() {
        StepVerifier.create(Val.fluxOfFalse()).expectNext(Val.FALSE).verifyComplete();
    }

    @Test
    void fluxOfUndefined() {
        StepVerifier.create(Val.fluxOfUndefined()).expectNext(Val.UNDEFINED).verifyComplete();
    }

    @Test
    void fluxOfNull() {
        StepVerifier.create(Val.fluxOfNull()).expectNext(Val.NULL).verifyComplete();
    }

    @Test
    void fluxOfBoolean() {
        StepVerifier.create(Val.fluxOf(true)).expectNext(Val.TRUE).verifyComplete();
        StepVerifier.create(Val.fluxOf(false)).expectNext(Val.FALSE).verifyComplete();
    }

    @Test
    void ofJsonBadJson() {
        assertThatThrownBy(() -> Val.ofJson("}{")).isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void ofJsonGoodJson() throws JsonProcessingException {
        var val = Val.ofJson("\"ABC\"");
        assertThatJson(val.get()).isString().isEqualTo("ABC");
    }

    @Test
    void optional() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.UNDEFINED.optional()).isEmpty();
        sa.assertThat(Val.error().optional()).isEmpty();
        sa.assertAll();
        assertThatJson(Val.TRUE.optional().get()).isBoolean().isTrue();
    }

    @Test
    void getValType() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.UNDEFINED.getValType()).isEqualTo(Val.UNDEFINED_LITERAL);
        sa.assertThat(Val.error().getValType()).isEqualTo(Val.ERROR_LITERAL);
        sa.assertThat(Val.TRUE.getValType()).isEqualTo("BOOLEAN");
        sa.assertAll();
    }

    @Test
    void toBoolean() {
        StepVerifier.create(Val.toBoolean(Val.TRUE)).expectNext(Boolean.TRUE).verifyComplete();
        StepVerifier.create(Val.toBoolean(Val.UNDEFINED)).expectError(PolicyEvaluationException.class).verify();
    }

    @Test
    void toBigDecimal() {
        StepVerifier.create(Val.toBigDecimal(Val.of(100L))).expectNext(BigDecimal.valueOf(100L)).verifyComplete();
        StepVerifier.create(Val.toBigDecimal(Val.TRUE)).expectError(PolicyEvaluationException.class).verify();
        StepVerifier.create(Val.toBigDecimal(Val.UNDEFINED)).expectError(PolicyEvaluationException.class).verify();
    }

    @Test
    void toText() {
        StepVerifier.create(Val.toText(Val.of(""))).expectNext("").verifyComplete();
        StepVerifier.create(Val.toText(Val.TRUE)).expectError(PolicyEvaluationException.class).verify();
        StepVerifier.create(Val.toText(Val.UNDEFINED)).expectError(PolicyEvaluationException.class).verify();
    }

    @Test
    void toObjectNode() {
        StepVerifier.create(Val.toObjectNode(Val.ofEmptyObject())).expectNext(JSON.objectNode()).verifyComplete();
        StepVerifier.create(Val.toObjectNode(Val.TRUE)).expectError(PolicyEvaluationException.class).verify();
        StepVerifier.create(Val.toObjectNode(Val.UNDEFINED)).expectError(PolicyEvaluationException.class).verify();
    }

    @Test
    void toArrayNode() {
        StepVerifier.create(Val.toArrayNode(Val.ofEmptyArray())).expectNext(JSON.arrayNode()).verifyComplete();
        StepVerifier.create(Val.toArrayNode(Val.TRUE)).expectError(PolicyEvaluationException.class).verify();
        StepVerifier.create(Val.toArrayNode(Val.UNDEFINED)).expectError(PolicyEvaluationException.class).verify();
    }

    @Test
    void toJsonNode() {
        StepVerifier.create(Val.toJsonNode(Val.ofEmptyArray())).expectNext(JSON.arrayNode()).verifyComplete();
        StepVerifier.create(Val.toJsonNode(Val.UNDEFINED)).expectError(PolicyEvaluationException.class).verify();
    }

    @Test
    void getBoolean() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.getBoolean()).isTrue();
        var value = Val.of("");
        sa.assertThatThrownBy(value::getBoolean).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.BOOLEAN_OPERATION_TYPE_MISMATCH_S_ERROR, "STRING"));
        sa.assertAll();
    }

    @Test
    void getLong() {
        var sa     = new SoftAssertions();
        var number = Val.of(123L);
        sa.assertThat(number.getLong()).isEqualTo(123L);
        var value = Val.of("");
        sa.assertThatThrownBy(value::getLong).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.NUMBER_OPERATION_TYPE_MISMATCH_S_ERROR, "STRING"));
        sa.assertAll();
    }

    @Test
    void requireBoolean() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.requireBoolean(Val.TRUE)).isEqualTo(Val.TRUE);
        var value = Val.requireBoolean(Val.UNDEFINED);
        sa.assertThat(value.isError()).isTrue();
        sa.assertAll();
    }

    @Test
    void requireJsonNode() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.requireJsonNode(Val.TRUE)).isEqualTo(Val.TRUE);
        sa.assertThat(Val.requireJsonNode(Val.error()).isError()).isTrue();
        sa.assertThat(Val.requireJsonNode(Val.UNDEFINED).isError()).isTrue();
        sa.assertAll();
    }

    @Test
    void requireArrayNode() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.requireArrayNode(Val.ofEmptyArray())).isEqualTo(Val.ofEmptyArray());
        sa.assertThat(Val.requireArrayNode(Val.of(1)).isError()).isTrue();
        sa.assertThat(Val.requireArrayNode(Val.error()).isError()).isTrue();
        sa.assertThat(Val.requireArrayNode(Val.UNDEFINED).isError()).isTrue();
        sa.assertAll();
    }

    @Test
    void requireObjectNode() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.requireObjectNode(Val.ofEmptyObject())).isEqualTo(Val.ofEmptyObject());
        sa.assertThat(Val.requireObjectNode(Val.of(1)).isError()).isTrue();
        sa.assertThat(Val.requireObjectNode(Val.error()).isError()).isTrue();
        sa.assertThat(Val.requireObjectNode(Val.UNDEFINED).isError()).isTrue();
        sa.assertAll();
    }

    @Test
    void requireText() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.requireText(Val.of(""))).isEqualTo(Val.of(""));
        sa.assertThat(Val.requireText(Val.of(1)).isError()).isTrue();
        sa.assertThat(Val.requireText(Val.error()).isError()).isTrue();
        sa.assertThat(Val.requireText(Val.UNDEFINED).isError()).isTrue();
        sa.assertAll();
    }

    @Test
    void requireNumber() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.requireNumber(Val.of(1))).isEqualTo(Val.of(1));
        sa.assertThat(Val.requireNumber(Val.of(1)).isError()).isFalse();
        sa.assertThat(Val.requireNumber(Val.of("")).isError()).isTrue();
        sa.assertThat(Val.requireNumber(Val.error()).isError()).isTrue();
        sa.assertThat(Val.requireNumber(Val.UNDEFINED).isError()).isTrue();
        sa.assertAll();
    }

    @Test
    void getText() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.getText()).isEqualTo("true");
        sa.assertThat(Val.UNDEFINED.getText()).isEqualTo(Val.UNDEFINED_LITERAL);
        sa.assertThat(Val.of("ABC").getText()).isEqualTo("ABC");
        sa.assertThat(Val.of(123).getText()).isEqualTo("123");
        sa.assertAll();
    }

    @Test
    void decimalValue() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.of(100D).decimalValue()).isEqualByComparingTo(new BigDecimal("100"));
        var errorValue = Val.error();
        sa.assertThatThrownBy(errorValue::decimalValue).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.NUMBER_OPERATION_TYPE_MISMATCH_S_ERROR, "ERROR"));
        sa.assertThatThrownBy(Val.UNDEFINED::decimalValue).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.NUMBER_OPERATION_TYPE_MISMATCH_S_ERROR, "undefined"));
        var emptyStringValue = Val.of("");
        sa.assertThatThrownBy(emptyStringValue::decimalValue).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.NUMBER_OPERATION_TYPE_MISMATCH_S_ERROR, "STRING"));
        sa.assertAll();
    }

    @Test
    void getObjectNode() {
        var sa = new SoftAssertions();
        sa.assertThatThrownBy(Val.UNDEFINED::getObjectNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.OBJECT_OPERATION_TYPE_MISMATCH_S_ERROR, "undefined"));
        var errorValue = Val.error();
        sa.assertThatThrownBy(errorValue::getObjectNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.OBJECT_OPERATION_TYPE_MISMATCH_S_ERROR, "ERROR"));
        var emptyStringValue = Val.of("");
        sa.assertThatThrownBy(emptyStringValue::getObjectNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.OBJECT_OPERATION_TYPE_MISMATCH_S_ERROR, "STRING"));
        sa.assertAll();
        assertThatJson(Val.ofEmptyObject().getObjectNode()).isObject();
    }

    @Test
    void getArrayNode() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.of(JSON.arrayNode()).getArrayNode()).isEqualTo(JSON.arrayNode());
        sa.assertThatThrownBy(Val.UNDEFINED::getArrayNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.ARRAY_OPERATION_TYPE_MISMATCH_S_ERROR, "undefined"));
        var errorValue = Val.error();
        sa.assertThatThrownBy(errorValue::getArrayNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.ARRAY_OPERATION_TYPE_MISMATCH_S_ERROR, "ERROR"));
        var emptyStringValue = Val.of("");
        sa.assertThatThrownBy(emptyStringValue::getArrayNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.ARRAY_OPERATION_TYPE_MISMATCH_S_ERROR, "STRING"));
        sa.assertAll();
        assertThatJson(Val.ofEmptyArray().getJsonNode()).isArray();
    }

    @Test
    void getJsonNode() {
        var sa = new SoftAssertions();
        sa.assertThatThrownBy(Val.UNDEFINED::getJsonNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(Val.UNDEFINED_VALUE_ERROR);
        var errorValue = Val.error();
        sa.assertThatThrownBy(errorValue::getJsonNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.VALUE_IS_AN_ERROR_S_ERROR, Val.UNKNOWN_ERROR));
        sa.assertAll();
        assertThatJson(Val.ofEmptyArray().getJsonNode()).isArray();
    }

    @Test
    void equalsTest() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE).isEqualTo(Val.TRUE);
        sa.assertThat(Val.UNDEFINED).isEqualTo(Val.UNDEFINED);
        sa.assertThat(Val.UNDEFINED).isEqualTo(Val.UNDEFINED.withTrace(getClass()));
        sa.assertThat(Val.error("ABC")).isEqualTo(Val.error("ABC"));
        sa.assertThat(Val.error("X")).isNotEqualTo(Val.error("Y"));
        sa.assertThat(Val.UNDEFINED).isNotEqualTo(Val.error("X"));
        sa.assertThat(Val.of(1L)).isNotEqualTo(Val.error("X"));
        sa.assertThat(Val.of(1L)).isNotEqualTo(BigInteger.valueOf(1L));
        sa.assertThat(Val.TRUE).isNotEqualTo(Val.UNDEFINED);
        sa.assertThat(Val.of("")).isEqualTo(Val.of(""));
        sa.assertThat(Val.TRUE).isNotEqualTo(Val.UNDEFINED);
        sa.assertThat(Val.UNDEFINED).isNotEqualTo(Val.TRUE);
        sa.assertThat(Val.FALSE).isEqualTo(Val.FALSE);
        sa.assertThat(Val.FALSE).isNotEqualTo(Val.TRUE);
        sa.assertThat(Val.of(1)).isEqualTo(Val.of(1.0D));
        sa.assertThat(Val.of(1)).isNotEqualTo(Val.of(""));
        sa.assertAll();
    }

    @Test
    void hashTest() throws JsonProcessingException {
        var sa = new SoftAssertions();
        sa.assertThat(Val.UNDEFINED.hashCode()).isEqualTo(Val.UNDEFINED.hashCode());
        sa.assertThat(Val.error("ABC").hashCode()).isEqualTo(Val.error("ABC").hashCode());
        sa.assertThat(Val.error("X").hashCode()).isNotEqualTo(Val.error("Y").hashCode());
        sa.assertThat(Val.of(1L).hashCode()).isNotEqualTo(BigInteger.valueOf(1L).hashCode());
        sa.assertThat(Val.TRUE.hashCode()).isNotEqualTo(Val.UNDEFINED.hashCode());
        sa.assertThat(Val.UNDEFINED.hashCode()).isNotEqualTo(Val.TRUE.hashCode());
        sa.assertThat(Val.FALSE.hashCode()).isEqualTo(Val.FALSE.hashCode());
        sa.assertThat(Val.FALSE.hashCode()).isNotEqualTo(Val.TRUE.hashCode());
        sa.assertThat(Val.of(1).hashCode()).isEqualTo(Val.of(1.0D).hashCode());
        sa.assertThat(Val.ofEmptyArray().hashCode()).isEqualTo(Val.ofEmptyArray().hashCode());
        sa.assertThat(Val.ofJson("[1,2,\"x\"]").hashCode()).isEqualTo(Val.ofJson("[1,2,\"x\"]").hashCode());
        sa.assertThat(Val.ofJson("[1,{},\"x\"]").hashCode()).isNotEqualTo(Val.ofJson("[1,null,\"x\"]").hashCode());
        sa.assertThat(Val.ofJson("{\"key\":\"value\"}").hashCode())
                .isEqualTo(Val.ofJson("{\"key\":\"value\"}").hashCode());
        sa.assertThat(Val.ofJson("{\"key\":\"value\"}").hashCode())
                .isNotEqualTo(Val.ofJson("{\"key\":\"value2\"}").hashCode());
        sa.assertAll();
    }

    @Test
    void orElse() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.orElse(JSON.arrayNode())).isEqualTo(JSON.booleanNode(true));
        sa.assertThat(Val.error().orElse(JSON.arrayNode())).isEqualTo(JSON.arrayNode());
        sa.assertThat(Val.UNDEFINED.orElse(JSON.arrayNode())).isEqualTo(JSON.arrayNode());
        sa.assertAll();
    }

    @Test
    void orElseGet() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.orElseGet(JSON::arrayNode)).isEqualTo(JSON.booleanNode(true));
        sa.assertThat(Val.error().orElseGet(JSON::arrayNode)).isEqualTo(JSON.arrayNode());
        sa.assertThat(Val.UNDEFINED.orElseGet(JSON::arrayNode)).isEqualTo(JSON.arrayNode());
        sa.assertAll();
    }

    @Test
    void ifDefinedIsDefined() {
        var calledWithValue = new HashSet<JsonNode>();
        Val.TRUE.ifDefined(calledWithValue::add);
        assertThat(calledWithValue).hasSize(1);

    }

    @Test
    void ifDefinedIsUndefined() {
        var calledWithValue = new HashSet<JsonNode>();
        Val.UNDEFINED.ifDefined(calledWithValue::add);
        assertThat(calledWithValue).isEmpty();
    }

    @Test
    void secretsManagement() {
        var secret = Val.of("not to be known").asSecret().withTrace(getClass());
        assertThat(secret.isSecret()).isTrue();
        assertThat(secret).hasToString("SECRET");
        assertThatJson(secret.getTrace()).inPath("$.value").isString().isEqualTo("|SECRET|");
    }

    @Test
    void withTraceNoArguments() {
        var givenTracedValue1 = Val.of("X").withTrace(getClass());
        assertThatJson(givenTracedValue1.getTrace()).inPath("$.trace.operator").isString().isEqualTo("ValTests");
        assertThatJson(givenTracedValue1.getTrace()).inPath("$.value").isString().isEqualTo("X");
    }

    @Test
    void traceOfUntraced() {
        var givenUntraced = Val.of("X");
        assertThatJson(givenUntraced.getTrace()).inPath("$.value").isString().isEqualTo("X");
        assertThatJson(givenUntraced.getTrace()).isObject().doesNotContainKey("trace");
    }

    @Test
    void traceOfUndefined() {
        var givenUndefined = Val.UNDEFINED;
        assertThatJson(givenUndefined.getTrace()).inPath("$.value").isString().isEqualTo("|UNDEFINED|");
    }

    @Test
    void traceOfError() {
        var givenError = Val.error("xxx");
        assertThatJson(givenError.getTrace()).inPath("$.value").isString().isEqualTo("|ERROR| xxx");
    }

    @Test
    void withParentTrace() {
        var givenTracedValue2 = Val.of("Y").withParentTrace(getClass(), Val.of("X"));
        assertThatJson(givenTracedValue2.getTrace()).inPath("$.value").isString().isEqualTo("Y");
        assertThatJson(givenTracedValue2.getTrace()).inPath("$.trace.arguments.parentValue.value").isEqualTo("X");
    }

    @Test
    void withTraceOfArgumentMap() {
        var givenTracedValue3 = Val.of("Z").withTrace(getClass(), Map.of("arg1", Val.of("X"), "arg2", Val.of("Y")));
        assertThatJson(givenTracedValue3.getTrace()).inPath("$.value").isString().isEqualTo("Z");
        assertThatJson(givenTracedValue3.getTrace()).inPath("$.trace.arguments.arg1.value").isEqualTo("X");
        assertThatJson(givenTracedValue3.getTrace()).inPath("$.trace.arguments.arg2.value").isEqualTo("Y");
    }

    @Test
    void withTraceOfArgumentArray() {
        var givenTracedValue4 = Val.of("A").withTrace(getClass(), Val.of("X"), Val.of("Y"), Val.of("Z"));
        assertThatJson(givenTracedValue4.getTrace()).inPath("$.value").isString().isEqualTo("A");
        assertThatJson(givenTracedValue4.getTrace()).inPath("$.trace.arguments.['arguments[0]'].value").isEqualTo("X");
        assertThatJson(givenTracedValue4.getTrace()).inPath("$.trace.arguments.['arguments[1]'].value").isEqualTo("Y");
        assertThatJson(givenTracedValue4.getTrace()).inPath("$.trace.arguments.['arguments[2]'].value").isEqualTo("Z");
    }

    @Test
    void withTraceOfSingleElementArgumentArray() {
        var givenTracedValue4 = Val.of("A").withTrace(getClass(), Val.of("X"));
        assertThatJson(givenTracedValue4.getTrace()).inPath("$.value").isString().isEqualTo("A");
        assertThatJson(givenTracedValue4.getTrace()).inPath("$.trace.arguments.argument.value").isEqualTo("X");
    }

    @Test
    void withTraceWithLeftHandAndArgumentArray() {
        var givenTracedValue5 = Val.of("B").withTrace(Val.of("A"), getClass(), Val.of("X"), Val.of("Y"), Val.of("Z"));
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.value").isString().isEqualTo("B");
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.trace.arguments.['arguments[0]'].value").isEqualTo("X");
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.trace.arguments.['arguments[1]'].value").isEqualTo("Y");
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.trace.arguments.['arguments[2]'].value").isEqualTo("Z");
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.trace.arguments.leftHandValue.value").isEqualTo("A");
    }

    @Test
    void withTraceWithLeftHandAndArgumentArrayOfOneElement() {
        var givenTracedValue5 = Val.of("B").withTrace(Val.of("A"), getClass(), Val.of("X"));
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.value").isString().isEqualTo("B");
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.trace.arguments.argument.value").isEqualTo("X");
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.trace.arguments.leftHandValue.value").isEqualTo("A");
    }

    @Test
    void withTraceOfNamedParameters() {
        var givenTracedValue6 = Val.of("Q").withTrace(getClass(), new ExpressionArgument("left", Val.of("A")),
                new ExpressionArgument("right", Val.of("B")));
        assertThatJson(givenTracedValue6.getTrace()).inPath("$.value").isString().isEqualTo("Q");
        assertThatJson(givenTracedValue6.getTrace()).inPath("$.trace.arguments.left.value").isEqualTo("A");
        assertThatJson(givenTracedValue6.getTrace()).inPath("$.trace.arguments.right.value").isEqualTo("B");
    }

    @Test
    void getArgumentsOfTrace() {
        var trace = new Trace(getClass(), Val.of("X"), Val.of("Y"), Val.of("Z"));
        assertThat(trace.getArguments()).contains(new ExpressionArgument("arguments[0]", Val.of("X")),
                new ExpressionArgument("arguments[1]", Val.of("Y")),
                new ExpressionArgument("arguments[2]", Val.of("Z")));
    }

    @Test
    void orElseThrow() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.orElseThrow(RuntimeException::new)).isEqualTo(JSON.booleanNode(true));
        sa.assertThatThrownBy(() -> Val.UNDEFINED.orElseThrow(RuntimeException::new))
                .isInstanceOf(RuntimeException.class).hasMessage(null);
        var errorValue = Val.error();
        sa.assertThatThrownBy(() -> errorValue.orElseThrow(RuntimeException::new)).isInstanceOf(RuntimeException.class)
                .hasMessage(null);
        sa.assertAll();
    }

    @Test
    void filter() {
        var sa = new SoftAssertions();
        sa.assertThat(Val.UNDEFINED.filter(JsonNode::isArray)).isEqualTo(Val.UNDEFINED);
        sa.assertThat(Val.TRUE.filter(JsonNode::isArray)).isEqualTo(Val.UNDEFINED);
        sa.assertThat(Val.ofEmptyArray().filter(JsonNode::isArray)).isEqualTo(Val.ofEmptyArray());
        sa.assertThat(Val.of(10).filter(json -> json.intValue() > 5)).isEqualTo(Val.of(10));
        sa.assertThat(Val.of(10).filter(json -> json.intValue() < 5)).isEqualTo(Val.UNDEFINED);
        var tenValue = Val.of(10);
        sa.assertThatThrownBy(() -> tenValue.filter(null)).isInstanceOf(NullPointerException.class);
        sa.assertAll();
    }

}
