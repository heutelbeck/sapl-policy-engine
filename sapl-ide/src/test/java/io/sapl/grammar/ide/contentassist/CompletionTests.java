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

import io.sapl.grammar.ide.AbstractSaplLanguageServerTests;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Range;
import org.eclipse.xtext.testing.TestCompletionConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This class uses the xtext test classes to test auto-completion results.
 */
@SpringBootTest
@ContextConfiguration(classes = SAPLIdeSpringTestConfiguration.class)
public class CompletionTests extends AbstractSaplLanguageServerTests {

    protected void assertProposalsSimple(final Collection<String> expectedProposals,
            final CompletionList completionList) {
        final var actualMethods = toProposalsList(completionList);
        if (!actualMethods.containsAll(expectedProposals)) {
            throw new AssertionError("Expected: " + expectedProposals + " but got " + actualMethods);
        }
    }

    protected void assertDoesNotContainProposals(final Collection<String> unwantedProposals,
            final CompletionList completionList) {
        final var availableProposals = toProposalsList(completionList);
        for (final String unwantedProposal : unwantedProposals) {
            if (availableProposals.contains(unwantedProposal)) {
                throw new AssertionError(
                        "Expected not to find " + unwantedProposal + " but found it in " + availableProposals);
            }
        }
    }

    protected void assertProposalsEmpty(String documentWithCursor) {
        final var testCase = parseTestCase(documentWithCursor);
        testCompletion((TestCompletionConfiguration it) -> {
            it.setModel(testCase.document());
            it.setLine(testCase.line());
            it.setColumn(testCase.column());
            it.setAssertCompletionList(completionList -> {
                final var proposals = toProposalsList(completionList);
                assertThat(proposals).isEmpty();
            });
        });
    }

    protected void assertProposalsContain(String documentWithCursor, List<String> expected) {
        final var testCase = parseTestCase(documentWithCursor);
        testCompletion((TestCompletionConfiguration it) -> {
            it.setModel(testCase.document());
            it.setLine(testCase.line());
            it.setColumn(testCase.column());
            it.setAssertCompletionList(completionList -> {
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    protected void assertProposalsContainWantedAndDoNotContainUnwanted(String documentWithCursor, List<String> expected,
            List<String> unwanted) {
        final var testCase = parseTestCase(documentWithCursor);
        testCompletion((TestCompletionConfiguration it) -> {
            it.setModel(testCase.document());
            it.setLine(testCase.line());
            it.setColumn(testCase.column());
            it.setAssertCompletionList(completionList -> {
                assertProposalsSimple(expected, completionList);
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    protected void assertProposalsDoNotContain(String documentWithCursor, List<String> unwanted) {
        final var testCase = parseTestCase(documentWithCursor);
        testCompletion((TestCompletionConfiguration it) -> {
            it.setModel(testCase.document());
            it.setLine(testCase.line());
            it.setColumn(testCase.column());
            it.setAssertCompletionList(completionList -> {
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    protected List<String> toLabelsList(CompletionList cl) {
        return cl.getItems().stream().map(item -> item.getLabel()).toList();
    }

    protected List<String> toProposalsList(CompletionList cl) {
        return cl.getItems().stream().map(item -> item.getTextEdit().getLeft().getNewText()).toList();
    }

    /**
     * Applies a completion item's TextEdit to the document and returns the
     * resulting text.
     * This simulates what happens when a user selects a completion.
     */
    protected String applyTextEdit(String document, CompletionItem item) {
        var textEdit = item.getTextEdit().getLeft();
        var range    = textEdit.getRange();
        var newText  = textEdit.getNewText();
        return applyEdit(document, range, newText);
    }

    /**
     * Applies an edit (replacing a range with new text) to a document.
     */
    private String applyEdit(String document, Range range, String newText) {
        var lines     = document.split("\n", -1);
        var startLine = range.getStart().getLine();
        var startChar = range.getStart().getCharacter();
        var endLine   = range.getEnd().getLine();
        var endChar   = range.getEnd().getCharacter();

        var sb = new StringBuilder();

        // Add all lines before start line
        for (var i = 0; i < startLine; i++) {
            sb.append(lines[i]).append('\n');
        }

        // Add text before the edit on the start line
        sb.append(lines[startLine].substring(0, startChar));

        // Add the new text
        sb.append(newText);

        // Add text after the edit on the end line
        sb.append(lines[endLine].substring(endChar));

        // Add remaining lines
        for (var i = endLine + 1; i < lines.length; i++) {
            sb.append('\n').append(lines[i]);
        }

        return sb.toString();
    }

    /**
     * Finds a completion item by its label.
     */
    protected Optional<CompletionItem> findByLabel(CompletionList cl, String label) {
        return cl.getItems().stream().filter(item -> label.equals(item.getLabel())).findFirst();
    }

    /**
     * Asserts that a completion with the given label exists and when applied
     * produces the expected
     * document.
     */
    protected void assertCompletionProducesDocument(String documentWithCursor, String label,
            String expectedDocumentAfterCompletion) {
        var testCase = parseTestCase(documentWithCursor);
        testCompletion((TestCompletionConfiguration it) -> {
            it.setModel(testCase.document());
            it.setLine(testCase.line());
            it.setColumn(testCase.column());
            it.setAssertCompletionList(completionList -> {
                var item = findByLabel(completionList, label);
                assertThat(item).as("Completion with label '%s' should exist", label).isPresent();
                var result = applyTextEdit(testCase.document(), item.get());
                assertThat(result).as("Document after applying completion '%s'", label)
                        .isEqualTo(expectedDocumentAfterCompletion);
            });
        });
    }

    /**
     * Asserts that completions with the given labels exist and verifies both
     * display and insertion.
     * Returns all labels that matched the given proposals (for further inspection).
     */
    protected void assertLabelsAndProposals(String documentWithCursor, List<String> expectedLabels,
            List<String> expectedProposals) {
        var testCase = parseTestCase(documentWithCursor);
        testCompletion((TestCompletionConfiguration it) -> {
            it.setModel(testCase.document());
            it.setLine(testCase.line());
            it.setColumn(testCase.column());
            it.setAssertCompletionList(completionList -> {
                var labels    = toLabelsList(completionList);
                var proposals = toProposalsList(completionList);
                assertThat(labels).as("Labels (what user sees)").containsAll(expectedLabels);
                assertThat(proposals).as("Proposals (what gets inserted)").containsAll(expectedProposals);
            });
        });
    }

    private record DocumentAndCursor(String document, int line, int column) {}

    private static DocumentAndCursor parseTestCase(String testCase) {
        final var sb            = new StringBuilder();
        var       line          = 0;
        var       column        = 0;
        var       currentLine   = 0;
        var       currentColumn = 0;
        var       foundACursor  = false;
        for (var i = 0; i < testCase.length(); i++) {
            final var c = testCase.charAt(i);
            if (c == '§') {
                line   = currentLine;
                column = currentColumn;
                if (foundACursor) {
                    throw new IllegalArgumentException(String.format("""
                            The test case does contain more than one cursor marker. \
                            Second cursor found at: (line=%d, column=%d). \
                            The position of the cursor is indicated by the '§' character \
                            in the input String, and more than one was encountered.""", line, column));
                }
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
            throw new IllegalArgumentException("""
                    The test case does not contain a cursor marker. \
                    The position of the cursor is indicated by the '§' \
                    character in the input String.""");
        }
        return new DocumentAndCursor(sb.toString(), line, column);
    }
}
