/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.conformance;

import io.sapl.api.documentation.EntryDocumentation;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.documentation.ParameterDocumentation;
import io.sapl.documentation.LibraryDocumentationExtractor;
import lombok.experimental.UtilityClass;
import lombok.val;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * The catalogue of the function libraries that the function corpus covers:
 * the contract of each library as JSON, with its name and functions, and of
 * each function its name, parameters and the schema of what it returns. Other
 * implementations of SAPL compare their libraries with it, so that the names
 * and signatures a policy relies on do not drift apart. The documentation is
 * each implementation's own and not part of the catalogue.
 * <p>
 * The corpus covers a library if the directory "functions" holds a case file
 * named after it. Libraries are sorted by name, functions by name and number
 * of parameters.
 */
@UtilityClass
public class FunctionCatalogue {

    /**
     * The name of the file of the catalogue in the conformance corpus.
     */
    public static final String FILE_NAME = "function-catalogue.json";

    private static final String CASE_FILE_EXTENSION = ".cases";
    private static final String FUNCTIONS           = "functions";

    private static final String ERROR_UNREADABLE = "The function catalogue at '%s' cannot be read.";
    private static final String ERROR_UNWRITABLE = "The function catalogue at '%s' cannot be written.";

    private static final JsonMapper      MAPPER = JsonMapper.builder().build();
    private static final JsonNodeFactory NODES  = JsonNodeFactory.instance;

    /**
     * @return the catalogue of the libraries of this engine that the function
     * corpus covers
     */
    public static JsonNode catalogue() {
        val covered   = coveredLibraries();
        val libraries = ConformanceLibraries.libraries().stream()
                .map(library -> LibraryDocumentationExtractor.extractFunctionLibrary(library.getClass()))
                .filter(library -> covered.contains(library.name()))
                .sorted(Comparator.comparing(LibraryDocumentation::name)).toList();
        val catalogue = NODES.arrayNode();
        libraries.forEach(library -> catalogue.add(libraryEntry(library)));
        return catalogue;
    }

    /**
     * @return the catalogue as the conformance corpus holds it
     * @throws UncheckedIOException if the file cannot be read
     */
    public static JsonNode stated() {
        try {
            return MAPPER.readTree(Files.readString(path()));
        } catch (IOException e) {
            throw new UncheckedIOException(ERROR_UNREADABLE.formatted(path()), e);
        }
    }

    /**
     * Writes the catalogue of this engine into the conformance corpus.
     *
     * @throws UncheckedIOException if the file cannot be written
     */
    public static void write() {
        try {
            Files.writeString(path(), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(catalogue()) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(ERROR_UNWRITABLE.formatted(path()), e);
        }
    }

    private static Path path() {
        return ConformanceCorpus.pathOf(FILE_NAME);
    }

    private static List<String> coveredLibraries() {
        return ConformanceCorpus.caseFilesOf(FUNCTIONS).stream().map(file -> file.path().toFile().getName())
                .map(name -> name.substring(0, name.length() - CASE_FILE_EXTENSION.length())).toList();
    }

    private static ObjectNode libraryEntry(LibraryDocumentation library) {
        val functions = NODES.arrayNode();
        library.entries().stream()
                .sorted(Comparator.comparing(EntryDocumentation::name)
                        .thenComparing(entry -> entry.parameters().size()))
                .forEach(entry -> functions.add(functionEntry(entry)));
        val entry = NODES.objectNode();
        entry.put("name", library.name());
        entry.set(FUNCTIONS, functions);
        return entry;
    }

    private static ObjectNode functionEntry(EntryDocumentation function) {
        val parameters = NODES.arrayNode();
        function.parameters().forEach(parameter -> parameters.add(parameterEntry(parameter)));
        val entry = NODES.objectNode();
        entry.put("name", function.name());
        entry.set("parameters", parameters);
        entry.set("schema", function.schema() == null ? NODES.nullNode() : MAPPER.readTree(function.schema()));
        return entry;
    }

    private static ObjectNode parameterEntry(ParameterDocumentation parameter) {
        val types = NODES.arrayNode();
        parameter.allowedTypes().forEach(types::add);
        val entry = NODES.objectNode();
        entry.put("name", parameter.name());
        entry.set("types", types);
        entry.put("varArgs", parameter.varArgs());
        return entry;
    }
}
