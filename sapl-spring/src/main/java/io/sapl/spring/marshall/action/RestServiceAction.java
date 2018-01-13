package io.sapl.spring.marshall.action;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.spring.marshall.Action;
import lombok.Value;

@Value
public class RestServiceAction implements Action {

	HttpMethod httpMethod;
	Map<String, List<String>> parameters;
	String uri;
	
	public RestServiceAction(HttpServletRequest request){
		httpMethod = HttpMethod.resolve(request.getMethod());
		Map<String, List<String>> paramMap = new HashMap<>();
				request.getParameterMap()
				.entrySet()
				.stream()
				.forEach(entry -> paramMap.put(entry.getKey(), Arrays.asList(entry.getValue())));
		this.parameters = Collections.unmodifiableMap(paramMap);
		uri = request.getRequestURI();
	}
	
	@Override
	public JsonNode getAsJson() {
		ObjectMapper om = new ObjectMapper();
		return om.convertValue(this, JsonNode.class);
	}

}
