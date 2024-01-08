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
package io.sapl.test.dsl.interfaces;

import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sAPLTest.FixtureRegistration;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.VerifyStep;
import io.sapl.test.steps.WhenStep;
import java.util.List;

public interface StepConstructor {
    SaplTestFixture constructTestFixture(List<FixtureRegistration> fixtureRegistrations, TestSuite testSuite);

    GivenOrWhenStep constructTestCase(SaplTestFixture saplTestFixture, io.sapl.test.grammar.sAPLTest.Object environment,
            boolean needsMocks);

    WhenStep constructWhenStep(List<GivenStep> givenSteps, GivenOrWhenStep fixture);

    ExpectStep constructExpectStep(TestCase testCase, WhenStep whenStep);

    VerifyStep constructVerifyStep(TestCase testCase, ExpectOrVerifyStep expectOrVerifyStep);
}
