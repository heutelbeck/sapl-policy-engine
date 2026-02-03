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

import java.util.List;
import java.util.stream.Collectors;

import lombok.val;

/**
 * Documentation for a single function or attribute finder within a library.
 *
 * @param type
 * the entry type (function, attribute, or environment attribute)
 * @param name
 * the function or attribute name
 * @param documentation
 * markdown documentation describing the entry's purpose and usage
 * @param schema
 * JSON schema for the return value, or null if not specified. Stored as a JSON
 * string.
 * @param parameters
 * documentation for each parameter
 */
public record EntryDocumentation(
        EntryType type,
        String name,
        String documentation,
        String schema,
        List<ParameterDocumentation> parameters) {

    /**
     * Generates a code template for IDE autocompletion. For functions:
     * {@code functionName(param1, param2)} For
     * attributes: {@code <attributeName(param1, param2)>}
     *
     * @param namespace
     * the library namespace
     *
     * @return the code template string
     */
    public String codeTemplate(String namespace) {
        val template = new StringBuilder();

        if (type != EntryType.FUNCTION) {
            template.append('<');
        }

        template.append(namespace).append('.').append(name);

        if (type == EntryType.FUNCTION) {
            template.append('(');
            template.append(parameters.stream().map(this::parameterTemplate).collect(Collectors.joining(", ")));
            template.append(')');
        } else if (!parameters.isEmpty()) {
            template.append('(');
            template.append(parameters.stream().map(this::parameterTemplate).collect(Collectors.joining(", ")));
            template.append(')');
            template.append('>');
        } else {
            template.append('>');
        }

        return template.toString();
    }

    /**
     * Generates a code template using an alias instead of the fully qualified name.
     *
     * @param alias
     * the import alias to use
     *
     * @return the code template string with alias
     */
    public String codeTemplateWithAlias(String alias) {
        val template = new StringBuilder();

        if (type != EntryType.FUNCTION) {
            template.append('<');
        }

        template.append(alias);

        if (type == EntryType.FUNCTION) {
            template.append('(');
            template.append(parameters.stream().map(this::parameterTemplate).collect(Collectors.joining(", ")));
            template.append(')');
        } else if (!parameters.isEmpty()) {
            template.append('(');
            template.append(parameters.stream().map(this::parameterTemplate).collect(Collectors.joining(", ")));
            template.append(')');
            template.append('>');
        } else {
            template.append('>');
        }

        return template.toString();
    }

    private String parameterTemplate(ParameterDocumentation parameter) {
        if (parameter.varArgs()) {
            return parameter.name() + "...";
        }
        return parameter.name();
    }

}
