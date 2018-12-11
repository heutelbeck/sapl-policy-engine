package io.sapl.grammar.tests;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.pip.AttributeException;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;
import reactor.core.publisher.Flux;

public class MockAttributeContext implements AttributeContext {

	@Override
	public JsonNode evaluate(String attribute, JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
		if ("ATTRIBUTE".equals(attribute)) {
			return JsonNodeFactory.instance.textNode(attribute);
		} else if ("EXCEPTION".equals(attribute)) {
			throw new AttributeException();
		} else {
			return value;
		}
	}

	@Override
	public Flux<JsonNode> reactiveEvaluate(String attribute, JsonNode value, Map<String, JsonNode> variables) {
		if ("ATTRIBUTE".equals(attribute)) {
			return Flux.just(JsonNodeFactory.instance.textNode(attribute));
		} else if ("EXCEPTION".equals(attribute)) {
			return Flux.error(new AttributeException());
		} else {
			return Flux.just(value);
		}
	}

	@Override
	public Boolean provides(String function) {
		return true;
	}

	@Override
	public Collection<String> findersInLibrary(String pipName) {
		return Collections.emptyList();
	}

	@Override
	public void loadPolicyInformationPoint(Object pip) throws AttributeException {
		// NOP
	}

	@Override
	public Collection<PolicyInformationPointDocumentation> getDocumentation() {
		return Collections.emptyList();
	}

}
