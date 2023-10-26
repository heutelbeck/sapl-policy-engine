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
package io.sapl.grammar.ide.contentassist;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.Map;

import org.hamcrest.collection.IsMapContaining;
import org.junit.jupiter.api.Test;

class TestFunctionContextTests {

    @Test
    void isProvidedFunctionThrowsUnsupportedOperationException() {
        var context = new TestFunctionContext();
        assertThrows(UnsupportedOperationException.class, () -> context.isProvidedFunction(""));
    }

    @Test
    void providedFunctionsOfLibraryReturnsFunctionsForKnownPip() {
        var                context   = new TestFunctionContext();
        Collection<String> functions = context.providedFunctionsOfLibrary("filter");
        assertThat(functions, hasItems("blacken", "remove", "replace"));
    }

    @Test
    void providedFunctionsOfLibraryReturnsNoFunctionsForUnknownPip() {
        var      context   = new TestFunctionContext();
        Object[] functions = context.providedFunctionsOfLibrary("foo").toArray();
        assertEquals(0, functions.length);
    }

    @Test
    void getAvailableLibrariesReturnsClockPip() {
        var                context   = new TestFunctionContext();
        Collection<String> libraries = context.getAvailableLibraries();
        assertThat(libraries, hasItems("filter", "standard", "time"));
    }

    @Test
    void getDocumentationThrowsUnsupportedOperationException() {
        var context = new TestFunctionContext();
        assertThrows(UnsupportedOperationException.class, context::getDocumentation);
    }

    @Test
    void evaluateThrowsUnsupportedOperationException() {
        var context = new TestFunctionContext();
        assertThrows(UnsupportedOperationException.class, () -> context.evaluate(null, null, null, null));
    }

    @Test
    void getCodeTemplatesReturnsAllKnownFunctions() {
        var                context   = new TestFunctionContext();
        Collection<String> functions = context.getCodeTemplates();
        assertThat(functions, hasItems("filter.blacken", "filter.remove", "filter.replace", "standard.length",
                "standard.numberToString", "time.after", "time.before", "time.between"));
    }

    @Test
    void getAllFullyQualifiedFunctionsReturnsAllKnownFunctions() {
        var                context   = new TestFunctionContext();
        Collection<String> functions = context.getAllFullyQualifiedFunctions();
        assertThat(functions, hasItems("filter.blacken", "filter.remove", "filter.replace", "standard.length",
                "standard.numberToString", "time.after", "time.before", "time.between"));
    }

    @Test
    void getDocumentedCodeTemplatesReturnsAllKnownDocumentedCodeTemplates() {
        var                 context   = new TestFunctionContext();
        Map<String, String> functions = context.getDocumentedCodeTemplates();
        assertThat(functions, IsMapContaining.hasEntry("filter.blacken", "documentation"));
    }

}
