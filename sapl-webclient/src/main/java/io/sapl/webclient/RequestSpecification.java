package io.sapl.webclient;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;

@Data
public class RequestSpecification {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private JsonNode url;
	private Map<String, String> headers;
	private String rawBody;
	private JsonNode body;

	public static RequestSpecification from(JsonNode value) throws JsonProcessingException {
		return MAPPER.treeToValue(value, RequestSpecification.class);
	}

}
