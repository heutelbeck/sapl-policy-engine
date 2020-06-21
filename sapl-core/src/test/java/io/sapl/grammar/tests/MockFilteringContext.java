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

import java.util.ArrayList;
import java.util.Collection;

import io.sapl.api.functions.FunctionException;
import io.sapl.grammar.sapl.impl.Val;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.functions.LibraryDocumentation;

public class MockFilteringContext implements FunctionContext {

	@Override
	public Val evaluate(String function, Val... parameters) throws FunctionException {
		if ("EXCEPTION".equals(function)) {
			throw new FunctionException();
		} else if ("EMPTY_STRING".equals(function)) {
			return Val.of("");
		} else {
			return Val.of(function);
		}
	}

	@Override
	public Boolean provides(String function) {
		return true;
	}

	@Override
	public Collection<String> functionsInLibrary(String libraryName) {
		return new ArrayList<>();
	}

	@Override
	public void loadLibrary(Object library) throws FunctionException {
	}

	@Override
	public Collection<LibraryDocumentation> getDocumentation() {
		return new ArrayList<>();
	}

}
