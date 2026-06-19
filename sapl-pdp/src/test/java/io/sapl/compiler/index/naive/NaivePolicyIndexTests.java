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
package io.sapl.compiler.index.naive;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.compiler.index.PolicyIndexResult;
import io.sapl.compiler.index.PolicyMatches;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.sapl.compiler.index.IndexTestFixtures.*;
import static io.sapl.util.SaplTesting.evaluationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("NaivePolicyIndex")
class NaivePolicyIndexTests {

    @AfterEach
    void clearPredicateResults() {
        PREDICATE_RESULTS.clear();
    }

    @Nested
    @DisplayName("match")
    class Match {

        @ParameterizedTest(name = "predicate={0} -> matches={1}")
        @MethodSource
        void whenPredicateEvaluatedThenCorrectMatch(Value predicateResult, boolean shouldMatch) {
            val p1  = configurablePredicate(1L);
            val doc = stubDocumentWithApplicability("policy1", p1.operator());

            PREDICATE_RESULTS.put(1L, predicateResult);
            val result = NaivePolicyIndex.create(List.of(doc)).match(evaluationContext());

            if (shouldMatch) {
                assertThat(result.matchingDocuments()).hasSize(1);
            } else {
                assertThat(result.matchingDocuments()).isEmpty();
            }
        }

        static Stream<Arguments> whenPredicateEvaluatedThenCorrectMatch() {
            return Stream.of(arguments(Value.TRUE, true), arguments(Value.FALSE, false));
        }

        @Test
        @DisplayName("predicate error produces error vote")
        void whenPredicateErrorThenErrorVote() {
            val p1  = configurablePredicate(1L);
            val doc = stubDocumentWithApplicability("policy1", p1.operator());

            PREDICATE_RESULTS.put(1L, new ErrorValue("broken"));
            val result = NaivePolicyIndex.create(List.of(doc)).match(evaluationContext());

            assertThat(result).satisfies(r -> {
                assertThat(r.matchingDocuments()).isEmpty();
                assertThat(r.errorVotes()).hasSize(1);
            });
        }

        @Test
        @DisplayName("constant TRUE applicability always matches")
        void whenConstantTrueThenMatches() {
            val result = NaivePolicyIndex.create(List.of(stubDocument("p1"))).match(evaluationContext());
            assertThat(result.matchingDocuments()).hasSize(1);
        }

        @Test
        @DisplayName("empty index returns empty result")
        void whenEmptyThenEmptyResult() {
            val result = NaivePolicyIndex.create(List.of()).match(evaluationContext());
            assertThat(result).satisfies(r -> {
                assertThat(r.matchingDocuments()).isEmpty();
                assertThat(r.errorVotes()).isEmpty();
            });
        }
    }

    @Nested
    @DisplayName("matchWhile")
    class MatchWhile {

        @Test
        @DisplayName("yields all matches when consumer always continues")
        void whenAlwaysContinueThenAllMatches() {
            val docs     = List.of(stubDocument("p1"), stubDocument("p2"), stubDocument("p3"));
            val index    = NaivePolicyIndex.create(docs);
            val received = new ArrayList<String>();

            index.matchWhile(evaluationContext(), step -> {
                step.matchingDocuments().forEach(d -> received.add(d.metadata().name()));
                return true;
            });

            assertThat(received).containsExactly("p1", "p2", "p3");
        }

