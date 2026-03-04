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

import org.eclipse.lsp4j.FoldingRangeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SAPLTest folding range provider")
class SAPLTestFoldingRangeProviderTests {

    private final SAPLTestFoldingRangeProvider provider = new SAPLTestFoldingRangeProvider();

    @Nested
    @DisplayName("requirement folding")
    class RequirementFolding {

        @Test
        @DisplayName("emits folding range for requirement block")
        void whenRequirement_thenEmitsFoldingRange() {
            var document = new SAPLTestParsedDocument("test.sapltest", """
                    requirement "test" {
                        scenario "s1"
                            when "u" attempts "a" on "r"
                            expect permit;
                    }
                    """);

            var ranges = provider.provideFoldingRanges(document);

            assertThat(ranges).anySatisfy(range -> {
                assertThat(range.getStartLine()).isZero();
                assertThat(range.getEndLine()).isEqualTo(4);
            });
        }

        @Test
        @DisplayName("emits folding ranges for requirement and nested scenarios")
        void whenRequirementWithScenarios_thenEmitsRangesForBoth() {
            var document = new SAPLTestParsedDocument("test.sapltest", """
                    requirement "test" {
                        scenario "s1"
                            when "u" attempts "a" on "r"
                            expect permit;
                        scenario "s2"
                            when "u" attempts "a" on "r"
                            expect deny;
                    }
                    """);

            var ranges = provider.provideFoldingRanges(document);

            assertThat(ranges).hasSize(3);
        }

    }

    @Nested
    @DisplayName("comment folding")
    class CommentFolding {

        @Test
        @DisplayName("emits folding range for block comments")
        void whenBlockComment_thenEmitsCommentFoldingRange() {
            var document = new SAPLTestParsedDocument("test.sapltest", """
                    /*
                     * Block comment
                     */
                    requirement "test" {
                        scenario "s1"
                            when "u" attempts "a" on "r"
                            expect permit;
                    }
                    """);

            var ranges = provider.provideFoldingRanges(document);

            assertThat(ranges).anySatisfy(range -> {
                assertThat(range.getStartLine()).isZero();
                assertThat(range.getEndLine()).isEqualTo(2);
                assertThat(range.getKind()).isEqualTo(FoldingRangeKind.Comment);
            });
        }

    }

}
