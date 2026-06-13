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
package io.sapl.compiler.index;

import static io.sapl.util.SaplTesting.compilePolicyFull;
import static io.sapl.util.SaplTesting.subscriptionContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.EvaluationContext;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.index.canonical.CanonicalPolicyIndex;
import io.sapl.compiler.index.naive.NaivePolicyIndex;
import io.sapl.compiler.index.smtdd.SmtddPolicyIndex;
import lombok.val;

/**
 * The canonical and SMTDD indexes are optimizations of the naive linear scan,
 * so for every subscription they must report exactly the same matched documents
 * and error votes as naive. This is the fast, index-level companion to the
 * benchmark's end-to-end decision-agreement test: it builds policies through
 * the
 * real compiler (so conditions decompose and equality predicates group exactly
 * as in production) and deliberately stresses the corners a realistic generator
 * does not reliably hit.
 * <p>
 * Conditions are drawn from a shared operand pool so equality predicates
 * collapse
 * into multi-way branches: {@code subject.rN == "vK"},
 * {@code subject.rN in [..]},
 * and {@code subject.rN != "vK"} over the same operands with different values;
 * decomposed {@code ||} over those; a division predicate that can error; and a
 * bare field that can be undefined or non-boolean. Subscriptions drive each
 * operand to a matching value, a non-matching value, an error, undefined, or a
 * type mismatch. Naive is the oracle.
 */
@DisplayName("Index backends agree with naive over a decomposed, equality-grouped corpus")
class IndexDifferentialTests {

    private static final int    POLICY_COUNT       = 80;
    private static final int    SUBSCRIPTION_COUNT = 60;
    private static final int[]  STRING_OPERANDS    = { 1, 2, 3, 4 };  // subject.rN, equality-grouped
    private static final int[]  NUMBER_OPERANDS    = { 1, 2, 3 };     // subject.nN, division (can error)
    private static final int[]  BOOL_OPERANDS      = { 1, 2, 3 };     // subject.bN, bare (can be undefined/non-boolean)
    private static final String VALUES             = "vw";            // equality constants v0..v2 are "v","w","x"

    private static final List<CompiledDocument> CORPUS = buildCorpus();
    private static final NaivePolicyIndex       NAIVE  = NaivePolicyIndex.create(CORPUS);
    private static final CanonicalPolicyIndex   CANON  = CanonicalPolicyIndex.create(CORPUS);
    private static final SmtddPolicyIndex       SMTDD  = SmtddPolicyIndex.create(CORPUS, 0);

    @ParameterizedTest(name = "subscription {0}")
    @MethodSource("subscriptions")
    @DisplayName("canonical and SMTDD match naive on every subscription")
    void backendsAgreeWithNaive(int index, String subjectJson) {
        val ctx   = subscriptionContext(wrap(subjectJson));
        val naive = NAIVE.match(ctx);
        val canon = CANON.match(ctx);
        val smtdd = SMTDD.match(ctx);
        assertThat(matched(canon)).as("canonical matched | subscription %d", index).isEqualTo(matched(naive));
        assertThat(errored(canon)).as("canonical errored | subscription %d", index).isEqualTo(errored(naive));
        assertThat(matched(smtdd)).as("smtdd matched | subscription %d", index).isEqualTo(matched(naive));
        assertThat(errored(smtdd)).as("smtdd errored | subscription %d", index).isEqualTo(errored(naive));
    }

    private static Stream<Arguments> subscriptions() {
        val random  = new Random(987654321L);
        val streams = new ArrayList<Arguments>();
        for (var s = 0; s < SUBSCRIPTION_COUNT; s++) {
            streams.add(arguments(s, randomSubject(random)));
        }
        return streams.stream();
    }

    @Nested
    @DisplayName("equality grouping")
    class EqualityGrouping {

        @Test
        @DisplayName("an undefined operand makes every equality on it false, not an error")
        void whenOperandUndefinedThenAllEqualitiesFalse() {
            val documents = policies("policy \"a\" permit subject.r1 == \"v\"",
                    "policy \"b\" permit subject.r1 == \"w\"", "policy \"c\" permit subject.r1 != \"v\"");
            val ctx       = subscriptionContext(wrap("{}")); // r1 missing -> undefined
            assertAgreesWithNaive("undefined operand", documents, ctx);
            // undefined == X is false; undefined != X is true.
            assertThat(matched(NaivePolicyIndex.create(documents).match(ctx))).containsExactly("c");
        }

