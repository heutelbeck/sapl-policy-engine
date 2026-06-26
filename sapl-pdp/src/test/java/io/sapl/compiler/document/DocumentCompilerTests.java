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
package io.sapl.compiler.document;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

@DisplayName("DocumentCompiler")
class DocumentCompilerTests {

    private CompilationContext ctx;

    @BeforeEach
    void setUp() {
        val functionBroker = mock(FunctionBroker.class);
        ctx = new CompilationContext(functionBroker);
    }

    @Nested
    @DisplayName("parseDocument")
    class ParseDocumentTests {

        @Test
        @DisplayName("returns valid document for correct policy")
        void whenValidPolicyThenDocumentIsValid() {
            val document = DocumentCompiler.parseDocument("policy \"test\" permit");

            assertThat(document.isInvalid()).isFalse();
            assertThat(document.errorMessage()).isEqualTo("OK");
        }

        @Test
        @DisplayName("returns invalid document for syntax errors")
        void whenInvalidSyntaxThenDocumentIsInvalid() {
            val document = DocumentCompiler.parseDocument("xyz");

            assertThat(document.isInvalid()).isTrue();
        }

        @Test
        @DisplayName("identifies policy type correctly")
        void whenSinglePolicyThenTypeIsPolicy() {
            val document = DocumentCompiler.parseDocument("policy \"single-policy\" deny");

            assertThat(document.isInvalid()).isFalse();
            assertThat(document.type()).isEqualTo(DocumentType.POLICY);
        }

