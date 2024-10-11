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

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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
import com.fasterxml.jackson.databind.node.BaseJsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.SaplVersion;
import io.sapl.api.pdp.SaplError;
import lombok.Getter;
import reactor.core.publisher.Flux;

/**
 * This class is the central value during policy evaluation. It can be a JSON
 * value, an error,or undefined. A Val can be marked as secret.
 */
public class Val implements Traced, Serializable {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    static final String ERROR_LITERAL                              = "ERROR";
    static final String UNDEFINED_LITERAL                          = "undefined";
    static final String VALUE_IS_AN_ERROR_S_ERROR                  = "Value is an error: '%s'.";
    static final String VALUE_UNDEFINED_ERROR                      = "Value undefined";
    static final String VALUE_NOT_AN_ERROR                         = "Value not an error.";
    static final String UNDEFINED_VALUE_ERROR                      = "Undefined value error.";
    static final String OBJECT_OPERATION_TYPE_MISMATCH_S_ERROR     = "Type mismatch. Expected an object, but got %s.";
    static final String ARRAY_OPERATION_TYPE_MISMATCH_S_ERROR      = "Type mismatch. Expected an array, but got %s.";
    static final String BOOLEAN_OPERATION_TYPE_MISMATCH_S_ERROR    = "Type mismatch. Boolean operation expects boolean values, but got: '%s'.";
    static final String NUMBER_OPERATION_TYPE_MISMATCH_S_ERROR     = "Type mismatch. Number operation expects number values, but got: '%s'.";
    static final String TEXT_OPERATION_TYPE_MISMATCH_S_ERROR       = "Type mismatch. Text operation expects text values, but got: '%s'.";
    static final String ARITHMETIC_OPERATION_TYPE_MISMATCH_S_ERROR = "Type mismatch. Number operation expects number values, but got: '%s'.";

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
    public static final Val UNDEFINED = new Val(null, false, null, null);

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

    private static final NumericAwareComparator NUMERIC_AWARE_COMPARATOR = new NumericAwareComparator();

    private final BaseJsonNode value;

    @Getter
    private final boolean secret;
    final Trace           trace;
    @Getter
    private SaplError     error;

    private Val(JsonNode value) {
        this(value, false, null, null);
    }

    private Val(JsonNode value, boolean isSecret, Trace trace, SaplError error) {
        this.value  = (BaseJsonNode) value;
        this.secret = isSecret;
        this.trace  = trace;
        this.error  = error;
    }

    /**
     * @return marks a value to be a secret.
     */
    public Val asSecret() {
        return new Val(value, true, trace, error);
    }

