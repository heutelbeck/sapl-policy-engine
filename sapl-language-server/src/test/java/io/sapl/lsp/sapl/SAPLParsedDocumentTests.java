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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.compiler.diagnostics.Problem;
import io.sapl.compiler.document.DocumentCompiler;
import lombok.val;

@DisplayName("SAPL parsed document")
class SAPLParsedDocumentTests {

    private static final String URI = "test.sapl";

    static Stream<Arguments> validDocuments() {
        return Stream.of(arguments("a policy without a body", """
                policy "test"
                permit
                """), arguments("a condition with a lazy operator", """
                policy "test"
                permit
                  subject == "admin" || action == "read";
                """), arguments("a policy set", """
                set "test policies"
                first or abstain

                policy "policy 1"
                permit
                  subject == "admin";

                policy "policy 2"
                deny
                """), arguments("a policy with every part", """
                policy "complex policy"
                permit
                  action == "read";
                  var user = subject.name;
                  user == "admin";
                obligation "log access"
                advice "consider caching"
                transform { "allowed": true }
                """));
    }

    @MethodSource("validDocuments")
    @ParameterizedTest(name = "{0}")
    @DisplayName("a valid document has no problem, and the editor gets its parse tree and its tokens")
    void whenDocumentIsValidThenItHasNoProblemAndItsTreeAndTokens(String description, String content) {
        val document = new SAPLParsedDocument(URI, content);

        assertThat(document).satisfies(parsed -> {
            assertThat(parsed.hasErrors()).isFalse();
            assertThat(parsed.isSyntaxValid()).isTrue();
            assertThat(parsed.getDiagnostics()).isEmpty();
            assertThat(parsed.getParseTree()).isNotNull();
            assertThat(parsed.getTokens()).isNotEmpty();
        });
    }

    @Test
    @DisplayName("a syntax error is a problem of the syntax")
    void whenDocumentHasASyntaxErrorThenItHasAProblemOfItsSyntax() {
        val document = new SAPLParsedDocument(URI, """
                policy
                permit
                """);

        assertThat(document).satisfies(parsed -> {
            assertThat(parsed.hasErrors()).isTrue();
            assertThat(parsed.isSyntaxValid()).isFalse();
            assertThat(parsed.getDiagnostics()).isNotEmpty();
        });
    }

    @Test
    @DisplayName("an attribute finder in a schema breaks a rule, and the syntax of the document has no problem")
    void whenAttributeFinderStandsInASchemaThenARuleIsBrokenAndTheSyntaxHasNoProblem() {
        val document = new SAPLParsedDocument(URI, """
                subject schema <time.now>
                policy "test"
                permit
                """);

        assertThat(document).satisfies(parsed -> {
            assertThat(parsed.isSyntaxValid()).isTrue();
            assertThat(parsed.getDiagnostics()).singleElement().extracting(Diagnostic::message).asString()
                    .contains("Attribute");
        });
    }

    static Stream<Arguments> documentsBeyondTheGuards() {
        return Stream.of(
                arguments("a flat expression beyond the default depth",
                        "policy \"test\" permit subject" + " + 1".repeat(256) + ";", Problem.EXPRESSION_TOO_DEEP),
                arguments("a document beyond the size limit", "a".repeat(DocumentCompiler.MAX_DOCUMENT_SIZE_BYTES + 1),
                        Problem.DOCUMENT_TOO_LARGE),
                arguments("a trojan source character", "policy \u202E\"test\" permit", Problem.BIDIRECTIONAL_CONTROL),
                arguments("deeply nested input",
                        "policy \"test\" permit " + "(".repeat(100_000) + "true" + ")".repeat(100_000),
                        Problem.NESTING_TOO_DEEP));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentsBeyondTheGuards")
    @DisplayName("a document beyond the guards of the engine is reported as the engine reports it, and the editor still gets a parse tree")
    void whenDocumentIsBeyondTheGuardsThenItIsReportedAndTheEditorStillGetsATree(String description, String content,
            Problem problem) {
        val document = new SAPLParsedDocument(URI, content);

        assertThat(document).satisfies(parsed -> {
            assertThat(parsed.getDiagnostics()).singleElement().extracting(Diagnostic::problem).isEqualTo(problem);
            assertThat(parsed.getParseTree()).isNotNull();
        });
    }

}
