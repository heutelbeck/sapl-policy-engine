/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
