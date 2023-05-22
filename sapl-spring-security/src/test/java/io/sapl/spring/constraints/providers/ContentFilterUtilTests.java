package io.sapl.spring.constraints.providers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

class ContentFilterUtilTests {
	private final static ObjectMapper MAPPER = new ObjectMapper();

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DataPoint {
		String  a = "";
		Integer b = 0;
	}

	@Test
	void test() throws JsonProcessingException {
		var constraint = MAPPER.readTree("""
				{
					"conditions" : [
						{
							"path" : "$.a",
							"type" : "=~", 
							"value" : "^.BC$"
						}
					] 
				}
				""");
		var condition  = ContentFilterUtil.predicateFromConditions(constraint, MAPPER);
		var data       = new DataPoint("ABC", 100);
		assertTrue(condition.test(data));
	}
}
