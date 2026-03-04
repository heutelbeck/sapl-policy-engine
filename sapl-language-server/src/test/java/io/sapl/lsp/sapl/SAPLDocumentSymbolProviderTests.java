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

import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SAPL document symbol provider")
class SAPLDocumentSymbolProviderTests {

    private final SAPLDocumentSymbolProvider provider = new SAPLDocumentSymbolProvider();

    @Nested
    @DisplayName("single policy")
    class SinglePolicy {

        @Test
        @DisplayName("emits policy symbol with correct name and kind")
        void whenSinglePolicy_thenEmitsPolicySymbol() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "allow read"
                    permit
                    """);

            var symbols = provider.provideDocumentSymbols(document);

            assertThat(symbols).hasSize(1).first().satisfies(symbol -> {
                assertThat(symbol.getName()).isEqualTo("allow read");
                assertThat(symbol.getKind()).isEqualTo(SymbolKind.Function);
            });
        }

        @Test
        @DisplayName("emits variable children for policy with var definitions")
        void whenPolicyWithVars_thenEmitsVariableChildren() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                        var x = "hello";
                        var y = 42;
                    """);

            var symbols = provider.provideDocumentSymbols(document);

            assertThat(symbols).hasSize(1).first().satisfies(policy -> {
                assertThat(policy.getChildren()).hasSize(2);
                assertThat(policy.getChildren().get(0).getName()).isEqualTo("x");
                assertThat(policy.getChildren().get(0).getKind()).isEqualTo(SymbolKind.Variable);
                assertThat(policy.getChildren().get(1).getName()).isEqualTo("y");
            });
        }

    }

    @Nested
    @DisplayName("policy set")
    class PolicySet {

        @Test
        @DisplayName("emits policy set with nested policies")
        void whenPolicySet_thenEmitsPolicySetWithChildren() {
            var document = new SAPLParsedDocument("test.sapl", """
                    set "access control"
                    first or abstain

                    policy "p1" permit

                    policy "p2" deny
                    """);

            var symbols = provider.provideDocumentSymbols(document);

            assertThat(symbols).hasSize(1).first().satisfies(set -> {
                assertThat(set.getName()).isEqualTo("access control");
                assertThat(set.getKind()).isEqualTo(SymbolKind.Module);
                assertThat(set.getChildren()).hasSize(2);
                assertThat(set.getChildren().get(0).getName()).isEqualTo("p1");
                assertThat(set.getChildren().get(1).getName()).isEqualTo("p2");
            });
        }

        @Test
        @DisplayName("emits set-level variables as children")
        void whenPolicySetWithVars_thenEmitsVariableChildren() {
            var document = new SAPLParsedDocument("test.sapl", """
                    set "test"
                    first or abstain
                    var config = "default";

                    policy "p1" permit
                    """);

            var symbols = provider.provideDocumentSymbols(document);

            assertThat(symbols).hasSize(1).first().satisfies(set -> {
                assertThat(set.getChildren()).hasSize(2);
                assertThat(set.getChildren().get(0).getName()).isEqualTo("config");
                assertThat(set.getChildren().get(0).getKind()).isEqualTo(SymbolKind.Variable);
                assertThat(set.getChildren().get(1).getName()).isEqualTo("p1");
                assertThat(set.getChildren().get(1).getKind()).isEqualTo(SymbolKind.Function);
            });
        }

    }

    @Nested
    @DisplayName("range computation")
    class RangeComputation {

        @Test
        @DisplayName("selection range points to policy name token")
        void whenPolicy_thenSelectionRangeCoversName() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                    """);

            var symbols = provider.provideDocumentSymbols(document);

            assertThat(symbols).hasSize(1).first().satisfies(symbol -> {
                var selectionRange = symbol.getSelectionRange();
                assertThat(selectionRange.getStart().getLine()).isZero();
                assertThat(selectionRange.getStart().getCharacter()).isEqualTo(7);
                assertThat(selectionRange.getEnd().getCharacter()).isEqualTo(13);
            });
        }

    }

}