    /**
     * @param trace a trace
     * @return the Val with attached trace.
     */
    private Val withTrace(Trace trace) {
        return new Val(value, secret, trace, error);
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
     * @param inheritsSecretStatusOfTrace if true, and a previous value is a secret,
     * the new value also is a secret.
     * @param arguments the arguments
     * @return the Val with attached trace
     */
    public Val withTrace(Class<?> operation, boolean inheritsSecretStatusOfTrace, Val... arguments) {
        var newVal = withTrace(new Trace(operation, arguments));

        if (!inheritsSecretStatusOfTrace)
            return newVal;

        for (var argument : arguments) {
            if (argument.isSecret()) {
                newVal = newVal.asSecret();
                break;
            }
        }
        return newVal;
    }

    /**
     * Attaches a trace to the Val including arguments.
     *
     * @param operation traced operation
     * @param inheritsSecretStatusOfTrace if true, and a previous value is a secret,
     * the new value also is a secret.
     * @param arguments the arguments with parameter names
     * @return the Val with attached trace
     */
    public Val withTrace(Class<?> operation, boolean inheritsSecretStatusOfTrace, Map<String, Val> arguments) {
        var newVal = withTrace(new Trace(operation, arguments));

        if (!inheritsSecretStatusOfTrace)
            return newVal;

        for (var entry : arguments.entrySet()) {
            if (entry.getValue().isSecret()) {
                newVal = newVal.asSecret();
                break;
            }
        }
        return newVal;
    }

    /**
     * Attaches a trace to the Val parent value.
     *
     * @param operation traced operation
     * @param inheritsSecretStatusOfTrace if true, and a previous value is a secret,
     * the new value also is a secret.
     * @param parentValue the parent value
     * @return the Val with attached trace
     */
    public Val withParentTrace(Class<?> operation, boolean inheritsSecretStatusOfTrace, Val parentValue) {
        final var newVal = withTrace(new Trace(operation, new ExpressionArgument(Trace.PARENT_VALUE, parentValue)));
        if (inheritsSecretStatusOfTrace && parentValue.isSecret()) {
            return newVal.asSecret();
        }
        return newVal;
    }

    /**
     * Attaches a trace to the Val including arguments.
     *
     * @param operation traced operation
     * @param inheritsSecretStatusOfTrace if true, and a previous value is a secret,
     * the new value also is a secret.
     * @param arguments the arguments with parameter names
     * @return the Val with attached trace
     */
    public Val withTrace(Class<?> operation, boolean inheritsSecretStatusOfTrace, ExpressionArgument... arguments) {
        var newVal = withTrace(new Trace(operation, arguments));

        if (!inheritsSecretStatusOfTrace)
            return newVal;

        for (var argument : arguments) {
            if (argument.value().isSecret()) {
                newVal = newVal.asSecret();
                break;
            }
        }
        return newVal;
    }

    /**
     * Attaches a trace to the Val including arguments for attribute finders.
     *
     * @param leftHandValue left hand value of attribute finder
     * @param operation traced operation
     * @param inheritsSecretStatusOfTrace if true, and a previous value is a secret,
     * the new value also is a secret.
     * @param arguments the arguments with parameter names
     * @return the Val with attached trace
     */
    public Val withTrace(Val leftHandValue, Class<?> operation, boolean inheritsSecretStatusOfTrace, Val... arguments) {
        var newVal = this.withTrace(new Trace(leftHandValue, operation, arguments));
        if (!inheritsSecretStatusOfTrace)
            return newVal;

        if (leftHandValue.isSecret())
            return newVal.asSecret();

        for (var argument : arguments) {
            if (argument.isSecret()) {
                newVal = newVal.asSecret();
                break;
            }
        }
        return newVal;
    }

    /**
     * Creates a Val with a given JSON value.
     *
     * @param value a JSON value or null.
     * @return Val with a given JSON value or UNDEFINED if value was null.
     */
    public static Val of(JsonNode value) {
        return null == value ? UNDEFINED : new Val(value);
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
     * @return a Val with an error.
     */
    public static Val error(SaplError error) {
        if (null == error) {
            error = SaplError.UNKNOWN_ERROR;
        }
        return new Val(null, false, null, error);
    }

    /**
     * @return a Val with an error Message.
     */
    public static Val error(String errorMessage) {
        var error = SaplError.UNKNOWN_ERROR;
        if (null != errorMessage) {
            error = SaplError.of(errorMessage);
        }
        return new Val(null, false, null, error);
    }

    /**
     * @return the error message, or NoSuchElementException if not an error.
     */
    public String getMessage() {
        if (isError()) {
            return error.message();
        }
        throw new NoSuchElementException(VALUE_NOT_AN_ERROR);
    }

    /**
     * @return true, iff the Val is an error.
     */
    public boolean isError() {
        return null != error;
    }

    /**
     * @return true, iff the Val is not an error.
     */
    public boolean noError() {
        return null == error;
    }

    /**
     * @return the JsonNode value of the Val, or a NoSuchElementException, if Val is
     * undefined or an error.
     */
    public JsonNode get() {
        if (isError()) {
            throw new NoSuchElementException(String.format(VALUE_IS_AN_ERROR_S_ERROR, getMessage()));
        }
        if (null == value) {
            throw new NoSuchElementException(VALUE_UNDEFINED_ERROR);
        }
        return value;
    }

    /**
     * @return true, iff Val not an error or undefined.
     */
    public boolean isDefined() {
        return null != value;
    }

    /**
     * @return true, iff error or undefined.
     */
    public boolean isUndefined() {
        return null == value && noError();
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
     * Returns the given field or the alternative given.
     *
     * @param fieldName the field name
     * @param errorSupplier supplier for error if field not present.
     * @return the field vale if Val is an object with the field. Else throw.
     * @throws Exception if field is not present supplied Exception is thrown.
     */
    public JsonNode fieldJsonNodeOrElseThrow(String fieldName, Supplier<? extends RuntimeException> errorSupplier) {
        final var isObjectAndFieldIsPresent = isObject() && value.has(fieldName);
        if (!isObjectAndFieldIsPresent)
            throw errorSupplier.get();

        return value.get(fieldName);
    }

    /**
     * Returns the given field or the alternative given.
     *
     * @param fieldName the field name
     * @param other alternative to return if undefined, error, nonObject, or field
     * not present
     * @return the field vale if Val is an object with the field. Else returns
     * other.
     */
    public JsonNode fieldJsonNodeOrElse(String fieldName, JsonNode other) {
        final var isObjectAndFieldIsPresent = isObject() && value.has(fieldName);
        return isObjectAndFieldIsPresent ? value.get(fieldName) : other;
    }

    /**
     * Returns the given field or the supplied alternative given.
     *
     * @param fieldName the field name
     * @param other alternative supplier to return if undefined, error, nonObject,
     * or field not present
     * @return the field vale if Val is an object with the field. Else returns
     * other.
     */
    public JsonNode fieldJsonNodeOrElse(String fieldName, Supplier<JsonNode> other) {
        final var isObjectAndFieldIsPresent = isObject() && value.has(fieldName);
        return isObjectAndFieldIsPresent ? value.get(fieldName) : other.get();
    }

    /**
     * Returns the given field or the alternative given.
     *
     * @param fieldName the field name
     * @param other alternative to return if undefined, error, nonObject, or field
     * not present
     * @return the field vale if Val is an object with the field. Else returns
     * other.
     */
    public Val fieldValOrElse(String fieldName, Val other) {
        final var isObjectAndFieldIsPresent = isObject() && value.has(fieldName);
        return isObjectAndFieldIsPresent ? Val.of(value.get(fieldName)) : other;
    }

    /**
     * Returns the given field or the supplied alternative given.
     *
     * @param fieldName the field name
     * @param other alternative supplier to return if undefined, error, nonObject,
     * or field not present
     * @return the field vale if Val is an object with the field. Else returns
     * other.
     */
    public Val fieldValOrElse(String fieldName, Supplier<Val> other) {
        final var isObjectAndFieldIsPresent = isObject() && value.has(fieldName);
        return isObjectAndFieldIsPresent ? Val.of(value.get(fieldName)) : other.get();
    }

    /**
     * Returns the supplied given other if the Val is undefined or an error.
     *
     * @param other a JSON node
     * @return the result of other if Val undefined or error.
     */
    public JsonNode orElse(Supplier<? extends JsonNode> other) {
        return isDefined() ? value : other.get();
    }

    /**
     * @param <X> error type
     * @param exceptionSupplier a supplier for a Throwable.
     * @return the value of the Val, if defined. Else the supplied Throwable is
     * thrown.
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
     * false for the value.
     */
    public Val filter(Predicate<? super JsonNode> predicate) {
        Objects.requireNonNull(predicate);
        if (isUndefined())
            return this;
        else
            return predicate.test(value) ? this : UNDEFINED;
    }

    /**
     * @param left a Val
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
     * @param left a Val
     * @param right a Val
     * @return a Boolean Val which is true, iff left and right are equal.
     */
    public static Val areEqual(Val left, Val right) {
        return Val.of(!notEqualBool(left, right));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Val other)) {
            return false;
        }
        if (isError() != other.isError()) {
            return false;
        }
        if (isError()) {
            return Objects.equals(error, other.getError());
        }
        if (isDefined() != other.isDefined()) {
            return false;
        }
        if (null == value) {
            return true;
        }
        return value.equals(NUMERIC_AWARE_COMPARATOR, other.get());
    }

    @Override
    public int hashCode() {
        if (null == value)
            return Objects.hash(error);

        return Objects.hash(hashCodeOfJsonNode(value), error);
    }

    private static int hashCodeOfJsonNode(JsonNode json) {
        if (json.isNumber())
            return json.decimalValue().stripTrailingZeros().hashCode();

        if (!json.isContainerNode())
            return json.hashCode();

        if (json.isEmpty())
            return 0;

        if (json.isArray())
            return hashCodeOfArrayNode(json);

        return hashCodeOfObjectNode((ObjectNode) json);
    }

    private static int hashCodeOfArrayNode(Iterable<JsonNode> arrayNode) {
        int hash = 1;

        for (JsonNode element : arrayNode)
            hash = 31 * hash + hashCodeOfJsonNode(element);

        return hash;
    }

    private static int hashCodeOfObjectNode(ObjectNode objectNode) {
        final var fieldIterator = objectNode.fields();
        var       hash          = 0;

        Map.Entry<String, JsonNode> entry;
        while (fieldIterator.hasNext()) {
            entry = fieldIterator.next();
            hash  = 31 * hash + (entry.getKey().hashCode() ^ hashCodeOfJsonNode(entry.getValue()));
        }

        return hash;
    }

    @Override
    public String toString() {
        if (isSecret()) {
            return "SECRET";
        }
        if (isError()) {
            return ERROR_LITERAL + '[' + error.message() + ']';
        }
        return null != value ? value.toString() : UNDEFINED_LITERAL;
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
     * @return a Flux only containing the Val with a JSON null value.
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
     * @return Flux only containing a Val with the boolean value.
     */
    public static Flux<Val> fluxOf(boolean val) {
        return Flux.just(of(val));
    }

    /**
     * @param val a Long value
     * @return Flux only containing a Val with the Long value.
     */
    public static Flux<Val> fluxOf(long val) {
        return Flux.just(of(val));
    }

    /**
     * @param val a Boolean number
     * @return a Val with the given value.
     */
    public static Val of(Boolean bool) {
        if (bool) {
            return TRUE;
        } else {
            return FALSE;
        }
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
     * Flux only containing a PolicyEvaluationException error.
     */
    public static Flux<Boolean> toBoolean(Val value) {
        if (value.isBoolean()) {
            return Flux.just(value.get().booleanValue());
        }
        return Flux.error(new PolicyEvaluationException(BOOLEAN_OPERATION_TYPE_MISMATCH_S_ERROR, typeOf(value)));
    }

    /**
     * @return if the Val is Boolean, the Boolean value is returned. Else throws a
     * PolicyEvaluationException.
     */
    public boolean getBoolean() {
        if (isBoolean()) {
            return value.booleanValue();
        }
        throw new PolicyEvaluationException(BOOLEAN_OPERATION_TYPE_MISMATCH_S_ERROR, typeOf(this));
    }

    /**
     * @return if the Val is a number, the number is returned as Long. Else throws a
     * PolicyEvaluationException.
     */
    public long getLong() {
        if (isNumber()) {
            return value.longValue();
        }
        throw new PolicyEvaluationException(NUMBER_OPERATION_TYPE_MISMATCH_S_ERROR, typeOf(this));
    }

    /**
     * @return the Val as a String. If the Val was a JSON String, the contents is
     * returned. Else the contents toString is used.
     */
    public String getText() {
        if (isUndefined()) {
            return UNDEFINED_LITERAL;
        }
        if (isTextual()) {
            return value.textValue();
        }
        return value.toString();
    }

    /**
     * @param value a Val
     * @return a Flux of the value of the Val as an JsonNode. Or a Flux with an
     * error.
     */
    public static Flux<JsonNode> toJsonNode(Val value) {
        if (value.isUndefined()) {
            return Flux.error(new PolicyEvaluationException(UNDEFINED_VALUE_ERROR));
        }
        return Flux.just(value.get());
    }

    /**
     * @return the value of the Val as a JsonNode. Throws a PolicyEvaluation
     * Exception if the value is not defined.
     */
    public JsonNode getJsonNode() {
        if (this.isDefined()) {
            return value;
        }
        if (this.isError()) {
            throw new PolicyEvaluationException(String.format(VALUE_IS_AN_ERROR_S_ERROR, getMessage()));
        }
        throw new PolicyEvaluationException(UNDEFINED_VALUE_ERROR);
    }

    /**
     * @param value a Val
     * @return a Flux of the value of the Val an ArrayNode. Or a Flux with an error.
     */
    public static Flux<ArrayNode> toArrayNode(Val value) {
        if (value.isUndefined() || !value.get().isArray()) {
            return Flux.error(new PolicyEvaluationException(ARRAY_OPERATION_TYPE_MISMATCH_S_ERROR, typeOf(value)));
        }
        return Flux.just((ArrayNode) value.get());
    }

    /**
     * @return the value of the Val as an ArrayNode. Throws a PolicyEvaluation
     * Exception if the value is not a JSON array.
     */
    public ArrayNode getArrayNode() {
        if (this.isArray()) {
            return (ArrayNode) value;
        }
        throw new PolicyEvaluationException(ARRAY_OPERATION_TYPE_MISMATCH_S_ERROR, typeOf(this));
    }

    /**
     * @return the value of the Val as an ObjectNode. Throws a PolicyEvaluation
     * Exception if the value is not a JSON object.
     */
    public ObjectNode getObjectNode() {
        if (this.isObject()) {
            return (ObjectNode) value;
        }
        throw new PolicyEvaluationException(OBJECT_OPERATION_TYPE_MISMATCH_S_ERROR, typeOf(this));
    }

    /**
     * @return the value of the Val as a BigDecimal. Throws a PolicyEvaluation
     * Exception if the value is not a number.
     */
    public BigDecimal decimalValue() {
        if (this.isNumber()) {
            return value.decimalValue();
        }
        throw new PolicyEvaluationException(ARITHMETIC_OPERATION_TYPE_MISMATCH_S_ERROR, typeOf(this));
    }

    /**
     * Converts Val to a Flux of ObjectNode.
     *
     * @param value a Val
     * @return a Flux only containing the ObjectNode value or an error if Val not
     * ObjectNode.
     */
    public static Flux<ObjectNode> toObjectNode(Val value) {
        if (value.isUndefined() || !value.get().isObject()) {
            return Flux.error(new PolicyEvaluationException(OBJECT_OPERATION_TYPE_MISMATCH_S_ERROR, typeOf(value)));
        }
        return Flux.just((ObjectNode) value.get());
    }

    /**
     * Converts Val to a Flux of String.
     *
     * @param value a Val
     * @return a Flux only containing the String value an error if Val not textual.
     */
    public static Flux<String> toText(Val value) {
        if (value.isUndefined() || !value.get().isTextual()) {
            return Flux.error(new PolicyEvaluationException(TEXT_OPERATION_TYPE_MISMATCH_S_ERROR, typeOf(value)));
        }
        return Flux.just(value.get().textValue());
    }

    /**
     * Converts Val to a Flux of BigDecimal.
     *
     * @param value a Val
     * @return a Flux only containing the number value as BigDecimal or an error if
     * Val not a number.
     */
    public static Flux<BigDecimal> toBigDecimal(Val value) {
        if (value.isUndefined() || !value.get().isNumber()) {
            return Flux.error(new PolicyEvaluationException(ARITHMETIC_OPERATION_TYPE_MISMATCH_S_ERROR, typeOf(value)));
        }
        return Flux.just(value.get().decimalValue());
    }

    /**
     * @param value a Val
     * @return a String describing the type of the Val.
     */
    public static String typeOf(Val value) {
        if (value.isError()) {
            return ERROR_LITERAL;
        }
        return value.isDefined() ? value.get().getNodeType().toString() : UNDEFINED_LITERAL;
    }

    /**
     * @return a String describing the type of the Val.
     */
    public String getValType() {
        return typeOf(this);
    }

    @Override
    public JsonNode getTrace() {
        JsonNode val;
        if (isSecret())
            val = JSON.textNode("|SECRET|");
        else if (isError()) {
            val = JSON.textNode("|ERROR| " + error.message());
        } else if (isUndefined()) {
            val = JSON.textNode("|UNDEFINED|");
        } else {
            val = value;
        }

        final var traceJson = JSON.objectNode();
        traceJson.set(Trace.VALUE, val);
        if (null != trace) {
            traceJson.set(Trace.TRACE_KEY, trace.getTrace());
        }
        return traceJson;
    }

    @Override
    public Collection<Val> getErrorsFromTrace() {
        final var errors = new ArrayList<Val>();
        collectErrors(errors);
        return errors;
    }

    void collectErrors(List<Val> errors) {
        if (isError()) {
            errors.add(this);
        }
        if (null != trace) {
            trace.collectErrors(errors);
        }
    }

    private static class NumericAwareComparator implements Comparator<JsonNode>, Serializable {

        private static final long serialVersionUID = SaplVersion.VERISION_UID;

        @Override
        public int compare(JsonNode o1, JsonNode o2) {
            if (o1.equals(o2)) {
                return 0;
            }
            if ((o1 instanceof NumericNode) && (o2 instanceof NumericNode)) {
                return o1.decimalValue().compareTo(o2.decimalValue());
            }
            return 1;
        }
    }

}
