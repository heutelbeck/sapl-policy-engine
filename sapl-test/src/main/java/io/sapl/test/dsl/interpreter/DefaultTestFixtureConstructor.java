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

package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sapltest.CustomFunctionLibrary;
import io.sapl.test.grammar.sapltest.FixtureRegistration;
import io.sapl.test.grammar.sapltest.Given;
import io.sapl.test.grammar.sapltest.GivenStep;
import io.sapl.test.grammar.sapltest.Pip;
import io.sapl.test.grammar.sapltest.SaplFunctionLibrary;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
class DefaultTestFixtureConstructor {

    private final DocumentInterpreter        documentInterpreter;
    private final PdpConfigurationHandler    pdpConfigurationHandler;
    private final FunctionLibraryInterpreter functionLibraryInterpreter;
    private final ReflectionHelper           reflectionHelper;

    SaplTestFixture constructTestFixture(final Given given, final List<GivenStep> givenSteps) {
        if (given == null) {
            throw new SaplTestException("Given is null");
        }

        var saplTestFixture = documentInterpreter.getFixtureFromDocument(given.getDocument());

        if (saplTestFixture == null) {
            throw new SaplTestException("TestFixture is null");
        }

        if (saplTestFixture instanceof SaplIntegrationTestFixture integrationTestFixture) {
            saplTestFixture = pdpConfigurationHandler.applyPdpConfigurationToFixture(integrationTestFixture,
                    given.getPdpVariables(), given.getPdpCombiningAlgorithm());
        }

        if (givenSteps != null) {
            final var fixtureRegistrations = givenSteps.stream().filter(FixtureRegistration.class::isInstance)
                    .map(FixtureRegistration.class::cast).toList();

            handleFixtureRegistrations(saplTestFixture, fixtureRegistrations);
        }

        return saplTestFixture;
    }

    @SneakyThrows
    private void handleFixtureRegistrations(final SaplTestFixture fixture,
            final List<FixtureRegistration> fixtureRegistrations) {
        for (var fixtureRegistration : fixtureRegistrations) {
            if (fixtureRegistration instanceof SaplFunctionLibrary saplFunctionLibrary) {
                final var library = functionLibraryInterpreter.getFunctionLibrary(saplFunctionLibrary.getLibrary());
                fixture.registerFunctionLibrary(library);
            } else if (fixtureRegistration instanceof CustomFunctionLibrary customFunctionLibrary) {
                final var functionLibrary = reflectionHelper.constructInstanceOfClass(customFunctionLibrary.getFqn());
                fixture.registerFunctionLibrary(functionLibrary);
            } else if (fixtureRegistration instanceof Pip pip) {
                final var customPip = reflectionHelper.constructInstanceOfClass(pip.getFqn());
                fixture.registerPIP(customPip);
            } else {
                throw new SaplTestException("Unknown type of FixtureRegistration");
            }
        }
    }
}
