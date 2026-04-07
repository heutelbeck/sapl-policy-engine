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
package io.sapl.compiler.index.smtdd;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.compiler.index.PolicyIndexResult;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.sapl.compiler.index.IndexTestFixtures.PREDICATE_RESULTS;
import static io.sapl.compiler.index.IndexTestFixtures.configurablePredicate;
import static io.sapl.compiler.index.IndexTestFixtures.stubDocument;
import static io.sapl.compiler.index.IndexTestFixtures.stubDocumentWithApplicability;
import static io.sapl.compiler.index.IndexTestFixtures.stubDocumentWithConstantApplicability;
import static io.sapl.util.SaplTesting.evaluationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("SmtddPolicyIndex")
class SmtddPolicyIndexTests {

    @AfterEach
    void clearResults() {
        PREDICATE_RESULTS.clear();
    }

    @Nested
    @DisplayName("match")
    class MatchTests {

        @MethodSource
        @DisplayName("single predicate evaluation")
        @ParameterizedTest(name = "predicate={0} -> matches={1}")
        void whenPredicateEvaluatedThenCorrectMatch(Value predicateResult, boolean shouldMatch) {
            val predicate = configurablePredicate(1L);
            val document  = stubDocumentWithApplicability("policy1", predicate.operator());

            PREDICATE_RESULTS.put(1L, predicateResult);
            val result = SmtddPolicyIndex.create(List.of(document), 0).match(evaluationContext());

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
            val predicate = configurablePredicate(1L);
            val document  = stubDocumentWithApplicability("policy1", predicate.operator());

            PREDICATE_RESULTS.put(1L, new ErrorValue("broken"));
            val result = SmtddPolicyIndex.create(List.of(document), 0).match(evaluationContext());

            assertThat(result).satisfies(r -> {
                assertThat(r.matchingDocuments()).isEmpty();
                assertThat(r.errorVotes()).hasSize(1);
            });
        }

        @Test
        @DisplayName("constant TRUE applicability always matches")
        void whenConstantTrueThenMatches() {
            val result = SmtddPolicyIndex.create(List.of(stubDocument("p1")), 0).match(evaluationContext());
            assertThat(result.matchingDocuments()).hasSize(1);
        }

        @Test
        @DisplayName("constant FALSE applicability never matches")
        void whenConstantFalseThenNotMatched() {
            val document = stubDocumentWithConstantApplicability("p1", Value.FALSE);
            val result   = SmtddPolicyIndex.create(List.of(document), 0).match(evaluationContext());
            assertThat(result.matchingDocuments()).isEmpty();
        }

        @Test
        @DisplayName("constant error applicability produces error vote")
        void whenConstantErrorThenErrorVote() {
            val document = stubDocumentWithConstantApplicability("p1", new ErrorValue("broken"));
            val result   = SmtddPolicyIndex.create(List.of(document), 0).match(evaluationContext());
            assertThat(result).satisfies(r -> {
                assertThat(r.matchingDocuments()).isEmpty();
                assertThat(r.errorVotes()).hasSize(1);
            });
        }

        @Test
        @DisplayName("empty index returns empty result")
        void whenEmptyThenEmptyResult() {
            val result = SmtddPolicyIndex.create(List.of(), 0).match(evaluationContext());
            assertThat(result).satisfies(r -> {
                assertThat(r.matchingDocuments()).isEmpty();
                assertThat(r.errorVotes()).isEmpty();
            });
        }

        @Test
        @DisplayName("multiple matching documents returned")
        void whenMultipleMatchThenAllReturned() {
            val documents = List.of(stubDocument("p1"), stubDocument("p2"), stubDocument("p3"));
            val result    = SmtddPolicyIndex.create(documents, 0).match(evaluationContext());
            assertThat(result.matchingDocuments()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("matchWhile")
    class MatchWhileTests {

        @Test
        @DisplayName("yields all matches when consumer always continues")
        void whenAlwaysContinueThenAllMatches() {
            val documents = List.of(stubDocument("p1"), stubDocument("p2"), stubDocument("p3"));
            val index     = SmtddPolicyIndex.create(documents, 0);
            val received  = new ArrayList<String>();

            index.matchWhile(evaluationContext(), step -> {
                step.matchingDocuments().forEach(d -> received.add(d.metadata().name()));
                return true;
            });

            assertThat(received).containsExactly("p1", "p2", "p3");
        }

        @Test
        @DisplayName("stops after consumer returns false")
        void whenConsumerStopsThenNoMoreResults() {
            val documents = List.of(stubDocument("p1"), stubDocument("p2"), stubDocument("p3"));
            val index     = SmtddPolicyIndex.create(documents, 0);
            val callCount = new AtomicInteger(0);

            index.matchWhile(evaluationContext(), step -> {
                callCount.incrementAndGet();
                return callCount.get() < 2;
            });

            assertThat(callCount.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("error votes yielded incrementally")
        void whenErrorThenYieldedIncrementally() {
            val predicate = configurablePredicate(1L);
            val document  = stubDocumentWithApplicability("policy1", predicate.operator());
            val index     = SmtddPolicyIndex.create(List.of(document), 0);

            PREDICATE_RESULTS.put(1L, new ErrorValue("broken"));
            val errors = new ArrayList<PolicyIndexResult>();

            index.matchWhile(evaluationContext(), step -> {
                errors.add(step);
                return true;
            });

            assertThat(errors).isNotEmpty();
        }
    }

}
