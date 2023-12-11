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
package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sAPLTest.CustomFunctionLibrary;
import io.sapl.test.grammar.sAPLTest.FixtureRegistration;
import io.sapl.test.grammar.sAPLTest.Pip;
import io.sapl.test.grammar.sAPLTest.SaplFunctionLibrary;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.GivenOrWhenStep;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class DefaultTestFixtureConstructor {

    private final TestSuiteInterpreter       testSuiteInterpreter;
    private final FunctionLibraryInterpreter functionLibraryInterpreter;
    private final ReflectionHelper           reflectionHelper;

    GivenOrWhenStep buildTestFixture(final List<FixtureRegistration> fixtureRegistrations, final TestSuite testSuite,
            final io.sapl.test.grammar.sAPLTest.Object environment, final boolean needsMocks) {
        var saplTestFixture = testSuiteInterpreter.getFixtureFromTestSuite(testSuite, environment);

        if (saplTestFixture == null) {
            throw new SaplTestException("could not build test fixture");
        }

        if (fixtureRegistrations != null) {
            handleFixtureRegistrations(saplTestFixture, fixtureRegistrations);
        }

        final var givenOrWhenStep = needsMocks ? saplTestFixture.constructTestCaseWithMocks()
                : saplTestFixture.constructTestCase();
        return (GivenOrWhenStep) givenOrWhenStep;
    }

    private void handleFixtureRegistrations(final SaplTestFixture fixture,
            final List<FixtureRegistration> fixtureRegistrations) {
        try {
            for (var fixtureRegistration : fixtureRegistrations) {
                if (fixtureRegistration instanceof SaplFunctionLibrary saplFunctionLibrary) {
                    final var library = functionLibraryInterpreter.getFunctionLibrary(saplFunctionLibrary.getLibrary());
                    fixture.registerFunctionLibrary(library);
                } else if (fixtureRegistration instanceof CustomFunctionLibrary customFunctionLibrary) {
                    final var functionLibrary = reflectionHelper
                            .constructInstanceOfClass(customFunctionLibrary.getFqn());
                    fixture.registerFunctionLibrary(functionLibrary);
                } else if (fixtureRegistration instanceof Pip pip) {
                    final var customPip = reflectionHelper.constructInstanceOfClass(pip.getFqn());
                    fixture.registerPIP(customPip);
                } else {
                    throw new SaplTestException("Unknown type of FixtureRegistration");
                }
            }
        } catch (Exception e) {
            throw new SaplTestException(e);
        }
    }
}
