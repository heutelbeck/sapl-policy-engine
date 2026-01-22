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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("SAPLTest parser tests")
class SAPLTestParserTests {

    @Test
    @DisplayName("minimal document parses without syntax errors")
    void whenParsingMinimalDocument_thenNoSyntaxErrors() {
        var document = """
                requirement "Arkham Access Control" {
                    scenario "basic permit"
                        when subject "investigator" attempts action "enter" on resource "library"
                        expect permit;
                }
                """;
        var errors   = parseAndCollectErrors(document);
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("multiple requirements parse without syntax errors")
    void whenParsingMultipleRequirements_thenNoSyntaxErrors() {
        var document = """
                requirement "R'lyeh Access" {
                    scenario "dreamer denied"
                        when subject "dreamer" attempts action "wake" on resource "cthulhu"
                        expect deny;
                }
                requirement "Miskatonic Library" {
                    scenario "student permitted"
                        when subject "student" attempts action "read" on resource "necronomicon"
                        expect permit;
                }
                """;
        var errors   = parseAndCollectErrors(document);
        assertThat(errors).isEmpty();
    }

    static Stream<Arguments> documentSpecifications() {
        return Stream.of(arguments("single document (unit test)", """
                requirement "Test" {
                    given
                        - document "elder_policy"
                    scenario "test"
                        when "cultist" attempts "summon" on "shoggoth"
                        expect permit;
                }
                """), arguments("multiple documents (integration test)", """
                requirement "Test" {
                    given
                        - documents "policy1", "policy2", "policy3"
                    scenario "test"
                        when "cultist" attempts "summon" on "shoggoth"
                        expect permit;
                }
                """), arguments("single document in documents syntax", """
                requirement "Test" {
                    given
                        - documents "single_policy"
                    scenario "test"
                        when "cultist" attempts "summon" on "shoggoth"
                        expect permit;
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentSpecifications")
    @DisplayName("document specifications parse without syntax errors")
    void whenParsingDocumentSpecification_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Document spec '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> pdpConfigurations() {
        return Stream.of(arguments("variables", """
                requirement "Test" {
                    given
                        - variables { "maxTentacles": 8, "dimension": "outer" }
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("priority deny algorithm", """
                requirement "Test" {
                    given
                        - priority deny or deny errors propagate
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("priority permit algorithm", """
                requirement "Test" {
                    given
                        - priority permit or permit errors propagate
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("unique algorithm", """
                requirement "Test" {
                    given
                        - unique or abstain errors propagate
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("first or deny algorithm", """
                requirement "Test" {
                    given
                        - first or deny errors abstain
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("first or permit algorithm", """
                requirement "Test" {
                    given
                        - first or permit errors abstain
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pdpConfigurations")
    @DisplayName("PDP configurations parse without syntax errors")
    void whenParsingPdpConfiguration_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("PDP security '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> mockDefinitions() {
        return Stream.of(arguments("simple function mock (no params)", """
                requirement "Test" {
                    given
                        - function eldritch.summon() maps to true
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("function mock with parameter matchers", """
                requirement "Test" {
                    given
                        - function eldritch.summon(any, "specific", 42) maps to true
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("function mock with matching parameter", """
                requirement "Test" {
                    given
                        - function eldritch.summon(matching text) maps to true
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("function mock returning error", """
                requirement "Test" {
                    given
                        - function eldritch.summon() maps to error
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect indeterminate;
                }
                """), arguments("function mock returning error with message", """
                requirement "Test" {
                    given
                        - function eldritch.summon() maps to error("The stars are not right")
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect indeterminate;
                }
                """), arguments("environment attribute mock (no entity)", """
                requirement "Test" {
                    given
                        - attribute "cosmosAlign" <cosmos.alignment> emits "chaotic"
                    scenario "test"
                        when "cultist" attempts "check" on "stars"
                        expect permit;
                }
                """), arguments("environment attribute mock without initial value", """
                requirement "Test" {
                    given
                        - attribute "phaseMock" <cosmos.phase>
                    scenario "test"
                        when "cultist" attempts "observe" on "moon"
                        expect permit;
                }
                """), arguments("entity attribute mock with any matcher", """
                requirement "Test" {
                    given
                        - attribute "alignMock" any.<cosmos.alignment> emits "chaotic"
                    scenario "test"
                        when "cultist" attempts "check" on "stars"
                        expect permit;
                }
                """), arguments("entity attribute mock with specific matcher", """
                requirement "Test" {
                    given
                        - attribute "alignMock" {"cosmos": true}.<cosmos.alignment> emits "chaotic"
                    scenario "test"
                        when "cultist" attempts "check" on "stars"
                        expect permit;
                }
                """), arguments("entity attribute mock with parameters", """
                requirement "Test" {
                    given
                        - attribute "alignMock" any.<cosmos.alignment(any, 42)> emits "chaotic"
                    scenario "test"
                        when "cultist" attempts "check" on "stars"
                        expect permit;
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mockDefinitions")
    @DisplayName("mock definitions parse without syntax errors")
    void whenParsingMockDefinition_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Mock '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> authorizationSubscriptions() {
        return Stream.of(arguments("minimal subscription", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "summon" on "shoggoth"
                        expect permit;
                }
                """), arguments("subscription with explicit keywords", """
                requirement "Test" {
                    scenario "test"
                        when subject "cultist" attempts action "summon" on resource "shoggoth"
                        expect permit;
                }
                """), arguments("subscription with environment", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "summon" on "shoggoth" in environment { "moon": "full" }
                        expect permit;
                }
                """), arguments("subscription with complex json subject", """
                requirement "Test" {
                    scenario "test"
                        when { "name": "cultist", "rank": "acolyte", "years": 13 } attempts "summon" on "shoggoth"
                        expect permit;
                }
                """), arguments("subscription with array action", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts ["summon", "invoke", "call"] on "shoggoth"
                        expect permit;
                }
                """), arguments("subscription with null resource", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "meditate" on null
                        expect permit;
                }
                """), arguments("subscription with number values", """
                requirement "Test" {
                    scenario "test"
                        when 42 attempts 13 on 666
                        expect permit;
                }
                """), arguments("subscription with boolean values", """
                requirement "Test" {
                    scenario "test"
                        when true attempts false on true
                        expect permit;
                }
                """), arguments("subscription with undefined", """
                requirement "Test" {
                    scenario "test"
                        when undefined attempts "test" on undefined
                        expect not-applicable;
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("authorizationSubscriptions")
    @DisplayName("authorization subscriptions parse without syntax errors")
    void whenParsingAuthorizationSubscription_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Subscription '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> simpleExpectations() {
        return Stream.of(arguments("permit", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "enter" on "temple"
                        expect permit;
                }
                """), arguments("deny", """
                requirement "Test" {
                    scenario "test"
                        when "outsider" attempts "enter" on "temple"
                        expect deny;
                }
                """), arguments("indeterminate", """
                requirement "Test" {
                    scenario "test"
                        when "unknown" attempts "enter" on "void"
                        expect indeterminate;
                }
                """), arguments("not-applicable", """
                requirement "Test" {
                    scenario "test"
                        when "tourist" attempts "visit" on "museum"
                        expect not-applicable;
                }
                """), arguments("permit with obligations", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "enter" on "temple"
                        expect permit with obligations { "type": "log" }, { "type": "notify" };
                }
                """), arguments("permit with resource", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "read" on "tome"
                        expect permit with resource { "content": "redacted" };
                }
                """), arguments("permit with advice", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "enter" on "temple"
                        expect permit with advice { "message": "beware the tentacles" };
                }
                """), arguments("permit with all components", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "enter" on "temple"
                        expect permit
                            with obligations { "type": "log" }
                            with resource { "access": "granted" }
                            with advice { "hint": "proceed carefully" };
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("simpleExpectations")
    @DisplayName("simple expectations parse without syntax errors")
    void whenParsingSimpleExpectation_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Expectation '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> matcherExpectations() {
        return Stream.of(arguments("any decision", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "enter" on "temple"
                        expect decision any;
                }
                """), arguments("is permit", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "enter" on "temple"
                        expect decision is permit;
                }
                """), arguments("is deny", """
                requirement "Test" {
                    scenario "test"
                        when "outsider" attempts "enter" on "temple"
                        expect decision is deny;
                }
                """), arguments("with obligation", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "enter" on "temple"
                        expect decision is permit, with obligation;
                }
                """), arguments("with obligation equals", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "enter" on "temple"
                        expect decision is permit, with obligation equals { "type": "log" };
                }
                """), arguments("with obligation matching", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "enter" on "temple"
                        expect decision is permit, with obligation matching object;
                }
                """), arguments("with obligation containing key", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "enter" on "temple"
                        expect decision is permit, with obligation containing key "type";
                }
                """), arguments("with obligation containing key and value", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "enter" on "temple"
                        expect decision is permit, with obligation containing key "type" with value matching text "log";
                }
                """), arguments("with advice", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "enter" on "temple"
                        expect decision is permit, with advice;
                }
                """), arguments("with resource", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "read" on "tome"
                        expect decision is permit, with resource;
                }
                """), arguments("with resource equals", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "read" on "tome"
                        expect decision is permit, with resource equals { "content": "forbidden knowledge" };
                }
                """), arguments("with resource matching", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "read" on "tome"
                        expect decision is permit, with resource matching object;
                }
                """), arguments("multiple matchers", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "read" on "tome"
                        expect decision is permit, with obligation, with resource, with advice;
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matcherExpectations")
    @DisplayName("matcher expectations parse without syntax errors")
    void whenParsingMatcherExpectation_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Matcher expectation '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> jsonNodeMatchers() {
        return Stream.of(arguments("null matcher", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "check" on "void"
                        expect decision is permit, with resource matching null;
                }
                """), arguments("text matcher", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "read" on "inscription"
                        expect decision is permit, with resource matching text;
                }
                """), arguments("text with specific value", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "read" on "inscription"
                        expect decision is permit, with resource matching text "Ph'nglui mglw'nafh";
                }
                """), arguments("number matcher", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "count" on "tentacles"
                        expect decision is permit, with resource matching number;
                }
                """), arguments("number with specific value", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "count" on "tentacles"
                        expect decision is permit, with resource matching number 8;
                }
                """), arguments("boolean matcher", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "check" on "awakened"
                        expect decision is permit, with resource matching boolean;
                }
                """), arguments("boolean with true", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "check" on "awakened"
                        expect decision is permit, with resource matching boolean true;
                }
                """), arguments("boolean with false", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "check" on "awakened"
                        expect decision is permit, with resource matching boolean false;
                }
                """), arguments("array matcher", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "list" on "artifacts"
                        expect decision is permit, with resource matching array;
                }
                """), arguments("array with element matchers", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "list" on "artifacts"
                        expect decision is permit, with resource matching array where [text, number, boolean];
                }
                """), arguments("object matcher", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "describe" on "entity"
                        expect decision is permit, with resource matching object;
                }
                """), arguments("object with field matchers",
                """
                        requirement "Test" {
                            scenario "test"
                                when "cultist" attempts "describe" on "entity"
                                expect decision is permit, with resource matching object where { "name" is text and "tentacles" is number };
                        }
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonNodeMatchers")
    @DisplayName("JSON node matchers parse without syntax errors")
    void whenParsingJsonNodeMatcher_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("JSON node matcher '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> stringMatchers() {
        return Stream.of(arguments("string is null", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "read" on "void"
                        expect decision is permit, with resource matching text null;
                }
                """), arguments("string is blank", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "read" on "empty"
                        expect decision is permit, with resource matching text blank;
                }
                """), arguments("string is empty", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "read" on "nothing"
                        expect decision is permit, with resource matching text empty;
                }
                """), arguments("string is null or empty", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "read" on "maybe"
                        expect decision is permit, with resource matching text null-or-empty;
                }
                """), arguments("string is null or blank", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "read" on "perhaps"
                        expect decision is permit, with resource matching text null-or-blank;
                }
                """),
                arguments("string equals with compressed whitespace",
                        """
                                requirement "Test" {
                                    scenario "test"
                                        when "cultist" attempts "read" on "text"
                                        expect decision is permit, with resource matching text equal to "hello world" with compressed whitespace;
                                }
                                """),
                arguments("string equals ignoring case",
                        """
                                requirement "Test" {
                                    scenario "test"
                                        when "cultist" attempts "read" on "text"
                                        expect decision is permit, with resource matching text equal to "CTHULHU" case-insensitive;
                                }
                                """),
                arguments("string with regex", """
                        requirement "Test" {
                            scenario "test"
                                when "cultist" attempts "match" on "pattern"
                                expect decision is permit, with resource matching text with regex "^[a-z]+$";
                        }
                        """), arguments("string starting with", """
                        requirement "Test" {
                            scenario "test"
                                when "cultist" attempts "check" on "name"
                                expect decision is permit, with resource matching text starting with "Ph'nglui";
                        }
                        """),
                arguments("string starting with case insensitive",
                        """
                                requirement "Test" {
                                    scenario "test"
                                        when "cultist" attempts "check" on "name"
                                        expect decision is permit, with resource matching text starting with "PH'NGLUI" case-insensitive;
                                }
                                """),
                arguments("string ending with", """
                        requirement "Test" {
                            scenario "test"
                                when "cultist" attempts "check" on "name"
                                expect decision is permit, with resource matching text ending with "fhtagn";
                        }
                        """),
                arguments("string ending with case insensitive",
                        """
                                requirement "Test" {
                                    scenario "test"
                                        when "cultist" attempts "check" on "name"
                                        expect decision is permit, with resource matching text ending with "FHTAGN" case-insensitive;
                                }
                                """),
                arguments("string containing", """
                        requirement "Test" {
                            scenario "test"
                                when "cultist" attempts "search" on "text"
                                expect decision is permit, with resource matching text containing "Cthulhu";
                        }
                        """),
                arguments("string containing case insensitive",
                        """
                                requirement "Test" {
                                    scenario "test"
                                        when "cultist" attempts "search" on "text"
                                        expect decision is permit, with resource matching text containing "CTHULHU" case-insensitive;
                                }
                                """),
                arguments("string containing in order",
                        """
                                requirement "Test" {
                                    scenario "test"
                                        when "cultist" attempts "search" on "text"
                                        expect decision is permit, with resource matching text containing stream "Ph'nglui", "mglw'nafh", "Cthulhu" in order;
                                }
                                """),
                arguments("string with length", """
                        requirement "Test" {
                            scenario "test"
                                when "cultist" attempts "measure" on "text"
                                expect decision is permit, with resource matching text with length 42;
                        }
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stringMatchers")
    @DisplayName("string matchers parse without syntax errors")
    void whenParsingStringMatcher_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("String matcher '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> streamExpectations() {
        return Stream.of(arguments("permit once", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect
                            - permit once;
                }
                """), arguments("deny multiple times", """
                requirement "Test" {
                    scenario "test"
                        when "outsider" attempts "enter" on "temple"
                        expect
                            - deny 3 times;
                }
                """), arguments("mixed decisions", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect
                            - permit once
                            - deny once
                            - indeterminate once;
                }
                """), arguments("with next full decision", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "enter" on "temple"
                        expect
                            - permit with obligations { "log": true };
                }
                """), arguments("with next matcher decision", """
                requirement "Test" {
                    scenario "test"
                        when "cultist" attempts "enter" on "temple"
                        expect
                            - decision is permit, with obligation;
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("streamExpectations")
    @DisplayName("stream expectations parse without syntax errors")
    void whenParsingStreamExpectation_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Stream expectation '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> thenBlockExamples() {
        return Stream.of(arguments("simple then-expect sequence", """
                requirement "Test" {
                    given
                        - attribute "statusMock" <user.status> emits "active"
                    scenario "status changes"
                        when "user" attempts "access" on "resource"
                        expect permit
                        then
                            - attribute "statusMock" emits "inactive"
                        expect deny;
                }
                """), arguments("multiple then-expect sequences", """
                requirement "Test" {
                    given
                        - attribute "phaseMock" any.<moon.phase> emits "new"
                    scenario "moon cycle"
                        when "werewolf" attempts "transform" on "self"
                        expect deny
                        then
                            - attribute "phaseMock" emits "waxing"
                        expect deny
                        then
                            - attribute "phaseMock" emits "full"
                        expect permit;
                }
                """), arguments("then with multiple attribute emits", """
                requirement "Test" {
                    given
                        - attribute "mockA" <pip.attrA> emits true
                        - attribute "mockB" <pip.attrB> emits false
                    scenario "multiple attrs"
                        when "user" attempts "action" on "resource"
                        expect deny
                        then
                            - attribute "mockA" emits false
                            - attribute "mockB" emits true
                        expect permit;
                }
                """), arguments("then with error value", """
                requirement "Test" {
                    given
                        - attribute "errorMock" <pip.unstable> emits "stable"
                    scenario "error scenario"
                        when "user" attempts "action" on "resource"
                        expect permit
                        then
                            - attribute "errorMock" emits error("connection lost")
                        expect indeterminate;
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("thenBlockExamples")
    @DisplayName("then blocks parse without syntax errors")
    void whenParsingThenBlock_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Then block '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> verifyBlockExamples() {
        return Stream.of(arguments("function verification (once)", """
                requirement "Test" {
                    given
                        - function time.dayOfWeek() maps to "MONDAY"
                    scenario "check day"
                        when "user" attempts "access" on "resource"
                        expect permit
                        verify
                            - function time.dayOfWeek() is called once;
                }
                """), arguments("function verification (N times)", """
                requirement "Test" {
                    given
                        - function logger.log(any) maps to true
                    scenario "logging"
                        when "user" attempts "action" on "resource"
                        expect permit
                        verify
                            - function logger.log(any) is called 3 times;
                }
                """), arguments("attribute verification (environment)", """
                requirement "Test" {
                    given
                        - attribute "timeMock" <time.now> emits "2025-01-06"
                    scenario "time check"
                        when "user" attempts "access" on "resource"
                        expect permit
                        verify
                            - attribute <time.now> is called once;
                }
                """), arguments("attribute verification (entity)", """
                requirement "Test" {
                    given
                        - attribute "locMock" any.<user.location> emits "Berlin"
                    scenario "location check"
                        when "user" attempts "access" on "resource"
                        expect permit
                        verify
                            - attribute any.<user.location> is called 2 times;
                }
                """), arguments("multiple verifications", """
                requirement "Test" {
                    given
                        - function time.dayOfWeek() maps to "MONDAY"
                        - function logger.log(any) maps to true
                        - attribute "timeMock" <time.now> emits "2025-01-06"
                    scenario "comprehensive"
                        when "user" attempts "action" on "resource"
                        expect permit
                        verify
                            - function time.dayOfWeek() is called once
                            - function logger.log(any) is called 2 times
                            - attribute <time.now> is called once;
                }
                """), arguments("verify with then blocks", """
                requirement "Test" {
                    given
                        - attribute "statusMock" <user.status> emits "active"
                    scenario "status changes"
                        when "user" attempts "access" on "resource"
                        expect permit
                        then
                            - attribute "statusMock" emits "inactive"
                        expect deny
                        verify
                            - attribute <user.status> is called 2 times;
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("verifyBlockExamples")
    @DisplayName("verify blocks parse without syntax errors")
    void whenParsingVerifyBlock_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Verify block '%s' should parse without errors", description).isEmpty();
    }

    @Test
    @DisplayName("scenario-level given blocks parse without syntax errors")
    void whenParsingScenarioLevelGiven_thenNoSyntaxErrors() {
        var document = """
                requirement "Test" {
                    given
                        - document "base_policy"
                    scenario "with scenario given"
                        given
                            - function eldritch.check() maps to true
                        when "cultist" attempts "enter" on "temple"
                        expect permit;
                }
                """;
        var errors   = parseAndCollectErrors(document);
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("complex combined document parses without syntax errors")
    void whenParsingComplexDocument_thenNoSyntaxErrors() {
        var document = """
                requirement "Miskatonic University Access Control" {
                    given
                        - documents "base_policy", "library_policy"
                        - variables { "institution": "Miskatonic", "founded": 1797 }
                        - priority deny or deny errors propagate
                        - function library.checkAccess() maps to true
                        - attribute "statusMock" <student.status> emits "enrolled"

                    scenario "student accesses restricted section"
                        given
                            - function library.hasPermission(any, "restricted") maps to true
                        when subject { "name": "Herbert West", "role": "student", "department": "medicine" }
                            attempts action { "type": "read", "section": "restricted" }
                            on resource { "title": "Necronomicon", "location": "restricted_vault" }
                            in environment { "time": "night", "supervised": false }
                        expect decision is permit,
                            with obligation containing key "type" with value matching text "log",
                            with advice
                        verify
                            - function library.checkAccess() is called once;

                    scenario "outsider denied access"
                        when subject { "name": "Unknown Visitor", "role": "visitor" }
                            attempts "browse" on "restricted_section"
                        expect deny with obligations { "type": "alert", "level": "high" };

                    scenario "streaming access check"
                        when "night_watchman" attempts "patrol" on "campus"
                        expect
                            - permit once
                            - deny once
                            - permit once;
                }

                requirement "R'lyeh Deep One Protocol" {
                    given
                        - document "deep_one_policy"
                        - function depth.check() maps to true

                    scenario "surface access"
                        when "diver" attempts "descend" on "outer_reef"
                        expect permit;

                    scenario "deep access denied"
                        when "explorer" attempts "descend" on "rlyeh_coordinates"
                        expect decision is deny, with resource matching object where { "warning" is text and "depth" is number };
                }
                """;
        var errors   = parseAndCollectErrors(document);
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("document without given block parses without syntax errors")
    void whenParsingWithoutGivenBlock_thenNoSyntaxErrors() {
        var document = """
                requirement "Minimal Test" {
                    scenario "uses all documents with default algorithm"
                        when "user" attempts "read" on "resource"
                        expect permit;

                    scenario "another test"
                        when "admin" attempts "delete" on "resource"
                        expect deny;
                }
                """;
        var errors   = parseAndCollectErrors(document);
        assertThat(errors).isEmpty();
    }

    static Stream<Arguments> invalidSyntaxSnippets() {
        return Stream.of(arguments("empty document", ""),
                arguments("missing requirement name",
                        "requirement { scenario \"test\" when \"a\" attempts \"b\" on \"c\" expect permit; }"),
                arguments("missing scenario name", """
                        requirement "Test" {
                            scenario when "a" attempts "b" on "c" expect permit;
                        }
                        """), arguments("missing when clause", """
                        requirement "Test" {
                            scenario "test" expect permit;
                        }
                        """), arguments("missing expect clause", """
                        requirement "Test" {
                            scenario "test" when "a" attempts "b" on "c";
                        }
                        """), arguments("missing semicolon", """
                        requirement "Test" {
                            scenario "test" when "a" attempts "b" on "c" expect permit
                        }
                        """), arguments("invalid decision type", """
                        requirement "Test" {
                            scenario "test" when "a" attempts "b" on "c" expect allowed;
                        }
                        """), arguments("incomplete given step", """
                        requirement "Test" {
                            given
                                - function
                            scenario "test" when "a" attempts "b" on "c" expect permit;
                        }
                        """), arguments("unclosed brace in requirement", """
                        requirement "Test" {
                            scenario "test" when "a" attempts "b" on "c" expect permit;
                        """), arguments("unclosed string literal", """
                        requirement "Test {
                            scenario "test" when "a" attempts "b" on "c" expect permit;
                        }
                        """), arguments("invalid combining algorithm", """
                        requirement "Test" {
                            given
                                - invalid-algorithm
                            scenario "test" when "a" attempts "b" on "c" expect permit;
                        }
                        """));
    }

    @ParameterizedTest(name = "reject: {0}")
    @MethodSource("invalidSyntaxSnippets")
    @DisplayName("invalid syntax produces parse errors")
    void whenParsingInvalidSyntax_thenSyntaxErrorsAreReported(String description, String snippet) {
        var errors = parseAndCollectErrors(snippet);
        assertThat(errors).as("Parsing invalid SAPLTest (%s) should produce syntax errors", description).isNotEmpty();
    }

    // Helper Methods

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
