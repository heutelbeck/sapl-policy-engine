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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * so for every subscription they must report exactly the same applicable
 * documents and error votes as naive. This is exercised on the real production
 * structure: documents are compiled policies, so each body condition is a
 * type-checked atom and a policy is the conjunction of its conditions. Many
 * policies share conditions, so an erroring shared condition must not poison
 * sibling policies that a dominating {@code false} condition keeps
 * inapplicable,
 * and must not be reported as a match where it genuinely errors.
 * <p>
 * Three corpora are stressed: conditions that can only error (division by
 * zero), conditions that can only be undefined (a missing field, which the body
 * type-check turns into an error), and a mix of both. Each is driven by many
 * seeded subscriptions, and naive is the oracle.
 */
@DisplayName("Index backends agree with naive over realistic policy corpora")
class IndexDifferentialTests {

    private static final int CONDITION_COUNT    = 12;
    private static final int POLICY_COUNT       = 60;
    private static final int SUBSCRIPTION_COUNT = 40;

    private enum K {
        TRUE,
        FALSE,
        ERROR,
        UNDEFINED
    }

    /**
     * A shared body condition. An error-type condition evaluates to true, false,
     * or a division-by-zero error depending on {@code subject.eN}. An
     * undefined-type condition evaluates to true, false, or undefined depending
     * on {@code subject.uN}; undefined reaches the body type-check and becomes an
     * error there.
     */
    private record Condition(int id, boolean errorType) {

        String source() {
            return errorType ? "(10 / subject.e%d > 0)".formatted(id) : "subject.u%d".formatted(id);
        }

        String field() {
            return (errorType ? "e" : "u") + id;
        }

        K[] valueDomain() {
            return errorType ? new K[] { K.TRUE, K.FALSE, K.ERROR } : new K[] { K.TRUE, K.FALSE, K.UNDEFINED };
        }
    }

    private record Corpus(String label, List<Condition> conditions, List<CompiledDocument> documents) {}

    private static final Corpus ERROR_ONLY     = corpus("error-only", 101L, id -> true);
    private static final Corpus UNDEFINED_ONLY = corpus("undefined-only", 202L, id -> false);
    private static final Corpus BOTH           = corpus("both", 303L, id -> id % 2 == 0);

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpusSubscriptions")
    @DisplayName("canonical and SMTDD match naive for a large shared-condition corpus")
    void backendsAgreeWithNaiveAcrossSubscriptions(String label, List<CompiledDocument> documents, String subjectJson) {
        assertBackendsAgreeWithNaive(label, documents, context(subjectJson));
    }

    private static Stream<Arguments> corpusSubscriptions() {
        val scenarios = new ArrayList<Arguments>();
        for (val corpus : List.of(ERROR_ONLY, UNDEFINED_ONLY, BOTH)) {
            val random = new Random(corpus.label().length() * 31L + 7L);
            for (var s = 0; s < SUBSCRIPTION_COUNT; s++) {
                val valuation = randomValuation(random, corpus.conditions());
                scenarios.add(arguments(corpus.label() + " #" + s, corpus.documents(),
                        subjectJson(corpus.conditions(), valuation)));
            }
        }
        return scenarios.stream();
    }

    @Nested
    @DisplayName("a shared erroring condition does not poison sibling policies")
    class SharedErrorIsolation {

        @Test
        @DisplayName("error condition dominated by a false sibling leaves the policy inapplicable")
        void whenErrorDominatedByFalseThenInapplicable() {
            final List<CompiledDocument> documents = List
                    .of(compilePolicyFull("policy \"p\" permit (10 / subject.e1 > 0); (10 / subject.e2 > 0)"));
            val                          ctx       = context("{\"e1\": 0, \"e2\": -5}");
            assertBackendsAgreeWithNaive("error dominated by false", documents, ctx);
            assertThat(matched(naive(documents, ctx))).isEmpty();
            assertThat(errored(naive(documents, ctx))).isEmpty();
        }

        @Test
        @DisplayName("error condition with no dominating sibling makes the policy indeterminate")
        void whenErrorNotDominatedThenErrorVote() {
            final List<CompiledDocument> documents = List
                    .of(compilePolicyFull("policy \"p\" permit (10 / subject.e1 > 0); (10 / subject.e2 > 0)"));
            val                          ctx       = context("{\"e1\": 0, \"e2\": 5}");
            assertBackendsAgreeWithNaive("error not dominated", documents, ctx);
            assertThat(errored(naive(documents, ctx))).containsExactly("p");
        }

        @Test
        @DisplayName("a shared erroring condition errors one policy and drops another in the same evaluation")
        void whenSharedErrorThenEachSiblingResolvesIndependently() {
            final List<CompiledDocument> documents = List.of(
                    compilePolicyFull("policy \"drops\" permit (10 / subject.e1 > 0); (10 / subject.e2 > 0)"),
                    compilePolicyFull("policy \"errors\" permit (10 / subject.e1 > 0); (10 / subject.e3 > 0)"));
            val                          ctx       = context("{\"e1\": 0, \"e2\": -5, \"e3\": 5}");
            assertBackendsAgreeWithNaive("shared error", documents, ctx);
            assertThat(matched(naive(documents, ctx))).isEmpty();
            assertThat(errored(naive(documents, ctx))).containsExactly("errors");
        }
    }

    @Nested
    @DisplayName("an undefined condition behaves like an error at the policy boundary")
    class UndefinedConditions {

        @Test
        @DisplayName("undefined condition dominated by a false sibling leaves the policy inapplicable")
        void whenUndefinedDominatedByFalseThenInapplicable() {
            final List<CompiledDocument> documents = List
                    .of(compilePolicyFull("policy \"p\" permit subject.u1; subject.u2"));
            val                          ctx       = context("{\"u2\": false}"); // u1 missing -> undefined
            assertBackendsAgreeWithNaive("undefined dominated by false", documents, ctx);
            assertThat(matched(naive(documents, ctx))).isEmpty();
            assertThat(errored(naive(documents, ctx))).isEmpty();
        }

