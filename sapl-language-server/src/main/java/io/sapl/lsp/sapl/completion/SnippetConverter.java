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
package io.sapl.lsp.sapl.completion;

import java.util.List;

import io.sapl.api.documentation.EntryDocumentation;
import io.sapl.api.documentation.EntryType;
import io.sapl.api.documentation.ParameterDocumentation;
import lombok.experimental.UtilityClass;

/**
 * Converts EntryDocumentation code templates to LSP snippet format.
 *
 * LSP snippets use tab stops with placeholders:
 * {@code ${1:paramName}} for the first parameter,
 * {@code ${2:paramName}} for the second, etc.
 *
 * This enables IDE users to tab through parameters when inserting a function
 * or attribute completion.
 */
@UtilityClass
public class SnippetConverter {

    /**
     * Generates an LSP snippet for a function or attribute entry.
     *
     * Examples:
     * - Function: {@code time.dayOfWeekFrom(${1:dateTime})}
     * - Attribute with params: {@code <pip.attr(${1:param1}, ${2:param2})>}
     * - Attribute without params: {@code <pip.attr>}
     *
     * @param entry the function or attribute documentation
     * @param namespace the library namespace
     * @return LSP snippet string with tab stops
     */
    public static String toSnippet(EntryDocumentation entry, String namespace) {
        var snippet = new StringBuilder();

        if (entry.type() != EntryType.FUNCTION) {
            snippet.append('<');
        }

        snippet.append(namespace).append('.').append(entry.name());

        var parameters = entry.parameters();
        if (entry.type() == EntryType.FUNCTION) {
            snippet.append('(');
            appendParametersAsSnippet(snippet, parameters);
            snippet.append(')');
        } else if (!parameters.isEmpty()) {
            snippet.append('(');
            appendParametersAsSnippet(snippet, parameters);
            snippet.append(")>");
        } else {
            snippet.append('>');
        }

        return snippet.toString();
    }

    /**
     * Generates an LSP snippet using an alias instead of the fully qualified name.
     *
     * @param entry the function or attribute documentation
     * @param alias the import alias to use
     * @return LSP snippet string with tab stops
     */
    public static String toSnippetWithAlias(EntryDocumentation entry, String alias) {
        var snippet = new StringBuilder();

        if (entry.type() != EntryType.FUNCTION) {
            snippet.append('<');
        }

        snippet.append(alias);

        var parameters = entry.parameters();
        if (entry.type() == EntryType.FUNCTION) {
            snippet.append('(');
            appendParametersAsSnippet(snippet, parameters);
            snippet.append(')');
        } else if (!parameters.isEmpty()) {
            snippet.append('(');
            appendParametersAsSnippet(snippet, parameters);
            snippet.append(")>");
        } else {
            snippet.append('>');
        }

        return snippet.toString();
    }

    /**
     * Appends parameters as LSP snippet placeholders.
     * Each parameter becomes ${index:name} where index starts at 1.
     * Varargs parameters get a ... suffix in the placeholder name.
     */
    private static void appendParametersAsSnippet(StringBuilder snippet, List<ParameterDocumentation> parameters) {
        for (var i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                snippet.append(", ");
            }
            var param     = parameters.get(i);
            var paramName = param.varArgs() ? param.name() + "..." : param.name();
            snippet.append("${").append(i + 1).append(':').append(paramName).append('}');
        }
    }

    /**
     * Escapes special characters in snippet text that would otherwise be
     * interpreted as snippet syntax.
     *
     * Backslashes must be escaped first to avoid double-escaping the
     * backslashes added for $ and } escaping.
     *
     * @param text the text to escape
     * @return escaped text safe for use in snippets
     */
    public static String escapeSnippetText(String text) {
        return text.replace("\\", "\\\\").replace("$", "\\$").replace("}", "\\}");
    }

}
