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
package io.sapl.grammar.ide.contentassist;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.functions.LibraryDocumentation;
import lombok.SneakyThrows;

class TestFunctionContext implements FunctionContext {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Set<String>> availableLibraries;

    static final String PERSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
            	"name": { "type": "string" }
              }
            }
            """;

    static final String DOG_SCHEMA = """
            {
              "type": "object",
              "properties": {
            	"race": { "type": "string" }
              }
            }
            """;

    public TestFunctionContext() {
        availableLibraries = new HashMap<>();
        availableLibraries.put("filter", Set.of("blacken", "remove", "replace"));
        availableLibraries.put("standard", Set.of("length", "numberToString"));
        availableLibraries.put("time", Set.of("after", "before", "between"));
        availableLibraries.put("schemaTest", Set.of("person", "dog", "food", "location"));
    }

    @Override
    public Boolean isProvidedFunction(String function) {
        List<String> availableFunctions = new ArrayList<>();
        for (var lib : availableLibraries.entrySet()) {
            var key = lib.getKey();
            for (var value : lib.getValue()) {
                availableFunctions.add(key.concat(".").concat(value));
            }
        }
        return availableFunctions.contains(function);
    }

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
        // @formatter:off
        return List.of(
                "filter.blacken",    "filter.blacken(a,b,c)",
                "schemaTest.person", "schemaTest.person()",
                "schemaTest.dog",    "schemaTest.dog()",
                "schemaTest.food",   "schemaTest.food(String species)",
                "schemaTest.food",   "schemaTest.food()");
        // @formatter:on
    }

    @Override
    public Collection<String> getAllFullyQualifiedFunctions() {
        return List.of("filter.blacken", "filter.remove", "filter.replace", "standard.length",
                "standard.numberToString", "time.after", "time.before", "time.between", "schemaTest.person",
                "schemaTest.dog", "schemaTest.food", "schemaTest.location");
    }

    @Override
    public Map<String, String> getDocumentedCodeTemplates() {
        // @formatter:off
        return Map.of(
                "filter.blacken(a,b,c)",           "blacken documentation",
                "schemaTest.person()",             "person documentation",
                "schemaTest.location()",           "location documentation",
                "schemaTest.dog()",                "dog documentation",
                "schemaTest.food(String species)", "food with species parameter documentation",
                "schemaTest.food()",               "food documentation");
        // @formatter:on
    }

    @Override
    @SneakyThrows
    public Map<String, JsonNode> getFunctionSchemas() {
        var schemas = new HashMap<String, JsonNode>();
        schemas.put("schemaTest.person", MAPPER.readValue(PERSON_SCHEMA, JsonNode.class));
        schemas.put("schemaTest.dog", MAPPER.readValue(DOG_SCHEMA, JsonNode.class));
        schemas.put("schemaTest.location", schemaFromResource("geographical_location_schema.json"));
        return schemas;
    }

    private JsonNode schemaFromResource(String resourcePath) throws IOException {
        try (var is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            return MAPPER.readValue(is, JsonNode.class);
        }
    }

}
