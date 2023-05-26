/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
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

/**
 * Val is the basic data type SAPL expressions evaluate to. A Val may contain a
 * Jackson JsonNode, an error, or may be undefined. Val is immutable.
 * 
 * @author Dominic Heutelbeck
 */
/**
 * @author dominic
 *
 */
/**
 * @author dominic
 *
 */
/**
 * @author dominic
 *
 */
public class Val implements Traced {

	static final String ERROR_TEXT                           = "ERROR";
	static final String UNDEFINED_TEXT                       = "undefined";
	static final String UNKNOWN_ERROR                        = "Unknown Error";
	static final String UNDEFINED_VALUE_ERROR                = "Undefined value error.";
	static final String OBJECT_OPERATION_TYPE_MISMATCH_S     = "Type mismatch. Expected an object, but got %s.";
	static final String ARRAY_OPERATION_TYPE_MISMATCH_S      = "Type mismatch. Expected an array, but got %s.";
	static final String BOOLEAN_OPERATION_TYPE_MISMATCH_S    = "Type mismatch. Boolean operation expects boolean values, but got: '%s'.";
	static final String NUMBER_OPERATION_TYPE_MISMATCH_S     = "Type mismatch. Number operation expects number values, but got: '%s'.";
	static final String TEXT_OPERATION_TYPE_MISMATCH_S       = "Type mismatch. Text operation expects text values, but got: '%s'.";
	static final String ARITHMETIC_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Number operation expects number values, but got: '%s'.";

	/**
	 * Convenience Instance of a JsonNodeFactory.
	 */
	public static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	/**
	 * Convenience Instance of a ObjectMapper. Attention, this is not the same as
	 * the globally available bean in Spring.
	 */
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * Constant 'undefined' Val.
	 */
	public static final Val UNDEFINED = new Val();

	/**
	 * Constant 'true' Val.
	 */
	public static final Val TRUE = new Val(JSON.booleanNode(true));

	/**
	 * Constant 'false' Val.
	 */
	public static final Val FALSE = new Val(JSON.booleanNode(false));

	/**
	 * Constant 'null' Val.
	 */
	public static final Val NULL = Val.of(JSON.nullNode());

	private final JsonNode value;
	private final String   errorMessage;
	private final boolean  secret;
	private final Trace    trace;

	private Val(String errorMessage) {
		this.value        = null;
		this.errorMessage = errorMessage;
		this.secret       = false;
		this.trace        = null;
	}

	private Val() {
		this.value        = null;
		this.errorMessage = null;
		this.secret       = false;
		this.trace        = null;
	}

	private Val(JsonNode value, String errorMessage, boolean isSecret) {
		this.value        = value;
		this.errorMessage = errorMessage;
		this.secret       = isSecret;
		this.trace        = null;
	}

	private Val(JsonNode value, String errorMessage, boolean isSecret, Trace trace) {
		this.value        = value;
		this.errorMessage = errorMessage;
		this.secret       = isSecret;
		this.trace        = trace;
	}

	private Val(JsonNode value) {
		this.value        = value;
		this.errorMessage = null;
		this.secret       = false;
		this.trace        = null;
	}

	/**
	 * @return marks a value to be a secret.
	 */
	public Val asSecret() {
		return new Val(value, errorMessage, true);
	}

	/**
	 * @param trace a trace
	 * @return the Val with attached trace.
	 */
	public Val withTrace(Trace trace) {
		return new Val(value, errorMessage, secret, trace);
	}

	/**
	 * Attaches a trace to the Val.
	 * 
	 * @param operation traced operation
	 * @return the Val with attached trace
	 */
	public Val withTrace(Class<?> operation) {
		return withTrace(new Trace(operation));
	}

	/**
	 * Attaches a trace to the Val including arguments.
	 * 
	 * @param operation traced operation
	 * @param arguments the arguments
	 * @return the Val with attached trace
	 */
	public Val withTrace(Class<?> operation, Val... arguments) {
		return withTrace(new Trace(operation, arguments));
	}

	/**
	 * Attaches a trace to the Val including arguments.
	 * 
	 * @param operation traced operation
	 * @param arguments the arguments with parameter names
	 * @return the Val with attached trace
	 */
	public Val withTrace(Class<?> operation, Map<String, Traced> arguments) {
		return withTrace(new Trace(operation, arguments));
	}

