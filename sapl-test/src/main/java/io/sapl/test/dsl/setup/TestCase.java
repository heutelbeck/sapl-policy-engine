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

package io.sapl.test.dsl.setup;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.TestNode;
import io.sapl.test.grammar.sapltest.Document;
import io.sapl.test.grammar.sapltest.Environment;
import io.sapl.test.grammar.sapltest.Given;
import io.sapl.test.grammar.sapltest.GivenStep;
import io.sapl.test.grammar.sapltest.ImportType;
import io.sapl.test.grammar.sapltest.MockDefinition;
import io.sapl.test.grammar.sapltest.PdpCombiningAlgorithm;
import io.sapl.test.grammar.sapltest.PdpVariables;
import io.sapl.test.grammar.sapltest.Requirement;
import io.sapl.test.grammar.sapltest.Scenario;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestCase implements TestNode, Runnable {

    @Getter
    private final String                               identifier;
    private final StepConstructor                      stepConstructor;
    private final GivenBlock                           givenBlock;
    private final Scenario                             scenario;
    private final Map<ImportType, Map<String, Object>> fixtureRegistrations;

    public static TestCase from(final StepConstructor stepConstructor, final Requirement requirement, Scenario scenario,
            Map<ImportType, Map<String, Object>> fixtureRegistrations) {
        if (stepConstructor == null || requirement == null || scenario == null) {
            throw new SaplTestException("StepConstructor or testSuite or testCase is null");
        }

        final var name = scenario.getName();

        if (name == null) {
            throw new SaplTestException("Name of the test case is null");
        }

        final var requirementGiven = requirement.getGiven();
        final var scenarioGiven    = scenario.getGiven();

        if (requirementGiven == null && scenarioGiven == null) {
            throw new SaplTestException("Neither Requirement nor Scenario defines a GivenBlock");
        }

        final var givenBlock = getMergedGivenBlock(requirementGiven, scenarioGiven);

        return new TestCase(name, stepConstructor, givenBlock, scenario, fixtureRegistrations);
    }

    private static GivenBlock getMergedGivenBlock(final Given requirementGiven, final Given scenarioGiven) {
        Document              document              = null;
        PdpVariables          pdpVariables          = null;
        PdpCombiningAlgorithm pdpCombiningAlgorithm = null;
        Environment           environment           = null;

        if (requirementGiven != null) {
            document              = requirementGiven.getDocument();
            pdpVariables          = requirementGiven.getPdpVariables();
            pdpCombiningAlgorithm = requirementGiven.getPdpCombiningAlgorithm();
            environment           = requirementGiven.getEnvironment();
        }

        if (scenarioGiven != null) {
            final var scenarioDocument              = scenarioGiven.getDocument();
            final var scenarioPdpVariables          = scenarioGiven.getPdpVariables();
            final var scenarioPdpCombiningAlgorithm = scenarioGiven.getPdpCombiningAlgorithm();
            final var scenarioEnvironment           = scenarioGiven.getEnvironment();

            if (scenarioDocument != null) {
                document = scenarioDocument;
            }
            if (scenarioPdpVariables != null) {
                pdpVariables = scenarioPdpVariables;
            }
            if (scenarioPdpCombiningAlgorithm != null) {
                pdpCombiningAlgorithm = scenarioPdpCombiningAlgorithm;
            }
            if (scenarioEnvironment != null) {
                environment = scenarioEnvironment;
            }
        }

        final var givenSteps = new ArrayList<GivenStep>();
        addGivenStepsFromGiven(givenSteps, requirementGiven);
        addGivenStepsFromGiven(givenSteps, scenarioGiven);

        return new GivenBlock(document, pdpVariables, pdpCombiningAlgorithm, environment, givenSteps);
    }

    private static void addGivenStepsFromGiven(final Collection<GivenStep> givenSteps, final Given given) {
        if (given != null) {
            final var steps = given.getGivenSteps();
            if (steps != null) {
                givenSteps.addAll(steps);
            }
        }
    }

    @Override
    public void run() {
        final var givenSteps = givenBlock.givenSteps();

        final var saplTestFixture = stepConstructor.constructTestFixture(givenBlock.document(),
                givenBlock.pdpVariables(), givenBlock.pdpConfiguration(), givenSteps, fixtureRegistrations);

        final var needsMocks      = givenSteps.stream().anyMatch(MockDefinition.class::isInstance);
        final var initialTestCase = stepConstructor.constructTestCase(saplTestFixture, givenBlock.environment(),
                needsMocks);
        final var expectation     = scenario.getExpectation();

        final var whenStep   = stepConstructor.constructWhenStep(givenSteps, initialTestCase, expectation);
        final var expectStep = stepConstructor.constructExpectStep(scenario, whenStep);
        final var verifyStep = stepConstructor.constructVerifyStep(scenario, expectStep);

        verifyStep.verify();
    }
}
