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

import org.eclipse.lsp4j.FoldingRangeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SAPL folding range provider")
class SAPLFoldingRangeProviderTests {

    private final SAPLFoldingRangeProvider provider = new SAPLFoldingRangeProvider();

    @Nested
    @DisplayName("policy folding")
    class PolicyFolding {

        @Test
        @DisplayName("emits folding range for multi-line policy")
        void whenMultiLinePolicy_thenEmitsFoldingRange() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                        action == "read";
                    """);

            var ranges = provider.provideFoldingRanges(document);

            assertThat(ranges).hasSize(1).first().satisfies(range -> {
                assertThat(range.getStartLine()).isZero();
                assertThat(range.getEndLine()).isEqualTo(2);
            });
        }

    }

    @Nested
    @DisplayName("policy set folding")
    class PolicySetFolding {

        @Test
        @DisplayName("emits folding ranges for policy set and nested policies")
        void whenPolicySet_thenEmitsSetAndPolicyRanges() {
            var document = new SAPLParsedDocument("test.sapl", """
                    set "test"
                    first or abstain

                    policy "p1"
                    permit
                        action == "read";

                    policy "p2"
                    deny
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
            var document = new SAPLParsedDocument("test.sapl", """
                    /*
                     * This is a
                     * block comment
                     */
                    policy "test"
                    permit
                    """);

            var ranges = provider.provideFoldingRanges(document);

            assertThat(ranges).anySatisfy(range -> {
                assertThat(range.getStartLine()).isZero();
                assertThat(range.getEndLine()).isEqualTo(3);
                assertThat(range.getKind()).isEqualTo(FoldingRangeKind.Comment);
            });
        }

    }

}
