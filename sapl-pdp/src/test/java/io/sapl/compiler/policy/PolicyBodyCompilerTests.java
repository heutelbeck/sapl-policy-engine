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

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.TracedValue;
import io.sapl.api.model.Value;
import io.sapl.ast.PolicyBody;
import io.sapl.ast.Statement;
import io.sapl.compiler.document.AstTransformer;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.policy.policybody.PolicyBodyCompiler;
import io.sapl.compiler.policy.policybody.TracedValueAndBodyCoverage;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.ATTRIBUTE_BROKER;
import static io.sapl.util.SaplTesting.FUNCTION_BROKER;
import static io.sapl.util.SaplTesting.TEST_LOCATION;
import static io.sapl.util.SaplTesting.attributeBroker;
import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.errorAttributeBroker;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.sequenceBroker;
import static io.sapl.util.SaplTesting.toObjectValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PolicyBodyCompiler")
class PolicyBodyCompilerTests {

    private static final AstTransformer TRANSFORMER = new AstTransformer();

    record TestCase(
            String description,
            String bodyContent,
            Map<String, Value> variables,
            Map<String, Value[]> attributes,
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
            return new TestCase(desc, body, Map.of(), Map.of(attrName, new Value[] { attrValue }), Value.TRUE,
                    expectedStream, condCount, hitCount, hitIndices.length > 0 ? List.of(hitIndices) : List.of());
        }