	/**
	 * Attaches a trace to the Val parent value.
	 * 
	 * @param operation   traced operation
	 * @param parentValue the parent value
	 * @return the Val with attached trace
	 */
	public Val withParentTrace(Class<?> operation, Traced parentValue) {
		return withTrace(new Trace(operation, new ExpressionArgument("parentValue", parentValue)));
	}

	/**
	 * Attaches a trace to the Val including arguments.
	 * 
	 * @param operation traced operation
	 * @param arguments the arguments with parameter names
	 * @return the Val with attached trace
	 */
	public Val withTrace(Class<?> operation, ExpressionArgument... arguments) {
		return withTrace(new Trace(operation, arguments));
	}

	/**
	 * Attaches a trace to the Val including arguments for attribute finders.
	 * 
	 * @param leftHandValue left hand value of attribute finder
	 * @param operation     traced operation
	 * @param arguments     the arguments with parameter names
	 * @return the Val with attached trace
	 */
	public Val withTrace(Traced leftHandValue, Class<?> operation, Traced... arguments) {
		return withTrace(new Trace(leftHandValue, operation, arguments));
	}

	/**
	 * Creates a Val with a given JSON value.
	 * 
	 * @param value a JSON value or null.
	 * @return Val with a given JSON value or UNDEFINED if value was null.
	 */
	public static Val of(JsonNode value) {
		return value == null ? UNDEFINED : new Val(value);
	}

	/**
	 * @return a Val with an empty JSON object.
	 */
	public static Val ofEmptyObject() {
		return new Val(JSON.objectNode());
	}

	/**
	 * @return a Val with an empty JSON array.
	 */
	public static Val ofEmptyArray() {
		return new Val(JSON.arrayNode());
	}

	/**
	 * @return a Val with a an unknown error.
	 */
	public static Val error() {
		return new Val(UNKNOWN_ERROR);
	}

	/**
	 * @param errorMessage The error message. The same formatting options apply as
	 *                     with String.format.
	 * @param args         Arguments referenced by the format.
	 * @return a Val with a formatted error message.
	 */
	public static Val error(String errorMessage, Object... args) {
		return new Val(String.format(errorMessage == null ? "Undefined Error" : errorMessage, args));
	}

	/**
	 * @param throwable a Throwable
	 * @return a Val with an error message from the throwable. If no message is
	 *         preset, the type of the Throwable is used as the message.
	 */
	public static Val error(Throwable throwable) {
		if (throwable == null)
			return new Val("Undefined Error");

		return (throwable.getMessage() == null || throwable.getMessage().isBlank())
				? new Val(throwable.getClass().getSimpleName())
				: new Val(throwable.getMessage());
	}

	/**
	 * @param errorMessage The error message. The same formatting options apply as
	 *                     with String.format.
	 * @param args         Arguments referenced by the format.
	 * @return Flux of a Val with a formatted error message.
	 */
	public static Flux<Val> errorFlux(String errorMessage, Object... args) {
		return Flux.just(error(errorMessage, args));
	}

	/**
	 * @param errorMessage The error message. The same formatting options apply as
	 *                     with String.format.
	 * @param args         Arguments referenced by the format.
	 * @return Mono of a Val with a formatted error message.
	 */
	public static Mono<Val> errorMono(String errorMessage, Object... args) {
		return Mono.just(error(errorMessage, args));
	}

	/**
	 * @return true, if the contents is marked as a secret.
	 */
	public boolean isSecret() {
		return secret;
	}

	/**
	 * @return the error message, or NoSuchElementException if not an error.
	 */
	public String getMessage() {
		if (isError()) {
			return errorMessage;
		}
		throw new NoSuchElementException("Value not an error");
	}

	/**
	 * @return true, iff the Val is an error.
	 */
	public boolean isError() {
		return errorMessage != null;
	}

	/**
	 * @return true, iff the Val is not an error.
	 */
	public boolean noError() {
		return errorMessage == null;
	}

	/**
	 * @return the JsonNode value of the Val, or a NoSuchElementException, if Val is
	 *         undefined or an error.
	 */
	public JsonNode get() {
		if (isError()) {
			throw new NoSuchElementException("Value is an error: " + getMessage());
		}
		if (value == null) {
			throw new NoSuchElementException("Value undefined");
		}
		return value;
	}

	/**
	 * @return true, iff Val not an error or undefined.
	 */
	public boolean isDefined() {
		return value != null;
	}

