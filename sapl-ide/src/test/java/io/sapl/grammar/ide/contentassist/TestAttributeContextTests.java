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

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import org.junit.jupiter.api.Test;

public class TestAttributeContextTests {

	@Test
	public void isProvidedFunctionThrowsUnsupportedOperationException() {
		var context = new TestAttributeContext();
		assertThrows(UnsupportedOperationException.class, () -> {
			context.isProvidedFunction("");
		});
	}

	@Test
	public void providedFunctionsOfLibraryReturnsFunctionsForKnownPip() {
		var context = new TestAttributeContext();
		Collection<String> functions = context.providedFunctionsOfLibrary("clock");
		assertThat(functions, hasItems("now", "millis", "ticker"));
	}

	@Test
	public void providedFunctionsOfLibraryReturnsNoFunctionsForUnknownPip() {
		var context = new TestAttributeContext();
		Object[] functions = context.providedFunctionsOfLibrary("foo").toArray();
		assertEquals(0, functions.length);
	}

	@Test
	public void getAvailableLibrariesReturnsClockPip() {
		var context = new TestAttributeContext();
		Collection<String> libraries = context.getAvailableLibraries();
		assertThat(libraries, hasItem("clock"));
	}

	@Test
	public void getDocumentationThrowsUnsupportedOperationException() {
		var context = new TestAttributeContext();
		assertThrows(UnsupportedOperationException.class, () -> {
			context.getDocumentation();
		});
	}

	@Test
	public void evaluateAttributeThrowsUnsupportedOperationException() {
		var context = new TestAttributeContext();
		assertThrows(UnsupportedOperationException.class, () -> {
			context.evaluateAttribute(null, null, null, null);
		});
	}

	@Test
	public void evaluateEnvironmentAttributeThrowsUnsupportedOperationException() {
		var context = new TestAttributeContext();
		assertThrows(UnsupportedOperationException.class, () -> {
			context.evaluateEnvironmentAttribute(null, null, null);
		});
	}

	@Test
	public void getCodeTemplatesWithPrefixReturnsAllKnownFunctions() {
		var context = new TestAttributeContext();
		Collection<String> functions = context.getCodeTemplatesWithPrefix("", false);
		assertThat(functions, hasItems("clock.now", "clock.millis", "clock.ticker"));
	}

	@Test
	public void getAllFullyQualifiedFunctionsReturnsAllKnownFunctions() {
		var context = new TestAttributeContext();
		Collection<String> functions = context.getAllFullyQualifiedFunctions();
		assertThat(functions, hasItems("clock.now", "clock.millis", "clock.ticker"));
	}

}