        static TestCase mixed(String desc, String body, Map<String, Value> vars, String attrName, Value attrValue,
                Value expectedPure, Value expectedStream, long condCount, int hitCount, Long... hitIndices) {
            return new TestCase(desc, body, vars, Map.of(attrName, new Value[] { attrValue }), expectedPure,
                    expectedStream, condCount, hitCount, hitIndices.length > 0 ? List.of(hitIndices) : List.of());
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

    record StreamTestCase(
            String description,
            String bodyContent,
            Map<String, List<Value>> attributeSequences,
            List<Value> expectedSequence) {

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

    private static Value evaluateExpression(CompiledExpression expr, EvaluationContext evalCtx) {
        return switch (expr) {
        case Value v          -> v;
        case PureOperator p   -> p.evaluate(evalCtx);
        case StreamOperator s -> {
            var tv = s.stream().contextWrite(c -> c.put(EvaluationContext.class, evalCtx)).blockFirst();
            yield tv != null ? tv.value() : Value.error("Stream completed without emitting");
        }
        };
    }

    private static void verifySplitBodyCompilation(TestCase tc) {
        val body = parsePolicyBody(tc.bodyContent());

        // Compile body - get both sections (variables are compile-time now)
        var broker  = tc.attributes().isEmpty() ? ATTRIBUTE_BROKER : attributeBroker(tc.attributes());
        var vars    = toObjectValue(tc.variables());
        var compCtx = compilationContext(vars, FUNCTION_BROKER, broker);
        var evalCtx = evaluationContext(broker);

        val compiledBody  = PolicyBodyCompiler.compilePolicyBody(body, compCtx);
        val pureSection   = compiledBody.isApplicable();
        val streamSection = compiledBody.streamingSectionOfBody();

        // Verify pure section independently
        val pureValue = evaluateExpression(pureSection, evalCtx);
        assertThat(pureValue).as("pure section value").isEqualTo(tc.expectedPureValue());

        // Verify streaming section independently
        val streamValue = evaluateExpression(streamSection, evalCtx);
        assertThat(streamValue).as("streaming section value").isEqualTo(tc.expectedStreamValue());

        // Verify coverage pathway (uses separate broker to avoid attribute caching
        // issues)
        var broker2  = tc.attributes().isEmpty() ? ATTRIBUTE_BROKER : attributeBroker(tc.attributes());
        var compCtx2 = compilationContext(vars, FUNCTION_BROKER, broker2);
        var evalCtx2 = evaluationContext(broker2);

        val compiledBody2 = PolicyBodyCompiler.compilePolicyBody(body, compCtx2);
        val coverageFlux  = compiledBody2.coverageStream().contextWrite(c -> c.put(EvaluationContext.class, evalCtx2));

        StepVerifier.create(coverageFlux)
                .assertNext(r -> assertThat(r).satisfies(
                        cov -> assertThat(cov.bodyCoverage().numberOfConditions()).as("condition count")
                                .isEqualTo(tc.expectedConditionCount()),
                        cov -> assertThat(cov.bodyCoverage().hits()).as("hit count").hasSize(tc.expectedHitCount()),
                        cov -> {
                            if (!tc.expectedHitIndices().isEmpty()) {
                                assertThat(cov.bodyCoverage().hits().stream().map(Coverage.ConditionHit::statementId)
                                        .toList()).as("hit indices").isEqualTo(tc.expectedHitIndices());
                            }
                        }))
                .verifyComplete();
    }

    @Nested
    @DisplayName("Single emission scenarios")
    class SingleEmissionTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("testCases")
        @DisplayName("pure and streaming sections compile correctly")
        void whenBodyCompiled_thenBothSectionsCorrect(TestCase tc) {
            verifySplitBodyCompilation(tc);
        }

        static Stream<TestCase> testCases() {
            return Stream.of(
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
        void whenError_thenAppropiateSectionReturnsError(ErrorTestCase tc) {
            val body = parsePolicyBody(tc.bodyContent());
            val vars = toObjectValue(tc.variables());

            var broker  = tc.attrName() != null ? errorAttributeBroker(tc.attrName(), tc.attrError())
                    : ATTRIBUTE_BROKER;
            var compCtx = compilationContext(vars, FUNCTION_BROKER, broker);
            var evalCtx = evaluationContext(broker);

            val compiledBody  = PolicyBodyCompiler.compilePolicyBody(body, compCtx);
            val pureSection   = compiledBody.isApplicable();
            val streamSection = compiledBody.streamingSectionOfBody();

            // Test the appropriate section based on error originates
            if (tc.errorInPureSection()) {
                val pureValue = evaluateExpression(pureSection, evalCtx);
                assertThat(pureValue).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message())
                        .asString().containsIgnoringCase(tc.expectedErrorFragment());
            } else {
                val streamValue = evaluateExpression(streamSection, evalCtx);
                assertThat(streamValue).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message())
                        .asString().containsIgnoringCase(tc.expectedErrorFragment());
            }

            // Verify coverage pathway
            var broker2  = tc.attrName() != null ? errorAttributeBroker(tc.attrName(), tc.attrError())
                    : ATTRIBUTE_BROKER;
            var compCtx2 = compilationContext(vars, FUNCTION_BROKER, broker2);
            var evalCtx2 = evaluationContext(broker2);

            val compiledBody2 = PolicyBodyCompiler.compilePolicyBody(body, compCtx2);
            val coverageFlux  = compiledBody2.coverageStream()
                    .contextWrite(c -> c.put(EvaluationContext.class, evalCtx2));

            StepVerifier.create(coverageFlux)
                    .assertNext(r -> assertThat(r).satisfies(
                            cov -> assertThat(cov.value().value()).isInstanceOf(ErrorValue.class)
                                    .extracting(v -> ((ErrorValue) v).message()).asString()
                                    .containsIgnoringCase(tc.expectedErrorFragment()),
                            cov -> assertThat(cov.bodyCoverage().hits()).hasSize(tc.expectedHitCount())))
                    .verifyComplete();
        }

        static Stream<ErrorTestCase> errorCases() {
            // Note: VarDef redefinition is a compile-time exception, tested separately
            return Stream.of(ErrorTestCase.pureError("non-boolean condition", "42;", "boolean", 1),
                    ErrorTestCase.pureErrorWithVars("pure evaluates to error", "brokenVar;",
                            Map.of("brokenVar", Value.error("intentional")), "intentional", 1),
                    ErrorTestCase.streamError("stream emits error", "<test.attr>;", "test.attr", "stream failure",
                            "stream failure", 1));
        }

        @Test
        @DisplayName("VarDef redefinition throws at compile time")
        void whenVarDefRedefinition_thenCompileTimeException() {
            val body    = parsePolicyBody("var x = 1; var x = 2;");
            val compCtx = compilationContext(ATTRIBUTE_BROKER);
            assertThatThrownBy(() -> PolicyBodyCompiler.compilePolicyBody(body, compCtx))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining("redefine");
        }
    }

    @Nested
    @DisplayName("Stream re-emission scenarios")
    class StreamReEmissionTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("reEmissionCases")
        @DisplayName("streaming section re-emissions work correctly")
        void whenStreamReEmits_thenStreamingSectionEmitsSequence(StreamTestCase tc) {
            val body = parsePolicyBody(tc.bodyContent());

            // Streaming section pathway
            var broker  = sequenceBroker(tc.attributeSequences());
            var compCtx = compilationContext(broker);
            var evalCtx = evaluationContext(broker);

            val compiledBody  = PolicyBodyCompiler.compilePolicyBody(body, compCtx);
            val streamSection = compiledBody.streamingSectionOfBody();

            // Pure section should be TRUE (no pure conditions in these tests)
            val pureSection = compiledBody.isApplicable();
            assertThat(pureSection).isEqualTo(Value.TRUE);

            // Streaming section should be a StreamOperator
            assertThat(streamSection).isInstanceOf(StreamOperator.class);

            val                            streamFlux   = ((StreamOperator) streamSection).stream()
                    .contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
            StepVerifier.Step<TracedValue> stepVerifier = StepVerifier.create(streamFlux);
            for (var expected : tc.expectedSequence()) {
                stepVerifier = stepVerifier.assertNext(tv -> assertThat(tv.value()).isEqualTo(expected));
            }
            stepVerifier.verifyComplete();

            // Coverage pathway - separate broker
            var broker2  = sequenceBroker(tc.attributeSequences());
            var compCtx2 = compilationContext(broker2);
            var evalCtx2 = evaluationContext(broker2);

            val                                           compiledBody2 = PolicyBodyCompiler.compilePolicyBody(body,
                    compCtx2);
            val                                           coverageFlux  = compiledBody2.coverageStream()
                    .contextWrite(c -> c.put(EvaluationContext.class, evalCtx2));
            StepVerifier.Step<TracedValueAndBodyCoverage> covVerifier   = StepVerifier.create(coverageFlux);
            for (var expected : tc.expectedSequence()) {
                covVerifier = covVerifier.assertNext(r -> assertThat(r.value().value()).isEqualTo(expected));
            }
            covVerifier.verifyComplete();
        }

        static Stream<StreamTestCase> reEmissionCases() {
            return Stream.of(
                    new StreamTestCase("single stream re-emits three values", "<test.attr>;",
                            Map.of("test.attr", List.of(Value.TRUE, Value.FALSE, Value.TRUE)),
                            List.of(Value.TRUE, Value.FALSE, Value.TRUE)),
                    new StreamTestCase(
                            "chained streams - first stream changes", "<a.attr>; <b.attr>;", Map.of("a.attr",
                                    List.of(Value.TRUE, Value.TRUE, Value.FALSE), "b.attr", List.of(Value.TRUE)),
                            List.of(Value.TRUE, Value.TRUE, Value.FALSE)));
        }
    }

}
