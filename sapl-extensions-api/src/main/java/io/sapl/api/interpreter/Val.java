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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jackson.JsonNumEquals;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class Val {

	static final String ERROR_TEXT = "ERROR";
	static final String UNDEFINED_TEXT = "undefined";
	static final String UNKNOWN_ERROR = "Unknown Error";
	static final String UNDEFINED_VALUE_ERROR = "Undefined value error.";
	static final String OBJECT_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Expected an object, but got %s.";
	static final String ARRAY_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Expected an array, but got %s.";
	static final String BOOLEAN_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Boolean operation expects boolean values, but got: '%s'.";
	static final String TEXT_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Text operation expects text values, but got: '%s'.";
	static final String ARITHMETIC_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Number operation expects number values, but got: '%s'.";

	public static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	private static final ObjectMapper MAPPER = new ObjectMapper(); // .enable(SerializationFeature.INDENT_OUTPUT);

	public static final Val UNDEFINED = new Val();
	public static final Val TRUE = new Val(JSON.booleanNode(true));
	public static final Val FALSE = new Val(JSON.booleanNode(false));
	public static final Val NULL = Val.of(JSON.nullNode());

	private final JsonNode value;
	private String errorMessage;

	private Val(String errorMessage) {
		this.value = null;
		this.errorMessage = errorMessage;
	}

	private Val() {
		this.value = null;
		this.errorMessage = null;
	}

	private Val(JsonNode value) {
		this.value = value;
	}

	public static Val of(JsonNode value) {
		return value == null ? UNDEFINED : new Val(value);
	}

	public static Val ofEmptyObject() {
		return new Val(JSON.objectNode());
	}

	public static Val ofEmptyArray() {
		return new Val(JSON.arrayNode());
	}

	public static Val error() {
		return new Val(UNKNOWN_ERROR);
	}

	public static Val error(String errorMessage, Object... args) {
		return new Val(String.format(errorMessage, args));
	}

	public static Val error(Throwable throwable) {
		return (throwable.getMessage() == null || throwable.getMessage().isBlank())
				? new Val(throwable.getClass().getSimpleName())
				: new Val(throwable.getMessage());
	}

	public static Flux<Val> errorFlux(String errorMessage, Object... args) {
		return Flux.just(error(errorMessage, args));
	}

	public static Mono<Val> errorMono(String errorMessage, Object... args) {
		return Mono.just(error(errorMessage, args));
	}

	public String getMessage() {
		if (isError()) {
			return errorMessage;
		}
		throw new NoSuchElementException("Value not an error");
	}

	public boolean isError() {
		return errorMessage != null;
	}

	public boolean noError() {
		return errorMessage == null;
	}

	public JsonNode get() {
		if (isError()) {
			throw new NoSuchElementException("Value is an error: " + getMessage());
		}
		if (value == null) {
			throw new NoSuchElementException("Value undefined");
		}
		return value;
	}

	public boolean isDefined() {
		return value != null;
	}

	public boolean isUndefined() {
		return value == null && noError();
	}

	public boolean isArray() {
		return isDefined() && value.isArray();
	}

	public boolean isBigDecimal() {
		return isDefined() && value.isBigDecimal();
	}

	public boolean isBigInteger() {
		return isDefined() && value.isBigInteger();
	}

	public boolean isBoolean() {
		return isDefined() && value.isBoolean();
	}

	public boolean isDouble() {
		return isDefined() && value.isDouble();
	}

	public boolean isEmpty() {
		return isDefined() && value.isEmpty();
	}

	public boolean isFloat() {
		return isDefined() && value.isFloat();
	}

	public boolean isFloatingPointNumber() {
		return isDefined() && value.isFloatingPointNumber();
	}

	public boolean isInt() {
		return isDefined() && value.isInt();
	}

	public boolean isLong() {
		return isDefined() && value.isLong();
	}

	public boolean isNull() {
		return isDefined() && value.isNull();
	}

	public boolean isNumber() {
		return isDefined() && value.isNumber();
	}

	public boolean isObject() {
		return isDefined() && value.isObject();
	}

	public boolean isTextual() {
		return isDefined() && value.isTextual();
	}

	public boolean isValueNode() {
		return isDefined() && value.isValueNode();
	}

	public void ifDefined(Consumer<? super JsonNode> consumer) {
		if (isDefined())
			consumer.accept(value);
	}

	public JsonNode orElse(JsonNode other) {
		return isDefined() ? value : other;
	}

	public JsonNode orElseGet(Supplier<? extends JsonNode> other) {
		return isDefined() ? value : other.get();
	}

	public <X extends Throwable> JsonNode orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
		if (isDefined()) {
			return value;
		} else {
			throw exceptionSupplier.get();
		}
	}

	public Val filter(Predicate<? super JsonNode> predicate) {
		Objects.requireNonNull(predicate);
		if (isUndefined())
			return this;
		else
			return predicate.test(value) ? this : UNDEFINED;
	}

	public <U> Optional<U> map(Function<? super JsonNode, ? extends U> mapper) {
		Objects.requireNonNull(mapper);
		if (isUndefined())
			return Optional.empty();
		else {
			return Optional.ofNullable(mapper.apply(value));
		}
	}

	public static Val notEqual(Val left, Val right) {
		return Val.of(notEqualBool(left, right));
	}

	private static boolean notEqualBool(Val left, Val right) {
		if (left.isUndefined() && right.isUndefined()) {
			return false;
		}
		if (left.isUndefined() || right.isUndefined()) {
			return true;
		}
		if (left.isNumber() && right.isNumber()) {
			return left.decimalValue().compareTo(right.decimalValue()) != 0;
		} else {
			return !left.get().equals(right.get());
		}
	}

	public static Val equal(Val left, Val right) {
		return Val.of(!notEqualBool(left, right));
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Val)) {
			return false;
		}
		Val other = (Val) obj;
		if (isError() != other.isError()) {
			return false;
		}
		if (isError()) {
			return errorMessage.equals(other.getMessage());
		}
		if (isDefined() != other.isDefined()) {
			return false;
		}

		return JsonNumEquals.getInstance().equivalent(value, other.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(JsonNumEquals.getInstance().hash(value), errorMessage);
	}

	@Override
	public String toString() {
		if (isError()) {
			return "ERROR[" + errorMessage + "]";
		}
		return value != null ? String.format("Value[%s]", value.toString()) : "Value[undefined]";
	}

	public Optional<JsonNode> optional() {
		return isDefined() ? Optional.of(value) : Optional.empty();
	}

	public static Flux<Val> fluxOfTrue() {
		return Flux.just(TRUE);
	}

	public static Flux<Val> fluxOfUndefined() {
		return Flux.just(UNDEFINED);
	}

	public static Flux<Val> fluxOfFalse() {
		return Flux.just(FALSE);
	}

	public static Flux<Val> fluxOfNull() {
		return Flux.just(NULL);
	}

	public static Val ofJson(String val) throws JsonProcessingException {
		return Val.of(MAPPER.readValue(val, JsonNode.class));
	}

	public static Val of(boolean val) {
		return val ? TRUE : FALSE;
	}

	public static Flux<Val> fluxOf(boolean val) {
		return Flux.just(of(val));
	}

	public static Val of(BigDecimal val) {
		return Val.of(JSON.numberNode(val));
	}

	public static Val of(BigInteger val) {
		return Val.of(JSON.numberNode(val));
	}

	public static Val of(long val) {
		return Val.of(JSON.numberNode(val));
	}

	public static Val of(int val) {
		return Val.of(JSON.numberNode(val));
	}

	public static Val of(double val) {
		return Val.of(JSON.numberNode(val));
	}

	public static Val of(float val) {
		return Val.of(JSON.numberNode(val));
	}

	public static Flux<Val> fluxOf(BigDecimal val) {
		return Flux.just(of(val));
	}

	public static Flux<Val> fluxOf(BigInteger val) {
		return Flux.just(of(val));
	}

	public static Val of(String val) {
		return Val.of(JSON.textNode(val));
	}

	public static Flux<Val> fluxOf(String val) {
		return Flux.just(of(val));
	}

	public static Flux<Boolean> toBoolean(Val value) {
		if (value.isBoolean()) {
			return Flux.just(value.get().booleanValue());
		}
		return Flux.error(new PolicyEvaluationException(BOOLEAN_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
	}

	public boolean getBoolean() {
		if (isBoolean()) {
			return value.booleanValue();
		}
		throw new PolicyEvaluationException(BOOLEAN_OPERATION_TYPE_MISMATCH_S, typeOf(this));
	}

	public String getText() {
		if (isUndefined()) {
			return UNDEFINED_TEXT;
		}
		if (isTextual()) {
			return value.textValue();
		}
		return value.toString();
	}

	public static Val requireBoolean(Val value) {
		if (!value.isBoolean()) {
			return Val.error(BOOLEAN_OPERATION_TYPE_MISMATCH_S, typeOf(value));
		}
		return value;
	}

	public static Flux<JsonNode> toJsonNode(Val value) {
		if (value.isUndefined()) {
			return Flux.error(new PolicyEvaluationException(UNDEFINED_VALUE_ERROR));
		}
		return Flux.just(value.get());
	}

	public static Val requireJsonNode(Val value) {
		if (value.isError()) {
			return value;
		}
		if (value.isDefined()) {
			return value;
		}
		return Val.error(UNDEFINED_VALUE_ERROR);
	}

	public JsonNode getJsonNode() {
		if (this.isDefined()) {
			return value;
		}
		throw new PolicyEvaluationException(BOOLEAN_OPERATION_TYPE_MISMATCH_S, typeOf(this));
	}

	public static Flux<ArrayNode> toArrayNode(Val value) {
		if (value.isUndefined() || !value.get().isArray()) {
			return Flux.error(new PolicyEvaluationException(ARRAY_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
		}
		return Flux.just((ArrayNode) value.get());
	}

	public ArrayNode getArrayNode() {
		if (this.isArray()) {
			return (ArrayNode) value;
		}
		throw new PolicyEvaluationException(ARRAY_OPERATION_TYPE_MISMATCH_S, typeOf(this));
	}

	public ObjectNode getObjectNode() {
		if (this.isObject()) {
			return (ObjectNode) value;
		}
		throw new PolicyEvaluationException(ARRAY_OPERATION_TYPE_MISMATCH_S, typeOf(this));
	}

	public BigDecimal decimalValue() {
		if (this.isNumber()) {
			return value.decimalValue();
		}
		throw new PolicyEvaluationException(ARITHMETIC_OPERATION_TYPE_MISMATCH_S, typeOf(this));
	}

	public static Val requireArrayNode(Val value) {
		if (value.isError()) {
			return value;
		}
		if (value.isUndefined() || !value.get().isArray()) {
			return Val.error(ARRAY_OPERATION_TYPE_MISMATCH_S, typeOf(value));
		}
		return value;
	}

	public static Flux<ObjectNode> toObjectNode(Val value) {
		if (value.isUndefined() || !value.get().isObject()) {
			return Flux.error(new PolicyEvaluationException(OBJECT_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
		}
		return Flux.just((ObjectNode) value.get());
	}

	public static Val requireObjectNode(Val value) {
		if (value.isError()) {
			return value;
		}
		if (value.isUndefined() || !value.get().isObject()) {
			return Val.error(OBJECT_OPERATION_TYPE_MISMATCH_S, typeOf(value));
		}
		return value;
	}

	public static Flux<String> toText(Val value) {
		if (value.isUndefined() || !value.get().isTextual()) {
			return Flux.error(new PolicyEvaluationException(TEXT_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
		}
		return Flux.just(value.get().textValue());
	}

	public static Val requireText(Val value) {
		if (value.isError()) {
			return value;
		}
		if (value.isUndefined() || !value.get().isTextual()) {
			return Val.error(TEXT_OPERATION_TYPE_MISMATCH_S, typeOf(value));
		}
		return value;
	}

	public static Flux<BigDecimal> toBigDecimal(Val value) {
		if (value.isUndefined() || !value.get().isNumber()) {
			return Flux.error(new PolicyEvaluationException(ARITHMETIC_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
		}
		return Flux.just(value.get().decimalValue());
	}

	public static Val requireBigDecimal(Val value) {
		if (value.isError()) {
			return value;
		}
		if (value.isUndefined() || !value.get().isNumber()) {
			return Val.error(ARITHMETIC_OPERATION_TYPE_MISMATCH_S, typeOf(value));
		}
		return value;
	}

	public static Val requireNumber(Val value) {
		return requireBigDecimal(value);
	}

	public static String typeOf(Val value) {
		if (value.isError()) {
			return ERROR_TEXT;
		}
		return value.isDefined() ? value.get().getNodeType().toString() : UNDEFINED_TEXT;
	}

	public String getValType() {
		return typeOf(this);
	}

}
