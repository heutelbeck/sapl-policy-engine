package io.sapl.pdp.embedded;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import reactor.core.publisher.Flux;

@PolicyInformationPoint(name = TestPIP.NAME, description = TestPIP.DESCRIPTION)
public class TestPIP {

	public static final String NAME = "test";
	public static final String DESCRIPTION = "Policy information Point for testing";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Attribute
	public Flux<JsonNode> upper(JsonNode value, Map<String, JsonNode> variables) {
		return Flux.just(JSON.textNode(value.asText().toUpperCase()));
	}
}
