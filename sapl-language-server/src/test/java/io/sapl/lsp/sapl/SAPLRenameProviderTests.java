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
package io.sapl.lsp.sapl;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SAPL rename provider")
class SAPLRenameProviderTests {

    private final SAPLRenameProvider provider = new SAPLRenameProvider();

    @Nested
    @DisplayName("prepare rename")
    class PrepareRename {

        @Test
        @DisplayName("returns result when cursor is on variable definition")
        void whenCursorOnVarDef_thenReturnsPrepareResult() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                        var x = "hello";
                    """);

            var result = provider.prepareRename(document, new Position(2, 8));

            assertThat(result).isNotNull();
            assertThat(result.getPlaceholder()).isEqualTo("x");
        }

        @Test
        @DisplayName("returns null when cursor is on keyword")
        void whenCursorOnKeyword_thenReturnsNull() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                    """);

            var result = provider.prepareRename(document, new Position(1, 2));

            assertThat(result).isNull();
        }

    }

    @Nested
    @DisplayName("rename in policy")
    class RenameInPolicy {

        @Test
        @DisplayName("renames variable definition and references within policy")
        void whenRenamingPolicyVar_thenRenamesDefinitionAndReferences() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                        var x = "hello";
                        x == "hello";
                    """);

            var workspaceEdit = provider.provideRename(document, new Position(2, 8), "newName");

            assertThat(workspaceEdit).isNotNull();
            var edits = workspaceEdit.getChanges().get("test.sapl");
            assertThat(edits).hasSize(2);
        }

        @Test
        @DisplayName("does not rename references before variable definition")
        void whenRenamingPolicyVar_thenDoesNotRenameBeforeDefinition() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                        x == "before";
                        var x = "hello";
                        x == "after";
                    """);

            var workspaceEdit = provider.provideRename(document, new Position(3, 8), "newName");

            assertThat(workspaceEdit).isNotNull();
            var edits = workspaceEdit.getChanges().get("test.sapl");
            assertThat(edits).hasSize(2);
        }

    }

    @Nested
    @DisplayName("rename in policy set")
    class RenameInPolicySet {

        @Test
        @DisplayName("renames set-level variable across all policies")
        void whenRenamingSetVar_thenRenamesAcrossPolicies() {
            var document = new SAPLParsedDocument("test.sapl", """
                    set "test"
                    first or abstain
                    var config = "default";

                    policy "p1"
                    permit
                        config == "default";

                    policy "p2"
                    deny
                        config == "other";
                    """);

            var workspaceEdit = provider.provideRename(document, new Position(2, 8), "settings");

            assertThat(workspaceEdit).isNotNull();
            var edits = workspaceEdit.getChanges().get("test.sapl");
            assertThat(edits).hasSize(3);
        }

    }

}
