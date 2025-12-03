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
 * A bundle containing documentation for multiple libraries, suitable for JSON
 * serialization and transfer between PAP and IDE.
 *
 * @param libraries
 * documentation for all included libraries
 */
public record DocumentationBundle(List<LibraryDocumentation> libraries) {

    /**
     * Returns all function libraries in this bundle.
     *
     * @return list of function library documentation
     */
    public List<LibraryDocumentation> functionLibraries() {
        return libraries.stream().filter(lib -> lib.type() == LibraryType.FUNCTION_LIBRARY).toList();
    }

    /**
     * Returns all policy information points in this bundle.
     *
     * @return list of PIP documentation
     */
    public List<LibraryDocumentation> policyInformationPoints() {
        return libraries.stream().filter(lib -> lib.type() == LibraryType.POLICY_INFORMATION_POINT).toList();
    }

    /**
     * Finds a library by its namespace.
     *
     * @param namespace
     * the library namespace
     *
     * @return the library documentation, or null if not found
     */
    public LibraryDocumentation findLibrary(String namespace) {
        return libraries.stream().filter(lib -> lib.name().equals(namespace)).findFirst().orElse(null);
    }

    /**
     * Returns a combined map of all code templates to their documentation.
     *
     * @return map from code template to documentation across all libraries
     */
    public Map<String, String> allCodeTemplateDocumentation() {
        return libraries.stream().flatMap(lib -> lib.codeTemplateToDocumentation().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (existing, replacement) -> existing));
    }

}
