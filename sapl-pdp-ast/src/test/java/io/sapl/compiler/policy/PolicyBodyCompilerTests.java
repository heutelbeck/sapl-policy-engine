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
import io.sapl.ast.PolicyBody;
import io.sapl.ast.Statement;
import io.sapl.compiler.ast.SAPLCompiler;
import io.sapl.compiler.ast.AstTransformer;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.policy.policybody.PolicyBodyCompiler;
import io.sapl.compiler.policy.policybody.TracedPolicyBodyResultAndCoverage;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicyBodyCompiler")
class PolicyBodyCompilerTests {

    private static final AstTransformer TRANSFORMER = new AstTransformer();

    record TestCase(
            String description,
            String bodyContent,
            Map<String, Value> variables,
            Map<String, Value[]> attributes,
            Value expectedValue,
            long expectedConditionCount,
            int expectedHitCount,
            List<Long> expectedHitIndices) {

        static TestCase simple(String desc, String body, Value expected, long condCount, int hitCount,
                Long... hitIndices) {
            return new TestCase(desc, body, Map.of(), Map.of(), expected, condCount, hitCount,
                    hitIndices.length > 0 ? List.of(hitIndices) : List.of());
        }

        static TestCase withVars(String desc, String body, Map<String, Value> vars, Value expected, long condCount,
                int hitCount, Long... hitIndices) {
            return new TestCase(desc, body, vars, Map.of(), expected, condCount, hitCount,
                    hitIndices.length > 0 ? List.of(hitIndices) : List.of());
        }

        static TestCase withAttr(String desc, String body, String attrName, Value attrValue, Value expected,
                long condCount, int hitCount, Long... hitIndices) {
            return new TestCase(desc, body, Map.of(), Map.of(attrName, new Value[] { attrValue }), expected, condCount,
                    hitCount, hitIndices.length > 0 ? List.of(hitIndices) : List.of());
        }

        static TestCase withVarsAndAttr(String desc, String body, Map<String, Value> vars, String attrName,
                Value attrValue, Value expected, long condCount, int hitCount, Long... hitIndices) {
            return new TestCase(desc, body, vars, Map.of(attrName, new Value[] { attrValue }), expected, condCount,
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
            int expectedHitCount) {

        static ErrorTestCase simple(String desc, String body, String errorFragment, int hitCount) {
            return new ErrorTestCase(desc, body, Map.of(), null, null, errorFragment, hitCount);
        }

        static ErrorTestCase withVars(String desc, String body, Map<String, Value> vars, String errorFragment,
                int hitCount) {
            return new ErrorTestCase(desc, body, vars, null, null, errorFragment, hitCount);
        }

        static ErrorTestCase withErrorAttr(String desc, String body, String attrName, String attrError,
                String errorFragment, int hitCount) {
            return new ErrorTestCase(desc, body, Map.of(), attrName, attrError, errorFragment, hitCount);
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
                where
                    %s
                """.formatted(bodyContent);
        val document     = SAPLCompiler.parse(policySource);
        val element      = document.policyElement();
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

    private static Value evaluateProduction(CompiledExpression production, EvaluationContext evalCtx) {
        return switch (production) {
        case Value v          -> v;
        case PureOperator p   -> p.evaluate(evalCtx);
        case StreamOperator s -> {
            var tv = s.stream().contextWrite(c -> c.put(EvaluationContext.class, evalCtx)).blockFirst();
            yield tv != null ? tv.value() : Value.error("Stream completed without emitting");
        }
        };
    }

    private static void verifyBothPathways(TestCase tc) {
        val body = parsePolicyBody(tc.bodyContent());

        // Production pathway - fresh context
        var broker1  = tc.attributes().isEmpty() ? ATTRIBUTE_BROKER : attributeBroker(tc.attributes());
        var compCtx1 = compilationContext(broker1);
        var evalCtx1 = tc.variables().isEmpty() ? evaluationContext(broker1)
                : evaluationContext(broker1, tc.variables());

        val production      = PolicyBodyCompiler.compilePolicyBodyExpression(body, compCtx1);
        val productionValue = evaluateProduction(production, evalCtx1);
        assertThat(productionValue).as("production value").isEqualTo(tc.expectedValue());

        // Coverage pathway - separate fresh context
        var broker2  = tc.attributes().isEmpty() ? ATTRIBUTE_BROKER : attributeBroker(tc.attributes());
        var compCtx2 = compilationContext(broker2);
        var evalCtx2 = tc.variables().isEmpty() ? evaluationContext(broker2)
                : evaluationContext(broker2, tc.variables());

        val coverageFlux = PolicyBodyCompiler.compilePolicyBodyWithCoverage(body, compCtx2)
                .contextWrite(c -> c.put(EvaluationContext.class, evalCtx2));

        StepVerifier.create(coverageFlux).assertNext(r -> assertThat(r).satisfies(
                cov -> assertThat(cov.value().value()).as("coverage equals expected").isEqualTo(tc.expectedValue()),
                cov -> assertThat(cov.value().value()).as("coverage equals production").isEqualTo(productionValue),
                cov -> assertThat(cov.bodyCoverage().numberOfConditions()).as("condition count")
                        .isEqualTo(tc.expectedConditionCount()),
                cov -> assertThat(cov.bodyCoverage().hits()).as("hit count").hasSize(tc.expectedHitCount()), cov -> {
                    if (!tc.expectedHitIndices().isEmpty()) {
                        assertThat(cov.bodyCoverage().hits().stream().map(Coverage.ConditionHit::statementId).toList())
                                .as("hit indices").isEqualTo(tc.expectedHitIndices());
                    }
                })).verifyComplete();
    }

    @Nested
    @DisplayName("Single emission scenarios")
    class SingleEmissionTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("testCases")
        @DisplayName("both pathways produce matching results")
        void whenBodyCompiled_thenBothPathwaysMatch(TestCase tc) {
            verifyBothPathways(tc);
        }

        static Stream<TestCase> testCases() {
            return Stream.of(
                    // Empty/trivial bodies
                    TestCase.simple("empty body returns TRUE", "", Value.TRUE, 0L, 0),
                    TestCase.simple("only VarDefs returns TRUE", "var x = 1; var y = 2;", Value.TRUE, 0L, 0),

                    // Value stratum - single condition
                    TestCase.simple("single true condition", "true;", Value.TRUE, 1L, 1, 0L),
                    TestCase.simple("single false condition", "false;", Value.FALSE, 1L, 1, 0L),
                    TestCase.simple("constant expression true (1 == 1)", "1 == 1;", Value.TRUE, 1L, 1, 0L),
                    TestCase.simple("constant expression false (1 == 2)", "1 == 2;", Value.FALSE, 1L, 1, 0L),

                    // Pure stratum - single condition
                    TestCase.withVars("pure variable true", "flag;", Map.of("flag", Value.TRUE), Value.TRUE, 1L, 1, 0L),
                    TestCase.withVars("pure variable false", "flag;", Map.of("flag", Value.FALSE), Value.FALSE, 1L, 1,
                            0L),

                    // Stream stratum - single condition
                    TestCase.withAttr("stream emits true", "<test.attr>;", "test.attr", Value.TRUE, Value.TRUE, 1L, 1,
                            0L),
                    TestCase.withAttr("stream emits false", "<test.attr>;", "test.attr", Value.FALSE, Value.FALSE, 1L,
                            1, 0L),

                    // Multiple conditions - all true
                    TestCase.simple("three true conditions", "true; 1 == 1; 2 > 1;", Value.TRUE, 3L, 3, 0L, 1L, 2L),

                    // Short-circuit behavior
                    TestCase.simple("first false short-circuits", "false; true; true;", Value.FALSE, 3L, 1, 0L),
                    TestCase.simple("middle false short-circuits", "true; false; true;", Value.FALSE, 3L, 2, 0L, 1L),

                    // Mixed strata - values and pures
                    TestCase.withVars("values and pures - all true", "flag; true; otherFlag;",
                            Map.of("flag", Value.TRUE, "otherFlag", Value.TRUE), Value.TRUE, 3L, 3),
                    TestCase.withVars("value false short-circuits before pure", "flag; false; otherFlag;",
                            Map.of("flag", Value.TRUE, "otherFlag", Value.error("should not see")), Value.FALSE, 3L, 1,
                            1L),

                    // Mixed strata - pures and streams
                    TestCase.withVarsAndAttr("pure and stream - all true", "flag; <test.attr>;",
                            Map.of("flag", Value.TRUE), "test.attr", Value.TRUE, Value.TRUE, 2L, 2),
                    TestCase.withVarsAndAttr("pure false short-circuits stream", "flag; <test.attr>;",
                            Map.of("flag", Value.FALSE), "test.attr", Value.error("should not subscribe"), Value.FALSE,
                            2L, 1, 0L),

                    // VarDef interaction (constant folding)
                    TestCase.simple("VarDef used in condition (constant folded)", "var x = true; x;", Value.TRUE, 1L, 1,
                            0L),
                    TestCase.simple("multiple VarDefs (constant folded)", "var x = 5; var y = 5; x == y;", Value.TRUE,
                            1L, 1));
        }
    }

    @Nested
    @DisplayName("Error scenarios")
    class ErrorTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("errorCases")
        @DisplayName("errors propagate through both pathways")
        void whenError_thenBothPathwaysReturnError(ErrorTestCase tc) {
            val body = parsePolicyBody(tc.bodyContent());

            // Production pathway - fresh context
            var broker1  = tc.attrName() != null ? errorAttributeBroker(tc.attrName(), tc.attrError())
                    : ATTRIBUTE_BROKER;
            var compCtx1 = compilationContext(broker1);
            var evalCtx1 = tc.variables().isEmpty() ? evaluationContext(broker1)
                    : evaluationContext(broker1, tc.variables());

            val production      = PolicyBodyCompiler.compilePolicyBodyExpression(body, compCtx1);
            val productionValue = evaluateProduction(production, evalCtx1);
            assertThat(productionValue).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message())
                    .asString().containsIgnoringCase(tc.expectedErrorFragment());

            // Coverage pathway - separate fresh context
            var broker2  = tc.attrName() != null ? errorAttributeBroker(tc.attrName(), tc.attrError())
                    : ATTRIBUTE_BROKER;
            var compCtx2 = compilationContext(broker2);
            var evalCtx2 = tc.variables().isEmpty() ? evaluationContext(broker2)
                    : evaluationContext(broker2, tc.variables());

            val coverageFlux = PolicyBodyCompiler.compilePolicyBodyWithCoverage(body, compCtx2)
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
            return Stream.of(ErrorTestCase.simple("VarDef redefinition", "var x = 1; var x = 2;", "redefine", 0),
                    ErrorTestCase.simple("non-boolean condition", "42;", "boolean", 1),
                    ErrorTestCase.withVars("pure evaluates to error", "brokenVar;",
                            Map.of("brokenVar", Value.error("intentional")), "intentional", 1),
                    ErrorTestCase.withErrorAttr("stream emits error", "<test.attr>;", "test.attr", "stream failure",
                            "stream failure", 1));
        }
    }

    @Nested
    @DisplayName("Stream re-emission scenarios")
    class StreamReEmissionTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("reEmissionCases")
        @DisplayName("stream re-emissions match across both pathways")
        void whenStreamReEmits_thenBothPathwaysEmitSameSequence(StreamTestCase tc) {
            val body = parsePolicyBody(tc.bodyContent());

            // Production pathway - fresh context
            var broker1  = sequenceBroker(tc.attributeSequences());
            var compCtx1 = compilationContext(broker1);
            var evalCtx1 = evaluationContext(broker1);

            val production = PolicyBodyCompiler.compilePolicyBodyExpression(body, compCtx1);
            assertThat(production).isInstanceOf(StreamOperator.class);

            val                            prodStream   = ((StreamOperator) production).stream()
                    .contextWrite(c -> c.put(EvaluationContext.class, evalCtx1));
            StepVerifier.Step<TracedValue> prodVerifier = StepVerifier.create(prodStream);
            for (var expected : tc.expectedSequence()) {
                prodVerifier = prodVerifier.assertNext(tv -> assertThat(tv.value()).isEqualTo(expected));
            }
            prodVerifier.verifyComplete();

            // Coverage pathway - separate fresh context
            var broker2  = sequenceBroker(tc.attributeSequences());
            var compCtx2 = compilationContext(broker2);
            var evalCtx2 = evaluationContext(broker2);

            val                                                  coverageFlux = PolicyBodyCompiler
                    .compilePolicyBodyWithCoverage(body, compCtx2)
                    .contextWrite(c -> c.put(EvaluationContext.class, evalCtx2));
            StepVerifier.Step<TracedPolicyBodyResultAndCoverage> covVerifier  = StepVerifier.create(coverageFlux);
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
