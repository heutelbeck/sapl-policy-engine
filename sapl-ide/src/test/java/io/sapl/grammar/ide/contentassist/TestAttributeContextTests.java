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

import static org.junit.Assert.assertTrue;
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
		assertTrue(functions.contains("now"));
		assertTrue(functions.contains("millis"));
		assertTrue(functions.contains("ticker"));
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
		assertTrue(libraries.contains("clock"));
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
		assertTrue(functions.contains("clock.now"));
		assertTrue(functions.contains("clock.millis"));
		assertTrue(functions.contains("clock.ticker"));
	}

	@Test
	public void getAllFullyQualifiedFunctionsReturnsAllKnownFunctions() {
		var context = new TestAttributeContext();
		Collection<String> functions = context.getAllFullyQualifiedFunctions();
		assertTrue(functions.contains("clock.now"));
		assertTrue(functions.contains("clock.millis"));
		assertTrue(functions.contains("clock.ticker"));
	}

}
