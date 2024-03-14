/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.grammar.validation;

import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.extensions.InjectionExtension;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.testing.validation.ValidationTestHelper;
import org.eclipse.xtext.xbase.lib.Extension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.inject.Inject;

import io.sapl.test.grammar.sapltest.SAPLTest;
import io.sapl.test.grammar.sapltest.SapltestPackage;
import io.sapl.test.grammar.tests.SAPLTestInjectorProvider;

@ExtendWith(InjectionExtension.class)
@InjectWith(SAPLTestInjectorProvider.class)
class SAPLTestValidatorTest {

    @Inject
    @Extension
    private ParseHelper<SAPLTest> parseHelper;

    @Inject
    @Extension
    private ValidationTestHelper validator;

    @Test
    void durationNeedsToBeAValidJavaDuration_handlesInvalidDuration_hasError() throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    then
                        - wait "P2S";
                }""";
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getDuration(), null,
                SAPLTestValidator.MSG_INVALID_JAVA_DURATION);
    }

    @Test
    void durationNeedsToBeAValidJavaDuration_handlesValidDuration_hasNoError() throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    then
                        - wait "PT2S"
                    expect
                        - permit;
                }""";
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }

    @Test
    void durationNeedsToBeAValidJavaDuration_handlesNegativeDuration_hasError() throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    then
                        - wait "PT-1S"
                    expect
                        - permit;
                }""";
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getDuration(), null,
                SAPLTestValidator.MSG_JAVA_DURATION_ZERO_OR_NEGATIVE);
    }

    @Test
    void durationNeedsToBeAValidJavaDuration_handlesZeroDuration_hasError() throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    then
                        - wait "PT0S"
                    expect
                        - permit;
                }""";
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getDuration(), null,
                SAPLTestValidator.MSG_JAVA_DURATION_ZERO_OR_NEGATIVE);
    }

    @ParameterizedTest
    @ValueSource(doubles = { -2, -1, 0, 1, Integer.MIN_VALUE, Double.MIN_VALUE, Double.MAX_VALUE, -0.33, -0.5, 0.5 })
    void multipleAmountNeedsToBeNaturalNumberLargerThanOne_handlesInvalidAmount_hasError(final double number)
            throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    expect
                        - permit %s times;
                }""".formatted(number);
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getMultiple(), null,
                SAPLTestValidator.MSG_INVALID_MULTIPLE_AMOUNT);
    }

    @ParameterizedTest
    @ValueSource(ints = { 5, 12341, 2, 12, Integer.MAX_VALUE })
    void multipleAmountNeedsToBeNaturalNumberLargerThanOne_handlesValidAmount_hasNoError(final int number)
            throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    expect
                        - permit %d times;
                }""".formatted(number);
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }

    @Test
    void givenMayContainVirtualTimeOnlyOnce_inRequirement_handlesMultipleVirtualTimeDefinitions_hasError()
            throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                     given
                    	- virtual-time
                    	- virtual-time

                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    expect permit;
                }""";
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getGiven(), null,
                SAPLTestValidator.MSG_GIVEN_WITH_MORE_THAN_ONE_VIRTUAL_TIME);
    }

    @Test
    void givenMayContainVirtualTimeOnlyOnce_inScenario_handlesMultipleVirtualTimeDefinitions_hasError()
            throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    given
                    	- virtual-time
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    expect permit;
                }""";
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getGiven(), null,
                SAPLTestValidator.MSG_GIVEN_WITH_MORE_THAN_ONE_VIRTUAL_TIME);
    }

    @Test
    void givenMayContainVirtualTimeOnlyOnce_inRequirement_handlesSingleVirtualTimeDefinition_hasNoError()
            throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                     given
                    	- virtual-time

                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    expect permit;
                }""";
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }

    @Test
    void givenMayContainVirtualTimeOnlyOnce_inScenario_handlesSingleVirtualTimeDefinition_hasNoError()
            throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                     given
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    expect permit;
                }""";
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "\\\\x", "[a-zA-Z", "(0-9" })
    void stringMatchesRegexMustContainValidRegex_handlesInvalidRegex_hasError(final String regex) throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    expect decision with resource matching text with regex "%s";
                }""".formatted(regex);
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getStringMatchesRegex(), null,
                SAPLTestValidator.MSG_STRING_MATCHES_REGEX_WITH_INVALID_REGEX);
    }

    @ParameterizedTest
    @ValueSource(strings = { "[a-zA-Z]", "abc", "abc.?" })
    void stringMatchesRegexMustContainValidRegex_handlesValidRegex_hasNoError(final String regex) throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    expect decision with resource matching text with regex "%s";
                }""".formatted(regex);
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }

    @ParameterizedTest
    @ValueSource(doubles = { -1, 0, 0.5, Integer.MAX_VALUE + 1D, -5, -0.1 })
    void stringWithLengthNeedsToBeNaturalNumberLargerThanZero_handlesInvalidLength_hasError(final double number)
            throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    expect decision with resource matching text with length %s;
                }""".formatted(number);
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getStringWithLength(), null,
                SAPLTestValidator.MSG_INVALID_STRING_WITH_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(doubles = { 1, 5, Integer.MAX_VALUE, 555 })
    void stringWithLengthNeedsToBeNaturalNumberLargerThanZero_handlesValidLength_hasNoError(final double number)
            throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    expect decision with resource matching text with length %s;
                }""".formatted(number);
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }

    @Test
    void requirementNameNeedsToBeUnique_hasNoError() throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    given
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    expect permit;
                }

                requirement "requirement2" {
                    scenario "scenario"
                    given
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    expect permit;
                }
                """;
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }

    @Test
    void requirementNameNeedsToBeUnique_hasError() throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    given
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    expect permit;
                }

                requirement "requirement" {
                    scenario "scenario"
                    given
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    expect permit;
                }
                """;
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getSAPLTest(), null,
                SAPLTestValidator.MSG_DUPLICATE_REQUIREMENT_NAME);
    }

    @Test
    void scenarioNameNeedsToBeUnique_hasNoError() throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    given
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    expect permit;

                    scenario "scenario2"
                    given
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    expect permit;
                }
                """;
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }

    @Test
    void scenarioNameNeedsToBeUnique_hasError() throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    given
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    then expect permit;

                    scenario "scenario"
                    given
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    expect permit;
                }
                """;
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getRequirement(), null,
                SAPLTestValidator.MSG_DUPLICATE_SCENARIO_NAME);
    }

    @Test
    void repeatedExpectNeedsToAlternateBlocksAndEndWithExpectBlock_hasNoError() throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    given
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    expect
                        - permit
                    then
                        - wait "PT1S"
                    expect
                        - deny;
                }
                """;
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }

    @Test
    void repeatedExpectNeedsToAlternateBlocksAndEndWithExpectBlock_multipleAlternatingBlocks_hasNoError()
            throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    given
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    expect
                        - permit
                    then
                        - wait "PT1S"
                    expect
                        - deny
                    then
                        - wait "PT1S"
                    expect
                        - deny;
                }
                """;
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }

    @Test
    void repeatedExpectNeedsToAlternateBlocksAndEndWithExpectBlock_nonAlternatingBlocks_hasError() throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    given
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    expect
                        - permit
                    expect
                        - deny;
                }
                """;
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getRepeatedExpect(), null,
                SAPLTestValidator.MSG_NON_ALTERNATING_EXPECT_OR_ADJUSTMENT_BLOCKS);
    }

    @Test
    void repeatedExpectNeedsToAlternateBlocksAndEndWithExpectBlock_endingWithAdjustBlock_hasError() throws Exception {
        final var testDefinition = """
                 requirement "requirement" {
                    scenario "scenario"
                    given
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    then
                        - wait "PT1S";
                }
                """;
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getRepeatedExpect(), null,
                SAPLTestValidator.MSG_INVALID_REPEATED_EXPECT);
    }
}
