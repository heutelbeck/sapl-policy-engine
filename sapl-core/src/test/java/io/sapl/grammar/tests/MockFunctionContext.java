package io.sapl.grammar.tests;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.functions.FunctionException;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.functions.LibraryDocumentation;

public class MockFunctionContext implements FunctionContext {

	@Override
	public Optional<JsonNode> evaluate(String function, ArrayNode parameters) throws FunctionException {
		if ("EXCEPTION".equals(function)) {
			throw new FunctionException();
		} else if ("PARAMETERS".equals(function)) {
			return Optional.of(parameters);
		} else {
			return Optional.of(JsonNodeFactory.instance.textNode(function));
		}
	}

	@Override
	public Boolean provides(String function) {
		return true;
	}

	@Override
	public Collection<String> functionsInLibrary(String libraryName) {
		return new ArrayList<>();
	}

	@Override
	public void loadLibrary(Object library) throws FunctionException {
	}

	@Override
	public Collection<LibraryDocumentation> getDocumentation() {
		return new ArrayList<>();
	}

}
