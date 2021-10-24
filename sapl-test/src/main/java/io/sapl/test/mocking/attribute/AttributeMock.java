package io.sapl.test.mocking.attribute;

import java.util.List;
import java.util.Map;

import io.sapl.api.interpreter.Val;

import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Flux;

public interface AttributeMock {

	Flux<Val> evaluate(Val parentValue, Map<String, JsonNode> variables, List<Flux<Val>> args);
	
	void assertVerifications();
	
	String getErrorMessageForCurrentMode();
}