        @Test
        @DisplayName("a matching value selects exactly the matching equality branch")
        void whenOperandMatchesThenOnlyThatBranchMatches() {
            val documents = policies("policy \"a\" permit subject.r1 == \"v\"",
                    "policy \"b\" permit subject.r1 == \"w\"", "policy \"c\" permit subject.r1 in [\"w\", \"x\"]");
            val ctx       = subscriptionContext(wrap("{\"r1\": \"w\"}"));
            assertAgreesWithNaive("matching value", documents, ctx);
            assertThat(matched(NaivePolicyIndex.create(documents).match(ctx))).containsExactlyInAnyOrder("b", "c");
        }
    }

    @Nested
    @DisplayName("decomposed boolean structure")
    class DecomposedStructure {

        @Test
        @DisplayName("a true equality dominates an erroring division in a decomposed OR")
        void whenOrHasTrueEqualityAndErrorThenMatches() {
            val documents = policies("policy \"p\" permit (10 / subject.n1 > 0) || subject.r1 == \"v\"");
            val ctx       = subscriptionContext(wrap("{\"n1\": 0, \"r1\": \"v\"}")); // division errors, equality true
            assertAgreesWithNaive("true dominates error in OR", documents, ctx);
            assertThat(matched(NaivePolicyIndex.create(documents).match(ctx))).containsExactly("p");
        }

        @Test
        @DisplayName("an erroring division with no dominating sibling makes the policy indeterminate")
        void whenOrHasErrorAndNoDominatorThenErrors() {
            val documents = policies("policy \"p\" permit (10 / subject.n1 > 0) || subject.r1 == \"v\"");
            val ctx       = subscriptionContext(wrap("{\"n1\": 0, \"r1\": \"other\"}")); // error, equality false
            assertAgreesWithNaive("error with no dominator in OR", documents, ctx);
            assertThat(errored(NaivePolicyIndex.create(documents).match(ctx))).containsExactly("p");
        }
    }

    @Nested
    @DisplayName("non-boolean and shared-error corners")
    class NonBooleanAndSharedError {

        @Test
        @DisplayName("a non-boolean bare field condition makes the policy indeterminate")
        void whenBareFieldNonBooleanThenErrors() {
            val documents = policies("policy \"p\" permit subject.b1");
            val ctx       = subscriptionContext(wrap("{\"b1\": \"not a boolean\"}"));
            assertAgreesWithNaive("non-boolean bare field", documents, ctx);
            assertThat(errored(NaivePolicyIndex.create(documents).match(ctx))).containsExactly("p");
        }

        @Test
        @DisplayName("a shared erroring division drops one policy and errors another in the same evaluation")
        void whenSharedErrorThenEachSiblingResolvesIndependently() {
            val documents = policies("policy \"drops\" permit (10 / subject.n1 > 0); subject.r1 == \"v\"",
                    "policy \"errors\" permit (10 / subject.n1 > 0); subject.r2 == \"w\"");
            val ctx       = subscriptionContext(wrap("{\"n1\": 0, \"r1\": \"other\", \"r2\": \"w\"}"));
            assertAgreesWithNaive("shared error", documents, ctx);
            val naive = NaivePolicyIndex.create(documents).match(ctx);
            assertThat(matched(naive)).isEmpty();
            assertThat(errored(naive)).containsExactly("errors");
        }
    }

    // --- corpus construction --------------------------------------------------

    private static List<CompiledDocument> buildCorpus() {
        val random   = new Random(123456789L);
        val policies = new ArrayList<CompiledDocument>(POLICY_COUNT);
        for (var p = 0; p < POLICY_COUNT; p++) {
            val conditionCount = 1 + random.nextInt(4);
            val body           = new ArrayList<String>(conditionCount);
            for (var c = 0; c < conditionCount; c++) {
                body.add(randomCondition(random));
            }
            policies.add(compilePolicyFull("policy \"p%d\" permit %s".formatted(p, String.join("; ", body))));
        }
        return policies;
    }

