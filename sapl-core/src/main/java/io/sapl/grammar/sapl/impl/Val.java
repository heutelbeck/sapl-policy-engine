/**
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
package io.sapl.grammar.sapl.impl;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jackson.JsonNumEquals;

import io.sapl.api.interpreter.PolicyEvaluationException;
import reactor.core.publisher.Flux;

public class Val {

	private static final String UNDEFINED_VALUE_ERROR = "Undefined value error.";

	private static final String OBJECT_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Expected an object, but got %s.";

	private static final String ARRAY_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Expected an array, but got %s.";

	private static final String BOOLEAN_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Boolean operation expects boolean values, but got: '%s'.";

	private static final String TEXT_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Text operation expects text values, but got: '%s'.";

	private static final String ARITHMETIC_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Number operation expects number values, but got: '%s'.";

	public static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final Val UNDEFINED = new Val();

	private static final Val TRUE = new Val(JSON.booleanNode(true));

	private static final Val FALSE = new Val(JSON.booleanNode(false));

	private static final Val NULL = Val.of(JSON.nullNode());

	private final JsonNode value;

	private Val() {
		this.value = null;
	}

	private Val(JsonNode value) {
		this.value = value;
	}

	public static Val of(JsonNode value) {
		return value == null ? UNDEFINED : new Val(value);
	}

	public JsonNode get() {
		if (value == null) {
			throw new NoSuchElementException("Value undefined");
		}
		return value;
	}

	public boolean isDefined() {
		return value != null;
	}

	public boolean isUndefined() {
		return value == null;
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

	public boolean isBinary() {
		return isDefined() && value.isBinary();
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

	public static Flux<Val> nullFlux() {
		return Flux.just(ofNull());
	}

	public void ifDefined(Consumer<? super JsonNode> consumer) {
		if (value != null)
			consumer.accept(value);
	}

	public JsonNode orElse(JsonNode other) {
		return value != null ? value : other;
	}

	public JsonNode orElseGet(Supplier<? extends JsonNode> other) {
		return value != null ? value : other.get();
	}

	public <X extends Throwable> JsonNode orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
		if (value != null) {
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
			return predicate.test(value) ? this : undefined();
	}

	public <U> Optional<U> map(Function<? super JsonNode, ? extends U> mapper) {
		Objects.requireNonNull(mapper);
		if (isUndefined())
			return Optional.empty();
		else {
			return Optional.ofNullable(mapper.apply(value));
		}
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
		if (value == other.value) {
			return true;
		}

		if (isDefined() != other.isDefined()) {
			return false;
		}

		return JsonNumEquals.getInstance().equivalent(value, other.value);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(value);
	}

	@Override
	public String toString() {
		return value != null ? String.format("Value[%s]", value.toString()) : "Value.undefined";
	}

	public Optional<JsonNode> optional() {
		return isUndefined() ? Optional.empty() : Optional.of(value);
	}

	public static Val ofNull() {
		return NULL;
	}

	public static Flux<Val> undefinedFlux() {
		return Flux.just(undefined());
	}

	public static Val undefined() {
		return UNDEFINED;
	}

	public static Val ofTrue() {
		return TRUE;
	}

	public static Flux<Val> fluxOfTrue() {
		return Flux.just(TRUE);
	}

	public static Val ofFalse() {
		return FALSE;
	}

	public static Flux<Val> fluxOfFalse() {
		return Flux.just(FALSE);
	}

	public static Val ofJsonString(String val) throws JsonMappingException, JsonProcessingException {
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

	public static Val of(long val) {
		return Val.of(JSON.numberNode(val));
	}

	public static Val of(int val) {
		return Val.of(JSON.numberNode(val));
	}

	public static Val of(double val) {
		return Val.of(JSON.numberNode(val));
	}

	public static Flux<Val> fluxOf(BigDecimal val) {
		return Flux.just(of(val));
	}

	public static Val of(String val) {
		return Val.of(JSON.textNode(val));
	}

	public static Flux<Val> fluxOf(String val) {
		return Flux.just(of(val));
	}

	public static Flux<Boolean> toBoolean(Val value) {
		if (value.isUndefined() || !value.get().isBoolean()) {
			return Flux.error(new PolicyEvaluationException(BOOLEAN_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
		}
		return Flux.just(value.get().booleanValue());
	}

	public static Flux<JsonNode> toJsonNode(Val value) {
		if (value.isUndefined()) {
			return Flux.error(new PolicyEvaluationException(UNDEFINED_VALUE_ERROR));
		}
		return Flux.just(value.get());
	}

	public static Flux<ArrayNode> toArrayNode(Val value) {
		if (value.isUndefined() || !value.get().isArray()) {
			return Flux.error(new PolicyEvaluationException(ARRAY_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
		}
		return Flux.just((ArrayNode) value.get());
	}

	public static Flux<ObjectNode> toObjectNode(Val value) {
		if (!value.isUndefined() || !value.get().isObject()) {
			return Flux.error(new PolicyEvaluationException(OBJECT_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
		}
		return Flux.just((ObjectNode) value.get());
	}

	public static Flux<String> toText(Val value) {
		if (value.isUndefined() || !value.get().isTextual()) {
			return Flux.error(new PolicyEvaluationException(TEXT_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
		}
		return Flux.just(value.get().textValue());
	}

	public static Flux<BigDecimal> toBigDecimal(Val value) {
		if (value.isUndefined() || !value.get().isNumber()) {
			return Flux.error(new PolicyEvaluationException(ARITHMETIC_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
		}
		return Flux.just(value.get().decimalValue());
	}

	public static String typeOf(Val value) {
		return value.isDefined() ? value.get().getNodeType().toString() : "undefined";
	}

}
