package io.sapl.pip.http;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;

@Data
public class RequestSpecification {

	public static final String HTTP_GET = "get";
	public static final String HTTP_POST = "post";
	public static final String HTTP_PUT = "put";
	public static final String HTTP_PATCH = "patch";
	public static final String HTTP_DELETE = "delete";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private JsonNode url;
	private Map<String, String> headers;
	private String rawBody;
	private JsonNode body;

	public static RequestSpecification from(JsonNode value) throws JsonProcessingException {
		return MAPPER.treeToValue(value, RequestSpecification.class);
	}

}
