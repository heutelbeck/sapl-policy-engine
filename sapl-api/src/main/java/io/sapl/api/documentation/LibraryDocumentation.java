/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.documentation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Complete documentation for a SAPL extension library (function library or
 * PIP).
 *
 * @param type
 * the library type
 * @param name
 * the library namespace used in SAPL expressions
 * @param description
 * short description of the library's purpose
 * @param documentation
 * detailed markdown documentation for the library
 * @param entries
 * documentation for all functions or attributes in this library
 */
public record LibraryDocumentation(
        LibraryType type,
        String name,
        String description,
        String documentation,
        List<EntryDocumentation> entries) {

    /**
     * Returns a map of code templates to their documentation strings. Useful for
     * IDE hover documentation.
     *
     * @return map from code template to documentation
     */
    public Map<String, String> codeTemplateToDocumentation() {
        return entries.stream()
                .collect(Collectors.toMap(entry -> entry.codeTemplate(name), EntryDocumentation::documentation));
    }

    /**
     * Returns a list of all code templates for this library.
     *
     * @return list of code template strings
     */
    public List<String> codeTemplates() {
        return entries.stream().map(entry -> entry.codeTemplate(name)).toList();
    }

    /**
     * Finds documentation for a specific entry by name.
     *
     * @param entryName
     * the function or attribute name
     *
     * @return the entry documentation, or null if not found
     */
    public EntryDocumentation findEntry(String entryName) {
        return entries.stream().filter(entry -> entry.name().equals(entryName)).findFirst().orElse(null);
    }

}