    private static String randomCondition(Random random) {
        val r = stringOperand(random);
        return switch (random.nextInt(7)) {
        case 0  -> "subject.r%d == \"%s\"".formatted(r, value(random));
        case 1  -> "subject.r%d != \"%s\"".formatted(r, value(random));
        case 2  -> "subject.r%d in [\"v\", \"w\"]".formatted(r);
        case 3  -> "(subject.r%d == \"%s\" || subject.r%d == \"%s\")".formatted(r, value(random), stringOperand(random),
                value(random));
        case 4  -> "(10 / subject.n%d > 0)".formatted(numberOperand(random));
        case 5  -> "subject.b%d".formatted(boolOperand(random));
        default -> "(subject.r%d == \"%s\" && subject.b%d)".formatted(r, value(random), boolOperand(random));
        };
    }

    private static String randomSubject(Random random) {
        val fields = new ArrayList<String>();
        for (val i : STRING_OPERANDS) {
            switch (random.nextInt(5)) {
            case 0  -> fields.add("\"r%d\": \"v\"".formatted(i));
            case 1  -> fields.add("\"r%d\": \"w\"".formatted(i));
            case 2  -> fields.add("\"r%d\": \"x\"".formatted(i));
            case 3  -> fields.add("\"r%d\": \"other\"".formatted(i));
            default -> { /* undefined: omit */ }
            }
        }
        for (val i : NUMBER_OPERANDS) {
            val n = switch (random.nextInt(3)) {
            case 0  -> 5;
            case 1  -> -5;
            default -> 0;
            };
            fields.add("\"n%d\": %d".formatted(i, n));
        }
        for (val i : BOOL_OPERANDS) {
            switch (random.nextInt(4)) {
            case 0  -> fields.add("\"b%d\": true".formatted(i));
            case 1  -> fields.add("\"b%d\": false".formatted(i));
            case 2  -> fields.add("\"b%d\": \"str\"".formatted(i));
            default -> { /* undefined: omit */ }
            }
        }
        return "{%s}".formatted(String.join(", ", fields));
    }

    private static int stringOperand(Random random) {
        return STRING_OPERANDS[random.nextInt(STRING_OPERANDS.length)];
    }

    private static int numberOperand(Random random) {
        return NUMBER_OPERANDS[random.nextInt(NUMBER_OPERANDS.length)];
    }

    private static int boolOperand(Random random) {
        return BOOL_OPERANDS[random.nextInt(BOOL_OPERANDS.length)];
    }

    private static String value(Random random) {
        return String.valueOf(VALUES.charAt(random.nextInt(VALUES.length())));
    }

    // --- assertions and helpers ----------------------------------------------

    private static List<CompiledDocument> policies(String... sources) {
        val documents = new ArrayList<CompiledDocument>(sources.length);
        for (val source : sources) {
            documents.add(compilePolicyFull(source));
        }
        return documents;
    }

    private static void assertAgreesWithNaive(String label, List<CompiledDocument> documents, EvaluationContext ctx) {
        val naive = NaivePolicyIndex.create(documents).match(ctx);
        val canon = CanonicalPolicyIndex.create(documents).match(ctx);
        val smtdd = SmtddPolicyIndex.create(documents, 0).match(ctx);
        assertThat(matched(canon)).as("canonical matched | %s", label).isEqualTo(matched(naive));
        assertThat(errored(canon)).as("canonical errored | %s", label).isEqualTo(errored(naive));
        assertThat(matched(smtdd)).as("smtdd matched | %s", label).isEqualTo(matched(naive));
        assertThat(errored(smtdd)).as("smtdd errored | %s", label).isEqualTo(errored(naive));
    }

    private static Set<String> matched(PolicyIndexResult result) {
        return result.matchingDocuments().stream().map(document -> document.metadata().name())
                .collect(Collectors.toSet());
    }

    private static Set<String> errored(PolicyIndexResult result) {
        return result.errorVotes().stream().map(vote -> vote.voter().name()).collect(Collectors.toSet());
    }

    private static String wrap(String subjectJson) {
        return "{\"subject\": %s, \"action\": \"a\", \"resource\": \"r\", \"environment\": \"e\"}"
                .formatted(subjectJson);
    }
}
