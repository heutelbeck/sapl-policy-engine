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
package io.sapl.test.junit;

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.test.grammar.antlr.SAPLTestParser.RequirementContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.SaplTestContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ScenarioContext;
import io.sapl.test.lang.SaplTestParser;
import io.sapl.test.plain.*;
import org.junit.jupiter.api.*;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.compiler.StringsUtil.unquoteString;

/**
 * JUnit 5 test adapter for executing SAPL test definitions.
 * <p>
 * Extend this class and place it in your test sources. All {@code .sapltest}
 * files in {@code src/test/resources} will be discovered and executed as JUnit
 * dynamic tests.
 * <p>
 * Example:
 *
 * <pre>{@code
 * public class SaplTests extends JUnitTestAdapter {
 *     // Empty body is sufficient for basic usage
 * }
 * }</pre>
 * <p>
 * Override {@link #getFixtureRegistrations()} to register function libraries
 * and PIPs for use in tests.
 */
public class JUnitTestAdapter {

    private List<SaplDocument> policies;
    private TestConfiguration  config;

    /**
     * JUnit 5 test factory that discovers and creates dynamic tests from
     * {@code .sapltest} files.
     *
     * @return list of dynamic test containers, one per {@code .sapltest} file
     */
    @TestFactory
    @DisplayName("SAPLTest")
    public List<DynamicContainer> getTests() {
        // Discover policies once for all tests
        policies = discoverPolicies();

        // Create base configuration (will be augmented per test file)
        config = createConfiguration();

        return TestDiscoveryHelper.discoverTests().stream().map(this::createTestContainer).toList();
    }

    /**
     * Override to register function libraries and PIPs for test execution.
     * <p>
     * Example:
     *
     * <pre>{@code
     * @Override
     * protected Map<ImportType, Map<String, Object>> getFixtureRegistrations() {
     *     return Map.of(ImportType.STATIC_FUNCTION_LIBRARY, Map.of("temporal", TemporalFunctionLibrary.class),
     *             ImportType.PIP, Map.of("myPip", new MyPolicyInformationPoint()));
     * }
     * }</pre>
     *
     * @return map of import type to name-to-object registrations
     */
    protected Map<ImportType, Map<String, Object>> getFixtureRegistrations() {
        return Map.of();
    }

    /**
     * Override to specify a custom combining algorithm for integration tests.
     * Default is DENY_OVERRIDES.
     *
     * @return the combining algorithm to use
     */
    protected CombiningAlgorithm getDefaultCombiningAlgorithm() {
        return CombiningAlgorithm.DENY_OVERRIDES;
    }

    /**
     * Override to specify custom policy directories to search.
     * Default searches "policies" and "policiesIT" under src/main/resources.
     *
     * @return list of directory names under src/main/resources to search for
     * policies
     */
    protected List<String> getPolicyDirectories() {
        return List.of("policies", "policiesIT");
    }

    /**
     * Discovers all SAPL policies from configured directories.
     */
    private List<SaplDocument> discoverPolicies() {
        var allPolicies = new ArrayList<SaplDocument>();

        for (var directory : getPolicyDirectories()) {
            var found = PolicyDiscoveryHelper.discoverPolicies(directory);
            allPolicies.addAll(found);
        }

        // Also check root for any policies
        if (allPolicies.isEmpty()) {
            allPolicies.addAll(PolicyDiscoveryHelper.discoverPolicies());
        }

        return allPolicies;
    }

    /**
     * Creates the test configuration with discovered policies.
     */
    private TestConfiguration createConfiguration() {
        var builder = TestConfiguration.builder().withSaplDocuments(policies)
                .withDefaultAlgorithm(getDefaultCombiningAlgorithm());

        // Add function libraries from registrations
        var registrations = getFixtureRegistrations();
        if (registrations.containsKey(ImportType.STATIC_FUNCTION_LIBRARY)) {
            for (var entry : registrations.get(ImportType.STATIC_FUNCTION_LIBRARY).entrySet()) {
                if (entry.getValue() instanceof Class<?> clazz) {
                    builder.withFunctionLibrary(clazz);
                }
            }
        }

        // Add PIPs from registrations
        if (registrations.containsKey(ImportType.PIP)) {
            for (var entry : registrations.get(ImportType.PIP).entrySet()) {
                builder.withPolicyInformationPoint(entry.getValue());
            }
        }

        return builder.build();
    }

    private DynamicContainer createTestContainer(String relativePath) {
        var filePath = Path.of(TestDiscoveryHelper.RESOURCES_ROOT, relativePath);
        var uri      = filePath.toUri();

        try {
            var content      = Files.readString(filePath);
            var saplTest     = SaplTestParser.parse(content);
            var testDocument = new SaplTestDocument(relativePath, relativePath, content);
            var tests        = buildDynamicTests(saplTest, testDocument);
            return DynamicContainer.dynamicContainer(relativePath, uri, tests);
        } catch (IOException exception) {
            return DynamicContainer.dynamicContainer(relativePath, uri,
                    Stream.of(DynamicTest.dynamicTest("Parse Error", () -> {
                        throw new RuntimeException("Failed to read test file: " + relativePath, exception);
                    })));
        } catch (Exception exception) {
            return DynamicContainer.dynamicContainer(relativePath, uri,
                    Stream.of(DynamicTest.dynamicTest("Parse Error", () -> {
                        throw exception;
                    })));
        }
    }

    private Stream<DynamicNode> buildDynamicTests(SaplTestContext saplTest, SaplTestDocument testDocument) {
        return saplTest.requirement().stream().map(req -> buildRequirementContainer(req, testDocument));
    }

    private DynamicContainer buildRequirementContainer(RequirementContext requirement, SaplTestDocument testDocument) {
        var name      = unquoteString(requirement.name.getText());
        var scenarios = requirement.scenario().stream().map(s -> buildScenarioTest(requirement, s, testDocument));
        return DynamicContainer.dynamicContainer(name, scenarios);
    }

    private DynamicTest buildScenarioTest(RequirementContext requirement, ScenarioContext scenario,
            SaplTestDocument testDocument) {
        var name = unquoteString(scenario.name.getText());
        return DynamicTest.dynamicTest(name, () -> executeScenario(requirement, scenario, testDocument));
    }

    /**
     * Executes a single scenario and throws on failure.
     */
    private void executeScenario(RequirementContext requirement, ScenarioContext scenario,
            SaplTestDocument testDocument) {
        var interpreter = new ScenarioInterpreter(config);
        var result      = interpreter.execute(testDocument, requirement, scenario);

        if (result.status() == TestStatus.FAILED) {
            throw new AssertionFailedError(result.failureMessage());
        } else if (result.status() == TestStatus.ERROR) {
            if (result.failureCause() != null) {
                throw new RuntimeException("Test execution error: " + result.failureCause().getMessage(),
                        result.failureCause());
            } else {
                throw new RuntimeException("Test execution error: " + result.failureMessage());
            }
        }
        // PASSED - test succeeds
    }
}
