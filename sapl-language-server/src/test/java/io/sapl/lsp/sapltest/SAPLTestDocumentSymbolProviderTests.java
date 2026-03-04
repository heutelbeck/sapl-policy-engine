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
package io.sapl.lsp.sapltest;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SAPLTest document symbol provider")
class SAPLTestDocumentSymbolProviderTests {

    private final SAPLTestDocumentSymbolProvider provider = new SAPLTestDocumentSymbolProvider();

    @Nested
    @DisplayName("requirement symbols")
    class RequirementSymbols {

        @Test
        @DisplayName("emits requirement with scenarios as children")
        void whenRequirementWithScenarios_thenEmitsSymbolHierarchy() {
            var document = new SAPLTestParsedDocument("test.sapltest", """
                    requirement "Access Control" {
                        scenario "user can read"
                            when "user" attempts "read" on "doc"
                            expect permit;
                        scenario "guest denied"
                            when "guest" attempts "write" on "doc"
                            expect deny;
                    }
                    """);

            var symbols = provider.provideDocumentSymbols(document);

            assertThat(symbols).hasSize(1).first().satisfies(req -> {
                assertThat(req.getName()).isEqualTo("Access Control");
                assertThat(req.getKind()).isEqualTo(SymbolKind.Module);
                assertThat(req.getChildren()).hasSize(2);
                assertThat(req.getChildren().get(0).getName()).isEqualTo("user can read");
                assertThat(req.getChildren().get(0).getKind()).isEqualTo(SymbolKind.Function);
                assertThat(req.getChildren().get(1).getName()).isEqualTo("guest denied");
            });
        }

    }

    @Nested
    @DisplayName("multiple requirements")
    class MultipleRequirements {

        @Test
        @DisplayName("emits one symbol per requirement")
        void whenMultipleRequirements_thenEmitsAll() {
            var document = new SAPLTestParsedDocument("test.sapltest", """
                    requirement "R1" {
                        scenario "s1"
                            when "u" attempts "a" on "r"
                            expect permit;
                    }
                    requirement "R2" {
                        scenario "s2"
                            when "u" attempts "a" on "r"
                            expect deny;
                    }
                    """);

            var symbols = provider.provideDocumentSymbols(document);

            assertThat(symbols).hasSize(2).extracting("name").containsExactly("R1", "R2");
        }

    }

    @Nested
    @DisplayName("range computation")
    class RangeComputation {

        @Test
        @DisplayName("selection range points to requirement name token")
        void whenRequirement_thenSelectionRangeCoversName() {
            var document = new SAPLTestParsedDocument("test.sapltest", """
                    requirement "test" {
                        scenario "s1"
                            when "u" attempts "a" on "r"
                            expect permit;
                    }
                    """);

            var symbols = provider.provideDocumentSymbols(document);

            assertThat(symbols).hasSize(1).first().satisfies(symbol -> {
                var selectionRange = symbol.getSelectionRange();
                assertThat(selectionRange.getStart().getLine()).isZero();
                assertThat(selectionRange.getStart().getCharacter()).isEqualTo(12);
                assertThat(selectionRange.getEnd().getCharacter()).isEqualTo(18);
            });
        }

    }

}
