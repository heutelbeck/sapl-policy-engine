/*
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static com.spotify.hamcrest.jackson.IsJsonArray.jsonArray;
import static com.spotify.hamcrest.jackson.IsJsonBoolean.jsonBoolean;
import static com.spotify.hamcrest.jackson.IsJsonObject.jsonObject;
import static com.spotify.hamcrest.jackson.IsJsonText.jsonText;
import static com.spotify.hamcrest.optional.OptionalMatchers.emptyOptional;
import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import reactor.test.StepVerifier;

class ValTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final String ERROR_MESSAGE = "Error Message";

	private static final String MESSAGE_STRING_1 = "MESSAGE STRING 1";

	private static final String MESSAGE_STRING_D = "MESSAGE STRING %d";

	@Test
	void createError() {
		var error = Val.error(ERROR_MESSAGE);
		assertAll(() -> assertEquals(ERROR_MESSAGE, error.getMessage()), () -> assertTrue(error.isError()),
				() -> assertFalse(error.isArray()), () -> assertFalse(error.isBigDecimal()),
				() -> assertFalse(error.isBoolean()), () -> assertFalse(error.isDefined()),
				() -> assertFalse(error.isDouble()), () -> assertFalse(error.isEmpty()),
				() -> assertFalse(error.isFloat()), () -> assertFalse(error.isFloatingPointNumber()),
				() -> assertFalse(error.isInt()), () -> assertFalse(error.isLong()), () -> assertFalse(error.isNull()),
				() -> assertFalse(error.isNumber()), () -> assertFalse(error.isObject()),
				() -> assertFalse(error.isUndefined()), () -> assertFalse(error.isValueNode()),
				() -> assertFalse(error.noError()), () -> assertFalse(error.isTextual()));
	}

	@Test
	void notEqualComparisonTest() {
		assertAll(() -> assertThat(Val.notEqual(Val.of("A"), Val.of("A")), is(Val.FALSE)),
				() -> assertThat(Val.notEqual(Val.of("A"), Val.of("B")), is(Val.TRUE)),
				() -> assertThat(Val.notEqual(Val.of(1.0D), Val.of(1)), is(Val.FALSE)),
				() -> assertThat(Val.notEqual(Val.of(1.0D), Val.of(1.1D)), is(Val.TRUE)),
				() -> assertThat(Val.notEqual(Val.of("X"), Val.of(1)), is(Val.TRUE)),
				() -> assertThat(Val.notEqual(Val.of(1.0D), Val.of("X")), is(Val.TRUE)),
				() -> assertThat(Val.notEqual(Val.UNDEFINED, Val.error()), is(Val.TRUE)),
				() -> assertThat(Val.notEqual(Val.error(), Val.UNDEFINED), is(Val.TRUE)),
				() -> assertThat(Val.notEqual(Val.UNDEFINED, Val.UNDEFINED), is(Val.FALSE)));
	}

	@Test
	void equalComparisonTest() {
		assertAll(() -> assertThat(Val.equal(Val.of("A"), Val.of("A")), is(Val.TRUE)),
				() -> assertThat(Val.equal(Val.of("A"), Val.of("B")), is(Val.FALSE)));
	}

	@Test
	void createErrorWithFormattedMessage() {
		assertEquals(MESSAGE_STRING_1, Val.error(MESSAGE_STRING_D, 1).getMessage());
	}

	@Test
	void createErrorWithNullMessage() {
		assertEquals("Undefined Error", Val.error((String) null).getMessage());
	}

	@Test
	void createErrorWithNullCause() {
		assertEquals("Undefined Error", Val.error((Throwable) null).getMessage());
	}

	@Test
	void createUnknownError() {
		assertEquals(Val.UNKNOWN_ERROR, Val.error().getMessage());
	}

	@Test
	void noError() {
		assertTrue(Val.UNDEFINED.noError());
	}

	@Test
	void errorMessageFromNonError() {
		assertThrows(NoSuchElementException.class, Val.UNDEFINED::getMessage);
	}

	@Test
	void gerValueFromError() {
		assertThrows(NoSuchElementException.class, () -> Val.error(ERROR_MESSAGE).get());
	}

	@Test
	void gerValueFromUndefined() {
		assertThrows(NoSuchElementException.class, Val.UNDEFINED::get);
	}

	@Test
	void getValueFromValue() {
		assertThat(Val.TRUE.get(), is(jsonBoolean(true)));
	}

	@Test
	void valOfNullJson() {
		assertTrue(Val.of((JsonNode) null).isUndefined());
	}

	@Test
	void valOfNull() {
		assertTrue(Val.NULL.isNull());
	}

	@Test
	void valOfJsonValue() {
		assertThat(Val.of(JSON.booleanNode(true)).get(), is(jsonBoolean(true)));
	}

	@Test
	void valOfEmptyObject() {
		assertThat(Val.ofEmptyObject().get(), is(jsonObject()));
	}

	@Test
	void valOfEmptyArray() {
		assertThat(Val.ofEmptyArray().get(), is(jsonArray()));
	}

	@Test
	void errorOfThrowableNoMessage() {
		assertEquals("RuntimeException", Val.error(new RuntimeException()).getMessage());
	}

	@Test
	void errorOfThrowableBlankMessage() {
		assertEquals("RuntimeException", Val.error(new RuntimeException("")).getMessage());
	}

	@Test
	void errorOfThrowableWithMessage() {
		assertEquals(ERROR_MESSAGE, Val.error(new RuntimeException(ERROR_MESSAGE)).getMessage());
	}

	@Test
	void isUndefined() {
		assertAll(() -> assertFalse(Val.TRUE.isUndefined()), () -> assertTrue(Val.UNDEFINED.isUndefined()),
				() -> assertFalse(Val.error().isUndefined()));
	}

	@Test
	void isArray() {
		assertAll(() -> assertFalse(Val.TRUE.isArray()), () -> assertTrue(Val.ofEmptyArray().isArray()),
				() -> assertFalse(Val.error().isArray()));
	}

	@Test
	void isBigDecimal() {
		assertAll(() -> assertFalse(Val.TRUE.isBigDecimal()), () -> assertTrue(Val.of(BigDecimal.ONE).isBigDecimal()),
				() -> assertFalse(Val.error().isBigDecimal()));
	}

	@Test
	void isBigInteger() {
		assertAll(() -> assertFalse(Val.TRUE.isBigInteger()), () -> assertTrue(Val.of(BigInteger.ONE).isBigInteger()),
				() -> assertFalse(Val.error().isBigInteger()));
	}

	@Test
	void isBoolean() {
		assertAll(() -> assertFalse(Val.of(1).isBoolean()), () -> assertTrue(Val.TRUE.isBoolean()),
				() -> assertFalse(Val.error().isBoolean()));
	}

	@Test
	void isDouble() {
		assertAll(() -> assertFalse(Val.of(1).isDouble()), () -> assertTrue(Val.of(1D).isDouble()),
				() -> assertFalse(Val.error().isDouble()));
	}

	@Test
	void isFloatingPointNumber() {
		assertAll(() -> assertFalse(Val.of(1).isFloatingPointNumber()),
				() -> assertTrue(Val.of(1F).isFloatingPointNumber()),
				() -> assertTrue(Val.of(1F).isFloatingPointNumber()),
				() -> assertTrue(Val.of(new BigDecimal(2.2F)).isFloatingPointNumber()),
				() -> assertFalse(Val.of(BigInteger.valueOf(10L)).isFloatingPointNumber()),
				() -> assertFalse(Val.error().isFloatingPointNumber()));
	}

	@Test
	void isInt() {
		assertAll(() -> assertFalse(Val.of(1L).isInt()), () -> assertTrue(Val.of(1).isInt()),
				() -> assertFalse(Val.error().isInt()));
	}

	@Test
	void isLong() {
		assertAll(() -> assertFalse(Val.of(1).isLong()), () -> assertTrue(Val.of(1L).isLong()),
				() -> assertFalse(Val.error().isLong()));
	}

	@Test
	void isFloat() {
		assertAll(() -> assertFalse(Val.of(1).isFloat()), () -> assertTrue(Val.of(1F).isFloat()),
				() -> assertFalse(Val.error().isFloat()));
	}

	@Test
	void isNull() {
		assertAll(() -> assertFalse(Val.of(1).isNull()), () -> assertTrue(Val.NULL.isNull()),
				() -> assertFalse(Val.error().isNull()));
	}

	@Test
	void isNumber() {
		assertAll(() -> assertFalse(Val.of("").isNumber()), () -> assertTrue(Val.of(1).isNumber()),
				() -> assertFalse(Val.error().isNumber()));
	}

	@Test
	void isObject() {
		assertAll(() -> assertFalse(Val.of("").isObject()), () -> assertTrue(Val.ofEmptyObject().isObject()),
				() -> assertFalse(Val.error().isObject()));
	}

	@Test
	void isTextual() {
		assertAll(() -> assertFalse(Val.TRUE.isTextual()), () -> assertTrue(Val.of("A").isTextual()),
				() -> assertFalse(Val.error().isTextual()));
	}

	@Test
	void isValueNode() {
		assertAll(() -> assertFalse(Val.ofEmptyArray().isValueNode()), () -> assertTrue(Val.TRUE.isValueNode()),
				() -> assertFalse(Val.error().isValueNode()));
	}

	@Test
	void isEmpty() {
		var array = JSON.arrayNode();
		array.add(false);
		assertAll(() -> assertFalse(Val.UNDEFINED.isEmpty()), () -> assertTrue(Val.ofEmptyArray().isEmpty()),
				() -> assertFalse(Val.of(array).isEmpty()), () -> assertFalse(Val.error().isEmpty()));
	}

	@Test
	void toStringTest() {
		assertAll(() -> assertEquals("Value[true]", Val.TRUE.toString()),
				() -> assertEquals("Value[undefined]", Val.UNDEFINED.toString()),
				() -> assertEquals("ERROR[" + Val.UNKNOWN_ERROR + "]", Val.error().toString()));
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
		assertAll(() -> assertThrows(JsonProcessingException.class, () -> Val.ofJson("}{")),
				() -> assertThat(Val.ofJson("\"ABC\"").get(), is(jsonText("ABC"))));
	}

	@Test
	void optional() {
		assertAll(() -> assertThat(Val.UNDEFINED.optional(), is(emptyOptional())),
				() -> assertThat(Val.error().optional(), is(emptyOptional())),
				() -> assertThat(Val.TRUE.optional(), is(optionalWithValue(is(jsonBoolean(true))))));
	}

	@Test
	void getValType() {
		assertAll(() -> assertEquals(Val.UNDEFINED_TEXT, Val.UNDEFINED.getValType()),
				() -> assertEquals(Val.ERROR_TEXT, Val.error().getValType()),
				() -> assertEquals("BOOLEAN", Val.TRUE.getValType()));
	}

	@Test
	void toBoolean() {
		StepVerifier.create(Val.toBoolean(Val.TRUE)).expectNext(true).verifyComplete();
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
		assertAll(() -> assertEquals(true, Val.TRUE.getBoolean()),
				() -> assertThrows(PolicyEvaluationException.class, () -> Val.of("").getBoolean()));
	}

	@Test
	void getLong() {
		assertAll(() -> assertEquals(123L, Val.of(123L).getLong()),
				() -> assertThrows(PolicyEvaluationException.class, () -> Val.of("").getLong()));
	}

	@Test
	void requireBoolean() {
		assertAll(() -> assertEquals(Val.TRUE, Val.requireBoolean(Val.TRUE)),
				() -> assertTrue(Val.requireBoolean(Val.UNDEFINED).isError()));
	}

	@Test
	void requireJsonNode() {
		assertAll(() -> assertEquals(Val.TRUE, Val.requireJsonNode(Val.TRUE)),
				() -> assertTrue(Val.requireJsonNode(Val.error()).isError()),
				() -> assertTrue(Val.requireJsonNode(Val.UNDEFINED).isError()));
	}

	@Test
	void requireArrayNode() {
		assertAll(() -> assertEquals(Val.ofEmptyArray(), Val.requireArrayNode(Val.ofEmptyArray())),
				() -> assertTrue(Val.requireArrayNode(Val.of(1)).isError()),
				() -> assertTrue(Val.requireArrayNode(Val.error()).isError()),
				() -> assertTrue(Val.requireArrayNode(Val.UNDEFINED).isError()));
	}

	@Test
	void requireObjectNode() {
		assertAll(() -> assertEquals(Val.ofEmptyObject(), Val.requireObjectNode(Val.ofEmptyObject())),
				() -> assertTrue(Val.requireObjectNode(Val.of(1)).isError()),
				() -> assertTrue(Val.requireObjectNode(Val.error()).isError()),
				() -> assertTrue(Val.requireObjectNode(Val.UNDEFINED).isError()));
	}

	@Test
	void requireText() {
		assertAll(() -> assertEquals(Val.of(""), Val.requireText(Val.of(""))),
				() -> assertTrue(Val.requireText(Val.of(1)).isError()),
				() -> assertTrue(Val.requireText(Val.error()).isError()),
				() -> assertTrue(Val.requireText(Val.UNDEFINED).isError()));
	}

	@Test
	void requireNumber() {
		assertAll(() -> assertEquals(Val.of(1), Val.requireNumber(Val.of(1))),
				() -> assertTrue(Val.requireNumber(Val.of("")).isError()),
				() -> assertTrue(Val.requireNumber(Val.error()).isError()),
				() -> assertTrue(Val.requireNumber(Val.UNDEFINED).isError()));
	}

	@Test
	void getText() {
		assertAll(() -> assertEquals("true", Val.TRUE.getText()),
				() -> assertEquals(Val.UNDEFINED_TEXT, Val.UNDEFINED.getText()),
				() -> assertEquals("ABC", Val.of("ABC").getText()), () -> assertEquals("123", Val.of(123).getText()));
	}

	@Test
	void decimalValue() {
		assertAll(() -> assertEquals(BigDecimal.valueOf(100D), Val.of(100D).decimalValue()),
				() -> assertThrows(PolicyEvaluationException.class, () -> Val.error().decimalValue()),
				() -> assertThrows(PolicyEvaluationException.class, Val.UNDEFINED::decimalValue),
				() -> assertThrows(PolicyEvaluationException.class, () -> Val.of("").decimalValue()));
	}

	@Test
	void getObjectNode() {
		assertAll(() -> assertThat(Val.ofEmptyObject().getObjectNode(), is(jsonObject())),
				() -> assertThrows(PolicyEvaluationException.class, () -> Val.error().getObjectNode()),
				() -> assertThrows(PolicyEvaluationException.class, Val.UNDEFINED::getObjectNode),
				() -> assertThrows(PolicyEvaluationException.class, () -> Val.of("").getObjectNode()));
	}

	@Test
	void getArrayNode() {
		assertAll(() -> assertThat(Val.ofEmptyArray().getArrayNode(), is(jsonArray())),
				() -> assertThrows(PolicyEvaluationException.class, () -> Val.error().getArrayNode()),
				() -> assertThrows(PolicyEvaluationException.class, Val.UNDEFINED::getArrayNode),
				() -> assertThrows(PolicyEvaluationException.class, () -> Val.of("").getArrayNode()));
	}

	@Test
	void getJsonNode() {
		assertAll(() -> assertThat(Val.ofEmptyArray().getJsonNode(), is(jsonArray())),
				() -> assertThrows(PolicyEvaluationException.class, () -> Val.error().getJsonNode()),
				() -> assertThrows(PolicyEvaluationException.class, Val.UNDEFINED::getJsonNode));
	}

	@Test
	void equalsTest() {
		var x = Val.TRUE;
		assertEquals(x, x);
		assertEquals(Val.UNDEFINED, Val.UNDEFINED);
		assertEquals(Val.error("ABC"), Val.error("ABC"));
		assertNotEquals(Val.error("X"), Val.error("Y"));
		assertNotEquals(Val.error("X"), Val.UNDEFINED);
		assertNotEquals(Val.of(1L), BigInteger.valueOf(1L));
		assertNotEquals(Val.TRUE, Val.UNDEFINED);
		assertNotEquals(Val.UNDEFINED, Val.TRUE);
		assertEquals(Val.FALSE, Val.FALSE);
		assertNotEquals(Val.FALSE, Val.TRUE);
		assertEquals(Val.of(1), Val.of(1.0D));
	}

	@Test
	void hashTest() {
		assertEquals(Val.UNDEFINED.hashCode(), Val.UNDEFINED.hashCode());
		assertEquals(Val.error("ABC").hashCode(), Val.error("ABC").hashCode());
		assertNotEquals(Val.error("X").hashCode(), Val.error("Y").hashCode());
		assertNotEquals(Val.of(1L).hashCode(), BigInteger.valueOf(1L).hashCode());
		assertNotEquals(Val.TRUE.hashCode(), Val.UNDEFINED.hashCode());
		assertNotEquals(Val.UNDEFINED.hashCode(), Val.TRUE.hashCode());
		assertEquals(Val.FALSE.hashCode(), Val.FALSE.hashCode());
		assertNotEquals(Val.FALSE.hashCode(), Val.TRUE.hashCode());
		assertEquals(Val.of(1).hashCode(), Val.of(1.0D).hashCode());
	}

	@Test
	void orElse() {
		assertEquals(JSON.booleanNode(true), Val.TRUE.orElse(JSON.arrayNode()));
		assertEquals(JSON.arrayNode(), Val.error().orElse(JSON.arrayNode()));
		assertEquals(JSON.arrayNode(), Val.UNDEFINED.orElse(JSON.arrayNode()));
	}

	@Test
	void orElseGet() {
		assertEquals(JSON.booleanNode(true), Val.TRUE.orElseGet(JSON::arrayNode));
		assertEquals(JSON.arrayNode(), Val.error().orElseGet(JSON::arrayNode));
		assertEquals(JSON.arrayNode(), Val.UNDEFINED.orElseGet(JSON::arrayNode));
	}

	@Test
	void ifDefinedIsDefined() {
		final var calledWithValue = new HashSet<JsonNode>();
		Val.TRUE.ifDefined(calledWithValue::add);
		assertTrue(calledWithValue.size() == 1);
	}

	@Test
	void ifDefinedIsUndefined() {
		final var calledWithValue = new HashSet<JsonNode>();
		Val.UNDEFINED.ifDefined(calledWithValue::add);
		assertTrue(calledWithValue.size() == 0);
	}

	@Test
	void orElseThrow() {
		assertAll(() -> assertEquals(JSON.booleanNode(true), Val.TRUE.orElseThrow(RuntimeException::new)),
				() -> assertThrows(RuntimeException.class, () -> Val.UNDEFINED.orElseThrow(RuntimeException::new)),
				() -> assertThrows(RuntimeException.class, () -> Val.error().orElseThrow(RuntimeException::new)));
	}

	@Test
	void filter() {
		assertAll(() -> assertEquals(Val.UNDEFINED, Val.UNDEFINED.filter(JsonNode::isArray)),
				() -> assertEquals(Val.UNDEFINED, Val.TRUE.filter(JsonNode::isArray)),
				() -> assertEquals(Val.ofEmptyArray(), Val.ofEmptyArray().filter(JsonNode::isArray)),
				() -> assertEquals(Val.of(10), Val.of(10).filter(json -> json.intValue() > 5)),
				() -> assertEquals(Val.UNDEFINED, Val.of(10).filter(json -> json.intValue() < 5)),
				() -> assertThrows(NullPointerException.class, () -> Val.of(10).filter(null)));
	}

	@Test
	void map() {
		assertAll(() -> assertThat(Val.of(10).map(json -> json.intValue() * 2), is(optionalWithValue(is(equalTo(20))))),
				() -> assertThat(Val.UNDEFINED.map(json -> json), is(emptyOptional())));
	}

}