        @Test
        @DisplayName("identifies policy set type correctly")
        void whenPolicySetThenTypeIsPolicySet() {
            val policySetDefinition = """
                    set "test-set"
                    priority deny or abstain errors propagate
                    policy "inner-policy" permit
                    """;
            val document            = DocumentCompiler.parseDocument(policySetDefinition);

            assertThat(document.isInvalid()).isFalse();
            assertThat(document.type()).isEqualTo(DocumentType.POLICY_SET);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidPolicyDefinitions")
        @DisplayName("detects validation errors")
        void whenValidationErrorThenDocumentIsInvalid(String description, String policyDefinition) {
            val document = DocumentCompiler.parseDocument(policyDefinition);

            assertThat(document.isInvalid()).isTrue();
        }

        static Stream<Arguments> invalidPolicyDefinitions() {
            return Stream.of(
                    arguments("regex type mismatch", "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432"),
                    arguments("reserved var 'subject'", "policy \"p\" permit var subject = {};"),
                    arguments("reserved var 'action'", "policy \"p\" permit var action = {};"),
                    arguments("reserved var 'resource'", "policy \"p\" permit var resource = {};"),
                    arguments("reserved var 'environment'", "policy \"p\" permit var environment = {};"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("validPolicyDefinitions")
        @DisplayName("parses valid policy variants")
        void whenValidPolicyVariantThenDocumentIsValid(String description, String policyDefinition) {
            val document = DocumentCompiler.parseDocument(policyDefinition);

            assertThat(document.isInvalid()).isFalse();
        }

        static Stream<Arguments> validPolicyDefinitions() {
            return Stream.of(arguments("policy with clause", "policy \"test\" permit true;"),
                    arguments("policy with transform", "policy \"test\" permit transform null"),
                    arguments("policy with obligation", "policy \"test\" permit obligation null"),
                    arguments("policy with advice", "policy \"test\" permit advice null"),
                    arguments("policy with import", "import simple.append policy \"test\" permit"),
                    arguments("policy with import alias", "import simple.append as concat policy \"test\" permit"),
                    arguments("policy with multiple imports",
                            "import simple.length import simple.append policy \"test\" permit"),
                    arguments("policy with complex expression", """
                            policy "complex"
                            permit
                                resource.type == "document";
                                var owner = resource.owner;
                                subject.id == owner;
                            """),
                    arguments("policy with attribute finder",
                            "policy \"test\" permit \"test\".<pip.attribute> == \"test\";"),
                    arguments("policy with environment attribute", "policy \"test\" permit <time.now> != undefined;"));
        }
    }

    @Nested
    @DisplayName("compileDocument")
    class CompileDocumentTests {

        @Test
        @DisplayName("compiles valid policy successfully")
        void whenValidPolicyThenCompiles() {
            assertThatCode(() -> DocumentCompiler.compileDocument("policy \"test\" permit", ctx))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("returns compiled document with metadata")
        void whenValidPolicyThenReturnsCompiledDocument() {
            val compiled = DocumentCompiler.compileDocument("policy \"test\" permit", ctx);

            assertThat(compiled).isNotNull();
            assertThat(compiled.metadata()).isNotNull();
        }

        @Test
        @DisplayName("throws on syntax errors")
        void whenInvalidSyntaxThenThrows() {
            assertThatThrownBy(() -> DocumentCompiler.compileDocument("xyz", ctx))
                    .isInstanceOf(SaplCompilerException.class);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432",
                "policy \"p\" permit var subject = {};", "policy \"p\" permit var action = {};",
                "policy \"p\" permit var resource = {};", "policy \"p\" permit var environment = {};" })
        @DisplayName("throws on validation errors")
        void whenValidationErrorThenThrows(String policyDefinition) {
            assertThatThrownBy(() -> DocumentCompiler.compileDocument(policyDefinition, ctx))
                    .isInstanceOf(SaplCompilerException.class);
        }

        @Test
        @DisplayName("compiles policy set successfully")
        void whenValidPolicySetThenCompiles() {
            val policySetDefinition = """
                    set "test-set"
                    priority deny or abstain errors propagate
                    policy "inner-policy" permit
                    """;

            assertThatCode(() -> DocumentCompiler.compileDocument(policySetDefinition, ctx)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("concurrent compilation")
    class ConcurrentCompilationTests {

        // Single-part attribute name shared by both documents. Document A imports it
        // (so it resolves). Document B does not import it (so it must stay
        // unresolved). If two parseDocument calls share mutable transformer state,
        // B can pick up A's import map and resolve a name the author never imported,
        // or A can lose its import map and fail a valid policy.
        private static final String IMPORTING_DOCUMENT = """
                import library.clash
                policy "importing" permit "x".<clash> == "x";
                """;

        private static final String NON_IMPORTING_DOCUMENT = """
                policy "non-importing" permit "x".<clash> == "x";
                """;

        private static final int ROUNDS  = 2000;
        private static final int THREADS = 8;

        @Test
        @DisplayName("each document resolves single-part names only against its own imports under concurrency")
        void whenTwoDocumentsWithCollidingShortNameCompiledInParallelThenEachResolvesAgainstOwnImports()
                throws Exception {
            val executor = Executors.newFixedThreadPool(THREADS);
            try {
                val tasks   = buildTasks();
                val futures = executor.invokeAll(tasks);
                assertThat(futures).allSatisfy(DocumentCompilerTests::assertSucceeded);
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
                    val document = DocumentCompiler.parseDocument(IMPORTING_DOCUMENT);
                    assertThat(document.isInvalid())
                            .as("imported short name 'clash' must resolve in the importing document").isFalse();
                    return null;
                };
            }
            return () -> {
                val document = DocumentCompiler.parseDocument(NON_IMPORTING_DOCUMENT);
                assertThat(document.isInvalid())
                        .as("un-imported short name 'clash' must stay unresolved in the non-importing document")
                        .isTrue();
                assertThat(document.errorMessage()).contains("clash");
                return null;
            };
        }
    }

    private static void assertSucceeded(Future<Void> future) {
        assertThatCode(future::get).doesNotThrowAnyException();
    }

    @Nested
    @DisplayName("trojan source protection")
    class TrojanSourceProtectionTests {

        private static final char LRI = '\u2066';
        private static final char RLI = '\u2067';
        private static final char PDI = '\u2069';
        private static final char RLO = '\u202E';

        @ParameterizedTest(name = "rejects input containing {0}")
        @ValueSource(chars = { LRI, RLI, PDI, RLO })
        @DisplayName("parseDocument rejects trojan source characters")
        void whenTrojanCharacterInParseDocumentThenThrows(char trojanChar) {
            val malicious = "policy \"te" + trojanChar + "st\" permit";

            assertThatThrownBy(() -> DocumentCompiler.parseDocument(malicious))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining("trojan source");
        }

        @ParameterizedTest(name = "rejects input containing {0}")
        @ValueSource(chars = { LRI, RLI, PDI, RLO })
        @DisplayName("compileDocument rejects trojan source characters")
        void whenTrojanCharacterInCompileDocumentThenThrows(char trojanChar) {
            val malicious = "policy \"te" + trojanChar + "st\" permit";

            assertThatThrownBy(() -> DocumentCompiler.compileDocument(malicious, ctx))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining("trojan source");
        }
    }

    @Nested
    @DisplayName("resource limits")
    class ResourceLimitTests {

        private static final int OVER_SIZE_LIMIT = 2 * 1024 * 1024 + 1;
        private static final int DEEP_NESTING    = 50_000;

        private static String deeplyNestedPolicy() {
            return "policy \"x\" permit " + "(".repeat(DEEP_NESTING) + "true" + ")".repeat(DEEP_NESTING);
        }

        @Test
        @DisplayName("parseDocument rejects an oversized document instead of parsing it")
        void whenDocumentExceedsSizeLimitThenDocumentIsInvalid() {
            val oversized = "a".repeat(OVER_SIZE_LIMIT);

            val document = DocumentCompiler.parseDocument(oversized);

            assertThat(document).satisfies(d -> {
                assertThat(d.isInvalid()).isTrue();
                assertThat(d.errorMessage()).contains("maximum size");
            });
        }

        @Test
        @DisplayName("compileDocument throws on an oversized document")
        void whenDocumentExceedsSizeLimitThenCompileThrows() {
            val oversized = "a".repeat(OVER_SIZE_LIMIT);

            assertThatThrownBy(() -> DocumentCompiler.compileDocument(oversized, ctx))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining("maximum size");
        }

        @Test
        @DisplayName("parseDocument rejects deeply nested input instead of overflowing the stack")
        void whenDeeplyNestedThenDocumentIsInvalid() {
            val nested = deeplyNestedPolicy();

            val document = DocumentCompiler.parseDocument(nested);

            assertThat(document).satisfies(d -> {
                assertThat(d.isInvalid()).isTrue();
                assertThat(d.errorMessage()).contains("nesting");
            });
        }

        @Test
        @DisplayName("compileDocument throws on deeply nested input instead of overflowing the stack")
        void whenDeeplyNestedThenCompileThrows() {
            val nested = deeplyNestedPolicy();

            assertThatThrownBy(() -> DocumentCompiler.compileDocument(nested, ctx))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining("nesting");
        }
    }
}
