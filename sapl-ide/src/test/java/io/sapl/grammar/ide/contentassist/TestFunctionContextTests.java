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
package io.sapl.grammar.ide.contentassist;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import org.junit.jupiter.api.Test;

public class TestFunctionContextTests {

	@Test
	public void isProvidedFunctionThrowsUnsupportedOperationException() {
		var context = new TestFunctionContext();
		assertThrows(UnsupportedOperationException.class, () -> {
			context.isProvidedFunction("");
		});
	}

	@Test
	public void providedFunctionsOfLibraryReturnsFunctionsForKnownPip() {
		var context = new TestFunctionContext();
		Collection<String> functions = context.providedFunctionsOfLibrary("filter");
		assertThat(functions, hasItems("blacken", "remove", "replace"));
	}

	@Test
	public void providedFunctionsOfLibraryReturnsNoFunctionsForUnknownPip() {
		var context = new TestFunctionContext();
		Object[] functions = context.providedFunctionsOfLibrary("foo").toArray();
		assertEquals(0, functions.length);
	}

	@Test
	public void getAvailableLibrariesReturnsClockPip() {
		var context = new TestFunctionContext();
		Collection<String> libraries = context.getAvailableLibraries();
		assertThat(libraries, hasItems("filter", "standard", "time"));
	}

	@Test
	public void getDocumentationThrowsUnsupportedOperationException() {
		var context = new TestFunctionContext();
		assertThrows(UnsupportedOperationException.class, () -> {
			context.getDocumentation();
		});
	}

	@Test
	public void evaluateThrowsUnsupportedOperationException() {
		var context = new TestFunctionContext();
		assertThrows(UnsupportedOperationException.class, () -> {
			context.evaluate(null, null, null, null);
		});
	}

	@Test
	public void getCodeTemplatesReturnsAllKnownFunctions() {
		var context = new TestFunctionContext();
		Collection<String> functions = context.getCodeTemplates();
		assertThat(functions, hasItems("filter.blacken", "filter.remove", "filter.replace", "standard.length",
				"standard.numberToString", "time.after", "time.before", "time.between"));
	}

	@Test
	public void getAllFullyQualifiedFunctionsReturnsAllKnownFunctions() {
		var context = new TestFunctionContext();
		Collection<String> functions = context.getAllFullyQualifiedFunctions();
		assertThat(functions, hasItems("filter.blacken", "filter.remove", "filter.replace", "standard.length",
				"standard.numberToString", "time.after", "time.before", "time.between"));
	}

}
