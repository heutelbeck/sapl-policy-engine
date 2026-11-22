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
package io.sapl.api.documentation;

import lombok.val;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

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
     * IDE hover documentation. Overloads with the same insertion text keep their
     * distinct descriptions in separate paragraphs.
     *
     * @return map from code template to documentation
     */
    public Map<String, String> codeTemplateToDocumentation() {
        val descriptions = new HashMap<String, SortedSet<String>>();
        for (val entry : entries) {
            val documented = descriptions.computeIfAbsent(entry.codeTemplate(name), ignored -> new TreeSet<>());
            if (!entry.documentation().isBlank()) {
                documented.add(entry.documentation());
            }
        }
        val templates = new HashMap<String, String>();
        descriptions.forEach((template, texts) -> templates.put(template, String.join("\n\n", texts)));
        return templates;
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