	/**
	 * @return true, iff error or undefined.
	 */
	public boolean isUndefined() {
		return value == null && noError();
	}

	/**
	 * @return true, iff value is an array.
	 */
	public boolean isArray() {
		return isDefined() && value.isArray();
	}

	/**
	 * @return true, iff value is a BigDecimal number.
	 */
	public boolean isBigDecimal() {
		return isDefined() && value.isBigDecimal();
	}

	/**
	 * @return true, iff value is a BigInteger number.
	 */
	public boolean isBigInteger() {
		return isDefined() && value.isBigInteger();
	}

	/**
	 * @return true, iff value is a Boolean.
	 */
	public boolean isBoolean() {
		return isDefined() && value.isBoolean();
	}

	/**
	 * @return true, iff value is a Double number.
	 */
	public boolean isDouble() {
		return isDefined() && value.isDouble();
	}

	/**
	 * @return true, iff value is an empty object or array.
	 */
	public boolean isEmpty() {
		return isDefined() && value.isEmpty();
	}

	/**
	 * @return true, iff value is a Float number.
	 */
	public boolean isFloat() {
		return isDefined() && value.isFloat();
	}

	/**
	 * @return true, iff value represents a non-integral numeric JSON value
	 */
	public boolean isFloatingPointNumber() {
		return isDefined() && value.isFloatingPointNumber();
	}

	/**
	 * @return true, iff value is a Integer number.
	 */
	public boolean isInt() {
		return isDefined() && value.isInt();
	}

	/**
	 * @return true, iff value is a Long number.
	 */
	public boolean isLong() {
		return isDefined() && value.isLong();
	}

	/**
	 * @return true, iff value is JSON null.
	 */
	public boolean isNull() {
		return isDefined() && value.isNull();
	}

	/**
	 * @return true, iff value is a number.
	 */
	public boolean isNumber() {
		return isDefined() && value.isNumber();
	}

	/**
	 * @return true, iff value is an object.
	 */
	public boolean isObject() {
		return isDefined() && value.isObject();
	}

	/**
	 * @return true, iff value is textual.
	 */
	public boolean isTextual() {
		return isDefined() && value.isTextual();
	}

	/**
	 * Method that returns true for all value nodes: ones that are not containers,
	 * and that do not represent "missing" nodes in the path. Such value nodes
	 * represent String, Number, Boolean and null values from JSON.
	 * 
	 * @return is a value node.
	 */
	public boolean isValueNode() {
		return isDefined() && value.isValueNode();
	}

	/**
	 * Calls the consumer with the value, if Val is defined and not an error.
	 * 
	 * @param consumer for a JSON node
	 */
	public void ifDefined(Consumer<? super JsonNode> consumer) {
		if (isDefined())
			consumer.accept(value);
	}

	/**
	 * Returns the given other if the Val is undefined or an error.
	 * 
	 * @param other a JSON node
	 * @return other if Val undefined or error.
	 */
	public JsonNode orElse(JsonNode other) {
		return isDefined() ? value : other;
	}

	/**
	 * Returns the supplied given other if the Val is undefined or an error.
	 * 
	 * @param other a JSON node
	 * @return the result of other if Val undefined or error.
	 */
	public JsonNode orElseGet(Supplier<? extends JsonNode> other) {
		return isDefined() ? value : other.get();
	}

