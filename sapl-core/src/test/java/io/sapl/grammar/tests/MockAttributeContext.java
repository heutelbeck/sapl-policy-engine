package io.sapl.grammar.tests;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pip.AttributeException;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;

public class MockAttributeContext implements AttributeContext {

	@Override
	public JsonNode evaluate(String attribute, JsonNode value, Map<String, JsonNode> variables)
			throws AttributeException {
		if ("ATTRIBUTE".equals(attribute)) {
			return JsonNodeFactory.instance.textNode(attribute);
		} else if ("EXCEPTION".equals(attribute)) {
			throw new AttributeException();
		} else {
			return value;
		}
	}

	@Override
	public Boolean provides(String function) {
		return true;
	}

	@Override
	public Collection<String> findersInLibrary(String libraryName) {
		return new ArrayList<>();
	}

	@Override
	public void loadPolicyInformationPoint(Object library) throws AttributeException {

	}

	@Override
	public Collection<PolicyInformationPointDocumentation> getDocumentation() {
		return new ArrayList<>();
	}

}
