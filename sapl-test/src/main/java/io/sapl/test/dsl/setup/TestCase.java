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
import io.sapl.test.grammar.sapltest.Given;
import io.sapl.test.grammar.sapltest.GivenStep;
import io.sapl.test.grammar.sapltest.ImportType;
import io.sapl.test.grammar.sapltest.MockDefinition;
import io.sapl.test.grammar.sapltest.Requirement;
import io.sapl.test.grammar.sapltest.Scenario;
import io.sapl.test.grammar.sapltest.TestException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestCase implements TestNode, Runnable {

    @Getter
    private final String                               identifier;
    private final StepConstructor                      stepConstructor;
    private final Given                                given;
    private final List<GivenStep>                      givenSteps;
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

        final var requirementBackground = requirement.getGiven();
        final var scenarioGivenBlock    = scenario.getGiven();

        if (requirementBackground == null && scenarioGivenBlock == null) {
            throw new SaplTestException("Neither Requirement nor Scenario defines a GivenBlock");
        }

        var givenBlock = scenarioGivenBlock == null ? requirementBackground : scenarioGivenBlock;

        final var givenSteps = new ArrayList<GivenStep>();
        addGivenStepsFromGiven(givenSteps, requirementBackground);
        addGivenStepsFromGiven(givenSteps, scenarioGivenBlock);

        return new TestCase(name, stepConstructor, givenBlock, givenSteps, scenario, fixtureRegistrations);
    }

    @Override
    public void run() {
        final var environment = given.getEnvironment();

        final var saplTestFixture = stepConstructor.constructTestFixture(given, givenSteps, fixtureRegistrations);

        final var needsMocks      = givenSteps.stream().anyMatch(MockDefinition.class::isInstance);
        final var initialTestCase = stepConstructor.constructTestCase(saplTestFixture, environment, needsMocks);
        final var expectation     = scenario.getExpectation();

        if (scenario.getExpectation() instanceof TestException) {
            Assertions.assertThatExceptionOfType(SaplTestException.class)
                    .isThrownBy(() -> stepConstructor.constructWhenStep(givenSteps, initialTestCase, expectation));
        } else {

            final var whenStep = stepConstructor.constructWhenStep(givenSteps, initialTestCase, expectation);

            final var expectStep = stepConstructor.constructExpectStep(scenario, whenStep);
            final var verifyStep = stepConstructor.constructVerifyStep(scenario, expectStep);

            verifyStep.verify();
        }
    }

    private static void addGivenStepsFromGiven(final List<GivenStep> givenSteps, final Given given) {
        if (given != null) {
            final var steps = given.getGivenSteps();
            if (steps != null) {
                givenSteps.addAll(steps);
            }
        }
    }
}
