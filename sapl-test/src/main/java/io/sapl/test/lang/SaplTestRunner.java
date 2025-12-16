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
package io.sapl.test.lang;

import java.util.ArrayList;
import java.util.List;

import io.sapl.test.grammar.antlr.SAPLTestParser.RequirementContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.SaplTestContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ScenarioContext;
import lombok.experimental.UtilityClass;

/**
 * Executes SAPL test definitions.
 * <p>
 * Each scenario is executed with a fresh test fixture instance for proper test
 * isolation.
 * <p>
 * TODO: Full implementation pending migration to ANTLR-based execution.
 */
@UtilityClass
public class SaplTestRunner {

    /**
     * Runs all scenarios in a SAPL test definition.
     *
     * @param test the parsed ANTLR parse tree
     * @return list of results, one per scenario
     */
    public static List<TestResult> run(SaplTestContext test) {
        var results = new ArrayList<TestResult>();
        for (var requirement : test.requirement()) {
            results.addAll(runRequirement(requirement));
        }
        return results;
    }

    private static List<TestResult> runRequirement(RequirementContext requirement) {
        var requirementName = stripQuotes(requirement.name.getText());
        var results         = new ArrayList<TestResult>();

        for (var scenario : requirement.scenario()) {
            results.add(runScenario(requirementName, scenario));
        }
        return results;
    }

    private static TestResult runScenario(String requirementName, ScenarioContext scenario) {
        var scenarioName = stripQuotes(scenario.name.getText());
        try {
            // TODO: Implement full scenario execution
            // For now, mark as pending implementation
            return TestResult.error(requirementName, scenarioName,
                    new UnsupportedOperationException("Scenario execution not yet implemented for ANTLR parser."));
        } catch (Exception exception) {
            return TestResult.error(requirementName, scenarioName, exception);
        }
    }

    /**
     * Strips surrounding quotes from a string token.
     *
     * @param text the string token (e.g., "\"value\"")
     * @return the unquoted string (e.g., "value")
     */
    public static String stripQuotes(String text) {
        if (text == null || text.length() < 2) {
            return text;
        }
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

}
