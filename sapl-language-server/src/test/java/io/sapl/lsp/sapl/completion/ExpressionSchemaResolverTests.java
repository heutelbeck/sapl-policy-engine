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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.EntryDocumentation;
import io.sapl.api.documentation.EntryType;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.documentation.LibraryType;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.lsp.configuration.LSPConfiguration;
import io.sapl.lsp.sapl.TestParsing;

/**
 * Tests for how imported function names are resolved to their fully qualified
 * names during schema inference.
 */
class ExpressionSchemaResolverTests {

    @Nested
    @DisplayName("import resolution honours dot boundaries")
    class ImportResolution {

        @Test
        @DisplayName("when reference equals the imported simple name then it resolves to the fully qualified name")
        void whenReferenceIsImportedSimpleNameThenResolvesToFullyQualifiedName() {
            var sapl = parse("""
                    import filter.blacken
                    policy "test"
                    permit
                    """);

            var resolved = ExpressionSchemaResolver.resolveImport("blacken", sapl);

            assertThat(resolved).isEqualTo("filter.blacken");
        }

        @Test
        @DisplayName("when reference is only a suffix of the imported name then it does not resolve to that function")
        void whenReferenceIsBareSuffixOfImportedNameThenDoesNotResolve() {
            var sapl = parse("""
                    import filter.blacken
                    policy "test"
                    permit
                    """);

            var resolved = ExpressionSchemaResolver.resolveImport("acken", sapl);

            assertThat(resolved).isEqualTo("acken");
        }

        @Test
        @DisplayName("when reference matches an alias then it resolves to the fully qualified name")
        void whenReferenceMatchesAliasThenResolvesToFullyQualifiedName() {
            var sapl = parse("""
                    import filter.blacken as redact
                    policy "test"
                    permit
                    """);

            var resolved = ExpressionSchemaResolver.resolveImport("redact", sapl);

            assertThat(resolved).isEqualTo("filter.blacken");
        }

        @Test
        @DisplayName("when reference is unrelated to any import then it is returned unchanged")
        void whenReferenceUnrelatedToImportsThenReturnedUnchanged() {
            var sapl = parse("""
                    import filter.blacken
                    policy "test"
                    permit
                    """);

            var resolved = ExpressionSchemaResolver.resolveImport("somethingElse", sapl);

            assertThat(resolved).isEqualTo("somethingElse");
        }
    }

    @Nested
    @DisplayName("function schema lookup matches the function identity")
    class FunctionSchemaIdentity {

        @Test
        @DisplayName("when a function name is a substring of a longer function name then it does not inherit that function's schema")
        void whenNameIsSubstringOfLongerFunctionThenDoesNotInheritItsSchema() {
            var document     = """
                    policy "test"
                    permit
                    where
                      var v = time.now();
                    """;
            var sapl         = parse(document);
            var cursorOffset = document.length();

            var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset,
                    configWithCollidingFunctionNames(), false);

            assertThat(proposals).contains("v", "v.year").doesNotContain("v.surplus");
        }
    }

    @Nested
    @DisplayName("a value defined by a binary expression carries no schema")
    class BinaryExpressionSchema {

        @Test
        @DisplayName("when a value is the sum of a schema-bearing function call and a literal then it has no schema expansions")
        void whenValueIsBinaryAdditionThenNoSchemaExpansions() {
            var document     = """
                    policy "test"
                    permit
                    where
                      var v = time.now() + 1;
                    """;
            var sapl         = parse(document);
            var cursorOffset = document.length();

            var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset,
                    configWithCollidingFunctionNames(), false);

            assertThat(proposals).contains("v").doesNotContain("v.year");
        }

        @Test
        @DisplayName("when a value compares a schema-bearing function call then it has no schema expansions")
        void whenValueIsComparisonThenNoSchemaExpansions() {
            var document     = """
                    policy "test"
                    permit
                    where
                      var v = time.now() > 1;
                    """;
            var sapl         = parse(document);
            var cursorOffset = document.length();

            var proposals = VariablesProposalsGenerator.variableProposalsForContext(sapl, cursorOffset,
                    configWithCollidingFunctionNames(), false);

            assertThat(proposals).contains("v").doesNotContain("v.year");
        }
    }

    private static final String NOW_SCHEMA = """
            { "type": "object", "properties": { "year": {} } }
            """;

    private static final String NOW_EXTENDED_SCHEMA = """
            { "type": "object", "properties": { "surplus": {} } }
            """;

    private static LSPConfiguration configWithCollidingFunctionNames() {
        var now         = new EntryDocumentation(EntryType.FUNCTION, "now", "Current time", NOW_SCHEMA, List.of());
        var nowExtended = new EntryDocumentation(EntryType.FUNCTION, "nowExtended", "Extended time",
                NOW_EXTENDED_SCHEMA, List.of());
        var timeLib     = new LibraryDocumentation(LibraryType.FUNCTION_LIBRARY, "time", "Time functions", "Docs",
                List.of(now, nowExtended));
        var bundle      = new DocumentationBundle(List.of(timeLib));
        var minimal     = LSPConfiguration.minimal();
        return new LSPConfiguration("", bundle, Map.of(), minimal.functionBroker());
    }

    private static SaplContext parse(String content) {
        return TestParsing.parseSilently(content);
    }

}