        @Test
        @DisplayName("stops after consumer returns false")
        void whenConsumerStopsThenNoMoreEvaluations() {
            val p1    = configurablePredicate(1L);
            val p2    = configurablePredicate(2L);
            val p3    = configurablePredicate(3L);
            val docs  = List.of(stubDocumentWithApplicability("policy1", p1.operator()),
                    stubDocumentWithApplicability("policy2", p2.operator()),
                    stubDocumentWithApplicability("policy3", p3.operator()));
            val index = NaivePolicyIndex.create(docs);

            PREDICATE_RESULTS.put(1L, Value.TRUE);
            PREDICATE_RESULTS.put(2L, Value.TRUE);
            PREDICATE_RESULTS.put(3L, Value.TRUE);

            val callCount = new AtomicInteger(0);
            index.matchWhile(evaluationContext(), step -> {
                callCount.incrementAndGet();
                return callCount.get() < 2;
            });

            assertThat(callCount.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("error votes are yielded incrementally")
        void whenErrorThenYieldedIncrementally() {
            val p1    = configurablePredicate(1L);
            val doc   = stubDocumentWithApplicability("policy1", p1.operator());
            val index = NaivePolicyIndex.create(List.of(doc));

            PREDICATE_RESULTS.put(1L, new ErrorValue("broken"));
            val errors = new ArrayList<PolicyIndexResult>();

            index.matchWhile(evaluationContext(), step -> {
                errors.add(step);
                return true;
            });

            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().errorVotes()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("matchKleene")
    class MatchKleene {

        @ParameterizedTest(name = "predicate={0} -> trueMatch={1}")
        @MethodSource
        void whenPredicateEvaluatedThenClassified(Value predicateResult, boolean shouldMatch) {
            val p1  = configurablePredicate(1L);
            val doc = stubDocumentWithApplicability("policy1", p1.operator());

            PREDICATE_RESULTS.put(1L, predicateResult);
            val result = NaivePolicyIndex.create(List.of(doc)).matchKleene(evaluationContext());

            assertThat(result).satisfies(r -> {
                assertThat(r.trueMatches()).hasSize(shouldMatch ? 1 : 0);
                assertThat(r.errorMatches()).isEmpty();
            });
        }

        static Stream<Arguments> whenPredicateEvaluatedThenClassified() {
            return Stream.of(arguments(Value.TRUE, true), arguments(Value.FALSE, false));
        }

        @Test
        @DisplayName("predicate error becomes an error match carrying the document and its error")
        void whenPredicateErrorThenErrorMatchCarriesDocumentAndError() {
            val p1    = configurablePredicate(1L);
            val doc   = stubDocumentWithApplicability("policy1", p1.operator());
            val error = new ErrorValue("broken");

            PREDICATE_RESULTS.put(1L, error);
            val result = NaivePolicyIndex.create(List.of(doc)).matchKleene(evaluationContext());

            assertThat(result.trueMatches()).isEmpty();
            assertThat(result.errorMatches()).singleElement().satisfies(errorMatch -> {
                assertThat(errorMatch.document().metadata().name()).isEqualTo("policy1");
                assertThat(errorMatch.error()).isEqualTo(error);
            });
        }

        @Test
        @DisplayName("constant TRUE applicability is a true match")
        void whenConstantTrueThenTrueMatch() {
            val result = NaivePolicyIndex.create(List.of(stubDocument("p1"))).matchKleene(evaluationContext());
            assertThat(result).satisfies(r -> {
                assertThat(r.trueMatches()).hasSize(1);
                assertThat(r.errorMatches()).isEmpty();
            });
        }

        @Test
        @DisplayName("empty index returns empty matches")
        void whenEmptyThenEmpty() {
            val result = NaivePolicyIndex.create(List.of()).matchKleene(evaluationContext());
            assertThat(result).satisfies(r -> {
                assertThat(r.trueMatches()).isEmpty();
                assertThat(r.errorMatches()).isEmpty();
            });
        }

        @Test
        @DisplayName("mixed documents partition into true matches and error matches, dropping false")
        void whenMixedThenPartitioned() {
            val pTrue  = configurablePredicate(1L);
            val pFalse = configurablePredicate(2L);
            val pError = configurablePredicate(3L);
            val docs   = List.of(stubDocumentWithApplicability("t", pTrue.operator()),
                    stubDocumentWithApplicability("f", pFalse.operator()),
                    stubDocumentWithApplicability("e", pError.operator()));

            PREDICATE_RESULTS.put(1L, Value.TRUE);
            PREDICATE_RESULTS.put(2L, Value.FALSE);
            PREDICATE_RESULTS.put(3L, new ErrorValue("boom"));

            val result = NaivePolicyIndex.create(docs).matchKleene(evaluationContext());

            assertThat(result).satisfies(r -> {
                assertThat(r.trueMatches()).extracting(d -> d.metadata().name()).containsExactly("t");
                assertThat(r.errorMatches()).extracting(em -> em.document().metadata().name()).containsExactly("e");
            });
        }
    }

    @Nested
    @DisplayName("matchKleeneWhile")
    class MatchKleeneWhile {

        @Test
        @DisplayName("yields all true matches when consumer always continues")
        void whenAlwaysContinueThenAllTrueMatches() {
            val docs     = List.of(stubDocument("p1"), stubDocument("p2"), stubDocument("p3"));
            val index    = NaivePolicyIndex.create(docs);
            val received = new ArrayList<String>();

            index.matchKleeneWhile(evaluationContext(), step -> {
                step.trueMatches().forEach(d -> received.add(d.metadata().name()));
                return true;
            });

            assertThat(received).containsExactly("p1", "p2", "p3");
        }

        @Test
        @DisplayName("stops after consumer returns false")
        void whenConsumerStopsThenNoMoreEvaluations() {
            val docs      = List.of(stubDocument("p1"), stubDocument("p2"), stubDocument("p3"));
            val index     = NaivePolicyIndex.create(docs);
            val callCount = new AtomicInteger(0);

            index.matchKleeneWhile(evaluationContext(), step -> {
                callCount.incrementAndGet();
                return callCount.get() < 2;
            });

            assertThat(callCount.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("error matches are yielded incrementally with their error")
        void whenErrorThenYieldedIncrementally() {
            val p1    = configurablePredicate(1L);
            val doc   = stubDocumentWithApplicability("policy1", p1.operator());
            val index = NaivePolicyIndex.create(List.of(doc));
            val error = new ErrorValue("broken");

            PREDICATE_RESULTS.put(1L, error);
            val steps = new ArrayList<PolicyMatches>();

            index.matchKleeneWhile(evaluationContext(), step -> {
                steps.add(step);
                return true;
            });

            assertThat(steps).singleElement()
                    .satisfies(step -> assertThat(step.errorMatches()).singleElement().satisfies(errorMatch -> {
                        assertThat(errorMatch.document().metadata().name()).isEqualTo("policy1");
                        assertThat(errorMatch.error()).isEqualTo(error);
                    }));
        }
    }

}
