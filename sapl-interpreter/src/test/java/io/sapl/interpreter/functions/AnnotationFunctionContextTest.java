package io.sapl.interpreter.functions;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.functions.FunctionException;

public class AnnotationFunctionContextTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	public void testAutoconfigure() throws FunctionException {
		AnnotationFunctionContext context = new AnnotationFunctionContext(
				new MockLibrary());
		context.evaluate(MockLibrary.NAME + ".helloTest", JSON.arrayNode());
	}

}
