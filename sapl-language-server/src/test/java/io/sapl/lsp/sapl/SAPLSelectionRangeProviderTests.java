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

import java.util.List;

import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SAPL selection range provider")
class SAPLSelectionRangeProviderTests {

    private final SAPLSelectionRangeProvider provider = new SAPLSelectionRangeProvider();

    @Nested
    @DisplayName("parent chain")
    class ParentChain {

        @Test
        @DisplayName("returns selection range with parent chain for cursor in expression")
        void whenCursorInExpression_thenReturnsChain() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                        action == "read";
                    """);

            var ranges = provider.provideSelectionRanges(document, List.of(new Position(2, 12)));

            assertThat(ranges).hasSize(1).first().satisfies(range -> {
                assertThat(range.getRange()).isNotNull();
                assertThat(range.getParent()).isNotNull();
            });
        }

        @Test
        @DisplayName("returns empty list when cursor is outside tree")
        void whenCursorOutsideTree_thenReturnsEmpty() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                    """);

            var ranges = provider.provideSelectionRanges(document, List.of(new Position(10, 0)));

            assertThat(ranges).isEmpty();
        }

    }

    @Nested
    @DisplayName("multiple positions")
    class MultiplePositions {

        @Test
        @DisplayName("returns one selection range per valid position")
        void whenMultiplePositions_thenReturnsOnePerPosition() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                        action == "read";
                    """);

            var ranges = provider.provideSelectionRanges(document, List.of(new Position(0, 8), new Position(2, 5)));

            assertThat(ranges).hasSize(2);
        }

    }

}
