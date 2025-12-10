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
package io.sapl.test;

import io.sapl.api.model.Value;
import io.sapl.test.steps.GivenStep;
import io.sapl.test.steps.WhenStep;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Core interface for SAPL test fixtures.
 * <p>
 * Provides methods for constructing test cases, registering PIPs and function
 * libraries, and setting up variables for policy evaluation.
 */
public interface SaplTestFixture {

    /**
     * Constructs a test case starting from the Given step, allowing mock
     * definitions.
     *
     * @return a {@link GivenStep} for defining mocks before the When step
     */
    GivenStep constructTestCaseWithMocks();

    /**
     * Constructs a test case starting directly from the When step.
     *
     * @return a {@link WhenStep} for defining the authorization subscription
     */
    WhenStep constructTestCase();

    /**
     * Registers a Policy Information Point instance.
     *
     * @param pip the PIP instance to register
     * @return this fixture for chaining
     */
    SaplTestFixture registerPIP(Object pip);

    /**
     * Registers a Policy Information Point by class.
     *
     * @param pipClass the PIP class to instantiate and register
     * @return this fixture for chaining
     */
    SaplTestFixture registerPIP(Class<?> pipClass);

    /**
     * Registers a function library instance.
     *
     * @param library the function library instance to register
     * @return this fixture for chaining
     */
    SaplTestFixture registerFunctionLibrary(Object library);

    /**
     * Registers a function library by class.
     *
     * @param staticLibrary the function library class to register
     * @return this fixture for chaining
     */
    SaplTestFixture registerFunctionLibrary(Class<?> staticLibrary);

    /**
     * Registers a variable for use during policy evaluation.
     *
     * @param key the variable name
     * @param value the variable value
     * @return this fixture for chaining
     */
    SaplTestFixture registerVariable(String key, Value value);

    /**
     * Resolves the base directory for coverage output.
     *
     * @return the path to the coverage output directory
     */
    default Path resolveCoverageBaseDir() {
        var saplSpecificOutputDir = System.getProperty("io.sapl.test.outputDir");
        return Paths.get(Objects.requireNonNullElse(saplSpecificOutputDir, "target")).resolve("sapl-coverage");
    }

}