	/**
	 * @param <X>               error type
	 * @param exceptionSupplier a supplier for a Throwable.
	 * @return the value of the Val, if defined. Else the supplied Thworable is
	 *         thrown.
	 * @throws X an Exception if value undefined.
	 */
	public <X extends Throwable> JsonNode orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
		if (isDefined()) {
			return value;
		} else {
			throw exceptionSupplier.get();
		}
	}

	/**
	 * @param predicate a predicate
	 * @return the original value, or an undefined Val if the predicate evaluates to
	 *         false for the value.
	 */
	public Val filter(Predicate<? super JsonNode> predicate) {
		Objects.requireNonNull(predicate);
		if (isUndefined())
			return this;
		else
			return predicate.test(value) ? this : UNDEFINED;
	}

	/**
	 * @param left  a Val
	 * @param right a Val
	 * @return a Boolean Val which is true, iff left and right are not equal.
	 */
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

	/**
	 * @param left  a Val
	 * @param right a Val
	 * @return a Boolean Val which is true, iff left and right are equal.
	 */
	public static Val equal(Val left, Val right) {
		return Val.of(!notEqualBool(left, right));
	}

	/**
	 * {@inheritDoc}
	 */
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
		if (isSecret()) {
			return "SECRET";
		}
		if (isError()) {
			return "ERROR[" + errorMessage + "]";
		}
		return value != null ? value.toString() : UNDEFINED_TEXT;
	}

	/**
	 * @return an Optional<JsonNode>. Empty if undefined or error.
	 */
	public Optional<JsonNode> optional() {
		return Optional.ofNullable(value);
	}

	/**
	 * @return a Flux only containing the Val with the boolean value True.
	 */
	public static Flux<Val> fluxOfTrue() {
		return Flux.just(TRUE);
	}

	/**
	 * @return a Flux only containing the Val with an undefined value.
	 */
	public static Flux<Val> fluxOfUndefined() {
		return Flux.just(UNDEFINED);
	}

	/**
	 * @return a Flux only containing the Val with the boolean value False.
	 */
	public static Flux<Val> fluxOfFalse() {
		return Flux.just(FALSE);
	}

	/**
	 * @return a Flux only containing the Val with the a JSON null value.
	 */
	public static Flux<Val> fluxOfNull() {
		return Flux.just(NULL);
	}

	/**
	 * @param val a JSON String
	 * @return a val containing the value of the JSON String.
	 * @throws JsonProcessingException if the JSON String was invalid.
	 */
	public static Val ofJson(String val) throws JsonProcessingException {
		return Val.of(MAPPER.readValue(val, JsonNode.class));
	}

	/**
	 * @param val a Boolean value
	 * @return a Val with the boolean value.
	 */
	public static Val of(boolean val) {
		return val ? TRUE : FALSE;
	}

	/**
	 * @param val a Boolean value
	 * @return Flux only containing the a Val with the boolean value.
	 */
	public static Flux<Val> fluxOf(boolean val) {
		return Flux.just(of(val));
	}

	/**
	 * @param val a Long value
	 * @return Flux only containing the a Val with the Long value.
	 */
	public static Flux<Val> fluxOf(long val) {
		return Flux.just(of(val));
	}

	/**
	 * @param val a BigDecimal number
	 * @return a Val with the given number value.
	 */
	public static Val of(BigDecimal val) {
		return Val.of(JSON.numberNode(val));
	}

	/**
	 * @param val a BigInteger number
	 * @return a Val with the given number value.
	 */
	public static Val of(BigInteger val) {
		return Val.of(JSON.numberNode(val));
	}

	/**
	 * @param val a Long number
	 * @return a Val with the given number value.
	 */
	public static Val of(long val) {
		return Val.of(JSON.numberNode(val));
	}

	/**
	 * @param val an Integer number
	 * @return a Val with the given number value.
	 */
	public static Val of(int val) {
		return Val.of(JSON.numberNode(val));
	}

	/**
	 * @param val a Double number
	 * @return a Val with the given number value.
	 */
	public static Val of(double val) {
		return Val.of(JSON.numberNode(val));
	}

	/**
	 * @param val a Float number
	 * @return a Val with the given number value.
	 */
	public static Val of(float val) {
		return Val.of(JSON.numberNode(val));
	}

	/**
	 * @param val a BigDecimal number
	 * @return a Flux only containing a Val with the given number.
	 */
	public static Flux<Val> fluxOf(BigDecimal val) {
		return Flux.just(of(val));
	}

	/**
	 * @param val a BigInteger number
	 * @return a Flux only containing a Val with the given number.
	 */
	public static Flux<Val> fluxOf(BigInteger val) {
		return Flux.just(of(val));
	}

	/**
	 * @param val a String value
	 * @return a Val with the String as a JSON Text value.
	 */
	public static Val of(String val) {
		return Val.of(JSON.textNode(val));
	}

	/**
	 * @param val a String value
	 * @return a Flux only containing a Val with the String as a JSON Text value.
	 */
	public static Flux<Val> fluxOf(String val) {
		return Flux.just(of(val));
	}

	/**
	 * @param value a Val
	 * @return if the Val is a Boolean, returns a Flux of just this Boolean. Else a
	 *         Flux only containing a PolicyEvaluationException error.
	 */
	public static Flux<Boolean> toBoolean(Val value) {
		if (value.isBoolean()) {
			return Flux.just(value.get().booleanValue());
		}
		return Flux.error(new PolicyEvaluationException(BOOLEAN_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
	}

	/**
	 * @return if the Val is Boolean, the Boolean value is returned. Else throws a
	 *         PolicyEvaluiationException.
	 */
	public boolean getBoolean() {
		if (isBoolean()) {
			return value.booleanValue();
		}
		throw new PolicyEvaluationException(BOOLEAN_OPERATION_TYPE_MISMATCH_S, typeOf(this));
	}

	/**
	 * @return if the Val is a number, the number is returned as Long. Else throws a
	 *         PolicyEvaluiationException.
	 */
	public long getLong() {
		if (isNumber()) {
			return value.longValue();
		}
		throw new PolicyEvaluationException(NUMBER_OPERATION_TYPE_MISMATCH_S, typeOf(this));
	}

	/**
	 * @return the Val as a String. If the Val was a JSON String, the contents is
	 *         returned. Else the contents toString is used.
	 */
	public String getText() {
		if (isUndefined()) {
			return UNDEFINED_TEXT;
		}
		if (isTextual()) {
			return value.textValue();
		}
		return value.toString();
	}

	/**
	 * Validation method to ensure a Val is a Boolean.
	 * 
	 * @param value a Val
	 * @return the input Val, or an error, if the input is not Boolean.
	 */
	public static Val requireBoolean(Val value) {
		if (!value.isBoolean()) {
			return Val.error(BOOLEAN_OPERATION_TYPE_MISMATCH_S, typeOf(value));
		}
		return value;
	}

	/**
	 * @param value a Val
	 * @return a Flux of the the value of the Val as an JsonNode. Or a Flux with an
	 *         error.
	 */
	public static Flux<JsonNode> toJsonNode(Val value) {
		if (value.isUndefined()) {
			return Flux.error(new PolicyEvaluationException(UNDEFINED_VALUE_ERROR));
		}
		return Flux.just(value.get());
	}

	/**
	 * Validation method to ensure a Val is a JsonNode, i.e., not undefined or an
	 * error.
	 * 
	 * @param value a Val
	 * @return the input Val, or an error, if the input is not a JsonNode.
	 */
	public static Val requireJsonNode(Val value) {
		if (value.isError()) {
			return value;
		}
		if (value.isDefined()) {
			return value;
		}
		return Val.error(UNDEFINED_VALUE_ERROR);
	}

	/**
	 * @return the value of the Val as a JsonNode. Throws a PolicyEvaluation
	 *         Exception if the value is not defined.
	 */
	public JsonNode getJsonNode() {
		if (this.isDefined()) {
			return value;
		}
		throw new PolicyEvaluationException(BOOLEAN_OPERATION_TYPE_MISMATCH_S, typeOf(this));
	}

	/**
	 * @param value a Val
	 * @return a Flux of the the value of the Val as an ArrayNode. Or a Flux with an
	 *         error.
	 */
	public static Flux<ArrayNode> toArrayNode(Val value) {
		if (value.isUndefined() || !value.get().isArray()) {
			return Flux.error(new PolicyEvaluationException(ARRAY_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
		}
		return Flux.just((ArrayNode) value.get());
	}

	/**
	 * @return the value of the Val as an ArrayNode. Throws a PolicyEvaluation
	 *         Exception if the value is not a JSON array.
	 */
	public ArrayNode getArrayNode() {
		if (this.isArray()) {
			return (ArrayNode) value;
		}
		throw new PolicyEvaluationException(ARRAY_OPERATION_TYPE_MISMATCH_S, typeOf(this));
	}

	/**
	 * @return the value of the Val as an ObjectNode. Throws a PolicyEvaluation
	 *         Exception if the value is not a JSON object.
	 */
	public ObjectNode getObjectNode() {
		if (this.isObject()) {
			return (ObjectNode) value;
		}
		throw new PolicyEvaluationException(ARRAY_OPERATION_TYPE_MISMATCH_S, typeOf(this));
	}

	/**
	 * @return the value of the Val as a BigDecimal. Throws a PolicyEvaluation
	 *         Exception if the value is not a number.
	 */
	public BigDecimal decimalValue() {
		if (this.isNumber()) {
			return value.decimalValue();
		}
		throw new PolicyEvaluationException(ARITHMETIC_OPERATION_TYPE_MISMATCH_S, typeOf(this));
	}

	/**
	 * Validation method to ensure a Val is a JSON array.
	 * 
	 * @param value a Val
	 * @return the input Val, or an error, if the input is not an array.
	 */
	public static Val requireArrayNode(Val value) {
		if (value.isError()) {
			return value;
		}
		if (value.isUndefined() || !value.get().isArray()) {
			return Val.error(ARRAY_OPERATION_TYPE_MISMATCH_S, typeOf(value));
		}
		return value;
	}

	/**
	 * Converts Val to a Flux of ObjectNode.
	 * 
	 * @param value a Val
	 * @return a Flux only containing the ObjectNode value or an error if Val not
	 *         ObjectNode.
	 */
	public static Flux<ObjectNode> toObjectNode(Val value) {
		if (value.isUndefined() || !value.get().isObject()) {
			return Flux.error(new PolicyEvaluationException(OBJECT_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
		}
		return Flux.just((ObjectNode) value.get());
	}

	/**
	 * Validation method to ensure a Val is a JSON object.
	 * 
	 * @param value a Val
	 * @return the input Val, or an error, if the input is not an object.
	 */
	public static Val requireObjectNode(Val value) {
		if (value.isError()) {
			return value;
		}
		if (value.isUndefined() || !value.get().isObject()) {
			return Val.error(OBJECT_OPERATION_TYPE_MISMATCH_S, typeOf(value));
		}
		return value;
	}

	/**
	 * Converts Val to a Flux of String.
	 * 
	 * @param value a Val
	 * @return a Flux only containing the String value an error if Val not textual.
	 */
	public static Flux<String> toText(Val value) {
		if (value.isUndefined() || !value.get().isTextual()) {
			return Flux.error(new PolicyEvaluationException(TEXT_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
		}
		return Flux.just(value.get().textValue());
	}

	/**
	 * Validation method to ensure a Val is a textual value.
	 * 
	 * @param value a Val
	 * @return the input Val, or an error, if the input is not textual.
	 */
	public static Val requireText(Val value) {
		if (value.isError()) {
			return value;
		}
		if (value.isUndefined() || !value.get().isTextual()) {
			return Val.error(TEXT_OPERATION_TYPE_MISMATCH_S, typeOf(value));
		}
		return value;
	}

	/**
	 * Converts Val to a Flux of BigDecimal.
	 * 
	 * @param value a Val
	 * @return a Flux only containing the number value as BigDecimal or an error if
	 *         Val not a number.
	 */
	public static Flux<BigDecimal> toBigDecimal(Val value) {
		if (value.isUndefined() || !value.get().isNumber()) {
			return Flux.error(new PolicyEvaluationException(ARITHMETIC_OPERATION_TYPE_MISMATCH_S, typeOf(value)));
		}
		return Flux.just(value.get().decimalValue());
	}

	/**
	 * Validation method to ensure a val is a numerical value.
	 * 
	 * @param value a Val
	 * @return the input Val, or an error, if the input is not a number.
	 */
	public static Val requireBigDecimal(Val value) {
		if (value.isError()) {
			return value;
		}
		if (value.isUndefined() || !value.get().isNumber()) {
			return Val.error(ARITHMETIC_OPERATION_TYPE_MISMATCH_S, typeOf(value));
		}
		return value;
	}

	/**
	 * Validation method to ensure a val is a numerical value.
	 * 
	 * @param value a Val
	 * @return the input Val, or an error, if the input is not a number.
	 */
	public static Val requireNumber(Val value) {
		return requireBigDecimal(value);
	}

	/**
	 * @param value a Val
	 * @return a String describing the type of the Val.
	 */
	public static String typeOf(Val value) {
		if (value.isError()) {
			return ERROR_TEXT;
		}
		return value.isDefined() ? value.get().getNodeType().toString() : UNDEFINED_TEXT;
	}

	/**
	 * @return a String describing the type of the Val.
	 */
	public String getValType() {
		return typeOf(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public JsonNode getTrace() {
		JsonNode val;
		if (isSecret())
			val = JSON.textNode("|SECRET|");
		else if (isError()) {
			val = JSON.textNode("|ERROR| " + errorMessage);
		} else if (isUndefined()) {
			val = JSON.textNode("|UNDEFINED|");
		} else {
			val = value;
		}

		var traceJson = JSON.objectNode();
		traceJson.set("value", val);
		if (trace != null) {
			traceJson.set("trace", trace.getTrace());
		}
		return traceJson;
	}

}