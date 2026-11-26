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
package io.sapl.lsp.sapl.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import tools.jackson.databind.JsonNode;

import io.sapl.lsp.configuration.LSPConfiguration;
import io.sapl.lsp.sapl.TestParsing;
import lombok.val;

@DisplayName("ExpressionEvaluator")
class ExpressionEvaluatorTests {

    @Test
    @DisplayName("request-dependent schemas cannot be inferred from a synthetic subscription")
    void whenSchemaDependsOnTheSubscriptionThenNoRequestValueIsInvented() {
        val document = """
                subject schema {"subject":{"properties":{"wrong":{}}},"alice":{"properties":{"right":{}}}}[(subject)]
                policy "p" permit
                """;

        assertThat(schemaOf(document)).isEmpty();
    }

    @Nested
    @DisplayName("isolation between documents")
    class DocumentIsolationTests {

        // Both documents declare a subject schema that calls the function
        // jsonToVal. The importing document imports the function and calls it by
        // its fully qualified name, so its schema resolves. The other document
        // calls it by its short name without importing it, so its schema must
        // stay unresolved. If the evaluations of two documents shared name
        // resolution state, the second document could resolve a name its author
        // never imported.
        private static final String IMPORTING_DOCUMENT = """
                import json.jsonToVal
                subject schema json.jsonToVal("{}")
                policy "importing" permit
                """;

        private static final String NON_IMPORTING_DOCUMENT = """
                subject schema jsonToVal("{}")
                policy "non-importing" permit
                """;

        private static final int ROUNDS  = 2000;
        private static final int THREADS = 8;

        @Test
        @DisplayName("a schema expression does not resolve names imported by a document evaluated before it")
        void whenDocumentsAreEvaluatedInSequenceThenImportsOfTheFirstDoNotApplyToTheSecond() {
            val importingSchema    = schemaOf(IMPORTING_DOCUMENT);
            val nonImportingSchema = schemaOf(NON_IMPORTING_DOCUMENT);

            assertThat(importingSchema).as("fully qualified name must resolve in the importing document").isPresent();
            assertThat(nonImportingSchema)
                    .as("un-imported short name 'jsonToVal' must stay unresolved in the non-importing document")
                    .isEmpty();
        }

        @Test
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        @DisplayName("each schema expression resolves names only against its own document under concurrency")
        void whenDocumentsAreEvaluatedInParallelThenEachResolvesNamesAgainstItsOwnDocument()
                throws InterruptedException {
            val executor = Executors.newFixedThreadPool(THREADS);
            try {
                val futures = executor.invokeAll(buildTasks());
                assertThat(futures).allSatisfy(ExpressionEvaluatorTests::assertSucceeded);
            } finally {
                executor.shutdownNow();
            }
        }

        private List<Callable<Void>> buildTasks() {
            return IntStream.range(0, ROUNDS).mapToObj(this::alternatingTask).toList();
        }

        private Callable<Void> alternatingTask(int round) {
            if (round % 2 == 0) {
                return () -> {
                    assertThat(schemaOf(IMPORTING_DOCUMENT))
                            .as("fully qualified name must resolve in the importing document").isPresent();
                    return null;
                };
            }
            return () -> {
                assertThat(schemaOf(NON_IMPORTING_DOCUMENT))
                        .as("un-imported short name 'jsonToVal' must stay unresolved in the non-importing document")
                        .isEmpty();
                return null;
            };
        }
    }

    @Nested
    @DisplayName("schema expressions nested beyond the stack")
    class DeepNestingTests {

        private static final int  NESTED_SIGNS = 5_000;
        private static final long LARGE_STACK  = 64L * 1024 * 1024;
        private static final long SMALL_STACK  = 256L * 1024;

        @Test
        @Timeout(30)
        @DisplayName("a schema expression nested too deeply for the stack yields no schema instead of failing the request")
        void whenSchemaExpressionExhaustsTheStackThenNoSchemaIsProposed() throws InterruptedException {
            val document = "subject schema " + "-".repeat(NESTED_SIGNS) + "{}\npolicy \"p\" permit";
            val sapl     = onThreadWithStack(LARGE_STACK, () -> TestParsing.parseSilently(document));

            val schema = onThreadWithStack(SMALL_STACK, () -> ExpressionEvaluator.evaluateExpressionToJsonNode(
                    sapl.schemaStatement().getFirst().schemaExpression, LSPConfiguration.minimal()));

            assertThat(schema).isEmpty();
        }
    }

    @Test
    @DisplayName("A constant schema beyond the source depth limit is refused before folding")
    void whenSchemaSourceExceedsDefaultDepthThenNoSchemaIsProposed() {
        val document = "subject schema {key: 1" + " + 1".repeat(256) + "}\npolicy \"p\" permit";
        assertThat(schemaOf(document)).isEmpty();
    }

    /**
     * Runs work on a thread of its own stack size and returns its result. What the
     * work throws fails the test.
     */
    private static <T> T onThreadWithStack(long stackSize, Supplier<T> work) throws InterruptedException {
        val result  = new AtomicReference<T>();
        val failure = new AtomicReference<Throwable>();
        val thread  = new Thread(null, () -> {
                        try {
                            result.set(work.get());
                        } catch (Throwable thrown) {
                            failure.set(thrown);
                        }
                    }, "stack-of-its-own", stackSize);
        thread.start();
        thread.join();
        assertThat(failure.get()).as("what the work threw").isNull();
        return result.get();
    }

    private static Optional<JsonNode> schemaOf(String document) {
        val sapl = TestParsing.parseSilently(document);
        return ExpressionEvaluator.evaluateExpressionToJsonNode(sapl.schemaStatement().getFirst().schemaExpression,
                LSPConfiguration.minimal());
    }

    private static void assertSucceeded(Future<Void> future) {
        assertThatCode(future::get).doesNotThrowAnyException();
    }

}
