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
package io.sapl.lsp.sapltest;

import static org.assertj.core.api.Assertions.assertThat;

import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

class SAPLTestParsedDocumentTests {

    private static final String TEST_URI = "file:///test.sapltest";

    @Test
    void whenParseValidDocument_thenNoErrors() {
        var content = """
                requirement "Access Control" {
                    scenario "basic permit"
                        when "user" attempts "read" on "document"
                        expect permit;
                }
                """;

        var document = new SAPLTestParsedDocument(TEST_URI, content);

        assertThat(document.hasErrors()).isFalse();
        assertThat(document.getUri()).isEqualTo(TEST_URI);
        assertThat(document.getContent()).isEqualTo(content);
        assertThat(document.getParseTree()).isNotNull();
        assertThat(document.getSaplTestParseTree()).isNotNull();
        assertThat(document.getTokens()).isNotEmpty();
    }

    @Test
    void whenParseDocumentWithSyntaxError_thenHasParseErrors() {
        var content = """
                requirement "Test" {
                    scenario "Missing closing brace"
                """;

        var document = new SAPLTestParsedDocument(TEST_URI, content);

        assertThat(document.hasErrors()).isTrue();
        assertThat(document.getParseErrors()).isNotEmpty();
    }

    @Test
    void whenParseDocumentWithMocks_thenParsesSuccessfully() {
        var content = """
                requirement "Mock Test" {
                    given
                        - document "testPolicy"
                        - function test.func() maps to "value"
                    scenario "with mocks"
                        when "user" attempts "read" on "doc"
                        expect permit;
                }
                """;

        var document = new SAPLTestParsedDocument(TEST_URI, content);

        assertThat(document.hasErrors()).isFalse();
        assertThat(document.getParseTree()).isNotNull();
    }

    @Test
    void whenParseDocumentWithExpectations_thenParsesSuccessfully() {
        var content = """
                requirement "Expectation Test" {
                    scenario "complex expectations"
                        when "user" attempts "read" on "doc"
                        expect decision is permit, with resource matching object;
                }
                """;

        var document = new SAPLTestParsedDocument(TEST_URI, content);

        assertThat(document.hasErrors()).isFalse();
    }

    @Test
    void whenGetTokens_thenReturnsAllTokens() {
        var content = """
                requirement "Simple" {
                    scenario "test"
                        when "x" attempts "y" on "z"
                        expect permit;
                }
                """;

        var document = new SAPLTestParsedDocument(TEST_URI, content);
        var tokens   = document.getTokens();

        assertThat(tokens).isNotEmpty();
        assertThat(tokens.stream().map(Token::getText)).contains("requirement", "scenario", "when", "expect", "permit");
    }

    @Test
    void whenParseDocumentWithStreamExpectation_thenParsesSuccessfully() {
        var content = """
                requirement "Stream Test" {
                    given
                        - document "testPolicy"
                        - attribute "timeMock" <time.now> emits "initial"
                    scenario "streaming test"
                        when "user" attempts "read" on "doc"
                        expect
                            - permit once
                        then
                            - attribute "timeMock" emits "changed"
                        expect
                            - deny once;
                }
                """;

        var document = new SAPLTestParsedDocument(TEST_URI, content);

        assertThat(document.hasErrors()).isFalse();
    }

}
