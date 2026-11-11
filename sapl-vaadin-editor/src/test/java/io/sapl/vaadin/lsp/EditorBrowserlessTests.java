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
package io.sapl.vaadin.lsp;

import static com.vaadin.browserless.BrowserlessApplicationContext.forComponent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.vaadin.browserless.BrowserlessApplicationContext;

import io.sapl.vaadin.DocumentChangedEvent;
import io.sapl.vaadin.IssueSeverity;
import io.sapl.vaadin.lsp.EditorTestPage.EditorKind;
import io.sapl.vaadin.lsp.EditorTestPage.Presentation;

import lombok.val;

/** Tests the Java component boundary. CodeMirror rendering and LSP traffic require a browser. */
@DisplayName("Editor configuration and notifications without a browser")
class EditorBrowserlessTests {

    private BrowserlessApplicationContext application;

    @AfterEach
    void closeApplication() {
        if (application != null) {
            application.close();
        }
    }

    @MethodSource("configurations")
    @ParameterizedTest(name = "{0}, configured={1}")
    @DisplayName("An editor opens with the requested viewing and editing preferences")
    void whenEditorOpensThenConfigurationIsApplied(EditorKind kind, boolean configured) {
        val page = open(kind, configured);

        assertThat(page.presentation()).isEqualTo(
                new Presentation(configured, configured, configured, configured, configured ? "13px" : null));
    }

    @EnumSource(EditorKind.class)
    @ParameterizedTest(name = "{0}")
    @DisplayName("Changing wrapping, color scheme following and font size preserves content and editing permissions")
    void whenReaderChangesPresentationThenDocumentAndPermissionsArePreserved(EditorKind kind) {
        val page = open(kind, true);
        page.loadDocument("original document");

        page.changePresentation(false, false, "0.875rem");

        assertThat(page.presentation()).isEqualTo(new Presentation(true, true, false, false, "0.875rem"));
        assertThat(page.document()).isEqualTo("original document");
        assertThat(page.documentChanges()).containsExactly(new DocumentChangedEvent("original document", false));

        page.changePresentation(true, true, null);

        assertThat(page.presentation()).isEqualTo(new Presentation(true, true, true, true, null));
        assertThat(page.documentChanges()).containsExactly(new DocumentChangedEvent("original document", false));
    }

    @EnumSource(EditorKind.class)
    @ParameterizedTest(name = "{0}")
    @DisplayName("Consumers can distinguish loading a document from a user edit")
    void whenDocumentIsLoadedAndEditedThenNotificationsIdentifyTheirOrigin(EditorKind kind) {
        val page = open(kind, false);

        page.loadDocument("original document");
        page.editDocument("edited document");

        assertThat(page.document()).isEqualTo("edited document");
        assertThat(page.documentChanges()).containsExactly(new DocumentChangedEvent("original document", false),
                new DocumentChangedEvent("edited document", true));
    }

    @EnumSource(EditorKind.class)
    @ParameterizedTest(name = "{0}")
    @DisplayName("A removed observer receives no further document changes")
    void whenDocumentObserverIsRemovedThenLaterChangesAreNotDelivered(EditorKind kind) {
        val page = open(kind, false);
        page.loadDocument("original document");

        page.stopObservingDocument();
        page.editDocument("edited document");
        page.loadDocument("replacement document");

        assertThat(page.document()).isEqualTo("replacement document");
        assertThat(page.documentChanges()).containsExactly(new DocumentChangedEvent("original document", false));
    }

    @EnumSource(EditorKind.class)
    @ParameterizedTest(name = "{0}")
    @DisplayName("Separate users keep their documents and viewing preferences independent")
    void whenOneUserEditsThenAnotherUserKeepsTheirDocumentAndPreferences(EditorKind kind) {
        val first  = open(kind, false);
        val second = new EditorTestPage(application.newUser().newWindow(), kind);
        first.loadDocument("first document");
        second.loadDocument("second document");

        first.editDocument("first edit");
        first.changePresentation(true, true, "13px");

        assertThat(first.document()).isEqualTo("first edit");
        assertThat(second.document()).isEqualTo("second document");
        assertThat(second.presentation()).isEqualTo(new Presentation(false, false, false, false, null));
        assertThat(second.documentChanges()).containsExactly(new DocumentChangedEvent("second document", false));
    }

    @MethodSource("clearedDiagnostics")
    @ParameterizedTest(name = "{0}, clear payload={1}")
    @DisplayName("Consumers receive an issue followed by an empty validation result when diagnostics clear")
    void whenDiagnosticsClearThenConsumersReceiveTheCurrentIssues(EditorKind kind, String clearPayload) {
        val page = open(kind, false);

        page.reportValidation("""
                [{"description":"Unknown function","severity":"error","line":3,"column":7,"offset":10,"length":4}]
                """);
        page.reportValidation(clearPayload);

        assertThat(page.validationResults()).hasSize(2).satisfies(results -> {
            assertThat(results.getFirst().getIssues()).singleElement()
                    .satisfies(issue -> assertThat(issue)
                            .extracting("description", "severity", "line", "column", "offset", "length")
                            .containsExactly("Unknown function", IssueSeverity.ERROR, 3, 7, 10, 4));
            assertThat(results.getLast().getIssues()).isEmpty();
        });
    }

    @EnumSource(value = EditorKind.class, names = { "SAPL", "SAPLTEST" })
    @ParameterizedTest(name = "{0}")
    @DisplayName("A removed validation observer receives no further diagnostics")
    void whenValidationObserverIsRemovedThenDiagnosticsAreNotDelivered(EditorKind kind) {
        val page = open(kind, false);

        page.stopObservingValidation();
        page.reportValidation("[]");

        assertThat(page.validationResults()).isEmpty();
    }

    @EnumSource(value = EditorKind.class, names = { "SAPL", "SAPLTEST" })
    @ParameterizedTest(name = "{0}")
    @DisplayName("SAPL and SAPLTest retain their language and configured language server endpoint")
    void whenLanguageEditorOpensThenItsLanguageServerSettingsAreRetained(EditorKind kind) {
        val page = open(kind, true);

        assertThat(page.language()).isEqualTo(kind == EditorKind.SAPL ? "sapl" : "sapltest");
        assertThat(page.languageServerUrl()).isEqualTo("/sapl-lsp");
    }

    private EditorTestPage open(EditorKind kind, boolean configured) {
        application = forComponent(() -> kind.create(configured));
        return new EditorTestPage(application.newUser().newWindow(), kind);
    }

    private static Stream<Arguments> configurations() {
        return Stream.of(EditorKind.values()).flatMap(kind -> Stream.of(arguments(kind, false), arguments(kind, true)));
    }

    private static Stream<Arguments> clearedDiagnostics() {
        return Stream.of(EditorKind.SAPL, EditorKind.SAPLTEST)
                .flatMap(kind -> Stream.of(arguments(kind, "[]"), arguments(kind, (String) null)));
    }
}
