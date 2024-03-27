/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.springdatacommon.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.SneakyThrows;

@Getter
public enum EnumConstraintHandlerType {

	R2DBC_QUERY_MANIPULATION("r2dbcQueryManipulation", getTemplateQueryManipulation()),
	MONGO_QUERY_MANIPULATION("mongoQueryManipulation", getTemplateQueryManipulation());
	
	private final String type;
	private final JsonNode template;

	EnumConstraintHandlerType(String type, JsonNode template) {
		this.type = type;
		this.template = template;
	}

	@SneakyThrows
	private static JsonNode getTemplateQueryManipulation() {
		var mapper = new ObjectMapper();
		return mapper.readTree("""
					{
					  "type": "",
					  "conditions": [
					    ""
					  ]
					}
				""");
	}

	@SneakyThrows
	public static JsonNode getQueryManipulationSelectionStructure() {
		var mapper = new ObjectMapper();
		return mapper.readTree("""
					{
					  "selection": {
					    "type": "",
					    "columns": [
					      ""
					    ]
					  }
					}
				""");
	}

}
