package io.sapl.spring.marshall.environment;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.spring.marshall.Environment;
import lombok.Value;

@Value
public class SystemEnvironment implements Environment {

	Map<String, String> envProps;

	public SystemEnvironment() {
		envProps = System.getenv();
	}

	@Override
	public JsonNode getAsJson() {
		return new ObjectMapper().convertValue(envProps, JsonNode.class);
	}

}
