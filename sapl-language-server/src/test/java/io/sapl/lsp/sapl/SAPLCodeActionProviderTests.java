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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import lombok.val;

@DisplayName("SAPL code action provider")
class SAPLCodeActionProviderTests {

    private static final String URI = "file:///policies/test.sapl";

    private static final String COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION = "policy \"p\" permit action = \"read\";";

    private static final int   END_OF_LINE   = COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION.length();
    private static final int   SIGN          = COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION.indexOf('=');
    private static final int   IN_THE_NAME   = COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION.indexOf("\"p\"") + 1;
    private static final int   CONDITION     = COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION.indexOf("action");
    private static final int   END_OF_STRING = COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION.indexOf(';');
    private static final Range WHOLE_LINE    = new Range(new Position(0, 0), new Position(0, END_OF_LINE));

    private final SAPLCodeActionProvider  provider            = new SAPLCodeActionProvider();
    private final SAPLDiagnosticsProvider diagnosticsProvider = new SAPLDiagnosticsProvider();
    private final SAPLParsedDocument      document            = new SAPLParsedDocument(URI,
            COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION);

    @Test
    @DisplayName("a problem with a quick fix is offered as a preferred quick fix that resolves the published diagnostic")
    void whenProblemHasAQuickFixThenItIsOfferedAsAPreferredQuickFixThatResolvesThePublishedDiagnostic() {
        val published = diagnosticsProvider.provideDiagnostics(document);

        val codeActions = provider.provideCodeActions(document, WHOLE_LINE);

        assertThat(codeActions).singleElement().satisfies(codeAction -> {
            assertThat(codeAction.getTitle()).isEqualTo("Replace with '=='");
            assertThat(codeAction.getKind()).isEqualTo(CodeActionKind.QuickFix);
            assertThat(codeAction.getIsPreferred()).isTrue();
            assertThat(codeAction.getDiagnostics()).isEqualTo(published);
            assertThat(codeAction.getEdit().getChanges()).containsOnlyKeys(URI);
        });
    }

    @Test
    @DisplayName("applying the edit of the quick fix replaces the '=' with '=='")
    void whenQuickFixIsAppliedThenTheSignOfADefinitionBecomesAComparison() {
        val codeActions = provider.provideCodeActions(document, WHOLE_LINE);

        assertThat(codeActions).singleElement()
                .extracting(codeAction -> ProtocolText.withEditsApplied(COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION,
                        codeAction.getEdit().getChanges().get(URI)))
                .isEqualTo("policy \"p\" permit action == \"read\";");
    }

    static Stream<Arguments> rangesThatTouchTheProblem() {
        return Stream.of(arguments("the cursor before the '='", cursorAt(SIGN)),
                arguments("the cursor after the '='", cursorAt(SIGN + 1)), arguments("a selection that holds the '='",
                        new Range(new Position(0, CONDITION), new Position(0, END_OF_STRING))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rangesThatTouchTheProblem")
    @DisplayName("the quick fix is offered for every range that touches the marked text")
    void whenRequestedRangeTouchesTheProblemThenTheQuickFixIsOffered(String description, Range requested) {
        val codeActions = provider.provideCodeActions(document, requested);

        assertThat(codeActions).hasSize(1);
    }

    static Stream<Arguments> rangesThatDoNotTouchTheProblem() {
        return Stream.of(arguments("the cursor in the name of the policy", cursorAt(IN_THE_NAME)),
                arguments("the cursor after the string", cursorAt(END_OF_LINE)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rangesThatDoNotTouchTheProblem")
    @DisplayName("no quick fix is offered for a range away from the marked text")
    void whenRequestedRangeDoesNotTouchTheProblemThenNoQuickFixIsOffered(String description, Range requested) {
        val codeActions = provider.provideCodeActions(document, requested);

        assertThat(codeActions).isEmpty();
    }

    static Stream<Arguments> documentsWithoutQuickFix() {
        return Stream.of(arguments("a valid document", "policy \"p\" permit action == \"read\";"),
                arguments("a policy without name", "policy permit action == \"read\";"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentsWithoutQuickFix")
    @DisplayName("no quick fix is offered for a document whose problems have none")
    void whenNoProblemHasAQuickFixThenNoQuickFixIsOffered(String description, String content) {
        val codeActions = provider.provideCodeActions(new SAPLParsedDocument(URI, content), WHOLE_LINE);

        assertThat(codeActions).isEmpty();
    }

    static Stream<Arguments> documentsWithAQuickFix() {
        return Stream.of(arguments("a missing ';'", "policy \"p\" permit action == \"read\"", "Insert ';'"),
                arguments("a superfluous ';'", "policy \"p\" permit action == \"read\";;", "Remove ';'"),
                arguments("an operator of another language", "policy \"p\" permit action === \"read\";",
                        "Replace with '=='"),
                arguments("a bracket that is not closed", "policy \"p\" permit lib.f(action;", "Insert ')'"),
                arguments("a misspelled effect", "policy \"p\" permitt action == \"read\";", "Replace with 'permit'"),
                arguments("a text in typographic quotation marks", "policy \"p\" permit action == \u201Cread\u201D;",
                        "Replace with '\"read\"'"));
    }

    @ParameterizedTest(name = "{0}: {2}")
    @MethodSource("documentsWithAQuickFix")
    @DisplayName("the quick fix of a problem reaches the editor, and applying it leaves a valid document")
    void whenProblemHasAQuickFixThenTheEditorOffersItAndItRepairsTheDocument(String description, String content,
            String title) {
        val wholeLine   = new Range(new Position(0, 0), new Position(0, content.length()));
        val codeActions = provider.provideCodeActions(new SAPLParsedDocument(URI, content), wholeLine);

        assertThat(codeActions).singleElement().satisfies(codeAction -> {
            assertThat(codeAction.getTitle()).isEqualTo(title);
            val repaired = ProtocolText.withEditsApplied(content, codeAction.getEdit().getChanges().get(URI));
            assertThat(new SAPLParsedDocument(URI, repaired).hasErrors()).as(repaired).isFalse();
        });
    }

    private static Range cursorAt(int character) {
        val cursor = new Position(0, character);
        return new Range(cursor, cursor);
    }

}
