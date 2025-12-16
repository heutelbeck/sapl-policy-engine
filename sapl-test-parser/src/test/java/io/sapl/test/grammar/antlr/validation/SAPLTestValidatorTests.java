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
package io.sapl.test.grammar.antlr.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.test.grammar.antlr.SAPLTestLexer;
import io.sapl.test.grammar.antlr.SAPLTestParser;
import io.sapl.test.grammar.antlr.SAPLTestParser.SaplTestContext;

class SAPLTestValidatorTests {

    private final SAPLTestValidator validator = new SAPLTestValidator();

    @Test
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

    @ParameterizedTest
    @ValueSource(strings = { "PT1S", "PT10M", "PT1H", "P1D", "PT0.5S" })
    void whenValidDuration_thenNoErrors(String duration) {
        var document = """
                requirement "Deep One Timing" {
                    scenario "delayed response"
                        when subject "dagon" attempts action "emerge" on resource "ocean"
                        expect
                            - no-event for "%s";
                }
                """.formatted(duration);

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
    void whenInvalidDurationFormat_thenError() {
        var document = """
                requirement "Invalid Duration" {
                    scenario "bad timing"
                        when subject "shoggoth" attempts action "ooze" on resource "corridor"
                        expect
                            - no-event for "not-a-duration";
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first()
                .satisfies(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_INVALID_JAVA_DURATION));
    }

    @Test
    void whenZeroDuration_thenError() {
        var document = """
                requirement "Zero Duration" {
                    scenario "instant"
                        when subject "nyarlathotep" attempts action "appear" on resource "dream"
                        expect
                            - no-event for "PT0S";
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first().satisfies(
                e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_JAVA_DURATION_ZERO_OR_NEGATIVE));
    }

    @Test
    void whenNegativeDuration_thenError() {
        var document = """
                requirement "Negative Duration" {
                    scenario "time reversal"
                        when subject "yog-sothoth" attempts action "warp" on resource "time"
                        expect
                            - no-event for "-PT1S";
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first().satisfies(
                e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_JAVA_DURATION_ZERO_OR_NEGATIVE));
    }

    @Test
    void whenMultipleAmountLessThanTwo_thenError() {
        var document = """
                requirement "Function Call Count" {
                    given
                        - function "eldritch.horror" maps to true is called 1 times
                    scenario "single call"
                        when subject "cultist" attempts action "invoke" on resource "ritual"
                        expect permit;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first()
                .satisfies(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_INVALID_MULTIPLE_AMOUNT));
    }

    @Test
    void whenMultipleAmountIsTwo_thenNoError() {
        var document = """
                requirement "Function Call Count" {
                    given
                        - function "eldritch.horror" maps to true is called 2 times
                    scenario "double call"
                        when subject "cultist" attempts action "invoke" on resource "ritual"
                        expect permit;
                }
                """;

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
    void whenMultipleVirtualTime_thenError() {
        var document = """
                requirement "Time Warp" {
                    given
                        - virtual-time
                        - virtual-time
                    scenario "paradox"
                        when subject "time_lord" attempts action "travel" on resource "tardis"
                        expect permit;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first().satisfies(
                e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_GIVEN_WITH_MORE_THAN_ONE_VIRTUAL_TIME));
    }

    @Test
    void whenSingleVirtualTime_thenNoError() {
        var document = """
                requirement "Time Control" {
                    given
                        - virtual-time
                    scenario "controlled time"
                        when subject "azathoth" attempts action "dream" on resource "universe"
                        expect permit;
                }
                """;

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
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
    void whenRepeatedExpectEndsWithThen_thenError() {
        var document = """
                requirement "Block Order" {
                    scenario "ends with then"
                        when subject "elder_thing" attempts action "freeze" on resource "antarctic"
                        expect
                            - permit once
                        then
                            - wait "PT1S";
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first()
                .satisfies(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_INVALID_REPEATED_EXPECT));
    }

    @Test
    void whenRepeatedExpectAlternatesCorrectly_thenNoError() {
        var document = """
                requirement "Block Order" {
                    scenario "correct alternation"
                        when subject "elder_thing" attempts action "freeze" on resource "antarctic"
                        expect
                            - permit once
                        then
                            - wait "PT1S"
                        expect
                            - deny once;
                }
                """;

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
    void whenRepeatedExpectHasConsecutiveExpectBlocks_thenError() {
        var document = """
                requirement "Block Order" {
                    scenario "consecutive expects"
                        when subject "deep_one" attempts action "swim" on resource "reef"
                        expect
                            - permit once
                        expect
                            - deny once;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first().satisfies(e -> assertThat(e.message())
                .isEqualTo(SAPLTestValidator.MSG_NON_ALTERNATING_EXPECT_OR_ADJUSTMENT_BLOCKS));
    }

    @Test
    void whenNullContext_thenEmptyErrors() {
        var errors = validator.validate(null);

        assertThat(errors).isEmpty();
    }

    // ========================================================================
    // EDGE CASES
    // ========================================================================

    @Test
    void whenMultipleErrorsInDocument_thenAllErrorsReported() {
        var document = """
                requirement "Eldritch Chaos" {
                    given
                        - virtual-time
                        - virtual-time
                        - function "chaos.invoke" maps to true is called 0 times
                    scenario "chaos scenario"
                        when subject "cultist" attempts action "summon" on resource "void"
                        expect
                            - no-event for "invalid-duration";
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(3);
    }

    @Test
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
    void whenScenarioLevelGivenHasMultipleVirtualTime_thenError() {
        var document = """
                requirement "Scenario Virtual Time" {
                    scenario "time paradox"
                        given
                            - virtual-time
                            - virtual-time
                        when "time_traveler" attempts "loop" on "timeline"
                        expect permit;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first().satisfies(
                e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_GIVEN_WITH_MORE_THAN_ONE_VIRTUAL_TIME));
    }

    @Test
    void whenRequirementAndScenarioBothHaveVirtualTime_thenTwoErrors() {
        var document = """
                requirement "Double Virtual Time" {
                    given
                        - virtual-time
                        - virtual-time
                    scenario "nested paradox"
                        given
                            - virtual-time
                            - virtual-time
                        when "chronos" attempts "manipulate" on "space_time"
                        expect permit;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(2);
    }

    @Test
    void whenDurationInWaitAdjustment_thenValidated() {
        var document = """
                requirement "Wait Duration" {
                    given
                        - virtual-time
                    scenario "invalid wait"
                        when "patient" attempts "wait" on "queue"
                        expect
                            - permit once
                        then
                            - wait "not-a-duration"
                        expect
                            - deny once;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first()
                .satisfies(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_INVALID_JAVA_DURATION));
    }

    @Test
    void whenZeroAmount_thenError() {
        var document = """
                requirement "Zero Amount" {
                    given
                        - function "void.call" maps to true is called 0 times
                    scenario "zero times"
                        when "nobody" attempts "nothing" on "void"
                        expect permit;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first()
                .satisfies(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_INVALID_MULTIPLE_AMOUNT));
    }

    @Test
    void whenLargeValidAmount_thenNoError() {
        var document = """
                requirement "Large Amount" {
                    given
                        - function "infinite.call" maps to true is called 999999 times
                    scenario "many calls"
                        when "automaton" attempts "repeat" on "task"
                        expect permit;
                }
                """;

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
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
    void whenConsecutiveThenBlocks_thenError() {
        var document = """
                requirement "Consecutive Then" {
                    given
                        - virtual-time
                    scenario "double adjustment"
                        when "subject" attempts "action" on "resource"
                        expect
                            - permit once
                        then
                            - wait "PT1S"
                        then
                            - wait "PT2S"
                        expect
                            - deny once;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first().satisfies(e -> assertThat(e.message())
                .isEqualTo(SAPLTestValidator.MSG_NON_ALTERNATING_EXPECT_OR_ADJUSTMENT_BLOCKS));
    }

    @Test
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
    void whenComplexDocumentWithMultipleRequirementsAndScenarios_thenAllValidationsRun() {
        var document = """
                requirement "Arkham Access" {
                    given
                        - policy "arkham_policy"
                        - function "access.check" maps to true is called 5 times
                    scenario "student access"
                        when "student" attempts "enter" on "library"
                        expect permit;
                    scenario "visitor denied"
                        when "visitor" attempts "enter" on "restricted"
                        expect deny;
                }
                requirement "Innsmouth Protocols" {
                    given
                        - virtual-time
                        - attribute "tide.level" emits "high", "low" with timing "PT1H"
                    scenario "tidal check"
                        when "fisherman" attempts "sail" on "harbor"
                        expect
                            - permit once
                        then
                            - wait "PT6H"
                        expect
                            - deny once;
                    scenario "night patrol"
                        when "watchman" attempts "patrol" on "docks"
                        expect
                            - no-event for "PT30M";
                }
                """;

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
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

    @ParameterizedTest
    @ValueSource(strings = { "PT1H30M", "P1DT12H", "PT0.001S", "PT999999H" })
    void whenVariousDurationFormats_thenNoErrors(String duration) {
        var document = """
                requirement "Duration Formats" {
                    given
                        - virtual-time
                    scenario "wait test"
                        when "waiter" attempts "wait" on "event"
                        expect
                            - permit once
                        then
                            - wait "%s"
                        expect
                            - deny once;
                }
                """.formatted(duration);

        var errors = validate(document);

        assertThat(errors).isEmpty();
    }

    @Test
    void whenAttributeMockHasDurationInTiming_thenValidated() {
        var document = """
                requirement "Attribute Timing" {
                    given
                        - attribute "cosmos.phase" emits "new", "full" with timing "invalid"
                    scenario "moon phase"
                        when "observer" attempts "watch" on "sky"
                        expect permit;
                }
                """;

        var errors = validate(document);

        assertThat(errors).hasSize(1).first()
                .satisfies(e -> assertThat(e.message()).isEqualTo(SAPLTestValidator.MSG_INVALID_JAVA_DURATION));
    }

    private java.util.List<ValidationError> validate(String document) {
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
