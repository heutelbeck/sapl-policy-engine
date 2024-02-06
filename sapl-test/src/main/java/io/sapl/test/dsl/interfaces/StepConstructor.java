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

import java.util.List;

import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sapltest.FixtureRegistration;
import io.sapl.test.grammar.sapltest.GivenStep;
import io.sapl.test.grammar.sapltest.TestCase;
import io.sapl.test.grammar.sapltest.TestSuite;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.VerifyStep;
import io.sapl.test.steps.WhenStep;

/**
 * Used to define how the concrete tests steps defined in
 * {@link io.sapl.test.steps} are derived from a
 * {@link io.sapl.test.grammar.sapltest.SAPLTest} instance. The Logic defined by
 * a class implementing this interface is then used by
 * {@link io.sapl.test.dsl.setup.TestCase} to construct and execute the actual
 * test. Can be used in a class derived from
 * {@link io.sapl.test.dsl.setup.BaseTestAdapter} to customize the entire test
 * construction logic.
 */
public interface StepConstructor {
    /**
     * Defines logic to construct an instance of {@link SaplTestFixture} and handles
     * fixture registrations.
     *
     * @param fixtureRegistrations The registrations to apply on the created
     *                             Fixture.
     * @param testSuite            TestSuite containing information required for
     *                             building the Fixture.
     * @return The created Fixture.
     */
    SaplTestFixture constructTestFixture(List<FixtureRegistration> fixtureRegistrations, TestSuite testSuite);

    /**
     * Constructs the initial TestCase from a given Fixture and an Environment.
     *
     * @param saplTestFixture The Fixture created in
     *                        {@link StepConstructor#constructTestFixture(List, TestSuite)}.
     * @param environment     The Environment to consider for the TestCase.
     * @param needsMocks      Additional information if mocking is required for the
     *                        TestCase.
     * @return The created initial TestCase.
     */
    GivenOrWhenStep constructTestCase(SaplTestFixture saplTestFixture, io.sapl.test.grammar.sapltest.Object environment,
            boolean needsMocks);

    /**
     * Defines logic to apply a List of GivenSteps to the initial TestCase to
     * construct a {@link WhenStep}.
     *
     * @param givenSteps      GivenSteps to apply to the initial TestCase.
     * @param initialTestCase The initial TestCase contructed from
     *                        {@link StepConstructor#constructTestCase(SaplTestFixture, io.sapl.test.grammar.sapltest.Object, boolean)}.
     * @return The created WhenStep instance.
     */
    WhenStep constructWhenStep(List<GivenStep> givenSteps, GivenOrWhenStep initialTestCase);

    /**
     * Defines logic to construct a {@link ExpectStep} from a WhenStep.
     *
     * @param testCase The TestCase to derive information from.
     * @param whenStep The WhenStep created in
     *                 {@link StepConstructor#constructWhenStep(List, GivenOrWhenStep)}.
     * @return The created ExpectStep.
     */
    ExpectStep constructExpectStep(TestCase testCase, WhenStep whenStep);

    /**
     * Defines logic to construct a {@link VerifyStep} from a ExpectStep.
     *
     * @param testCase   The TestCase to derive information from.
     * @param expectStep The ExpectStep created in
     *                   {@link StepConstructor#constructExpectStep(TestCase, WhenStep)}.
     * @return The created VerifyStep.
     */
    VerifyStep constructVerifyStep(TestCase testCase, ExpectStep expectStep);
}
