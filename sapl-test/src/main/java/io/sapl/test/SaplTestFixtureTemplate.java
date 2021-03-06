package io.sapl.test;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;

public abstract class SaplTestFixtureTemplate implements SaplTestFixture {

	protected AnnotationAttributeContext attributeCtx = new AnnotationAttributeContext();
	protected AnnotationFunctionContext functionCtx = new AnnotationFunctionContext();
	protected Map<String, JsonNode> variables = new HashMap<String, JsonNode>(1);
	
	@Override
	public SaplTestFixture registerPIP(Object pip) throws InitializationException {
		this.attributeCtx.loadPolicyInformationPoint(pip);
		return this;
	}

	@Override
	public SaplTestFixture registerFunctionLibrary(Object library) throws InitializationException {
		this.functionCtx.loadLibrary(library);
		return this;
	}

	@Override
	public SaplTestFixture registerVariable(String key, JsonNode value) {
		if (this.variables.containsKey(key)) {
			throw new SaplTestException("The VariableContext already contains a key \"" + key + "\"");
		}
		this.variables.put(key, value);
		return this;
	}

}
