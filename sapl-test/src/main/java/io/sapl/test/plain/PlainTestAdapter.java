/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.stream.QueueStream;
import io.sapl.api.stream.Stream;
import io.sapl.test.coverage.TestCoverageRecord;
import io.sapl.test.grammar.antlr.SAPLTestParser.RequirementContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.SaplTestContext;
import io.sapl.test.lang.SaplTestParser;
import io.sapl.test.plain.TestEvent.ExecutionCompleted;
import io.sapl.test.plain.TestEvent.ScenarioCompleted;
import lombok.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Test adapter for programmatic execution of SAPL tests.
 * <p>
 * This adapter is designed for use cases where tests need to be executed
 * programmatically without JUnit, such as in a PAP web application.
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * var security = TestConfiguration.builder().withSaplDocument(SaplDocument.of("myPolicy", policySource))
 *         .withSaplTestDocument(SaplTestDocument.of("myTest", testSource))
 *         .withDefaultAlgorithm(new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE)).build();
 *
 * var adapter = new PlainTestAdapter();
 *
 * // Synchronous execution
 * var results = adapter.execute(security);
 * System.out.println("All passed: " + results.allPassed());
 *
 * // Streaming execution with progress events
 * try (var events = adapter.executeStreaming(security)) {
 *     TestEvent event;
 *     while ((event = events.awaitNext()) != null) {
 *         if (event instanceof ScenarioCompleted sc) {
 *             System.out.println("Completed: " + sc.result().fullName());
 *         }
 *     }
 * }
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
        return runScenarios(config, event -> {});
    }

    /**
     * Executes tests and streams events as they progress, for interactive
     * progress reporting. The run executes on a virtual thread so the caller
     * pulls events live via {@link Stream#awaitNext()}; close the stream to
     * stop consuming early.
     * <p>
     * Events emitted:
     * <ul>
     * <li>{@link ScenarioCompleted} - after each scenario completes</li>
     * <li>{@link ExecutionCompleted} - once at the end with aggregated results</li>
     * </ul>
     *
     * @param config the test configuration
     * @return a closeable stream of test events
     */
    public Stream<TestEvent> executeStreaming(@NonNull TestConfiguration config) {
        var stream   = new QueueStream<TestEvent>();
        var producer = Thread.ofVirtual().name("sapl-test-run").start(() -> {
                         try {
                             runScenarios(config, stream::put);
                         } finally {
                             stream.complete();
                         }
                     });
        stream.onClose(producer::interrupt);
        return stream;
    }

    private PlainTestResults runScenarios(@NonNull TestConfiguration config, Consumer<TestEvent> onEvent) {
        var results  = new ArrayList<ScenarioResult>();
        var coverage = new HashMap<String, TestCoverageRecord>();

        for (var testDoc : config.saplTestDocuments()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            var docResults = executeTestDocument(testDoc, config);
            results.addAll(docResults);
            for (var result : docResults) {
                onEvent.accept(new ScenarioCompleted(result));
            }
            if (shouldStopOnFailure(config, docResults)) {
                break;
            }
        }

        for (var result : results) {
            if (result.coverage() != null) {
                coverage.put(result.fullName(), result.coverage());
            }
        }

        var finalResults = PlainTestResults.from(results, coverage);
        onEvent.accept(new ExecutionCompleted(finalResults));
        return finalResults;
    }

    private boolean shouldStopOnFailure(TestConfiguration config, List<ScenarioResult> results) {
        return config.failFast() && results.stream().anyMatch(r -> r.status() != TestStatus.PASSED);
    }

    private List<ScenarioResult> executeTestDocument(SaplTestDocument testDoc, TestConfiguration config) {
        var results = new ArrayList<ScenarioResult>();

        try {
            var parseTree = SaplTestParser.parse(testDoc.sourceCode());
            results.addAll(executeParseTree(testDoc, parseTree, config));
        } catch (RuntimeException e) {
            // Parse error - create error result for the whole document so that a
            // single malformed document cannot abort the aggregated execution.
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
