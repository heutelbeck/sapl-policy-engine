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

import static io.sapl.compiler.StringsUtil.unquoteString;

import java.util.ArrayList;
import java.util.List;

import io.sapl.test.grammar.antlr.SAPLTestParser.SaplTestContext;

/**
 * Runs SAPL test definitions and produces results.
 * <p>
 * This class interprets the parsed SAPL test AST and executes each scenario,
 * producing a list of test results.
 */
public final class SaplTestRunner {

    private SaplTestRunner() {
        // Utility class
    }

    /**
     * Runs all scenarios in the given test definition.
     *
     * @param ast the parsed test definition
     * @return list of test results, one per scenario
     */
    public static List<TestResult> run(SaplTestContext ast) {
        var results = new ArrayList<TestResult>();

        for (var requirement : ast.requirement()) {
            var requirementName = unquoteString(requirement.name.getText());

            for (var scenario : requirement.scenario()) {
                var scenarioName = unquoteString(scenario.name.getText());

                try {
                    // TODO: Implement actual scenario execution
                    // For now, return an error result indicating execution is not yet implemented
                    results.add(TestResult.error(requirementName, scenarioName,
                            new UnsupportedOperationException("Scenario execution not yet implemented")));
                } catch (Exception e) {
                    results.add(TestResult.error(requirementName, scenarioName, e));
                }
            }
        }

        return results;
    }

}
