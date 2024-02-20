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
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.utils.DocumentHelper;

/**
 * The primary entry point to run tests using the
 * {@link io.sapl.test.grammar.sapltest.SAPLTest} DSL. By extending this class
 * you can provide a custom Adapter for a test framework of your choice, see the
 * module io.sapl.test.junit for an example usage.
 *
 * @param <T> The target type of your adapter, which is used in
 *            {@link BaseTestAdapter#convertTestContainerToTargetRepresentation(TestContainer)}
 *            to convert from the high level abstraction {@link TestContainer}
 *            to your target representation.
 */
public abstract class BaseTestAdapter<T> {

    private final SaplTestInterpreter saplTestInterpreter;
    private final TestProvider        testProvider;

    protected BaseTestAdapter(final StepConstructor stepConstructor, final SaplTestInterpreter saplTestInterpreter) {
        this.testProvider        = TestProviderFactory.create(stepConstructor);
        this.saplTestInterpreter = saplTestInterpreter;
    }

    protected BaseTestAdapter(final SaplTestInterpreter saplTestInterpreter,
            final UnitTestPolicyResolver customUnitTestPolicyResolver,
            final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        this.testProvider        = TestProviderFactory.create(customUnitTestPolicyResolver,
                customIntegrationTestPolicyResolver);
        this.saplTestInterpreter = saplTestInterpreter;
    }

    protected BaseTestAdapter(final UnitTestPolicyResolver customUnitTestPolicyResolver,
            final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        this(SaplTestInterpreterFactory.create(), customUnitTestPolicyResolver, customIntegrationTestPolicyResolver);
    }

    protected BaseTestAdapter() {
        this(SaplTestInterpreterFactory.create(), null, null);
    }

    protected T createTest(final String filename) {
        if (filename == null) {
            throw new SaplTestException("provided filename is null");
        }

        final var input = DocumentHelper.findFileOnClasspath(filename);

        if (input == null) {
            throw new SaplTestException("file does not exist");
        }

        return createTestContainerAndConvertToTargetRepresentation(filename, input);
    }

    protected T createTest(final String identifier, final String testDefinition) {
        if (identifier == null || testDefinition == null) {
            throw new SaplTestException("identifier or input is null");
        }

        return createTestContainerAndConvertToTargetRepresentation(identifier, testDefinition);
    }

    private T createTestContainerAndConvertToTargetRepresentation(final String identifier,
            final String testDefinition) {
        final var saplTest = saplTestInterpreter.loadAsResource(testDefinition);

        final var testContainer = TestContainer.from(identifier, testProvider.buildTests(saplTest));

        return convertTestContainerToTargetRepresentation(testContainer);
    }

    protected abstract T convertTestContainerToTargetRepresentation(final TestContainer testContainer);
}
