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
package io.sapl.lsp.sapl.completion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.documentation.EntryDocumentation;
import io.sapl.api.documentation.EntryType;
import io.sapl.api.documentation.ParameterDocumentation;

/**
 * Tests for SnippetConverter LSP snippet generation.
 */
class SnippetConverterTests {

    @Test
    void whenFunctionWithNoParameters_thenEmptyParentheses() {
        var entry = new EntryDocumentation(EntryType.FUNCTION, "now", "Returns current time.", null, List.of());

        var snippet = SnippetConverter.toSnippet(entry, "time");

        assertThat(snippet).isEqualTo("time.now()");
    }

    @Test
    void whenFunctionWithOneParameter_thenSingleTabStop() {
        var params = List.of(ParameterDocumentation.untyped("dateTime"));
        var entry  = new EntryDocumentation(EntryType.FUNCTION, "dayOfWeekFrom", "Extracts day of week.", null, params);

        var snippet = SnippetConverter.toSnippet(entry, "time");

        assertThat(snippet).isEqualTo("time.dayOfWeekFrom(${1:dateTime})");
    }

    @Test
    void whenFunctionWithMultipleParameters_thenOrderedTabStops() {
        var params = List.of(ParameterDocumentation.untyped("text"), ParameterDocumentation.untyped("pattern"));
        var entry  = new EntryDocumentation(EntryType.FUNCTION, "matches", "Regex match.", null, params);

        var snippet = SnippetConverter.toSnippet(entry, "standard");

        assertThat(snippet).isEqualTo("standard.matches(${1:text}, ${2:pattern})");
    }

    @Test
    void whenFunctionWithVarargsParameter_thenVarargsSuffix() {
        var params = List.of(ParameterDocumentation.untyped("format"),
                ParameterDocumentation.varArgs("args", List.of()));
        var entry  = new EntryDocumentation(EntryType.FUNCTION, "format", "Formats string.", null, params);

        var snippet = SnippetConverter.toSnippet(entry, "text");

        assertThat(snippet).isEqualTo("text.format(${1:format}, ${2:args...})");
    }

    @Test
    void whenAttributeWithNoParameters_thenSimpleAttributeSyntax() {
        var entry = new EntryDocumentation(EntryType.ATTRIBUTE, "user", "Returns current user.", null, List.of());

        var snippet = SnippetConverter.toSnippet(entry, "auth");

        assertThat(snippet).isEqualTo("<auth.user>");
    }

    @Test
    void whenAttributeWithParameters_thenAttributeWithTabStops() {
        var params = List.of(ParameterDocumentation.untyped("path"), ParameterDocumentation.untyped("options"));
        var entry  = new EntryDocumentation(EntryType.ATTRIBUTE, "read", "Reads resource.", null, params);

        var snippet = SnippetConverter.toSnippet(entry, "io");

        assertThat(snippet).isEqualTo("<io.read(${1:path}, ${2:options})>");
    }

    @Test
    void whenEnvironmentAttributeWithNoParameters_thenSimpleSyntax() {
        var entry = new EntryDocumentation(EntryType.ENVIRONMENT_ATTRIBUTE, "clock", "System clock.", null, List.of());

        var snippet = SnippetConverter.toSnippet(entry, "system");

        assertThat(snippet).isEqualTo("<system.clock>");
    }

    @Test
    void whenEnvironmentAttributeWithParameters_thenAttributeWithTabStops() {
        var params = List.of(ParameterDocumentation.untyped("timezone"));
        var entry  = new EntryDocumentation(EntryType.ENVIRONMENT_ATTRIBUTE, "localTime", "Local time.", null, params);

        var snippet = SnippetConverter.toSnippet(entry, "time");

        assertThat(snippet).isEqualTo("<time.localTime(${1:timezone})>");
    }

    @Test
    void whenUsingAlias_thenAliasUsedInsteadOfNamespace() {
        var params = List.of(ParameterDocumentation.untyped("value"));
        var entry  = new EntryDocumentation(EntryType.FUNCTION, "hash", "Hashes value.", null, params);

        var snippet = SnippetConverter.toSnippetWithAlias(entry, "h");

        assertThat(snippet).isEqualTo("h(${1:value})");
    }

    @Test
    void whenUsingAliasForAttribute_thenAliasWithAttributeSyntax() {
        var entry = new EntryDocumentation(EntryType.ATTRIBUTE, "data", "Gets data.", null, List.of());

        var snippet = SnippetConverter.toSnippetWithAlias(entry, "d");

        assertThat(snippet).isEqualTo("<d>");
    }

    static Stream<Arguments> escapeTestCases() {
        return Stream.of(arguments("simple text", "simple text"), arguments("with $dollar", "with \\$dollar"),
                arguments("with }brace", "with \\}brace"), arguments("with \\backslash", "with \\\\backslash"),
                arguments("${1:placeholder}", "\\${1:placeholder\\}"),
                arguments("multiple $$ and }}", "multiple \\$\\$ and \\}\\}"));
    }

    @ParameterizedTest(name = "whenEscaping_{0}_thenProperlyEscaped")
    @MethodSource("escapeTestCases")
    void whenEscapingSpecialCharacters_thenProperlyEscaped(String input, String expected) {
        var escaped = SnippetConverter.escapeSnippetText(input);

        assertThat(escaped).isEqualTo(expected);
    }

    @Test
    void whenManyParameters_thenAllHaveUniqueTabStopIndices() {
        var params = List.of(ParameterDocumentation.untyped("a"), ParameterDocumentation.untyped("b"),
                ParameterDocumentation.untyped("c"), ParameterDocumentation.untyped("d"),
                ParameterDocumentation.untyped("e"));
        var entry  = new EntryDocumentation(EntryType.FUNCTION, "combine", "Combines values.", null, params);

        var snippet = SnippetConverter.toSnippet(entry, "util");

        assertThat(snippet).isEqualTo("util.combine(${1:a}, ${2:b}, ${3:c}, ${4:d}, ${5:e})");
    }

}
