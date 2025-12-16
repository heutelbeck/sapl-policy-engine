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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import io.sapl.test.grammar.antlr.SAPLTestParser.RequirementContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.SaplTestContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ScenarioContext;
import io.sapl.test.lang.SaplTestParser;
import io.sapl.test.lang.SaplTestRunner;

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

    /**
     * JUnit 5 test factory that discovers and creates dynamic tests from
     * {@code .sapltest} files.
     *
     * @return list of dynamic test containers, one per {@code .sapltest} file
     */
    @TestFactory
    @DisplayName("SAPLTest")
    public List<DynamicContainer> getTests() {
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

    private DynamicContainer createTestContainer(String relativePath) {
        var filePath = Path.of(TestDiscoveryHelper.RESOURCES_ROOT, relativePath);
        var uri      = filePath.toUri();

        try {
            var content  = Files.readString(filePath);
            var saplTest = SaplTestParser.parse(content);
            var tests    = buildDynamicTests(saplTest);
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

    private Stream<DynamicNode> buildDynamicTests(SaplTestContext saplTest) {
        return saplTest.requirement().stream().map(this::buildRequirementContainer);
    }

    private DynamicContainer buildRequirementContainer(RequirementContext requirement) {
        var name      = SaplTestRunner.stripQuotes(requirement.name.getText());
        var scenarios = requirement.scenario().stream().map(this::buildScenarioTest);
        return DynamicContainer.dynamicContainer(name, scenarios);
    }

    private DynamicTest buildScenarioTest(ScenarioContext scenario) {
        var name = SaplTestRunner.stripQuotes(scenario.name.getText());
        // TODO: Actually execute the test via SaplTestRunner
        return DynamicTest.dynamicTest(name, () -> {
            // Hollow shell - just pass for now
        });
    }

}
