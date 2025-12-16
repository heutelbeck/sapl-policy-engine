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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Comprehensive test suite for the ANTLR4 SAPLTest parser.
 * Tests cover all grammar rules and labeled alternatives.
 */
class SAPLTestParserTests {

    // ========================================================================
    // CATEGORY 1: Minimal Valid Documents
    // ========================================================================

    @Test
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

    // ========================================================================
    // CATEGORY 2: Document Specifications
    // ========================================================================

    static Stream<Arguments> documentSpecifications() {
        return Stream.of(arguments("single policy", """
                requirement "Test" {
                    given
                        - policy "elder_policy"
                    scenario "test"
                        when "cultist" attempts "summon" on "shoggoth"
                        expect permit;
                }
                """), arguments("document set", """
                requirement "Test" {
                    given
                        - set "eldritch_policies"
                    scenario "test"
                        when "cultist" attempts "summon" on "shoggoth"
                        expect permit;
                }
                """), arguments("multiple policies", """
                requirement "Test" {
                    given
                        - policies "policy1", "policy2", "policy3"
                    scenario "test"
                        when "cultist" attempts "summon" on "shoggoth"
                        expect permit;
                }
                """), arguments("multiple policies with pdp config", """
                requirement "Test" {
                    given
                        - policies "policy1", "policy2" with pdp configuration "pdp.json"
                    scenario "test"
                        when "cultist" attempts "summon" on "shoggoth"
                        expect permit;
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentSpecifications")
    void whenParsingDocumentSpecification_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Document spec '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 3: PDP Configuration
    // ========================================================================

    static Stream<Arguments> pdpConfigurations() {
        return Stream.of(arguments("pdp variables", """
                requirement "Test" {
                    given
                        - pdp variables { "maxTentacles": 8, "dimension": "outer" }
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("deny-overrides algorithm", """
                requirement "Test" {
                    given
                        - pdp combining-algorithm deny-overrides
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("permit-overrides algorithm", """
                requirement "Test" {
                    given
                        - pdp combining-algorithm permit-overrides
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("only-one-applicable algorithm", """
                requirement "Test" {
                    given
                        - pdp combining-algorithm only-one-applicable
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("deny-unless-permit algorithm", """
                requirement "Test" {
                    given
                        - pdp combining-algorithm deny-unless-permit
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("permit-unless-deny algorithm", """
                requirement "Test" {
                    given
                        - pdp combining-algorithm permit-unless-deny
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("environment", """
                requirement "Test" {
                    given
                        - environment { "stars": "right", "moon": "full" }
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pdpConfigurations")
    void whenParsingPdpConfiguration_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("PDP config '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 4: Import Statements
    // ========================================================================

    static Stream<Arguments> importStatements() {
        return Stream.of(arguments("pip import", """
                requirement "Test" {
                    given
                        - pip "io.sapl.pip.EldritchPip"
                    scenario "test"
                        when "cultist" attempts "query" on "cosmos"
                        expect permit;
                }
                """), arguments("static pip import", """
                requirement "Test" {
                    given
                        - static-pip "io.sapl.pip.StaticEldritchPip"
                    scenario "test"
                        when "cultist" attempts "query" on "cosmos"
                        expect permit;
                }
                """), arguments("function library import", """
                requirement "Test" {
                    given
                        - function-library "io.sapl.functions.CosmicFunctions"
                    scenario "test"
                        when "cultist" attempts "calculate" on "angles"
                        expect permit;
                }
                """), arguments("static function library import", """
                requirement "Test" {
                    given
                        - static-function-library "io.sapl.functions.StaticCosmicFunctions"
                    scenario "test"
                        when "cultist" attempts "calculate" on "angles"
                        expect permit;
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("importStatements")
    void whenParsingImportStatement_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Import '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 5: Mock Definitions
    // ========================================================================

    static Stream<Arguments> mockDefinitions() {
        return Stream.of(arguments("simple function mock", """
                requirement "Test" {
                    given
                        - function "eldritch.summon" maps to true
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("function mock with call count", """
                requirement "Test" {
                    given
                        - function "eldritch.summon" maps to true is called once
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("function mock with multiple call count", """
                requirement "Test" {
                    given
                        - function "eldritch.summon" maps to true is called 5 times
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("function mock with parameter matchers", """
                requirement "Test" {
                    given
                        - function "eldritch.summon" of (any, "specific", 42) maps to true
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("function mock with matching parameter", """
                requirement "Test" {
                    given
                        - function "eldritch.summon" of (matching text) maps to true
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect permit;
                }
                """), arguments("function mock returning error", """
                requirement "Test" {
                    given
                        - function "eldritch.summon" maps to error
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect indeterminate;
                }
                """), arguments("function mock returning error with message", """
                requirement "Test" {
                    given
                        - function "eldritch.summon" maps to error("The stars are not right")
                    scenario "test"
                        when "cultist" attempts "invoke" on "ritual"
                        expect indeterminate;
                }
                """), arguments("function stream mock", """
                requirement "Test" {
                    given
                        - function "eldritch.visions" maps to stream true, false, true
                    scenario "test"
                        when "seer" attempts "divine" on "future"
                        expect permit;
                }
                """), arguments("simple attribute mock", """
                requirement "Test" {
                    given
                        - attribute "cosmos.alignment" emits "chaotic"
                    scenario "test"
                        when "cultist" attempts "check" on "stars"
                        expect permit;
                }
                """), arguments("attribute mock with multiple values", """
                requirement "Test" {
                    given
                        - attribute "cosmos.phase" emits "waxing", "full", "waning"
                    scenario "test"
                        when "cultist" attempts "observe" on "moon"
                        expect permit;
                }
                """), arguments("attribute mock with timing", """
                requirement "Test" {
                    given
                        - attribute "cosmos.alignment" emits "chaotic" with timing "PT1S"
                    scenario "test"
                        when "cultist" attempts "check" on "stars"
                        expect permit;
                }
                """), arguments("attribute mock with parent matcher", """
                requirement "Test" {
                    given
                        - attribute "cosmos.alignment" of <any> emits "chaotic"
                    scenario "test"
                        when "cultist" attempts "check" on "stars"
                        expect permit;
                }
                """), arguments("attribute mock with parent and parameter matchers", """
                requirement "Test" {
                    given
                        - attribute "cosmos.alignment" of <"cosmos"> (any, 42) emits "chaotic"
                    scenario "test"
                        when "cultist" attempts "check" on "stars"
                        expect permit;
                }
                """), arguments("virtual time mock", """
                requirement "Test" {
                    given
                        - virtual-time
                    scenario "test"
                        when "cultist" attempts "wait" on "alignment"
                        expect permit;
                }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mockDefinitions")
    void whenParsingMockDefinition_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Mock '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 6: Authorization Subscription
    // ========================================================================

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
    void whenParsingAuthorizationSubscription_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Subscription '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 7: Simple Expectations
    // ========================================================================

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
    void whenParsingSimpleExpectation_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Expectation '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 8: Matcher Expectations
    // ========================================================================

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
    void whenParsingMatcherExpectation_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Matcher expectation '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 9: JSON Node Matchers
    // ========================================================================

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
    void whenParsingJsonNodeMatcher_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("JSON node matcher '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 10: String Matchers
    // ========================================================================

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
    void whenParsingStringMatcher_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("String matcher '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 11: Repeated Expectations
    // ========================================================================

    static Stream<Arguments> repeatedExpectations() {
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
                """), arguments("with no-event duration", """
                requirement "Test" {
                    given
                        - virtual-time
                    scenario "test"
                        when "cultist" attempts "wait" on "portal"
                        expect
                            - no-event for "PT5S";
                }
                """), arguments("expect and then blocks", """
                requirement "Test" {
                    given
                        - virtual-time
                        - attribute "cosmos.phase" emits "dark"
                    scenario "test"
                        when "cultist" attempts "observe" on "stars"
                        expect
                            - permit once
                        then
                            - attribute "cosmos.phase" emits "aligned"
                        expect
                            - deny once;
                }
                """), arguments("with wait adjustment", """
                requirement "Test" {
                    given
                        - virtual-time
                    scenario "test"
                        when "cultist" attempts "wait" on "alignment"
                        expect
                            - permit once
                        then
                            - wait "PT10S"
                        expect
                            - deny once;
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
    @MethodSource("repeatedExpectations")
    void whenParsingRepeatedExpectation_thenNoSyntaxErrors(String description, String document) {
        var errors = parseAndCollectErrors(document);
        assertThat(errors).as("Repeated expectation '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 12: Scenario-Level Given
    // ========================================================================

    @Test
    void whenParsingScenarioLevelGiven_thenNoSyntaxErrors() {
        var document = """
                requirement "Test" {
                    given
                        - policy "base_policy"
                    scenario "with scenario given"
                        given
                            - function "eldritch.check" maps to true
                        when "cultist" attempts "enter" on "temple"
                        expect permit;
                }
                """;
        var errors   = parseAndCollectErrors(document);
        assertThat(errors).isEmpty();
    }

    // ========================================================================
    // CATEGORY 13: Complex Combined Documents
    // ========================================================================

    @Test
    void whenParsingComplexDocument_thenNoSyntaxErrors() {
        var document = """
                requirement "Miskatonic University Access Control" {
                    given
                        - set "university_policies"
                        - pdp variables { "institution": "Miskatonic", "founded": 1797 }
                        - pdp combining-algorithm deny-overrides
                        - environment { "location": "Arkham", "state": "Massachusetts" }
                        - pip "io.sapl.pip.AcademicPip"
                        - function-library "io.sapl.functions.LibraryFunctions"
                        - function "library.checkAccess" maps to true is called once
                        - attribute "student.status" emits "enrolled", "suspended", "graduated"
                        - virtual-time

                    scenario "student accesses restricted section"
                        given
                            - function "library.hasPermission" of (any, "restricted") maps to true
                        when subject { "name": "Herbert West", "role": "student", "department": "medicine" }
                            attempts action { "type": "read", "section": "restricted" }
                            on resource { "title": "Necronomicon", "location": "restricted_vault" }
                            in environment { "time": "night", "supervised": false }
                        expect decision is permit,
                            with obligation containing key "type" with value matching text "log",
                            with advice;

                    scenario "outsider denied access"
                        when subject { "name": "Unknown Visitor", "role": "visitor" }
                            attempts "browse" on "restricted_section"
                        expect deny with obligations { "type": "alert", "level": "high" };

                    scenario "streaming access check"
                        when "night_watchman" attempts "patrol" on "campus"
                        expect
                            - permit once
                        then
                            - wait "PT1H"
                            - attribute "campus.status" emits "lockdown"
                        expect
                            - deny once
                        then
                            - wait "PT30M"
                        expect
                            - permit once;
                }

                requirement "R'lyeh Deep One Protocol" {
                    given
                        - policy "deep_one_policy"
                        - function "depth.check" maps to stream true, false, error("Too deep")

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

    // ========================================================================
    // CATEGORY 14: Invalid Syntax Tests
    // ========================================================================

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
                                - pdp combining-algorithm invalid-algorithm
                            scenario "test" when "a" attempts "b" on "c" expect permit;
                        }
                        """));
    }

    @ParameterizedTest(name = "reject: {0}")
    @MethodSource("invalidSyntaxSnippets")
    void whenParsingInvalidSyntax_thenSyntaxErrorsAreReported(String description, String snippet) {
        var errors = parseAndCollectErrors(snippet);
        assertThat(errors).as("Parsing invalid SAPLTest (%s) should produce syntax errors", description).isNotEmpty();
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

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
