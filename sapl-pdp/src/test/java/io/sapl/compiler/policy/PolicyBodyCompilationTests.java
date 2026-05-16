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
package io.sapl.compiler.policy;

import io.sapl.api.model.*;
import io.sapl.api.stream.Stream;
import io.sapl.ast.Outcome;
import io.sapl.ast.PolicyBody;
import io.sapl.ast.Statement;
import io.sapl.ast.VoterMetadata;
import io.sapl.attributes.broker.api.TestAttributeBroker;
import io.sapl.compiler.document.AstTransformer;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteResultWithCoverage;
import io.sapl.util.CoverageEvaluator;
import io.sapl.util.ExpressionEvaluator;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.model.Coverage;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;

import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PolicyCompiler: Body Compilation")
class PolicyBodyCompilationTests {

    private static final AstTransformer TRANSFORMER   = new AstTransformer();
    private static final VoterMetadata  STUB_METADATA = stubMetadata();

    record TestCase(
            String description,
            String bodyContent,
            Map<String, Value> variables,
            Map<String, Value> attributes,
            Value expectedPureValue,
            Value expectedStreamValue,
            long expectedConditionCount,
            int expectedHitCount,
            List<Long> expectedHitIndices) {

        static TestCase pureOnly(String desc, String body, Value expectedPure, long condCount, int hitCount,
                Long... hitIndices) {
            return new TestCase(desc, body, Map.of(), Map.of(), expectedPure, Value.TRUE, condCount, hitCount,
                    hitIndices.length > 0 ? List.of(hitIndices) : List.of());
        }

        static TestCase pureWithVars(String desc, String body, Map<String, Value> vars, Value expectedPure,
                long condCount, int hitCount, Long... hitIndices) {
            return new TestCase(desc, body, vars, Map.of(), expectedPure, Value.TRUE, condCount, hitCount,
                    hitIndices.length > 0 ? List.of(hitIndices) : List.of());
        }

        static TestCase streamOnly(String desc, String body, String attrName, Value attrValue, Value expectedStream,
                long condCount, int hitCount, Long... hitIndices) {
            return new TestCase(desc, body, Map.of(), Map.of(attrName, attrValue), Value.TRUE, expectedStream,
                    condCount, hitCount, hitIndices.length > 0 ? List.of(hitIndices) : List.of());
        }

        static TestCase mixed(String desc, String body, Map<String, Value> vars, String attrName, Value attrValue,
                Value expectedPure, Value expectedStream, long condCount, int hitCount, Long... hitIndices) {
            return new TestCase(desc, body, vars, Map.of(attrName, attrValue), expectedPure, expectedStream, condCount,
                    hitCount, hitIndices.length > 0 ? List.of(hitIndices) : List.of());
        }

        @Override
        public @NonNull String toString() {
            return description;
        }
    }

