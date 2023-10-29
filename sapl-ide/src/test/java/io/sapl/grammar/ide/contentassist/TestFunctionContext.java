/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.functions.LibraryDocumentation;

class TestFunctionContext implements FunctionContext {

	private final Map<String, Set<String>> availableLibraries;

	public TestFunctionContext() {
		availableLibraries = new HashMap<>();
		availableLibraries.put("filter", Set.of("blacken", "remove", "replace"));
		availableLibraries.put("standard", Set.of("length", "numberToString"));
		availableLibraries.put("time", Set.of("after", "before", "between"));
		availableLibraries.put("schemaTest", Set.of("person"));
	}

	@Override
	public Boolean isProvidedFunction(String function){
		List<String> availableFunctions = new ArrayList<>();
		for (var lib : availableLibraries.entrySet()) {
			var key = lib.getKey();
			for (var value : lib.getValue()){
				availableFunctions.add(key.concat(".").concat(value));
			}
		}
		return availableFunctions.contains(function);

    @Override
    public Collection<String> providedFunctionsOfLibrary(String pipName) {
        return availableLibraries.getOrDefault(pipName, Set.of());
    }

    @Override
    public Collection<String> getAvailableLibraries() {
        return availableLibraries.keySet();
    }

    @Override
    public Val evaluate(String function, Val... parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<LibraryDocumentation> getDocumentation() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getCodeTemplates() {
        return List.of("filter.blacken", "filter.remove", "filter.replace", "standard.length",
                "standard.numberToString", "time.after", "time.before", "time.between");
    }

	}

	@Override
	public List<String> getCodeTemplates() {
		return List.of("filter.blacken", "filter.remove", "filter.replace", "standard.length",
				"standard.numberToString", "time.after", "time.before", "time.between");
	}

	@Override
	public Collection<String> getAllFullyQualifiedFunctions() {
		return List.of("filter.blacken", "filter.remove", "filter.replace", "standard.length",
				"standard.numberToString", "time.after", "time.before", "time.between", "schemaTest.person.name");
	}

    @Override
    public Map<String, String> getDocumentedCodeTemplates() {
        return Map.of("filter.blacken", "documentation");
    }

	@Override
	public Map<String, String> getFunctionSchemas() {
		final String PERSON_SCHEMA = """
				{
				  "type": "object",
				  "properties": {
				  "name": { "type": "string" }
				  }
				}""";
		var schemas = new HashMap<String, String>();
		schemas.put("schemaTest.person", PERSON_SCHEMA);
		return schemas;
	}

}
