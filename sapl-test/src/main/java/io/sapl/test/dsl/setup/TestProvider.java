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
import io.sapl.test.grammar.sapltest.IntegrationTestSuite;
import io.sapl.test.grammar.sapltest.PoliciesByIdentifier;
import io.sapl.test.grammar.sapltest.PoliciesByInputString;
import io.sapl.test.grammar.sapltest.SAPLTest;
import io.sapl.test.grammar.sapltest.TestSuite;
import io.sapl.test.grammar.sapltest.UnitTestSuite;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestProvider {

    private final StepConstructor stepConstructor;

    public static TestProvider of(final StepConstructor stepConstructor) {
        return new TestProvider(stepConstructor);
    }

    public List<TestContainer> buildTests(final SAPLTest saplTest) {
        if (saplTest == null) {
            throw new SaplTestException("provided SAPLTest is null");
        }

        final var testSuites = saplTest.getTestSuites();

        if (testSuites == null || testSuites.isEmpty()) {
            throw new SaplTestException("provided SAPLTest does not contain a TestSuite");
        }

        return testSuites.stream().map(testSuite -> {
            final var testCases = testSuite.getTestCases();
            if (testCases == null || testCases.isEmpty()) {
                throw new SaplTestException("provided TestSuite does not contain a Test");
            }

            final var name = getDynamicContainerName(testSuite);

            return TestContainer.from(name,
                    testCases.stream().map(testCase -> TestCase.from(stepConstructor, testSuite, testCase)).toList());
        }).toList();
    }

    private String getDynamicContainerName(final TestSuite testSuite) {
        if (testSuite instanceof UnitTestSuite unitTestSuite) {
            return unitTestSuite.getPolicyName();
        } else if (testSuite instanceof IntegrationTestSuite integrationTestSuite) {
            final var policyResolverConfig = integrationTestSuite.getConfiguration();
            if (policyResolverConfig instanceof PoliciesByIdentifier policiesByIdentifier) {
                return policiesByIdentifier.getIdentifier();
            } else if (policyResolverConfig instanceof PoliciesByInputString policiesByInputString) {
                return String.join(",", policiesByInputString.getPolicies());
            }

            throw new SaplTestException("Unknown type of PolicyResolverConfig");
        }

        throw new SaplTestException("Unknown type of TestSuite");
    }
}
