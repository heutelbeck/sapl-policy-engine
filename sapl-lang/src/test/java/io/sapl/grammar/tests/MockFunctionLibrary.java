/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.tests;

import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionException;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;

@FunctionLibrary(name = MockFunctionLibrary.NAME)
public class MockFunctionLibrary {

	public static final String NAME = "mock";

	@Function
	public static Val emptyString(Val... parameters) {
		return Val.of("");
	}
	@Function
	public static Val nil(Val... parameters) {
		return Val.NULL;
	}
	@Function
	public static Val fail(Val... parameters) {
		throw new FunctionException();
	}

	@Function
	public static Val parameters(Val... parameters) {
		ArrayNode result = Val.JSON.arrayNode();
		for (Val parameter : parameters) {
			if (parameter.isDefined()) {
				result.add(parameter.get());
			}
		}
		return Val.of(result);
	}
}
