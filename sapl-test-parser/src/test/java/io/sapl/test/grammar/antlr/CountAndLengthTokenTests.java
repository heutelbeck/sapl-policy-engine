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
package io.sapl.test.grammar.antlr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Count and length positions accept only unsigned integers and support never/once")
class CountAndLengthTokenTests {

    static Stream<Arguments> malformedCountsAndLengths() {
        return Stream.of(arguments("negative repetition count", """
                requirement "Test" {
                    scenario "test"
                        when "a" attempts "b" on "c"
                        expect
                            - permit -3 times;
                }
                """), arguments("decimal repetition count", """
                requirement "Test" {
                    scenario "test"
                        when "a" attempts "b" on "c"
                        expect
                            - permit 2.5 times;
                }
                """), arguments("exponential repetition count", """
                requirement "Test" {
                    scenario "test"
                        when "a" attempts "b" on "c"
                        expect
                            - permit 1e2 times;
                }
                """), arguments("decimal string length", """
                requirement "Test" {
                    scenario "test"
                        when "a" attempts "b" on "c"
                        expect decision is permit, with resource matching text with length 2.5;
                }
                """), arguments("negative string length", """
                requirement "Test" {
                    scenario "test"
                        when "a" attempts "b" on "c"
                        expect decision is permit, with resource matching text with length -5;
                }
                """));
    }

    @ParameterizedTest(name = "reject: {0}")
    @MethodSource("malformedCountsAndLengths")
    @DisplayName("signed, decimal, and exponential counts and lengths are rejected at parse time")
    void whenParsingMalformedCountOrLength_thenSyntaxErrorsAreReported(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Malformed count/length (%s) must be rejected by the grammar", description).isNotEmpty();
    }

    static Stream<Arguments> validVerificationCounts() {
        return Stream.of(arguments("never called", """
                requirement "Test" {
                    given
                        - function audit.log() maps to true
                    scenario "test"
                        when "a" attempts "b" on "c"
                        expect permit
                        verify
                            - function audit.log() is never called;
                }
                """), arguments("called zero times", """
                requirement "Test" {
                    given
                        - function audit.log() maps to true
                    scenario "test"
                        when "a" attempts "b" on "c"
                        expect permit
                        verify
                            - function audit.log() is called 0 times;
                }
                """), arguments("called one time", """
                requirement "Test" {
                    given
                        - function audit.log() maps to true
                    scenario "test"
                        when "a" attempts "b" on "c"
                        expect permit
                        verify
                            - function audit.log() is called 1 times;
                }
                """), arguments("called once", """
                requirement "Test" {
                    given
                        - function audit.log() maps to true
                    scenario "test"
                        when "a" attempts "b" on "c"
                        expect permit
                        verify
                            - function audit.log() is called once;
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validVerificationCounts")
    @DisplayName("verification counts express never, once, and zero or more times")
    void whenParsingVerificationCount_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Verification count '%s' should parse without errors", description).isEmpty();
    }

    private List<String> parseAndCollectErrors(String input) {
        var errors      = new ArrayList<String>();
        var charStream  = CharStreams.fromString(input);
        var lexer       = new SAPLTestLexer(charStream);
        var tokenStream = new CommonTokenStream(lexer);
        var parser      = new SAPLTestParser(tokenStream);

        var errorListener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                    int charPositionInLine, String message, RecognitionException exception) {
                errors.add("line %d:%d %s".formatted(line, charPositionInLine, message));
            }
        };

        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        parser.saplTest();

        return errors;
    }

}
