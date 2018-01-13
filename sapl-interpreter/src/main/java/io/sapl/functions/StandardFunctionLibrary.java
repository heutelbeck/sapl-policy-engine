/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;

@FunctionLibrary(name = StandardFunctionLibrary.NAME, description = StandardFunctionLibrary.DESCRIPTION)
public class StandardFunctionLibrary {

	public static final String NAME = "standard";
	public static final String DESCRIPTION = "This library contains the mandantory functions for the SAPL implementation.";

	private static final String LENGTH_DOC = "length(JSON_VALUE): For STRING it retuns the length of the STRING. "
			+ "For ARRAY, it returns the number of elements in the array. "
			+ "For OBJECT, it returns the number of keys in the OBJECT. "
			+ "For NUMBER, BOOLEAN, or NULL, the function will return an error.";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Function(docs = LENGTH_DOC)
	public static JsonNode length(@Array @Text @JsonObject JsonNode parameter) {
		if (parameter.isTextual()) {
			return JSON.numberNode(parameter.textValue().length());
		} else {
			return JSON.numberNode(parameter.size());
		}
	}

}
