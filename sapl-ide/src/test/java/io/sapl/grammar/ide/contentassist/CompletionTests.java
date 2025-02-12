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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;

import org.eclipse.lsp4j.CompletionList;
import org.eclipse.xtext.testing.TestCompletionConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import io.sapl.grammar.ide.AbstractSaplLanguageServerTests;

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
