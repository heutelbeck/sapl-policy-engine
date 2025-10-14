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
package io.sapl.test.dsl.setup;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.grammar.sapltest.ImportType;
import io.sapl.test.grammar.sapltest.Requirement;
import io.sapl.test.grammar.sapltest.SAPLTest;
import io.sapl.test.grammar.sapltest.Scenario;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestProvider {

    private final StepConstructor stepConstructor;

    public static TestProvider of(final StepConstructor stepConstructor) {
        return new TestProvider(stepConstructor);
    }

    public List<TestContainer> buildTests(final SAPLTest saplTest,
            final Map<ImportType, Map<String, Object>> fixtureRegistrations) {
        if (saplTest == null) {
            throw new SaplTestException("provided SAPLTest is null");
        }

        final var requirements = saplTest.getRequirements();

        if (requirements == null || requirements.isEmpty()) {
            throw new SaplTestException("provided SAPLTest does not contain a Requirement");
        }

        if (requirements.stream().map(Requirement::getName).distinct().count() != requirements.size()) {
            throw new SaplTestException("Requirement name needs to be unique");
        }

        return requirements.stream().map(requirement -> {
            final var scenarios = requirement.getScenarios();
            if (scenarios == null || scenarios.isEmpty()) {
                throw new SaplTestException("provided Requirement does not contain a Scenario");
            }

            if (scenarios.stream().map(Scenario::getName).distinct().count() != scenarios.size()) {
                throw new SaplTestException("Scenario name needs to be unique within one Requirement");
            }

            return TestContainer.from(requirement.getName(),
                    scenarios.stream().map(
                            scenario -> TestCase.from(stepConstructor, requirement, scenario, fixtureRegistrations))
                            .toList());
        }).toList();
    }
}
