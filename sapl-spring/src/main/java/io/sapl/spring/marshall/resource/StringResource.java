package io.sapl.spring.marshall.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.spring.marshall.Resource;
import lombok.Value;

@Value
@Deprecated
/**
 * @deprecated
 */
public class StringResource implements Resource {

	String string;

	@Override
	public JsonNode getAsJson() {
		return new ObjectMapper().convertValue(string, JsonNode.class);
	}
}
