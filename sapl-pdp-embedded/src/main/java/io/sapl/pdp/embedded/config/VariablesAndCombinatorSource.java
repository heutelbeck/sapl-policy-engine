package io.sapl.pdp.embedded.config;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.interpreter.combinators.DocumentsCombinator;
import reactor.core.publisher.Flux;

public interface VariablesAndCombinatorSource {

	Flux<DocumentsCombinator> getDocumentsCombinator();

	Flux<Map<String, JsonNode>> getVariables();

	void dispose();

}