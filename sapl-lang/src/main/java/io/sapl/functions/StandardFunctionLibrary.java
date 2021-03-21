/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.Bool;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;

@FunctionLibrary(name = StandardFunctionLibrary.NAME, description = StandardFunctionLibrary.DESCRIPTION)
public class StandardFunctionLibrary {

	public static final String NAME = "standard";

	public static final String DESCRIPTION = "This library contains the mandatory functions for the SAPL implementation.";

	private static final String LENGTH_DOC = "length(JSON_VALUE): For STRING it returns the length of the STRING. "
			+ "For ARRAY, it returns the number of elements in the array. "
			+ "For OBJECT, it returns the number of keys in the OBJECT. "
			+ "For NUMBER, BOOLEAN, or NULL, the function will return an error.";

	private static final String NUMBER_TO_STRING_DOC = "numberToString(JSON_VALUE): For STRING it returns the input. "
			+ "For NUMBER or BOOLEAN it returns a JSON node representing the value converted to a string. "
			+ "For NULL it returns a JSON node representing the empty string. "
			+ "For ARRAY or OBJECT the function will return an error.";

	@Function(docs = LENGTH_DOC)
	public static Val length(@Array @Text @JsonObject Val parameter) {
		if (parameter.isTextual())
			return Val.of(parameter.getText().length());

		return Val.of(parameter.get().size());
	}

	@Function(docs = NUMBER_TO_STRING_DOC)
	public static Val numberToString(@Text @Number @Bool Val parameter) {
		JsonNode param = parameter.get();
		if (param.isNumber())
			return Val.of(param.numberValue().toString());

		if (param.isBoolean())
			return Val.of(String.valueOf(param.booleanValue()));

		if (param.isNull())
			return Val.of("");

		return parameter;
	}

}
