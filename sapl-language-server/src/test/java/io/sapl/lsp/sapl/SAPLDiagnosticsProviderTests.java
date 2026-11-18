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
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.compiler.diagnostics.Problem;
import lombok.val;

@DisplayName("SAPL diagnostics provider")
class SAPLDiagnosticsProviderTests {

    private static final String URI = "test.sapl";

    private static final String WRONG_OPERATOR = "Wrong operator '='. '=' only defines variables. To compare two values, use '=='.";

    private final SAPLDiagnosticsProvider provider = new SAPLDiagnosticsProvider();

    static Stream<Arguments> comparisonsWrittenWithTheSignOfADefinition() {
        val plainLine    = "    action = \"read\";";
        val afterTheFace = "    \"" + ProtocolText.GRINNING_FACE + "\" = subject.mood;";
        return ProtocolText.documentsWithEveryLineEndAndFaces(plainLine, afterTheFace);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("comparisonsWrittenWithTheSignOfADefinition")
    @DisplayName("a problem of the catalogue reaches the editor with its class, its message and the range of the wrong text")
    void whenComparisonIsWrittenWithTheSignOfADefinitionThenTheEditorMarksTheSignWithTheProblemOfTheCatalogue(
            String description, String content) {
        val diagnostics = provider.provideDiagnostics(new SAPLParsedDocument(URI, content));

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.getCode().getLeft()).isEqualTo("assignment-instead-of-comparison");
            assertThat(diagnostic.getMessage().getLeft()).isEqualTo(WRONG_OPERATOR);
            assertThat(ProtocolText.textOf(content, diagnostic.getRange())).isEqualTo("=");
            assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
            assertThat(diagnostic.getSource()).isEqualTo("sapl");
            assertThat(diagnostic.getRelatedInformation()).isNull();
        });
    }

    @Test
    @DisplayName("a policy without name reaches the editor with the problem of the catalogue, at the keyword policy")
    void whenPolicyHasNoNameThenTheEditorMarksTheKeywordPolicyWithTheProblemOfTheCatalogue() {
        val content  = "policy permit";
        val document = new SAPLParsedDocument(URI, content);

        val diagnostics = provider.provideDiagnostics(document);

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.getCode().getLeft()).isEqualTo(Problem.MISSING_POLICY_NAME.identifier());
            assertThat(diagnostic.getMessage().getLeft()).isEqualTo(document.getDiagnostics().getFirst().message());
            assertThat(ProtocolText.textOf(content, diagnostic.getRange())).isEqualTo("policy");
        });
    }

    @Test
    @DisplayName("the editor shows what the compiler reports beyond the syntax: a name that is not imported")
    void whenNameIsNotImportedThenTheEditorMarksTheNameAsTheCompilerReportsIt() {
        val content = """
                policy "p" permit
                  foo();""";

        val diagnostics = provider.provideDiagnostics(new SAPLParsedDocument(URI, content));

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.getCode().getLeft()).isEqualTo(Problem.NAME_NOT_IMPORTED.identifier());
            assertThat(ProtocolText.textOf(content, diagnostic.getRange())).isEqualTo("foo");
        });
    }

    @Test
    @DisplayName("a valid document has no diagnostics")
    void whenDocumentIsValidThenNoDiagnosticIsPublished() {
        val content = """
                policy "p"
                permit
                    action == "read";""";

        val diagnostics = provider.provideDiagnostics(new SAPLParsedDocument(URI, content));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    @DisplayName("a further region of a problem reaches the editor as related information at its place, with its label")
    void whenProblemHasAFurtherRegionThenTheEditorGetsItAsRelatedInformation() {
        val content = """
                import clock.now
                import time.now
                policy "p" permit""";

        val diagnostics = provider.provideDiagnostics(new SAPLParsedDocument(URI, content));

        assertThat(diagnostics).singleElement()
                .extracting(Diagnostic::getRelatedInformation, list(DiagnosticRelatedInformation.class)).singleElement()
                .satisfies(related -> {
                    assertThat(related.getMessage()).isEqualTo("first imported here");
                    assertThat(related.getLocation().getUri()).isEqualTo(URI);
                    assertThat(ProtocolText.textOf(content, related.getLocation().getRange())).isEqualTo("clock.now");
                });
    }

    static Stream<Arguments> documentsWhoseProblemHasNoText() {
        return Stream.of(arguments("a document that holds only a comment", "// access rules for the ward\n"),
                arguments("a document that holds only a schema statement", "subject schema { \"type\": \"object\" }"),
                arguments("a document that holds only a comment and no line break", "// only a comment"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentsWhoseProblemHasNoText")
    @DisplayName("a diagnostic always marks at least one visible character, so that the editor shows it")
    void whenProblemHasAnEmptyRegionThenTheDiagnosticStillMarksAVisibleCharacter(String description, String content) {
        val diagnostics = provider.provideDiagnostics(new SAPLParsedDocument(URI, content));

        assertThat(diagnostics).isNotEmpty()
                .allSatisfy(diagnostic -> assertThat(ProtocolText.textOf(content, diagnostic.getRange())).isNotBlank());
    }

}
