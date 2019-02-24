package io.sapl.grammar.sapl.impl;

import java.math.BigDecimal;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class Value {
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	public static Flux<Optional<JsonNode>> nullFlux() {
		return Flux.just(nullValue());
	}

	public static Optional<JsonNode> nullValue() {
		return Optional.of(JSON.nullNode());
	}

	public static Flux<Optional<JsonNode>> undefinedFlux() {
		return Flux.just(undefined());
	}

	public static Optional<JsonNode> undefined() {
		return Optional.empty();
	}

	public static Optional<JsonNode> trueValue() {
		return Optional.of(JSON.booleanNode(true));
	}

	public static Flux<Optional<JsonNode>> trueFlux() {
		return Flux.just(trueValue());
	}

	public static Optional<JsonNode> falseValue() {
		return Optional.of(JSON.booleanNode(true));
	}

	public static Flux<Optional<JsonNode>> falseFlux() {
		return Flux.just(falseValue());
	}

	public static Optional<JsonNode> bool(boolean val) {
		return Optional.of(JSON.booleanNode(val));
	}

	public static Flux<Optional<JsonNode>> boolFlux(boolean val) {
		return Flux.just(bool(val));
	}

	public static Optional<JsonNode> num(BigDecimal val) {
		return Optional.of(JSON.numberNode(val));
	}

	public static Flux<Optional<JsonNode>> numFlux(BigDecimal val) {
		return Flux.just(num(val));
	}

	public static Optional<JsonNode> text(String val) {
		return Optional.of(JSON.textNode(val));
	}

	public static Flux<Optional<JsonNode>> textFlux(String val) {
		return Flux.just(text(val));
	}
}