    record ErrorTestCase(
            String description,
            String bodyContent,
            Map<String, Value> variables,
            String attrName,
            String attrError,
            String expectedErrorFragment,
            boolean errorInPureSection,
            int expectedHitCount) {

        static ErrorTestCase pureError(String desc, String body, String errorFragment, int hitCount) {
            return new ErrorTestCase(desc, body, Map.of(), null, null, errorFragment, true, hitCount);
        }

        static ErrorTestCase pureErrorWithVars(String desc, String body, Map<String, Value> vars, String errorFragment,
                int hitCount) {
            return new ErrorTestCase(desc, body, vars, null, null, errorFragment, true, hitCount);
        }

        static ErrorTestCase streamError(String desc, String body, String attrName, String attrError,
                String errorFragment, int hitCount) {
            return new ErrorTestCase(desc, body, Map.of(), attrName, attrError, errorFragment, false, hitCount);
        }

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * Script step driving the parallel-evaluator stream re-emission tests.
     * {@link Publish} pushes a value to the shared broker; {@link ExpectValue},
     * {@link ExpectError}, and {@link ExpectNoValue} assert what both
     * evaluator streams emit at that point.
     */
    sealed interface Step {
        record Publish(String attributeName, Value value) implements Step {}

        record ExpectValue(Value value) implements Step {}

        record ExpectError() implements Step {}

        record ExpectNoValue() implements Step {}
    }

    record StreamTestCase(String description, String bodyContent, List<Step> script) {

        @Override
        public @NonNull String toString() {
            return description;
        }
    }

    private static PolicyBody parsePolicyBody(String bodyContent) {
        if (bodyContent == null || bodyContent.isBlank()) {
            return new PolicyBody(List.of(), TEST_LOCATION);
        }
        val policySource = """
                policy "test"
                permit
                    %s
                """.formatted(bodyContent);
        val document     = DocumentCompiler.parseDocument(policySource);
        val element      = document.sapl().policyElement();
        if (element instanceof PolicyOnlyElementContext policyOnly) {
            val policyCtx = policyOnly.policy();
            if (policyCtx.policyBody() != null) {
                val statements = policyCtx.policyBody().statements.stream().map(s -> (Statement) TRANSFORMER.visit(s))
                        .toList();
                return new PolicyBody(statements, TEST_LOCATION);
            }
        }
        return new PolicyBody(List.of(), TEST_LOCATION);
    }

    private static Value evaluatePureSection(CompiledExpression expr) {
        return switch (expr) {
        case Value v          -> v;
        case PureOperator p   -> p.evaluate(evaluationContext());
        case StreamOperator s -> Value.error("Pure section unexpectedly contained a StreamOperator: " + s);
        };
    }

    private static void verifySplitBodyCompilation(TestCase tc) throws InterruptedException {
        val body    = parsePolicyBody(tc.bodyContent());
        val vars    = toObjectValue(tc.variables());
        val compCtx = compilationContext(vars);

        val conditions    = PolicyCompiler.compileConditions(body.statements(), compCtx);
        val pureSection   = PolicyCompiler.compilePureSectionOfBodyExpression(conditions, body);
        val streamSection = PolicyCompiler.compileStreamingSectionOfBodyExpression(conditions, body);

        // Pure section evaluates with no attribute deps.
        val pureValue = evaluatePureSection(pureSection);
        assertThat(pureValue).as("pure section value").isEqualTo(tc.expectedPureValue());

        val coverageVoter = new CoverageVoter.Lazy(conditions, Vote.abstain(STUB_METADATA), STUB_METADATA);

        try (val broker = new TestAttributeBroker()) {
            // Prime every attribute the test case declares so the gate fires
            // immediately on open with the bound value already in the snapshot.
            for (val entry : tc.attributes().entrySet()) {
                broker.register(entry.getKey(), entry.getValue());
            }
            try (val expr = ExpressionEvaluator.evaluate(streamSection, broker);
                    val coverage = CoverageEvaluator.evaluate(coverageVoter, broker)) {
                val streamValue = expr.awaitNext();
                assertThat(streamValue).as("streaming section value").isEqualTo(tc.expectedStreamValue());

                val bodyCoverage = ((Coverage.PolicyCoverage) coverage.awaitNext().coverage()).bodyCoverage();
                assertThat(bodyCoverage.numberOfConditions()).as("condition count")
                        .isEqualTo(tc.expectedConditionCount());
                assertThat(bodyCoverage.hits()).as("hit count").hasSize(tc.expectedHitCount());
                if (!tc.expectedHitIndices().isEmpty()) {
                    assertThat(bodyCoverage.hits().stream().map(Coverage.ConditionHit::statementId).toList())
                            .as("hit indices").isEqualTo(tc.expectedHitIndices());
                }
            }
        }
    }

    @Nested
    @DisplayName("Single emission scenarios")
    class SingleEmissionTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("testCases")
        @DisplayName("pure and streaming sections compile correctly")
        void whenBodyCompiledThenBothSectionsCorrect(TestCase tc) throws InterruptedException {
            verifySplitBodyCompilation(tc);
        }

        static List<TestCase> testCases() {
            return List.of(
                    // Empty/trivial bodies - both sections return TRUE
                    TestCase.pureOnly("empty body returns TRUE", "", Value.TRUE, 0L, 0),
                    TestCase.pureOnly("only VarDefs returns TRUE", "var x = 1; var y = 2;", Value.TRUE, 0L, 0),

                    // Value stratum - single condition (pure only, no streaming)
                    TestCase.pureOnly("single true condition", "true;", Value.TRUE, 1L, 1, 0L),
                    TestCase.pureOnly("single false condition", "false;", Value.FALSE, 1L, 1, 0L),
                    TestCase.pureOnly("constant expression true (1 == 1)", "1 == 1;", Value.TRUE, 1L, 1, 0L),
                    TestCase.pureOnly("constant expression false (1 == 2)", "1 == 2;", Value.FALSE, 1L, 1, 0L),

                    // Pure stratum - single condition (pure only, no streaming)
                    TestCase.pureWithVars("pure variable true", "flag;", Map.of("flag", Value.TRUE), Value.TRUE, 1L, 1,
                            0L),
                    TestCase.pureWithVars("pure variable false", "flag;", Map.of("flag", Value.FALSE), Value.FALSE, 1L,
                            1, 0L),

                    // Stream stratum - single condition (streaming only, pure is TRUE)
                    TestCase.streamOnly("stream emits true", "<test.attr>;", "test.attr", Value.TRUE, Value.TRUE, 1L, 1,
                            0L),
                    TestCase.streamOnly("stream emits false", "<test.attr>;", "test.attr", Value.FALSE, Value.FALSE, 1L,
                            1, 0L),

                    // Multiple conditions - all true (pure only)
                    TestCase.pureOnly("three true conditions", "true; 1 == 1; 2 > 1;", Value.TRUE, 3L, 3, 0L, 1L, 2L),

                    // Short-circuit behavior (pure only)
                    TestCase.pureOnly("first false short-circuits", "false; true; true;", Value.FALSE, 3L, 1, 0L),
                    TestCase.pureOnly("middle false short-circuits", "true; false; true;", Value.FALSE, 3L, 2, 0L, 1L),

                    // Mixed strata - values and pures (pure only)
                    TestCase.pureWithVars("values and pures - all true", "flag; true; otherFlag;",
                            Map.of("flag", Value.TRUE, "otherFlag", Value.TRUE), Value.TRUE, 3L, 3),
                    TestCase.pureWithVars("value false short-circuits before pure", "flag; false; otherFlag;",
                            Map.of("flag", Value.TRUE, "otherFlag", Value.error("should not see")), Value.FALSE, 3L, 2,
                            0L, 1L),

                    // Mixed strata - pures and streams (separate expectations)
                    TestCase.mixed("pure and stream - all true", "flag; <test.attr>;", Map.of("flag", Value.TRUE),
                            "test.attr", Value.TRUE, Value.TRUE, Value.TRUE, 2L, 2),
                    TestCase.mixed("pure false, stream true", "flag; <test.attr>;", Map.of("flag", Value.FALSE),
                            "test.attr", Value.TRUE, Value.FALSE, Value.TRUE, 2L, 1, 0L),

                    // VarDef interaction (constant folding - pure only)
                    TestCase.pureOnly("VarDef used in condition (constant folded)", "var x = true; x;", Value.TRUE, 1L,
                            1, 0L),
                    TestCase.pureOnly("multiple VarDefs (constant folded)", "var x = 5; var y = 5; x == y;", Value.TRUE,
                            1L, 1));
        }
    }

