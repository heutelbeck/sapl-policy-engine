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
                 test "scenario" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    then expect - wait "P2S";
                }""";
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getDuration(), null,
                SAPLTestValidator.MSG_INVALID_JAVA_DURATION);
    }

    @Test
    void durationNeedsToBeAValidJavaDuration_handlesValidDuration_hasNoError() throws Exception {
        final var testDefinition = """
                 test "scenario" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    then expect - wait "PT2S";
                }""";
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }

    @ParameterizedTest
    @ValueSource(doubles = { -2, -1, 0, 1, Integer.MIN_VALUE, Double.MIN_VALUE, Double.MAX_VALUE, -0.33, -0.5, 0.5 })
    void multipleAmountNeedsToBeNaturalNumberLargerThanOne_handlesInvalidAmount_hasError(final double number)
            throws Exception {
        final var testDefinition = """
                 test "scenario" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    then expect - permit %s times;
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
                 test "scenario" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    then expect - permit %d times;
                }""".formatted(number);
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }

    @Test
    void testCaseMayContainVirtualTimeOnlyOnce_handlesMultipleVirtualTimeDefinitions_hasError() throws Exception {
        final var testDefinition = """
                 test "scenario" {
                    scenario "scenario"
                    given
                    	- virtual-time
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    then expect permit;
                }""";
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getTestCase(), null,
                SAPLTestValidator.MSG_TESTCASE_WITH_MORE_THAN_ONE_VIRTUAL_TIME);
    }

    @Test
    void testCaseMayContainVirtualTimeOnlyOnce_handlesSingleVirtualTimeDefinition_hasNoError() throws Exception {
        final var testDefinition = """
                 test "scenario" {
                    scenario "scenario"
                    given
                    	- virtual-time
                    when subject "willi" attempts action "read" on resource "something"
                    then expect permit;
                }""";
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "\\\\x", "[a-zA-Z", "(0-9" })
    void stringMatchesRegexMustContainValidRegex_handlesInvalidRegex_hasError(final String regex) throws Exception {
        final var testDefinition = """
                 test "scenario" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    then expect decision with resource matching text with regex "%s";
                }""".formatted(regex);
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertError(result, SapltestPackage.eINSTANCE.getStringMatchesRegex(), null,
                SAPLTestValidator.MSG_STRING_MATCHES_REGEX_WITH_INVALID_REGEX);
    }

    @ParameterizedTest
    @ValueSource(strings = { "[a-zA-Z]", "abc", "abc.?" })
    void stringMatchesRegexMustContainValidRegex_handlesValidRegex_hasNoError(final String regex) throws Exception {
        final var testDefinition = """
                 test "scenario" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    then expect decision with resource matching text with regex "%s";
                }""".formatted(regex);
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }

    @ParameterizedTest
    @ValueSource(doubles = { -1, 0, 0.5, Integer.MAX_VALUE + 1D, -5, -0.1 })
    void stringWithLengthNeedsToBeNaturalNumberLargerThanZero_handlesInvalidLength_hasError(final double number)
            throws Exception {
        final var testDefinition = """
                 test "scenario" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    then expect decision with resource matching text with length %s;
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
                 test "scenario" {
                    scenario "scenario"
                    when subject "willi" attempts action "read" on resource "something"
                    then expect decision with resource matching text with length %s;
                }""".formatted(number);
        final var result         = this.parseHelper.parse(testDefinition);
        this.validator.assertNoErrors(result);
    }
}
