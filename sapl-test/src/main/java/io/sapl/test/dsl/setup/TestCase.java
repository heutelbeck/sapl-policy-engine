/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.test.dsl.setup;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.TestNode;
import io.sapl.test.grammar.sAPLTest.TestException;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.ExpectOrVerifyStep;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestCase implements TestNode, Runnable {

    @Getter
    private final String                                 identifier;
    private final StepConstructor                        stepConstructor;
    private final TestSuite                              testSuite;
    private final io.sapl.test.grammar.sAPLTest.TestCase dslTestCase;

    public static TestCase from(final StepConstructor stepConstructor, final TestSuite testSuite,
            io.sapl.test.grammar.sAPLTest.TestCase testCase) {
        if (stepConstructor == null || testSuite == null || testCase == null) {
            throw new SaplTestException("One or more parameter(s) are null");
        }

        final var name = testCase.getName();

        if (name == null) {
            throw new SaplTestException("Name of the test case is null");
        }

        return new TestCase(name, stepConstructor, testSuite, testCase);
    }

    @Override
    public void run() {
        final var environment          = dslTestCase
                .getEnvironment() instanceof io.sapl.test.grammar.sAPLTest.Object object ? object : null;
        final var fixtureRegistrations = dslTestCase.getRegistrations();
        final var givenSteps           = dslTestCase.getGivenSteps();

        final var needsMocks  = givenSteps != null && !givenSteps.isEmpty();
        final var testFixture = stepConstructor.constructTestFixture(fixtureRegistrations, testSuite);

        final var initialTestCase = stepConstructor.constructTestCase(testFixture, environment, needsMocks);

        if (dslTestCase.getExpect() instanceof TestException) {
            Assertions.assertThatExceptionOfType(SaplTestException.class)
                    .isThrownBy(() -> stepConstructor.constructWhenStep(givenSteps, initialTestCase));
        } else {

            final var whenStep   = stepConstructor.constructWhenStep(givenSteps, initialTestCase);
            final var expectStep = stepConstructor.constructExpectStep(dslTestCase, whenStep);
            final var verifyStep = stepConstructor.constructVerifyStep(dslTestCase, (ExpectOrVerifyStep) expectStep);

            verifyStep.verify();
        }
    }
}
