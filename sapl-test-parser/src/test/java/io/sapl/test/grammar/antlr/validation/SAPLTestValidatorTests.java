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
package io.sapl.test.grammar.antlr.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.test.grammar.antlr.SAPLTestLexer;
import io.sapl.test.grammar.antlr.SAPLTestParser;
import io.sapl.test.grammar.antlr.SAPLTestParser.SaplTestContext;

@DisplayName("SAPLTest validator tests")
class SAPLTestValidatorTests {

    private final SAPLTestValidator validator = new SAPLTestValidator();

    @Test
    @DisplayName("valid document produces no errors")
    void whenValidDocument_thenNoErrors() {
        var document = """
                requirement "Cthulhu Access Control" {
                    scenario "tentacle access granted"
                        when subject "cultist" attempts action "summon" on resource "elder_god"
                        expect permit;
                }
                """;

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("duplicate requirement names produce error")
    void whenDuplicateRequirementNames_thenError() {
        var document = """
                requirement "Arkham Horror" {
                    scenario "first scenario"
                        when subject "investigator" attempts action "investigate" on resource "mansion"
                        expect permit;
                }
                requirement "Arkham Horror" {
                    scenario "second scenario"
                        when subject "cultist" attempts action "summon" on resource "shoggoth"
                        expect deny;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first()
                .satisfies(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_DUPLICATE_REQUIREMENT_NAME));
    }

    @Test
    @DisplayName("duplicate scenario names produce error")
    void whenDuplicateScenarioNames_thenError() {
        var document = """
                requirement "Miskatonic University" {
                    scenario "library access"
                        when subject "student" attempts action "read" on resource "necronomicon"
                        expect deny;
                    scenario "library access"
                        when subject "professor" attempts action "read" on resource "necronomicon"
                        expect permit;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first()
                .satisfies(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_DUPLICATE_SCENARIO_NAME));
    }

    @Test
    @DisplayName("multiple amount less than two produces error")
    void whenMultipleAmountLessThanTwo_thenError() {
        var document = """
                requirement "Function Call Count" {
                    given
                        - function eldritch.horror() maps to true
                    scenario "single call"
                        when subject "cultist" attempts action "invoke" on resource "ritual"
                        expect permit
                        verify
                            - function eldritch.horror() is called 1 times;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first()
                .satisfies(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_INVALID_MULTIPLE_AMOUNT));
    }

    @Test
    @DisplayName("multiple amount of two produces no error")
    void whenMultipleAmountIsTwo_thenNoError() {
        var document = """
                requirement "Function Call Count" {
                    given
                        - function eldritch.horror() maps to true
                    scenario "double call"
                        when subject "cultist" attempts action "invoke" on resource "ritual"
                        expect permit
                        verify
                            - function eldritch.horror() is called 2 times;
                }
                """;

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("invalid regex produces error")
    void whenInvalidRegex_thenError() {
        var document = """
                requirement "Pattern Matching" {
                    scenario "bad regex"
                        when subject "mi-go" attempts action "communicate" on resource "brain_cylinder"
                        expect decision is permit, with obligation matching object where { "pattern" is text with regex "[invalid(" };
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first().satisfies(
                e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_STRING_MATCHES_REGEX_WITH_INVALID_REGEX));
    }

    @Test
    @DisplayName("valid regex produces no error")
    void whenValidRegex_thenNoError() {
        var document = """
                requirement "Pattern Matching" {
                    scenario "good regex"
                        when subject "mi-go" attempts action "communicate" on resource "brain_cylinder"
                        expect decision is permit, with obligation matching object where { "pattern" is text with regex "^[a-z]+$" };
                }
                """;

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("string length of zero produces error")
    void whenStringLengthZero_thenError() {
        var document = """
                requirement "String Length" {
                    scenario "zero length"
                        when subject "hastur" attempts action "speak" on resource "name"
                        expect decision is permit, with obligation matching object where { "text" is text with length 0 };
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first()
                .satisfies(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_INVALID_STRING_WITH_LENGTH));
    }

    @Test
    @DisplayName("negative string length produces error")
    void whenStringLengthNegative_thenError() {
        var document = """
                requirement "String Length" {
                    scenario "negative length"
                        when subject "hastur" attempts action "speak" on resource "name"
                        expect decision is permit, with obligation matching object where { "text" is text with length -5 };
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first()
                .satisfies(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_INVALID_STRING_WITH_LENGTH));
    }

    @Test
    @DisplayName("positive string length produces no error")
    void whenStringLengthPositive_thenNoError() {
        var document = """
                requirement "String Length" {
                    scenario "positive length"
                        when subject "hastur" attempts action "speak" on resource "name"
                        expect decision is permit, with obligation matching object where { "text" is text with length 10 };
                }
                """;

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("null context produces empty errors")
    void whenNullContext_thenEmptyErrors() {
        var errors = validator.validate(null);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("multiple errors in document are all reported")
    void whenMultipleErrorsInDocument_thenAllErrorsReported() {
        var document = """
                requirement "Eldritch Chaos" {
                    given
                        - function chaos.invoke() maps to true
                        - function chaos.repeat() maps to true
                    scenario "chaos scenario"
                        when subject "cultist" attempts action "summon" on resource "void"
                        expect permit
                        verify
                            - function chaos.invoke() is called 0 times
                            - function chaos.repeat() is called 1 times;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(2);
    }

    @Test
    @DisplayName("duplicate scenario names across different requirements produce no error")
    void whenDuplicateScenarioNamesAcrossDifferentRequirements_thenNoError() {
        var document = """
                requirement "First Requirement" {
                    scenario "common name"
                        when "cultist" attempts "enter" on "temple"
                        expect permit;
                }
                requirement "Second Requirement" {
                    scenario "common name"
                        when "outsider" attempts "enter" on "temple"
                        expect deny;
                }
                """;

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("zero amount produces error")
    void whenZeroAmount_thenError() {
        var document = """
                requirement "Zero Amount" {
                    given
                        - function void.call() maps to true
                    scenario "zero times"
                        when "nobody" attempts "nothing" on "void"
                        expect permit
                        verify
                            - function void.call() is called 0 times;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first()
                .satisfies(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_INVALID_MULTIPLE_AMOUNT));
    }

    @Test
    @DisplayName("large valid amount produces no error")
    void whenLargeValidAmount_thenNoError() {
        var document = """
                requirement "Large Amount" {
                    given
                        - function infinite.call() maps to true
                    scenario "many calls"
                        when "automaton" attempts "repeat" on "task"
                        expect permit
                        verify
                            - function infinite.call() is called 999999 times;
                }
                """;

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("multiple amount in expect block is validated")
    void whenMultipleAmountInExpectBlock_thenValidated() {
        var document = """
                requirement "Expect Amount" {
                    scenario "invalid expect count"
                        when "subject" attempts "action" on "resource"
                        expect
                            - permit 1 times;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first()
                .satisfies(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_INVALID_MULTIPLE_AMOUNT));
    }

    @Test
    @DisplayName("empty regex produces no error")
    void whenEmptyRegex_thenNoError() {
        var document = """
                requirement "Empty Regex" {
                    scenario "empty pattern"
                        when "matcher" attempts "match" on "string"
                        expect decision is permit, with resource matching text with regex "";
                }
                """;

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("complex document with multiple requirements and scenarios runs all validations")
    void whenComplexDocumentWithMultipleRequirementsAndScenarios_thenAllValidationsRun() {
        var document = """
                requirement "Arkham Access" {
                    given
                        - document "arkham_policy"
                        - function access.check() maps to true
                    scenario "student access"
                        when "student" attempts "enter" on "library"
                        expect permit
                        verify
                            - function access.check() is called 5 times;
                    scenario "visitor denied"
                        when "visitor" attempts "enter" on "restricted"
                        expect deny;
                }
                requirement "Innsmouth Protocols" {
                    given
                        - documents "base_policy", "harbor_policy"
                        - attribute "tideMock" <tide.level> emits "high"
                    scenario "tidal check"
                        when "fisherman" attempts "sail" on "harbor"
                        expect permit
                        then
                            - attribute "tideMock" emits "low"
                        expect deny;
                    scenario "night patrol"
                        when "watchman" attempts "patrol" on "docks"
                        expect permit;
                }
                """;

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("triple duplicate requirement names produce two errors")
    void whenTripleDuplicateRequirementNames_thenTwoErrors() {
        var document = """
                requirement "Duplicate" {
                    scenario "first"
                        when "a" attempts "b" on "c"
                        expect permit;
                }
                requirement "Duplicate" {
                    scenario "second"
                        when "x" attempts "y" on "z"
                        expect deny;
                }
                requirement "Duplicate" {
                    scenario "third"
                        when "p" attempts "q" on "r"
                        expect indeterminate;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(2)
                .allSatisfy(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_DUPLICATE_REQUIREMENT_NAME));
    }

    @Test
    @DisplayName("triple duplicate scenario names produce two errors")
    void whenTripleDuplicateScenarioNames_thenTwoErrors() {
        var document = """
                requirement "Triple Duplicate Scenarios" {
                    scenario "same name"
                        when "a" attempts "b" on "c"
                        expect permit;
                    scenario "same name"
                        when "x" attempts "y" on "z"
                        expect deny;
                    scenario "same name"
                        when "p" attempts "q" on "r"
                        expect indeterminate;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(2)
                .allSatisfy(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_DUPLICATE_SCENARIO_NAME));
    }

    @Test
    @DisplayName("document with all new syntax produces no errors")
    void whenDocumentWithAllNewSyntax_thenNoErrors() {
        var document = """
                requirement "New Syntax Test" {
                    given
                        - document "unit_test_policy"
                        - unique or abstain errors propagate
                        - variables { "key": "value" }

                    scenario "unit test scenario"
                        when "user" attempts "read" on "resource"
                        expect permit;
                }

                requirement "Integration Test" {
                    given
                        - documents "policy1", "policy2"
                        - priority deny or deny errors propagate

                    scenario "integration scenario"
                        when "admin" attempts "delete" on "sensitive"
                        expect deny;
                }

                requirement "Minimal Test" {
                    scenario "no given block"
                        when "anyone" attempts "anything" on "something"
                        expect not-applicable;
                }
                """;

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    private List<ValidationError> validate(String document) {
        var parseTree = parse(document);
        return validator.validate(parseTree);
    }

    private SaplTestContext parse(String document) {
        var lexer  = new SAPLTestLexer(CharStreams.fromString(document));
        var tokens = new CommonTokenStream(lexer);
        var parser = new SAPLTestParser(tokens);
        return parser.saplTest();
    }

}
