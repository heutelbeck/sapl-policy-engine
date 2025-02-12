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

import io.sapl.api.pdp.SaplError;
import reactor.test.StepVerifier;

class ValTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void createError() {
        final var error = Val.error(SaplError.UNKNOWN_ERROR);

        final var sa = new SoftAssertions();
        sa.assertThat(error.getMessage()).isEqualTo(SaplError.UNKNOWN_ERROR_MESSAGE);
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
        final var sa = new SoftAssertions();
        sa.assertThat(Val.notEqual(Val.of("A"), Val.of("A"))).isEqualTo(Val.FALSE);
        sa.assertThat(Val.notEqual(Val.of("A"), Val.of("B"))).isEqualTo(Val.TRUE);
        sa.assertThat(Val.notEqual(Val.of(1.0D), Val.of(1))).isEqualTo(Val.FALSE);
        sa.assertThat(Val.notEqual(Val.of(1.0D), Val.of(1.1D))).isEqualTo(Val.TRUE);
        sa.assertThat(Val.notEqual(Val.of("X"), Val.of(1))).isEqualTo(Val.TRUE);
        sa.assertThat(Val.notEqual(Val.of(1.0D), Val.of("X"))).isEqualTo(Val.TRUE);
        sa.assertThat(Val.notEqual(Val.UNDEFINED, Val.error(SaplError.UNKNOWN_ERROR))).isEqualTo(Val.TRUE);
        sa.assertThat(Val.notEqual(Val.error(SaplError.UNKNOWN_ERROR), Val.UNDEFINED)).isEqualTo(Val.TRUE);
        sa.assertThat(Val.notEqual(Val.UNDEFINED, Val.UNDEFINED)).isEqualTo(Val.FALSE);
        sa.assertAll();
    }

    @Test
    void equalComparisonTest() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.areEqual(Val.of("A"), Val.of("A"))).isEqualTo(Val.TRUE);
        sa.assertThat(Val.areEqual(Val.of("A"), Val.of("B"))).isEqualTo(Val.FALSE);
        sa.assertAll();
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
    void getValueFromError() {
        final var value = Val.error(SaplError.UNKNOWN_ERROR);
        assertThatThrownBy(value::get).isInstanceOf(NoSuchElementException.class)
                .hasMessage(String.format(Val.VALUE_IS_AN_ERROR_S_ERROR, SaplError.UNKNOWN_ERROR_MESSAGE));
    }

    @Test
    void getValueFromUndefined() {
        assertThatThrownBy(Val.UNDEFINED::get).isInstanceOf(NoSuchElementException.class)
                .hasMessage(Val.VALUE_UNDEFINED_ERROR);
    }

    @Test
    void getValueFromValue() {
        final var value = Val.TRUE;
        assertThatJson(value.get()).isBoolean().isEqualTo(true);
    }

    @Test
    void valOfNullJson() {
        final var val = Val.of((JsonNode) null);
        assertThat(val.isUndefined()).isTrue();
    }

    @Test
    void valOfNull() {
        assertThat(Val.NULL.isNull()).isTrue();
    }

    @Test
    void valOfJsonValue() {
        final var value = Val.of(JSON.booleanNode(true));
        assertThatJson(value.get()).isBoolean().isEqualTo(true);
    }

    @Test
    void valOfEmptyObject() {
        final var value = Val.ofEmptyObject();
        assertThatJson(value.get()).isObject().isEmpty();
    }

    @Test
    void valOfEmptyArray() {
        final var value = Val.ofEmptyArray();
        assertThatJson(value.get()).isArray().isEmpty();
    }

    @Test
    void isUndefined() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.isUndefined()).isFalse();
        sa.assertThat(Val.UNDEFINED.isUndefined()).isTrue();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isUndefined()).isFalse();
        sa.assertAll();
    }

    @Test
    void isArray() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.isArray()).isFalse();
        sa.assertThat(Val.ofEmptyArray().isArray()).isTrue();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isArray()).isFalse();
        sa.assertAll();
    }

    @Test
    void isBigDecimal() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.isBigDecimal()).isFalse();
        sa.assertThat(Val.of(BigDecimal.ONE).isBigDecimal()).isTrue();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isBigDecimal()).isFalse();
        sa.assertAll();
    }

    @Test
    void isBigInteger() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.isBigInteger()).isFalse();
        sa.assertThat(Val.of(BigInteger.ONE).isBigInteger()).isTrue();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isBigInteger()).isFalse();
        sa.assertAll();
    }

    @Test
    void isBoolean() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.of(1).isBoolean()).isFalse();
        sa.assertThat(Val.TRUE.isBoolean()).isTrue();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isBoolean()).isFalse();
        sa.assertAll();
    }

    @Test
    void isDouble() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.of(1).isDouble()).isFalse();
        sa.assertThat(Val.of(1D).isDouble()).isTrue();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isDouble()).isFalse();
        sa.assertAll();
    }

    @Test
    void isFloatingPointNumber() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.of(1).isFloatingPointNumber()).isFalse();
        sa.assertThat(Val.of(1F).isFloatingPointNumber()).isTrue();
        sa.assertThat(Val.of(1F).isFloatingPointNumber()).isTrue();
        sa.assertThat(Val.of(new BigDecimal("2.2")).isFloatingPointNumber()).isTrue();
        sa.assertThat(Val.of(BigInteger.valueOf(10L)).isFloatingPointNumber()).isFalse();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isFloatingPointNumber()).isFalse();
        sa.assertAll();
    }

    @Test
    void isInt() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.of(1L).isInt()).isFalse();
        sa.assertThat(Val.of(1).isInt()).isTrue();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isInt()).isFalse();
        sa.assertAll();
    }

    @Test
    void isLong() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.of(1).isLong()).isFalse();
        sa.assertThat(Val.of(1L).isLong()).isTrue();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isLong()).isFalse();
        sa.assertAll();
    }

    @Test
    void isFloat() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.of(1).isFloat()).isFalse();
        sa.assertThat(Val.of(1F).isFloat()).isTrue();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isFloat()).isFalse();
        sa.assertAll();
    }

    @Test
    void isNull() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.of(1).isNull()).isFalse();
        sa.assertThat(Val.NULL.isNull()).isTrue();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isNull()).isFalse();
        sa.assertAll();
    }

    @Test
    void isNumber() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.of("").isNumber()).isFalse();
        sa.assertThat(Val.of(1).isNumber()).isTrue();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isNumber()).isFalse();
        sa.assertAll();
    }

    @Test
    void isObject() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.of("").isObject()).isFalse();
        sa.assertThat(Val.ofEmptyObject().isObject()).isTrue();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isObject()).isFalse();
        sa.assertAll();
    }

    @Test
    void isTextual() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.isTextual()).isFalse();
        sa.assertThat(Val.of("A").isTextual()).isTrue();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isTextual()).isFalse();
        sa.assertAll();
    }

    @Test
    void isValueNode() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.ofEmptyArray().isValueNode()).isFalse();
        sa.assertThat(Val.TRUE.isValueNode()).isTrue();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isValueNode()).isFalse();
        sa.assertAll();
    }

    @Test
    void isEmpty() {
        final var array = JSON.arrayNode();
        array.add(false);
        final var sa = new SoftAssertions();
        sa.assertThat(Val.UNDEFINED.isEmpty()).isFalse();
        sa.assertThat(Val.ofEmptyArray().isEmpty()).isTrue();
        sa.assertThat(Val.of(array).isEmpty()).isFalse();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).isEmpty()).isFalse();
        sa.assertAll();
    }

    @Test
    void toStringTest() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.toString()).isEqualTo("true");
        sa.assertThat(Val.UNDEFINED.toString()).isEqualTo("undefined");
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).toString())
                .isEqualTo("ERROR[" + SaplError.UNKNOWN_ERROR_MESSAGE + "]");
        sa.assertAll();
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
        final var val = Val.ofJson("\"ABC\"");
        assertThatJson(val.get()).isString().isEqualTo("ABC");
    }

    @Test
    void optional() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.UNDEFINED.optional()).isEmpty();
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).optional()).isEmpty();
        sa.assertAll();
        assertThatJson(Val.TRUE.optional().get()).isBoolean().isTrue();
    }

    @Test
    void getValType() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.UNDEFINED.getValType()).isEqualTo(Val.UNDEFINED_LITERAL);
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).getValType()).isEqualTo(Val.ERROR_LITERAL);
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
        final var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.getBoolean()).isTrue();
        final var value = Val.of("");
        sa.assertThatThrownBy(value::getBoolean).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.BOOLEAN_OPERATION_TYPE_MISMATCH_S_ERROR, "STRING"));
        sa.assertAll();
    }

    @Test
    void getLong() {
        final var sa     = new SoftAssertions();
        final var number = Val.of(123L);
        sa.assertThat(number.getLong()).isEqualTo(123L);
        final var value = Val.of("");
        sa.assertThatThrownBy(value::getLong).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.NUMBER_OPERATION_TYPE_MISMATCH_S_ERROR, "STRING"));
        sa.assertAll();
    }

    @Test
    void getText() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.getText()).isEqualTo("true");
        sa.assertThat(Val.UNDEFINED.getText()).isEqualTo(Val.UNDEFINED_LITERAL);
        sa.assertThat(Val.of("ABC").getText()).isEqualTo("ABC");
        sa.assertThat(Val.of(123).getText()).isEqualTo("123");
        sa.assertAll();
    }

    @Test
    void decimalValue() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.of(100D).decimalValue()).isEqualByComparingTo(new BigDecimal("100"));
        final var errorValue = Val.error(SaplError.UNKNOWN_ERROR);
        sa.assertThatThrownBy(errorValue::decimalValue).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.NUMBER_OPERATION_TYPE_MISMATCH_S_ERROR, "ERROR"));
        sa.assertThatThrownBy(Val.UNDEFINED::decimalValue).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.NUMBER_OPERATION_TYPE_MISMATCH_S_ERROR, "undefined"));
        final var emptyStringValue = Val.of("");
        sa.assertThatThrownBy(emptyStringValue::decimalValue).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.NUMBER_OPERATION_TYPE_MISMATCH_S_ERROR, "STRING"));
        sa.assertAll();
    }

    @Test
    void getObjectNode() {
        final var sa = new SoftAssertions();
        sa.assertThatThrownBy(Val.UNDEFINED::getObjectNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.OBJECT_OPERATION_TYPE_MISMATCH_S_ERROR, "undefined"));
        final var errorValue = Val.error(SaplError.UNKNOWN_ERROR);
        sa.assertThatThrownBy(errorValue::getObjectNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.OBJECT_OPERATION_TYPE_MISMATCH_S_ERROR, "ERROR"));
        final var emptyStringValue = Val.of("");
        sa.assertThatThrownBy(emptyStringValue::getObjectNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.OBJECT_OPERATION_TYPE_MISMATCH_S_ERROR, "STRING"));
        sa.assertAll();
        assertThatJson(Val.ofEmptyObject().getObjectNode()).isObject();
    }

    @Test
    void getArrayNode() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.of(JSON.arrayNode()).getArrayNode()).isEqualTo(JSON.arrayNode());
        sa.assertThatThrownBy(Val.UNDEFINED::getArrayNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.ARRAY_OPERATION_TYPE_MISMATCH_S_ERROR, "undefined"));
        final var errorValue = Val.error(SaplError.UNKNOWN_ERROR);
        sa.assertThatThrownBy(errorValue::getArrayNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.ARRAY_OPERATION_TYPE_MISMATCH_S_ERROR, "ERROR"));
        final var emptyStringValue = Val.of("");
        sa.assertThatThrownBy(emptyStringValue::getArrayNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.ARRAY_OPERATION_TYPE_MISMATCH_S_ERROR, "STRING"));
        sa.assertAll();
        assertThatJson(Val.ofEmptyArray().getJsonNode()).isArray();
    }

    @Test
    void getJsonNode() {
        final var sa = new SoftAssertions();
        sa.assertThatThrownBy(Val.UNDEFINED::getJsonNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(Val.UNDEFINED_VALUE_ERROR);
        final var errorValue = Val.error((String) null);
        sa.assertThatThrownBy(errorValue::getJsonNode).isInstanceOf(PolicyEvaluationException.class)
                .hasMessage(String.format(Val.VALUE_IS_AN_ERROR_S_ERROR, SaplError.UNKNOWN_ERROR_MESSAGE));
        sa.assertAll();
        assertThatJson(Val.ofEmptyArray().getJsonNode()).isArray();
    }

    @Test
    void equalsTest() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE).isEqualTo(Val.TRUE);
        sa.assertThat(Val.UNDEFINED).isEqualTo(Val.UNDEFINED);
        sa.assertThat(Val.UNDEFINED).isEqualTo(Val.UNDEFINED.withTrace(getClass()));
        sa.assertThat(Val.error(SaplError.of("ABC"))).isEqualTo(Val.error(SaplError.of("ABC")));
        sa.assertThat(Val.error(SaplError.of("ABC"))).isNotEqualTo(Val.error(SaplError.of("CBA")));
        sa.assertThat(Val.UNDEFINED).isNotEqualTo(Val.error(SaplError.of("ABC")));
        sa.assertThat(Val.of(1L)).isNotEqualTo(Val.error(SaplError.of("ABC")));
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
        final var sa = new SoftAssertions();
        sa.assertThat(Val.UNDEFINED.hashCode()).isEqualTo(Val.UNDEFINED.hashCode());
        sa.assertThat(Val.error(SaplError.of("ABC")).hashCode()).isEqualTo(Val.error(SaplError.of("ABC")).hashCode());
        sa.assertThat(Val.error(SaplError.of("ABC")).hashCode())
                .isNotEqualTo(Val.error(SaplError.of("BCA")).hashCode());
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
        final var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.orElse(JSON.arrayNode())).isEqualTo(JSON.booleanNode(true));
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).orElse(JSON.arrayNode())).isEqualTo(JSON.arrayNode());
        sa.assertThat(Val.UNDEFINED.orElse(JSON.arrayNode())).isEqualTo(JSON.arrayNode());
        sa.assertAll();
    }

    @Test
    void orElseGet() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.orElse(JSON::arrayNode)).isEqualTo(JSON.booleanNode(true));
        sa.assertThat(Val.error(SaplError.UNKNOWN_ERROR).orElse(JSON::arrayNode)).isEqualTo(JSON.arrayNode());
        sa.assertThat(Val.UNDEFINED.orElse(JSON::arrayNode)).isEqualTo(JSON.arrayNode());
        sa.assertAll();
    }

    @Test
    void ifDefinedIsDefined() {
        final var calledWithValue = new HashSet<JsonNode>();
        Val.TRUE.ifDefined(calledWithValue::add);
        assertThat(calledWithValue).hasSize(1);
    }

    @Test
    void fieldJsonNodeOrElseThrow() throws Exception {
        final var val = Val.ofJson("{ \"field\":123 }");
        final var sa  = new SoftAssertions();
        sa.assertThat(val.fieldJsonNodeOrElseThrow("field", RuntimeException::new)).isEqualTo(JSON.numberNode(123));
        sa.assertThatThrownBy(() -> val.fieldJsonNodeOrElseThrow("no field", RuntimeException::new))
                .isInstanceOf(RuntimeException.class);
        sa.assertThatThrownBy(() -> Val.UNDEFINED.fieldJsonNodeOrElseThrow("no field", RuntimeException::new))
                .isInstanceOf(RuntimeException.class);
        sa.assertAll();
    }

    @Test
    void fieldJsonNodeOrElseSupplier() throws Exception {
        final var val = Val.ofJson("{ \"field\":123 }");
        final var sa  = new SoftAssertions();
        sa.assertThat(val.fieldJsonNodeOrElse("field", () -> JSON.numberNode(321))).isEqualTo(JSON.numberNode(123));
        sa.assertThat(val.fieldJsonNodeOrElse("no field", () -> JSON.numberNode(321))).isEqualTo(JSON.numberNode(321));
        sa.assertThat(Val.UNDEFINED.fieldJsonNodeOrElse("no field", () -> JSON.numberNode(321)))
                .isEqualTo(JSON.numberNode(321));
        sa.assertAll();
    }

    @Test
    void fieldJsonNodeOrElse() throws Exception {
        final var val = Val.ofJson("{ \"field\":123 }");
        final var sa  = new SoftAssertions();
        sa.assertThat(val.fieldJsonNodeOrElse("field", JSON.numberNode(321))).isEqualTo(JSON.numberNode(123));
        sa.assertThat(val.fieldJsonNodeOrElse("no field", JSON.numberNode(321))).isEqualTo(JSON.numberNode(321));
        sa.assertThat(Val.UNDEFINED.fieldJsonNodeOrElse("no field", JSON.numberNode(321)))
                .isEqualTo(JSON.numberNode(321));
        sa.assertAll();
    }

    @Test
    void fieldValOrElseSupplier() throws Exception {
        final var val = Val.ofJson("{ \"field\":123 }");
        final var sa  = new SoftAssertions();
        sa.assertThat(val.fieldValOrElse("field", () -> Val.of(321))).isEqualTo(Val.of(123));
        sa.assertThat(val.fieldValOrElse("no field", () -> Val.of(321))).isEqualTo(Val.of(321));
        sa.assertThat(Val.UNDEFINED.fieldValOrElse("no field", () -> Val.of(321))).isEqualTo(Val.of(321));
        sa.assertAll();
    }

    @Test
    void fieldValOrElse() throws Exception {
        final var val = Val.ofJson("{ \"field\":123 }");
        final var sa  = new SoftAssertions();
        sa.assertThat(val.fieldValOrElse("field", Val.of(321))).isEqualTo(Val.of(123));
        sa.assertThat(val.fieldValOrElse("no field", Val.of(321))).isEqualTo(Val.of(321));
        sa.assertThat(Val.UNDEFINED.fieldValOrElse("no field", Val.of(321))).isEqualTo(Val.of(321));
        sa.assertAll();
    }

    @Test
    void ifDefinedIsUndefined() {
        final var calledWithValue = new HashSet<JsonNode>();
        Val.UNDEFINED.ifDefined(calledWithValue::add);
        assertThat(calledWithValue).isEmpty();
    }

    @Test
    void secretsManagement() {
        final var secret = Val.of("not to be known").asSecret().withTrace(getClass());
        assertThat(secret.isSecret()).isTrue();
        assertThat(secret).hasToString("SECRET");
        assertThatJson(secret.getTrace()).inPath("$.value").isString().isEqualTo("|SECRET|");
    }

    @Test
    void secretsManagementWithArrayOfExpressionArguments() throws JsonProcessingException {
        final var secret         = Val.ofJson("""
                {
                    "key":"secret"
                }
                """).asSecret().withTrace(getClass());
        final var secretArgument = new ExpressionArgument("secretArgument", secret);
        final var publicArgument = new ExpressionArgument("publicArgument", secret);

        final var tracedVal = Val.of(123).withTrace(getClass(), true, secretArgument, publicArgument);
        assertThat(tracedVal.isSecret()).isTrue();
        assertThat(tracedVal).hasToString("SECRET");
    }

    @Test
    void secretsManagementWithArrayOfExpressionArgumentsNoInherit() throws JsonProcessingException {
        final var secret         = Val.ofJson("""
                {
                    "key":"secret"
                }
                """).asSecret().withTrace(getClass());
        final var secretArgument = new ExpressionArgument("secretArgument", secret);
        final var publicArgument = new ExpressionArgument("publicArgument", secret);

        final var tracedVal = Val.of(123).withTrace(getClass(), false, secretArgument, publicArgument);
        assertThat(tracedVal.isSecret()).isFalse();
        assertThat(tracedVal).hasToString("123");
    }

    @Test
    void secretsManagementWithArrayOfArguments() throws JsonProcessingException {
        final var secret    = Val.ofJson("""
                {
                    "key":"secret"
                }
                """).asSecret().withTrace(getClass());
        final var tracedVal = Val.of(123).withTrace(getClass(), true, secret, Val.of("not secret"));
        assertThat(tracedVal.isSecret()).isTrue();
        assertThat(tracedVal).hasToString("SECRET");
    }

    @Test
    void secretsManagementWithArrayOfArgumentsNoInherit() throws JsonProcessingException {
        final var secret    = Val.ofJson("""
                {
                    "key":"secret"
                }
                """).asSecret().withTrace(getClass());
        final var tracedVal = Val.of(123).withTrace(getClass(), false, Val.of("not secret"), secret);
        assertThat(tracedVal.isSecret()).isFalse();
        assertThat(tracedVal).hasToString("123");
    }

    @Test
    void secretsManagementWithArrayOfArgumentsAndLeftHand() throws JsonProcessingException {
        final var secret    = Val.ofJson("""
                {
                    "key":"secret"
                }
                """).asSecret().withTrace(getClass());
        final var tracedVal = Val.of(123).withTrace(Val.of("left hand no secret"), getClass(), true, secret,
                Val.of("not secret"));
        assertThat(tracedVal.isSecret()).isTrue();
        assertThat(tracedVal).hasToString("SECRET");

        final var tracedVal2 = Val.of(123).withTrace(secret, getClass(), true, Val.of("not secret"),
                Val.of("also not secret"));
        assertThat(tracedVal2.isSecret()).isTrue();
        assertThat(tracedVal2).hasToString("SECRET");

    }

    @Test
    void secretsManagementWithArrayOfArgumentsNoInheritAndLeftHand() throws JsonProcessingException {
        final var secret    = Val.ofJson("""
                {
                    "key":"secret"
                }
                """).asSecret().withTrace(getClass());
        final var tracedVal = Val.of(123).withTrace(Val.of("left hand no secret"), getClass(), false, secret,
                Val.of("not secret"));
        assertThat(tracedVal.isSecret()).isFalse();
        assertThat(tracedVal).hasToString("123");

        final var tracedVal2 = Val.of(123).withTrace(secret, getClass(), false, Val.of("not secret"),
                Val.of("also not secret"));
        assertThat(tracedVal.isSecret()).isFalse();
        assertThat(tracedVal2).hasToString("123");
    }

    @Test
    void secretsManagementWithMapOfArguments() throws JsonProcessingException {
        final var secret    = Val.ofJson("""
                {
                    "key":"secret"
                }
                """).asSecret().withTrace(getClass());
        final var tracedVal = Val.of(123).withTrace(getClass(), true, Map.of("a", secret, "b", Val.of("not secret")));
        assertThat(tracedVal.isSecret()).isTrue();
        assertThat(tracedVal).hasToString("SECRET");
    }

    @Test
    void secretsManagementWithMapOfArgumentsNoInherit() throws JsonProcessingException {
        final var secret    = Val.ofJson("""
                {
                    "key":"secret"
                }
                """).asSecret().withTrace(getClass());
        final var tracedVal = Val.of(123).withTrace(getClass(), false, Map.of("a", secret, "b", Val.of("not secret")));
        assertThat(tracedVal.isSecret()).isFalse();
        assertThat(tracedVal).hasToString("123");
    }

    @Test
    void secretsManagementWithParentValue() throws JsonProcessingException {
        final var secret    = Val.ofJson("""
                {
                    "key":"secret"
                }
                """).asSecret().withTrace(getClass());
        final var tracedVal = Val.of(123).withParentTrace(getClass(), true, secret);
        assertThat(tracedVal.isSecret()).isTrue();
        assertThat(tracedVal).hasToString("SECRET");
    }

    @Test
    void secretsManagementWithPArentValueNoInherit() throws JsonProcessingException {
        final var secret    = Val.ofJson("""
                {
                    "key":"secret"
                }
                """).asSecret().withTrace(getClass());
        final var tracedVal = Val.of(123).withParentTrace(getClass(), false, secret);
        assertThat(tracedVal.isSecret()).isFalse();
        assertThat(tracedVal).hasToString("123");
    }

    @Test
    void withTraceNoArguments() {
        final var givenTracedValue1 = Val.of("X").withTrace(getClass());
        assertThatJson(givenTracedValue1.getTrace()).inPath("$.trace.operator").isString().isEqualTo("ValTests");
        assertThatJson(givenTracedValue1.getTrace()).inPath("$.value").isString().isEqualTo("X");
    }

    @Test
    void traceOfUntraced() {
        final var givenUntraced = Val.of("X");
        assertThatJson(givenUntraced.getTrace()).inPath("$.value").isString().isEqualTo("X");
        assertThatJson(givenUntraced.getTrace()).isObject().doesNotContainKey("trace");
    }

    @Test
    void traceOfUndefined() {
        final var givenUndefined = Val.UNDEFINED;
        assertThatJson(givenUndefined.getTrace()).inPath("$.value").isString().isEqualTo("|UNDEFINED|");
    }

    @Test
    void traceOfError() {
        final var givenError = Val.error(SaplError.of("xxx"));
        assertThatJson(givenError.getTrace()).inPath("$.value").isString().isEqualTo("|ERROR| xxx");
    }

    @Test
    void withParentTrace() {
        final var givenTracedValue2 = Val.of("Y").withParentTrace(getClass(), true, Val.of("X"));
        assertThatJson(givenTracedValue2.getTrace()).inPath("$.value").isString().isEqualTo("Y");
        assertThatJson(givenTracedValue2.getTrace()).inPath("$.trace.arguments.parentValue.value").isEqualTo("X");
    }

    @Test
    void withTraceOfArgumentMap() {
        final var givenTracedValue3 = Val.of("Z").withTrace(getClass(), true,
                Map.of("arg1", Val.of("X"), "arg2", Val.of("Y")));
        assertThatJson(givenTracedValue3.getTrace()).inPath("$.value").isString().isEqualTo("Z");
        assertThatJson(givenTracedValue3.getTrace()).inPath("$.trace.arguments.arg1.value").isEqualTo("X");
        assertThatJson(givenTracedValue3.getTrace()).inPath("$.trace.arguments.arg2.value").isEqualTo("Y");
    }

    @Test
    void withTraceOfArgumentArray() {
        final var givenTracedValue4 = Val.of("A").withTrace(getClass(), true, Val.of("X"), Val.of("Y"), Val.of("Z"));
        assertThatJson(givenTracedValue4.getTrace()).inPath("$.value").isString().isEqualTo("A");
        assertThatJson(givenTracedValue4.getTrace()).inPath("$.trace.arguments.['arguments[0]'].value").isEqualTo("X");
        assertThatJson(givenTracedValue4.getTrace()).inPath("$.trace.arguments.['arguments[1]'].value").isEqualTo("Y");
        assertThatJson(givenTracedValue4.getTrace()).inPath("$.trace.arguments.['arguments[2]'].value").isEqualTo("Z");
    }

    @Test
    void withTraceOfSingleElementArgumentArray() {
        final var givenTracedValue4 = Val.of("A").withTrace(getClass(), true, Val.of("X"));
        assertThatJson(givenTracedValue4.getTrace()).inPath("$.value").isString().isEqualTo("A");
        assertThatJson(givenTracedValue4.getTrace()).inPath("$.trace.arguments.argument.value").isEqualTo("X");
    }

    @Test
    void withTraceWithLeftHandAndArgumentArray() {
        final var givenTracedValue5 = Val.of("B").withTrace(Val.of("A"), getClass(), true, Val.of("X"), Val.of("Y"),
                Val.of("Z"));
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.value").isString().isEqualTo("B");
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.trace.arguments.['arguments[0]'].value").isEqualTo("X");
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.trace.arguments.['arguments[1]'].value").isEqualTo("Y");
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.trace.arguments.['arguments[2]'].value").isEqualTo("Z");
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.trace.arguments.leftHandValue.value").isEqualTo("A");
    }

    @Test
    void withTraceWithLeftHandAndArgumentArrayOfOneElement() {
        final var givenTracedValue5 = Val.of("B").withTrace(Val.of("A"), getClass(), true, Val.of("X"));
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.value").isString().isEqualTo("B");
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.trace.arguments.argument.value").isEqualTo("X");
        assertThatJson(givenTracedValue5.getTrace()).inPath("$.trace.arguments.leftHandValue.value").isEqualTo("A");
    }

    @Test
    void withTraceOfNamedParameters() {
        final var givenTracedValue6 = Val.of("Q").withTrace(getClass(), true,
                new ExpressionArgument("left", Val.of("A")), new ExpressionArgument("right", Val.of("B")));
        assertThatJson(givenTracedValue6.getTrace()).inPath("$.value").isString().isEqualTo("Q");
        assertThatJson(givenTracedValue6.getTrace()).inPath("$.trace.arguments.left.value").isEqualTo("A");
        assertThatJson(givenTracedValue6.getTrace()).inPath("$.trace.arguments.right.value").isEqualTo("B");
    }

    @Test
    void getArgumentsOfTrace() {
        final var trace = new Trace(getClass(), Val.of("X"), Val.of("Y"), Val.of("Z"));
        assertThat(trace.getArguments()).contains(new ExpressionArgument("arguments[0]", Val.of("X")),
                new ExpressionArgument("arguments[1]", Val.of("Y")),
                new ExpressionArgument("arguments[2]", Val.of("Z")));
    }

    @Test
    void orElseThrow() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.TRUE.orElseThrow(RuntimeException::new)).isEqualTo(JSON.booleanNode(true));
        sa.assertThatThrownBy(() -> Val.UNDEFINED.orElseThrow(RuntimeException::new))
                .isInstanceOf(RuntimeException.class).hasMessage(null);
        final var errorValue = Val.error(SaplError.UNKNOWN_ERROR);
        sa.assertThatThrownBy(() -> errorValue.orElseThrow(RuntimeException::new)).isInstanceOf(RuntimeException.class)
                .hasMessage(null);
        sa.assertAll();
    }

    @Test
    void filter() {
        final var sa = new SoftAssertions();
        sa.assertThat(Val.UNDEFINED.filter(JsonNode::isArray)).isEqualTo(Val.UNDEFINED);
        sa.assertThat(Val.TRUE.filter(JsonNode::isArray)).isEqualTo(Val.UNDEFINED);
        sa.assertThat(Val.ofEmptyArray().filter(JsonNode::isArray)).isEqualTo(Val.ofEmptyArray());
        sa.assertThat(Val.of(10).filter(json -> json.intValue() > 5)).isEqualTo(Val.of(10));
        sa.assertThat(Val.of(10).filter(json -> json.intValue() < 5)).isEqualTo(Val.UNDEFINED);
        final var tenValue = Val.of(10);
        sa.assertThatThrownBy(() -> tenValue.filter(null)).isInstanceOf(NullPointerException.class);
        sa.assertAll();
    }

}
