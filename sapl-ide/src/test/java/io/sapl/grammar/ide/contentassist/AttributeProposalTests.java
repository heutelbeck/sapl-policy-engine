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
package io.sapl.grammar.ide.contentassist;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

@Slf4j
class AttributeProposalTests extends CompletionTests {

    @Test
    void testCompletion_PolicyBody_attribute_without_import() {
        final var document = """
                policy "test" deny where
                subject.<t§""";
        final var expected = List.of("temperature.atLocation>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import2() {
        final var document = """
                policy "test" deny where
                subject.<t§
                var bar = 2;""";
        final var expected = List.of("temperature.atLocation>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import3() {
        final var document = """
                policy "test" deny where
                subject.<t§""";
        final var unwanted = List.of("tempetature.atLocation>.unit", "temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import3_extend() {
        final var document = """
                policy "test" deny where
                subject.<t§""";
        final var unwanted = List.of("temperature.now>.unit", "temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import4() {
        final var document = """
                policy "test" deny where
                subject.§
                var bar = 1;""";
        final var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import5() {
        final var document = """
                policy "test" deny where
                var foo = 1;
                foo.<t§""";
        final var expected = List.of("temperature.atLocation>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import5_extension() {
        final var document = """
                policy "test" deny where
                var foo = 1;
                foo.<temperature.atLocation>.§""";
        // TextEdit inserts after the dot, so proposals don't include the leading dot
        final var expected = List.of("unit");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import6() {
        final var document = """
                policy "test" deny where
                var foo = 1;
                foo.<t§
                var bar = 2;""";
        final var expected = List.of("temperature.atLocation>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import7() {
        final var document = """
                policy "test" deny where
                var foo = 1;
                subject.§""";
        final var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import8() {
        final var document = """
                policy "test" deny where
                var foo = 1;
                foo.§
                var bar = 1;""";
        final var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import9() {
        final var document = """
                subject schema general_schema
                policy "test" deny where
                subject§
                var foo = 1;""";
        final var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import_assigned_to_variable() {
        final var document = """
                policy "test" deny where
                var foo = <temperature.now()>;
                foo.§""";
        final var expected = List.of(".unit", ".value");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import_assigned_to_variable2() {
        final var document = """
                policy "test" deny where
                var foo = <temperature.now()>;
                foo.§
                var bar = 1;""";
        final var expected = List.of(".unit");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_function_import_assigned_to_variable() {
        final var document = """
                import temperature.now
                policy "test" deny where
                var foo = "".<now>;
                fo§""";
        final var expected = List.of("foo");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_function_import_assigned_to_variable_extended() {
        final var document = """
                import temperature.now
                policy "test" deny where
                var foo = <now>;
                foo.§""";
        final var expected = List.of(".unit");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_alias_import() {
        final var document = """
                import temperature.predicted as kredited
                policy "test" deny where
                var foo = <kr§""";
        final var expected = List.of("kredited(a1)>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_alias_import2() {
        final var document = """
                import temperature.mean as zzean
                policy "test" deny where
                <z§""";
        final var expected = List.of("zzean(a1, a2)>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_alias_import3() {
        final var document = """
                import temperature.now as cow
                policy "test" deny where
                <cow>.§""";
        final var expected = List.of("unit", "value");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_library_import_assigned_to_variable() {
        final var document = """
                import temperature.now as currenttemp
                policy "test" deny where
                var foo = <currenttemp>;
                foo.§""";
        final var expected = List.of(".unit", ".value");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute() {
        final var document = """
                policy "test" deny where
                var foo = 1;
                foo.<temperature§""";
        final var expected = List.of("temperature.atLocation>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute2() {
        final var document = """
                policy "test" deny where
                <temperature.now>.§""";
        final var expected = List.of("unit", "value");
        assertProposalsContain(document, expected);
    }

    /**
     * Verifies that applying the completion for schema property at cursor position
     * produces
     * correct final document (no double dots).
     */
    @Test
    void testCompletion_schema_extension_produces_correct_document() {
        var documentWithCursor = """
                policy "test" deny where
                var foo = 1;
                foo.<temperature.atLocation>.§""";

        var expectedFinalDocument = """
                policy "test" deny where
                var foo = 1;
                foo.<temperature.atLocation>.unit""";

        assertCompletionProducesDocument(documentWithCursor, ".unit", expectedFinalDocument);
    }

    /**
     * Verifies that at a position after a dot, the labels show what the user
     * expects to see and the proposals insert correctly without duplicating the
     * dot.
     * The labels include the leading dot for user clarity, but the inserted text
     * does not include it since Xtext inserts at the cursor position (after the
     * dot).
     */
    @Test
    void testCompletion_schema_extension_labels_and_proposals() {
        var document = """
                policy "test" deny where
                var foo = 1;
                foo.<temperature.atLocation>.§""";

        // User should see labels like ".unit" in the completion popup
        var expectedLabels = List.of(".unit", ".value");
        // Inserted text is just "unit" - the dot is already in the document
        var expectedProposals = List.of("unit", "value");

        assertLabelsAndProposals(document, expectedLabels, expectedProposals);
    }

    /**
     * Verifies that at cursor position right after closing bracket (no dot), no
     * schema extension
     * proposals should be offered that would corrupt the document.
     * Bug: at subject.&lt;demo.something&gt;§ accepting proposal incorrectly
     * appends demo.something&gt;
     */
    @Test
    void testCompletion_at_closing_bracket_should_not_offer_corrupting_proposals() {
        var documentWithCursor = """
                policy "test" deny where
                subject.<temperature.atLocation>§""";

        // At this position (right after >), we should NOT get proposals that would
        // corrupt the document by appending attribute syntax without the leading <
        var unwanted = List.of("temperature.atLocation>", "temperature.now>");
        assertProposalsDoNotContain(documentWithCursor, unwanted);
    }

    /**
     * Debug test to see what proposals are offered at closing bracket position.
     */
    @Test
    void testCompletion_at_closing_bracket_debug() {
        var documentWithCursor = """
                policy "test" deny where
                subject.<temperature.atLocation>§""";

        var testCase = parseTestCaseLocal(documentWithCursor);
        testCompletion((org.eclipse.xtext.testing.TestCompletionConfiguration it) -> {
            it.setModel(testCase.document());
            it.setLine(testCase.line());
            it.setColumn(testCase.column());
            it.setAssertCompletionList(completionList -> {
                var proposals = toProposalsList(completionList);
                var labels    = toLabelsList(completionList);
                log.info("PROPOSALS at >§: {}", proposals);
                log.info("LABELS at >§: {}", labels);
                // Check for any proposals ending with > that don't start with <
                for (var proposal : proposals) {
                    if (proposal.endsWith(">") && !proposal.startsWith("<")) {
                        throw new AssertionError("Found corrupting proposal without leading '<': " + proposal);
                    }
                }
            });
        });
    }

    private record TestCaseData(String document, int line, int column) {}

    private TestCaseData parseTestCaseLocal(String testCase) {
        var sb            = new StringBuilder();
        var line          = 0;
        var column        = 0;
        var currentLine   = 0;
        var currentColumn = 0;
        var foundACursor  = false;
        for (var i = 0; i < testCase.length(); i++) {
            var c = testCase.charAt(i);
            if (c == '§') {
                line         = currentLine;
                column       = currentColumn;
                foundACursor = true;
            } else {
                sb.append(c);
            }
            if (c == '\n') {
                currentColumn = 0;
                currentLine++;
            } else {
                currentColumn++;
            }
        }
        if (!foundACursor) {
            throw new IllegalArgumentException("No cursor marker found");
        }
        return new TestCaseData(sb.toString(), line, column);
    }
}
