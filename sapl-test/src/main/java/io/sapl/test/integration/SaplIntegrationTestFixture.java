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
package io.sapl.test.integration;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents;
import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.steps.GivenStep;
import io.sapl.test.steps.WhenStep;
import lombok.val;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixture for constructing integration test cases that evaluate policies using
 * a full Policy Decision Point with combining algorithms.
 * <p>
 * Unlike unit tests that evaluate single policies in isolation, integration
 * tests use a complete PDP configuration with multiple policies and the
 * configured combining algorithm to determine decisions.
 * <p>
 * Example usage:
 *
 * <pre>
 * var fixture = new SaplIntegrationTestFixture("policiesIT");
 * fixture.constructTestCase().when(AuthorizationSubscription.of("alice", "read", "document")).expectPermit().verify();
 * </pre>
 */
public class SaplIntegrationTestFixture implements SaplTestFixture {

    private static final String ERROR_MOCKS_NOT_SUPPORTED = """
            Integration tests do not support mocking.
            Use SaplUnitTestFixture for unit tests with mocks, or register real PIPs/functions.""";

    private final String resourcePath;

    private final List<Object>       policyInformationPoints       = new ArrayList<>();
    private final List<Class<?>>     staticFunctionLibraries       = new ArrayList<>();
    private final List<Object>       instantiatedFunctionLibraries = new ArrayList<>();
    private final Map<String, Value> variables                     = new HashMap<>();

    private PDPComponents pdpComponents;

    /**
     * Creates a fixture for integration testing policies from a classpath resource
     * directory.
     *
     * @param resourcePath the classpath path to the policy directory (e.g.,
     * "policiesIT"). The directory should contain pdp.json and .sapl files.
     */
    public SaplIntegrationTestFixture(String resourcePath) {
        this.resourcePath = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
    }

    @Override
    public GivenStep constructTestCaseWithMocks() {
        throw new SaplTestException(ERROR_MOCKS_NOT_SUPPORTED);
    }

    @Override
    public WhenStep constructTestCase() {
        return IntegrationTestStepBuilder.newBuilderAtWhenStep(buildPdp(), variables);
    }

    @Override
    public SaplTestFixture registerPIP(Object pip) {
        policyInformationPoints.add(pip);
        return this;
    }

    @Override
    public SaplTestFixture registerPIP(Class<?> pipClass) {
        try {
            var pipInstance = pipClass.getDeclaredConstructor().newInstance();
            policyInformationPoints.add(pipInstance);
        } catch (ReflectiveOperationException exception) {
            throw new SaplTestException("Failed to instantiate PIP class: " + pipClass.getName(), exception);
        }
        return this;
    }

    @Override
    public SaplTestFixture registerFunctionLibrary(Object library) {
        instantiatedFunctionLibraries.add(library);
        return this;
    }

    @Override
    public SaplTestFixture registerFunctionLibrary(Class<?> staticLibrary) {
        staticFunctionLibraries.add(staticLibrary);
        return this;
    }

    @Override
    public SaplTestFixture registerVariable(String key, Value value) {
        if (variables.containsKey(key)) {
            throw new SaplTestException("The variable context already contains a key '%s'.".formatted(key));
        }
        variables.put(key, value);
        return this;
    }

    private PolicyDecisionPoint buildPdp() {
        if (pdpComponents != null) {
            return pdpComponents.pdp();
        }

        val builder = PolicyDecisionPointBuilder.withDefaults().withResourcesSource(resourcePath);

        for (val pip : policyInformationPoints) {
            builder.withPolicyInformationPoint(pip);
        }

        for (val lib : staticFunctionLibraries) {
            builder.withFunctionLibrary(lib);
        }

        for (val lib : instantiatedFunctionLibraries) {
            builder.withFunctionLibraryInstance(lib);
        }

        pdpComponents = builder.build();
        return pdpComponents.pdp();
    }

    /**
     * Disposes resources held by this fixture. Call this method after tests
     * complete to clean up file watchers and background threads.
     */
    public void dispose() {
        if (pdpComponents != null) {
            pdpComponents.dispose();
        }
    }

}