        @Test
        @DisplayName("undefined condition with no dominating sibling makes the policy indeterminate")
        void whenUndefinedNotDominatedThenErrorVote() {
            final List<CompiledDocument> documents = List
                    .of(compilePolicyFull("policy \"p\" permit subject.u1; subject.u2"));
            val                          ctx       = context("{\"u2\": true}"); // u1 missing -> undefined
            assertBackendsAgreeWithNaive("undefined not dominated", documents, ctx);
            assertThat(errored(naive(documents, ctx))).containsExactly("p");
        }

        @Test
        @DisplayName("a shared undefined condition errors one policy and drops another in the same evaluation")
        void whenSharedUndefinedThenEachSiblingResolvesIndependently() {
            final List<CompiledDocument> documents = List.of(
                    compilePolicyFull("policy \"drops\" permit subject.u1; subject.u2"),
                    compilePolicyFull("policy \"errors\" permit subject.u1; subject.u3"));
            val                          ctx       = context("{\"u2\": false, \"u3\": true}"); // u1 missing ->
                                                                                               // undefined
            assertBackendsAgreeWithNaive("shared undefined", documents, ctx);
            assertThat(matched(naive(documents, ctx))).isEmpty();
            assertThat(errored(naive(documents, ctx))).containsExactly("errors");
        }
    }

    // --- corpus construction --------------------------------------------------

    private interface ConditionTypeChooser {
        boolean isErrorType(int id);
    }

    private static Corpus corpus(String label, long seed, ConditionTypeChooser chooser) {
        val conditions = new ArrayList<Condition>();
        for (var id = 1; id <= CONDITION_COUNT; id++) {
            conditions.add(new Condition(id, chooser.isErrorType(id)));
        }
        val random    = new Random(seed);
        val documents = new ArrayList<CompiledDocument>();
        for (var n = 0; n < POLICY_COUNT; n++) {
            val size = 1 + random.nextInt(4);
            val body = pickDistinct(random, conditions, size).stream().map(Condition::source)
                    .collect(Collectors.joining("; "));
            documents.add(compilePolicyFull("policy \"%s_p%d\" permit %s".formatted(label, n, body)));
        }
        return new Corpus(label, conditions, documents);
    }

    private static List<Condition> pickDistinct(Random random, List<Condition> conditions, int size) {
        val pool   = new ArrayList<>(conditions);
        val picked = new ArrayList<Condition>();
        for (var i = 0; i < size && !pool.isEmpty(); i++) {
            picked.add(pool.remove(random.nextInt(pool.size())));
        }
        return picked;
    }

    private static Map<Integer, K> randomValuation(Random random, List<Condition> conditions) {
        val valuation = new LinkedHashMap<Integer, K>();
        for (val condition : conditions) {
            val domain = condition.valueDomain();
            valuation.put(condition.id(), domain[random.nextInt(domain.length)]);
        }
        return valuation;
    }

    private static String subjectJson(List<Condition> conditions, Map<Integer, K> valuation) {
        val fields = new ArrayList<String>();
        for (val condition : conditions) {
            val k = valuation.get(condition.id());
            if (condition.errorType()) {
                val number = switch (k) {
                case TRUE  -> 5;
                case FALSE -> -5;
                default    -> 0;
                };
                fields.add("\"%s\": %d".formatted(condition.field(), number));
            } else if (k == K.TRUE) {
                fields.add("\"%s\": true".formatted(condition.field()));
            } else if (k == K.FALSE) {
                fields.add("\"%s\": false".formatted(condition.field()));
            }
            // UNDEFINED: the field is omitted, so subject.uN evaluates to undefined.
        }
        return "{\"subject\": {%s}, \"action\": \"a\", \"resource\": \"r\", \"environment\": \"e\"}"
                .formatted(String.join(", ", fields));
    }

    // --- assertions and helpers ----------------------------------------------

    private static void assertBackendsAgreeWithNaive(String label, List<CompiledDocument> documents,
            EvaluationContext ctx) {
        val expected = naive(documents, ctx);
        val canon    = CanonicalPolicyIndex.create(documents).match(ctx);
        val smtdd    = SmtddPolicyIndex.create(documents, 0).match(ctx);
        assertThat(matched(canon)).as("canonical matched | %s", label).isEqualTo(matched(expected));
        assertThat(errored(canon)).as("canonical errored | %s", label).isEqualTo(errored(expected));
        assertThat(matched(smtdd)).as("smtdd matched | %s", label).isEqualTo(matched(expected));
        assertThat(errored(smtdd)).as("smtdd errored | %s", label).isEqualTo(errored(expected));
    }

    private static PolicyIndexResult naive(List<CompiledDocument> documents, EvaluationContext ctx) {
        return NaivePolicyIndex.create(documents).match(ctx);
    }

    private static Set<String> matched(PolicyIndexResult result) {
        return result.matchingDocuments().stream().map(document -> document.metadata().name())
                .collect(Collectors.toSet());
    }

    private static Set<String> errored(PolicyIndexResult result) {
        return result.errorVotes().stream().map(vote -> vote.voter().name()).collect(Collectors.toSet());
    }

    private static EvaluationContext context(String subjectFields) {
        val json = subjectFields.startsWith("{\"subject\"") ? subjectFields
                : "{\"subject\": %s, \"action\": \"a\", \"resource\": \"r\", \"environment\": \"e\"}"
                        .formatted(subjectFields);
        return subscriptionContext(json);
    }
}
