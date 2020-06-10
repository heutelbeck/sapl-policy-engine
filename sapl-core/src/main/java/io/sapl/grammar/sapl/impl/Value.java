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
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class Value {

	private static final String UNDEFINED_VALUE_ERROR = "Undefined value error.";

	private static final String OBJECT_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Expected an object, but got %s.";

	private static final String ARRAY_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Expected an array, but got %s.";

	protected static final String BOOLEAN_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Boolean operation expects boolean values, but got: '%s'.";

	protected static final String TEXT_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Text operation expects text values, but got: '%s'.";

	protected static final String ARITHMETIC_OPERATION_TYPE_MISMATCH_S = "Type mismatch. Number operation expects number values, but got: '%s'.";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	public static Flux<Optional<JsonNode>> nullFlux() {
		return Flux.just(ofNull());
	}

	public static Optional<JsonNode> ofNull() {
		return Optional.of(JSON.nullNode());
	}

	public static Flux<Optional<JsonNode>> undefinedFlux() {
		return Flux.just(undefined());
	}

	public static Optional<JsonNode> undefined() {
		return Optional.empty();
	}

	public static Optional<JsonNode> ofTrue() {
		return Optional.of(JSON.booleanNode(true));
	}

	public static Flux<Optional<JsonNode>> fluxOfTrue() {
		return Flux.just(ofTrue());
	}

	public static Optional<JsonNode> ofFalse() {
		return Optional.of(JSON.booleanNode(false));
	}

	public static Flux<Optional<JsonNode>> fluxOfFalse() {
		return Flux.just(ofFalse());
	}

	public static Optional<JsonNode> of(boolean val) {
		return Optional.of(JSON.booleanNode(val));
	}

	public static Flux<Optional<JsonNode>> fluxOf(boolean val) {
		return Flux.just(of(val));
	}

	public static Optional<JsonNode> of(BigDecimal val) {
		return Optional.of(JSON.numberNode(val));
	}

	public static Flux<Optional<JsonNode>> fluxOf(BigDecimal val) {
		return Flux.just(of(val));
	}

	public static Optional<JsonNode> of(String val) {
		return Optional.of(JSON.textNode(val));
	}

	public static Flux<Optional<JsonNode>> fluxOf(String val) {
		return Flux.just(of(val));
	}

	public static Flux<Boolean> toBoolean(Optional<JsonNode> node) {
		if (!node.isPresent() || !node.get().isBoolean()) {
			return Flux.error(new PolicyEvaluationException(BOOLEAN_OPERATION_TYPE_MISMATCH_S, typeOf(node)));
		}
		return Flux.just(node.get().booleanValue());
	}

	public static Flux<JsonNode> toJsonNode(Optional<JsonNode> node) {
		if (!node.isPresent()) {
			return Flux.error(new PolicyEvaluationException(UNDEFINED_VALUE_ERROR));
		}
		return Flux.just(node.get());
	}

	public static Flux<ArrayNode> toArrayNode(Optional<JsonNode> node) {
		if (!node.isPresent() || !node.get().isArray()) {
			return Flux.error(new PolicyEvaluationException(ARRAY_OPERATION_TYPE_MISMATCH_S, typeOf(node)));
		}
		return Flux.just((ArrayNode) node.get());
	}

	public static Flux<ObjectNode> toObjectNode(Optional<JsonNode> node) {
		if (!node.isPresent() || !node.get().isObject()) {
			return Flux.error(new PolicyEvaluationException(OBJECT_OPERATION_TYPE_MISMATCH_S, typeOf(node)));
		}
		return Flux.just((ObjectNode) node.get());
	}

	public static Flux<String> toString(Optional<JsonNode> node) {
		if (!node.isPresent() || !node.get().isTextual()) {
			return Flux.error(new PolicyEvaluationException(TEXT_OPERATION_TYPE_MISMATCH_S, typeOf(node)));
		}
		return Flux.just(node.get().textValue());
	}

	public static Flux<BigDecimal> toBigDecimal(Optional<JsonNode> node) {
		if (!node.isPresent() || !node.get().isNumber()) {
			return Flux.error(new PolicyEvaluationException(ARITHMETIC_OPERATION_TYPE_MISMATCH_S, typeOf(node)));
		}
		return Flux.just(node.get().decimalValue());
	}

	public static String typeOf(Optional<JsonNode> node) {
		return node.isPresent() ? node.get().getNodeType().toString() : "undefined";
	}

}
