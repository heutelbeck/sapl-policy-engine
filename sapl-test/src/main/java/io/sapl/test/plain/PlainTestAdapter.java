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
package io.sapl.test.plain;

import io.sapl.test.coverage.TestCoverageRecord;
import io.sapl.test.grammar.antlr.SAPLTestParser.RequirementContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.SaplTestContext;
import io.sapl.test.lang.SaplTestException;
import io.sapl.test.lang.SaplTestParser;
import io.sapl.test.plain.TestEvent.ExecutionCompleted;
import io.sapl.test.plain.TestEvent.ScenarioCompleted;
import lombok.NonNull;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test adapter for programmatic execution of SAPL tests.
 * <p>
 * This adapter is designed for use cases where tests need to be executed
 * programmatically without JUnit, such as in a PAP web application.
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * var config = TestConfiguration.builder().withSaplDocument(SaplDocument.of("myPolicy", policySource))
 *         .withSaplTestDocument(SaplTestDocument.of("myTest", testSource))
 *         .withDefaultAlgorithm(CombiningAlgorithm.DENY_OVERRIDES).build();
 *
 * var adapter = new PlainTestAdapter();
 *
 * // Synchronous execution
 * var results = adapter.execute(config);
 * System.out.println("All passed: " + results.allPassed());
 *
 * // Reactive execution with progress events
 * adapter.executeReactive(config).subscribe(event -> {
 *     if (event instanceof ScenarioCompleted sc) {
 *         System.out.println("Completed: " + sc.result().fullName());
 *     }
 * });
 * }</pre>
 */
public class PlainTestAdapter {

    /**
     * Executes tests synchronously, blocking until all tests complete.
     *
     * @param config the test configuration
     * @return the aggregated test results
     */
    public PlainTestResults execute(@NonNull TestConfiguration config) {
        var lastEvent = executeReactive(config).blockLast();
        if (lastEvent instanceof ExecutionCompleted completed) {
            return completed.results();
        }
        // Should not happen, but return empty results if it does
        return PlainTestResults.from(List.of(), Map.of());
    }

    /**
     * Executes tests reactively, emitting events as tests progress.
     * <p>
     * Events emitted:
     * <ul>
     * <li>{@link ScenarioCompleted} - after each scenario completes</li>
     * <li>{@link ExecutionCompleted} - once at the end with aggregated results</li>
     * </ul>
     *
     * @param config the test configuration
     * @return a flux of test events
     */
    public Flux<TestEvent> executeReactive(@NonNull TestConfiguration config) {
        return Flux.create(sink -> {
            var results  = new ArrayList<ScenarioResult>();
            var coverage = new HashMap<String, TestCoverageRecord>();

            for (var testDoc : config.saplTestDocuments()) {
                var docResults = executeTestDocument(testDoc, config);
                results.addAll(docResults);

                // Check fail-fast
                if (config.failFast()) {
                    var hasFailure = docResults.stream().anyMatch(r -> r.status() != TestStatus.PASSED);
                    if (hasFailure) {
                        // Emit completed events for what we have
                        for (var result : docResults) {
                            sink.next(new ScenarioCompleted(result));
                        }
                        break;
                    }
                }

                // Emit events for each result
                for (var result : docResults) {
                    sink.next(new ScenarioCompleted(result));
                }
            }

            // Emit final results
            var finalResults = PlainTestResults.from(results, coverage);
            sink.next(new ExecutionCompleted(finalResults));
            sink.complete();
        });
    }

    private List<ScenarioResult> executeTestDocument(SaplTestDocument testDoc, TestConfiguration config) {
        var results = new ArrayList<ScenarioResult>();

        try {
            var parseTree = SaplTestParser.parse(testDoc.sourceCode());
            results.addAll(executeParseTree(testDoc, parseTree, config));
        } catch (SaplTestException e) {
            // Parse error - create error result for the whole document
            results.add(ScenarioResult.error(testDoc.id(), testDoc.name(), "Parse Error", Duration.ZERO, e, null));
        }

        return results;
    }

    private List<ScenarioResult> executeParseTree(SaplTestDocument testDoc, SaplTestContext parseTree,
            TestConfiguration config) {
        var results = new ArrayList<ScenarioResult>();

        for (var requirement : parseTree.requirement()) {
            var reqResults = executeRequirement(testDoc, requirement, config);
            results.addAll(reqResults);

            // Check fail-fast within document
            if (config.failFast()) {
                var hasFailure = reqResults.stream().anyMatch(r -> r.status() != TestStatus.PASSED);
                if (hasFailure) {
                    break;
                }
            }
        }

        return results;
    }

    private List<ScenarioResult> executeRequirement(SaplTestDocument testDoc, RequirementContext requirement,
            TestConfiguration config) {
        var results     = new ArrayList<ScenarioResult>();
        var interpreter = new ScenarioInterpreter(config);

        for (var scenario : requirement.scenario()) {
            var result = interpreter.execute(testDoc, requirement, scenario);
            results.add(result);

            // Check fail-fast within requirement
            if (config.failFast() && result.status() != TestStatus.PASSED) {
                break;
            }
        }

        return results;
    }
}
