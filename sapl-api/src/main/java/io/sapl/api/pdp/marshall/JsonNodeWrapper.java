package io.sapl.api.pdp.marshall;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface JsonNodeWrapper {

	@JsonIgnore
	default JsonNode getAsJson() {
		ObjectMapper om = new ObjectMapper();
		return om.convertValue(this, JsonNode.class);
	}
}