    @Nested
    @DisplayName("Error scenarios")
    class ErrorTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("errorCases")
        @DisplayName("errors propagate through appropriate section")
        void whenErrorThenAppropiateSectionReturnsError(ErrorTestCase tc) throws InterruptedException {
            val body    = parsePolicyBody(tc.bodyContent());
            val vars    = toObjectValue(tc.variables());
            val compCtx = compilationContext(vars);

            val conditions    = PolicyCompiler.compileConditions(body.statements(), compCtx);
            val pureSection   = PolicyCompiler.compilePureSectionOfBodyExpression(conditions, body);
            val streamSection = PolicyCompiler.compileStreamingSectionOfBodyExpression(conditions, body);

            if (tc.errorInPureSection()) {
                val pureValue = evaluatePureSection(pureSection);
                assertThat(pureValue).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message())
                        .asString().containsIgnoringCase(tc.expectedErrorFragment());
            }

            val coverageVoter = new CoverageVoter.Lazy(conditions, Vote.abstain(STUB_METADATA), STUB_METADATA);

            try (val broker = new TestAttributeBroker()) {
                if (tc.attrName() != null) {
                    broker.register(tc.attrName(), Value.error(tc.attrError()));
                }
                try (val expr = ExpressionEvaluator.evaluate(streamSection, broker);
                        val coverage = CoverageEvaluator.evaluate(coverageVoter, broker)) {
                    if (!tc.errorInPureSection()) {
                        val streamValue = expr.awaitNext();
                        assertThat(streamValue).isInstanceOf(ErrorValue.class)
                                .extracting(v -> ((ErrorValue) v).message()).asString()
                                .containsIgnoringCase(tc.expectedErrorFragment());
                    }

                    val cov  = coverage.awaitNext();
                    val vote = cov.voteResult().vote();
                    assertThat(vote).isNotNull();
                    assertThat(vote.errors()).isNotEmpty().first().extracting(e -> ((ErrorValue) e).message())
                            .asString().containsIgnoringCase(tc.expectedErrorFragment());
                    val bodyCoverage = ((Coverage.PolicyCoverage) cov.coverage()).bodyCoverage();
                    assertThat(bodyCoverage.hits()).hasSize(tc.expectedHitCount());
                }
            }
        }

        static List<ErrorTestCase> errorCases() {
            // Note: VarDef redefinition is a compile-time exception, tested separately
            return List.of(ErrorTestCase.pureError("non-boolean condition", "42;", "boolean", 1),
                    ErrorTestCase.pureErrorWithVars("pure evaluates to error", "brokenVar;",
                            Map.of("brokenVar", Value.error("intentional")), "intentional", 1),
                    ErrorTestCase.streamError("stream emits error", "<test.attr>;", "test.attr", "stream failure",
                            "stream failure", 1));
        }

        @Test
        @DisplayName("VarDef redefinition throws at compile time")
        void whenVarDefRedefinitionThenCompileTimeException() {
            val body       = parsePolicyBody("var x = 1; var x = 2;");
            val compCtx    = compilationContext();
            val statements = body.statements();
            assertThatThrownBy(() -> PolicyCompiler.compileConditions(statements, compCtx))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining("redefine");
        }
    }

    @Nested
    @DisplayName("Stream re-emission scenarios")
    class StreamReEmissionTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("reEmissionCases")
        @DisplayName("streaming section re-emissions work correctly")
        void whenStreamReEmitsThenStreamingSectionEmitsSequence(StreamTestCase tc) throws InterruptedException {
            val body    = parsePolicyBody(tc.bodyContent());
            val compCtx = compilationContext();

            val conditions    = PolicyCompiler.compileConditions(body.statements(), compCtx);
            val streamSection = PolicyCompiler.compileStreamingSectionOfBodyExpression(conditions, body);
            val pureSection   = PolicyCompiler.compilePureSectionOfBodyExpression(conditions, body);

            // No pure conditions in these tests; streaming section must be a StreamOperator
            assertThat(pureSection).isEqualTo(Value.TRUE);
            assertThat(streamSection).isInstanceOf(StreamOperator.class);

            val coverageVoter = new CoverageVoter.Lazy(conditions, Vote.abstain(STUB_METADATA), STUB_METADATA);

            try (val broker = new TestAttributeBroker()) {
                // Register a PIP for every attribute the script publishes to so the
                // broker waits for values rather than auto-firing UNDEFINED on open.
                for (val step : tc.script()) {
                    if (step instanceof Step.Publish(var name, var ignored)) {
                        broker.register(name);
                    }
                }
                try (val expr = ExpressionEvaluator.evaluate(streamSection, broker);
                        val coverage = CoverageEvaluator.evaluate(coverageVoter, broker)) {
                    runScript(tc, broker, expr, coverage);
                }
            }
        }

        static List<StreamTestCase> reEmissionCases() {
            return List.of(
                    new StreamTestCase("single stream re-emits three values", "<test.attr>;",
                            List.of(new Step.Publish("test.attr", Value.TRUE), new Step.ExpectValue(Value.TRUE),
                                    new Step.Publish("test.attr", Value.FALSE), new Step.ExpectValue(Value.FALSE),
                                    new Step.Publish("test.attr", Value.TRUE), new Step.ExpectValue(Value.TRUE),
                                    new Step.ExpectNoValue())),
                    new StreamTestCase("chained streams - first stream changes", "<a.attr>; <b.attr>;",
                            List.of(new Step.Publish("a.attr", Value.TRUE), new Step.ExpectNoValue(),
                                    new Step.Publish("b.attr", Value.TRUE), new Step.ExpectValue(Value.TRUE),
                                    new Step.Publish("a.attr", Value.TRUE), new Step.ExpectValue(Value.TRUE),
                                    new Step.Publish("a.attr", Value.FALSE), new Step.ExpectValue(Value.FALSE),
                                    new Step.ExpectNoValue())));
        }
    }

    private static VoterMetadata stubMetadata() {
        return new VoterMetadata() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public String pdpId() {
                return "test";
            }

            @Override
            public String configurationId() {
                return "test";
            }

            @Override
            public Outcome outcome() {
                return Outcome.PERMIT;
            }

            @Override
            public boolean hasConstraints() {
                return false;
            }
        };
    }

    /**
     * Walks the script of a {@link StreamTestCase}, dispatching each
     * {@link Step.Publish} to the shared broker and asserting the
     * corresponding observation against both the streaming-section and
     * coverage-pathway streams. {@link Step.ExpectValue} and
     * {@link Step.ExpectError} consume one emission from each stream;
     * {@link Step.ExpectNoValue} probes both with non-blocking tryNext.
     * Body value of the coverage result is the last condition hit
     * (or {@link Value#TRUE} when the body has no hits).
     */
    private static void runScript(StreamTestCase tc, TestAttributeBroker broker, Stream<Value> expr,
            Stream<VoteResultWithCoverage> coverage) throws InterruptedException {
        int idx = 0;
        for (val step : tc.script()) {
            val pos = "step " + idx + " (" + step + ")";
            switch (step) {
            case Step.Publish(var name, var value) -> broker.publishByName(name, value);
            case Step.ExpectValue(var expected)    -> {
                assertThat(expr.awaitNext()).as("expr at " + pos).isEqualTo(expected);
                assertThat(coverageBodyValue(coverage.awaitNext())).as("coverage at " + pos).isEqualTo(expected);
            }
            case Step.ExpectError ignored          -> {
                assertThat(expr.awaitNext()).as("expr at " + pos).isInstanceOf(ErrorValue.class);
                assertThat(coverageBodyValue(coverage.awaitNext())).as("coverage at " + pos)
                        .isInstanceOf(ErrorValue.class);
            }
            case Step.ExpectNoValue ignored        -> {
                assertThat(expr.tryNext()).as("expr at " + pos).isNotInstanceOf(Poll.Value.class);
                assertThat(coverage.tryNext()).as("coverage at " + pos).isNotInstanceOf(Poll.Value.class);
            }
            }
            idx++;
        }
    }

    private static Value coverageBodyValue(VoteResultWithCoverage r) {
        val hits = ((Coverage.PolicyCoverage) r.coverage()).bodyCoverage().hits();
        return hits.isEmpty() ? Value.TRUE : hits.getLast().result();
    }
}
