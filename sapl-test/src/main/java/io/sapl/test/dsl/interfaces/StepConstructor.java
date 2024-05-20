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
import io.sapl.test.grammar.sapltest.Document;
import io.sapl.test.grammar.sapltest.Environment;
import io.sapl.test.grammar.sapltest.Expectation;
import io.sapl.test.grammar.sapltest.GivenStep;
import io.sapl.test.grammar.sapltest.ImportType;
import io.sapl.test.grammar.sapltest.PdpCombiningAlgorithm;
import io.sapl.test.grammar.sapltest.PdpVariables;
import io.sapl.test.grammar.sapltest.Scenario;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.VerifyStep;
import io.sapl.test.steps.WhenStep;
import java.util.List;
import java.util.Map;

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
     * @param document              the document to test
     * @param pdpVariables          the pdp variables to apply to the fixture
     * @param pdpCombiningAlgorithm the pdp combining algorithm to set
     * @param givenSteps            containing the
     *                              {@link io.sapl.test.grammar.sapltest.Import} to
     *                              apply on the constructed fixture.
     * @return The created Fixture.
     */
    SaplTestFixture constructTestFixture(Document document, PdpVariables pdpVariables,
            PdpCombiningAlgorithm pdpCombiningAlgorithm, List<GivenStep> givenSteps,
            Map<ImportType, Map<String, Object>> fixtureRegistrations);

    /**
     * Constructs the initial TestCase from a given {@link SaplTestFixture} and an
     * {@link Environment}.
     *
     * @param saplTestFixture The Fixture created in
     *                        {@link StepConstructor#constructTestFixture(Document, PdpVariables, PdpCombiningAlgorithm, List, Map)}.
     * @param environment     The Environment to consider for the TestCase.
     * @param needsMocks      Additional information if mocking is required for the
     *                        TestCase.
     * @return The created initial TestCase.
     */
    GivenOrWhenStep constructTestCase(SaplTestFixture saplTestFixture, Environment environment, boolean needsMocks);

    /**
     * Defines logic to apply a List of GivenSteps to the initial TestCase to
     * construct a {@link WhenStep}.
     *
     * @param givenSteps      containing the
     *                        {@link io.sapl.test.grammar.sapltest.MockDefinition}
     *                        to apply on the constructed WhenStep.
     * @param initialTestCase The initial TestCase contructed from
     *                        {@link StepConstructor#constructTestCase(SaplTestFixture, Environment, boolean)}.
     * @return The created WhenStep instance.
     */
    WhenStep constructWhenStep(List<GivenStep> givenSteps, GivenOrWhenStep initialTestCase, Expectation expectation);

    /**
     * Defines logic to construct a {@link ExpectStep} from a WhenStep.
     *
     * @param scenario The Scenario to derive information from.
     * @param whenStep The WhenStep created in
     *                 {@link StepConstructor#constructWhenStep(List, GivenOrWhenStep, Expectation)}.
     * @return The created ExpectStep.
     */
    ExpectStep constructExpectStep(Scenario scenario, WhenStep whenStep);

    /**
     * Defines logic to construct a {@link VerifyStep} from a ExpectStep.
     *
     * @param scenario   The Scenario to derive information from.
     * @param expectStep The ExpectStep created in
     *                   {@link StepConstructor#constructExpectStep(Scenario, WhenStep)}.
     * @return The created VerifyStep.
     */
    VerifyStep constructVerifyStep(Scenario scenario, ExpectStep expectStep);
}
