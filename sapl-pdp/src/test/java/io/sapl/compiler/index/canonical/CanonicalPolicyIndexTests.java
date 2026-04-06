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
package io.sapl.compiler.index.canonical;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Value;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.expressions.StratifiedBooleanOperationCompiler.LazyAndPurePure;
import io.sapl.compiler.index.IndexTestFixtures;
import io.sapl.compiler.index.dnf.ConjunctiveClause;
import io.sapl.compiler.index.dnf.DisjunctiveFormula;
import io.sapl.compiler.index.dnf.Literal;
import io.sapl.compiler.index.PolicyIndex;
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
import static io.sapl.util.SaplTesting.TEST_LOCATION;
import static io.sapl.util.SaplTesting.evaluationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("CanonicalPolicyIndex")
class CanonicalPolicyIndexTests {

    @AfterEach
    void clearPredicateResults() {
        PREDICATE_RESULTS.clear();
    }

    @Nested
    @DisplayName("single formula matching")
    class SingleFormulaMatching {

        @ParameterizedTest(name = "negated={0}, result={1} -> matches={2}")
        @MethodSource
        void whenSingleLiteralThenMatchesDependsOnPolarity(boolean negated, Value predicateResult,
                boolean shouldMatch) {
            val p1       = configurablePredicate(1L);
            val formula  = new DisjunctiveFormula(new ConjunctiveClause(List.of(new Literal(p1, negated))));
            val document = stubDocument("policy1");
            val index    = CanonicalPolicyIndex.createFromFormulas(Map.of(formula, List.of(document)));

            PREDICATE_RESULTS.put(1L, predicateResult);
            val result = index.match(evaluationContext());

            assertThat(result).satisfies(r -> {
                if (shouldMatch) {
                    assertThat(r.matchingDocuments()).extracting(CompiledDocument::metadata).extracting(m -> m.name())
                            .containsExactly("policy1");
                } else {
                    assertThat(r.matchingDocuments()).isEmpty();
                }
                assertThat(r.errorVotes()).isEmpty();
            });
        }

        static Stream<Arguments> whenSingleLiteralThenMatchesDependsOnPolarity() {
            return Stream.of(arguments(false, Value.TRUE, true), arguments(false, Value.FALSE, false),
                    arguments(true, Value.TRUE, false), arguments(true, Value.FALSE, true));
        }
    }

    @Nested
    @DisplayName("conjunction matching")
    class ConjunctionMatching {

        @ParameterizedTest(name = "p1={0}, p2={1} -> matches={2}")
        @MethodSource
        void whenTwoLiteralConjunctionThenBothMustBeTrue(Value p1Result, Value p2Result, boolean shouldMatch) {
            val p1       = configurablePredicate(1L);
            val p2       = configurablePredicate(2L);
            val formula  = new DisjunctiveFormula(
                    new ConjunctiveClause(List.of(new Literal(p1, false), new Literal(p2, false))));
            val document = stubDocument("policy1");
            val index    = CanonicalPolicyIndex.createFromFormulas(Map.of(formula, List.of(document)));

            PREDICATE_RESULTS.put(1L, p1Result);
            PREDICATE_RESULTS.put(2L, p2Result);
            val result = index.match(evaluationContext());

            if (shouldMatch) {
                assertThat(result.matchingDocuments()).hasSize(1);
            } else {
                assertThat(result.matchingDocuments()).isEmpty();
            }
        }

        static Stream<Arguments> whenTwoLiteralConjunctionThenBothMustBeTrue() {
            return Stream.of(arguments(Value.TRUE, Value.TRUE, true), arguments(Value.TRUE, Value.FALSE, false),
                    arguments(Value.FALSE, Value.TRUE, false), arguments(Value.FALSE, Value.FALSE, false));
        }
    }

    @Nested
    @DisplayName("disjunction matching")
    class DisjunctionMatching {

        @ParameterizedTest(name = "p1={0}, p2={1} -> matches={2}")
        @MethodSource
        void whenTwoClauseDisjunctionThenEitherSuffices(Value p1Result, Value p2Result, boolean shouldMatch) {
            val p1       = configurablePredicate(1L);
            val p2       = configurablePredicate(2L);
            val formula  = new DisjunctiveFormula(List.of(new ConjunctiveClause(List.of(new Literal(p1, false))),
                    new ConjunctiveClause(List.of(new Literal(p2, false)))));
            val document = stubDocument("policy1");
            val index    = CanonicalPolicyIndex.createFromFormulas(Map.of(formula, List.of(document)));

            PREDICATE_RESULTS.put(1L, p1Result);
            PREDICATE_RESULTS.put(2L, p2Result);
            val result = index.match(evaluationContext());

            if (shouldMatch) {
                assertThat(result.matchingDocuments()).hasSize(1);
            } else {
                assertThat(result.matchingDocuments()).isEmpty();
            }
        }

        static Stream<Arguments> whenTwoClauseDisjunctionThenEitherSuffices() {
            return Stream.of(arguments(Value.TRUE, Value.TRUE, true), arguments(Value.TRUE, Value.FALSE, true),
                    arguments(Value.FALSE, Value.TRUE, true), arguments(Value.FALSE, Value.FALSE, false));
        }
    }

    @Nested
    @DisplayName("multiple formulas")
    class MultipleFormulas {

        @Test
        @DisplayName("independent formulas match independently")
        void whenMultipleFormulasThenEachMatchesIndependently() {
            val p1       = configurablePredicate(1L);
            val p2       = configurablePredicate(2L);
            val formula1 = new DisjunctiveFormula(new ConjunctiveClause(List.of(new Literal(p1, false))));
            val formula2 = new DisjunctiveFormula(new ConjunctiveClause(List.of(new Literal(p2, false))));
            val doc1     = stubDocument("policy1");
            val doc2     = stubDocument("policy2");

            val formulaMap = new LinkedHashMap<DisjunctiveFormula, List<CompiledDocument>>();
            formulaMap.put(formula1, List.of(doc1));
            formulaMap.put(formula2, List.of(doc2));
            val index = CanonicalPolicyIndex.createFromFormulas(formulaMap);

            PREDICATE_RESULTS.put(1L, Value.TRUE);
            PREDICATE_RESULTS.put(2L, Value.FALSE);
            val result = index.match(evaluationContext());

            assertThat(result.matchingDocuments()).extracting(CompiledDocument::metadata).extracting(m -> m.name())
                    .containsExactly("policy1");
        }

        @Test
        @DisplayName("shared predicate evaluated once matches multiple formulas")
        void whenSharedPredicateThenBothFormulasMatch() {
            val p1       = configurablePredicate(1L);
            val p2       = configurablePredicate(2L);
            val formula1 = new DisjunctiveFormula(new ConjunctiveClause(List.of(new Literal(p1, false))));
            val formula2 = new DisjunctiveFormula(
                    new ConjunctiveClause(List.of(new Literal(p1, false), new Literal(p2, false))));
            val doc1     = stubDocument("policy1");
            val doc2     = stubDocument("policy2");

            val formulaMap = new LinkedHashMap<DisjunctiveFormula, List<CompiledDocument>>();
            formulaMap.put(formula1, List.of(doc1));
            formulaMap.put(formula2, List.of(doc2));
            val index = CanonicalPolicyIndex.createFromFormulas(formulaMap);

            PREDICATE_RESULTS.put(1L, Value.TRUE);
            PREDICATE_RESULTS.put(2L, Value.TRUE);
            val result = index.match(evaluationContext());

            assertThat(result.matchingDocuments()).extracting(CompiledDocument::metadata).extracting(m -> m.name())
                    .containsExactlyInAnyOrder("policy1", "policy2");
        }

        @Test
        @DisplayName("multiple documents mapped to same formula all returned")
        void whenMultipleDocumentsPerFormulaThenAllReturned() {
            val p1      = configurablePredicate(1L);
            val formula = new DisjunctiveFormula(new ConjunctiveClause(List.of(new Literal(p1, false))));
            val doc1    = stubDocument("policy1");
            val doc2    = stubDocument("policy2");

            val index = CanonicalPolicyIndex.createFromFormulas(Map.of(formula, List.of(doc1, doc2)));

            PREDICATE_RESULTS.put(1L, Value.TRUE);
            val result = index.match(evaluationContext());

            assertThat(result.matchingDocuments()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("predicate error produces error vote not matching document")
        void whenPredicateErrorThenErrorVoteNotMatchingDocument() {
            val p1       = configurablePredicate(1L);
            val formula  = new DisjunctiveFormula(new ConjunctiveClause(List.of(new Literal(p1, false))));
            val document = stubDocument("policy1");
            val index    = CanonicalPolicyIndex.createFromFormulas(Map.of(formula, List.of(document)));

            PREDICATE_RESULTS.put(1L, new ErrorValue("evaluation failed"));
            val result = index.match(evaluationContext());

            assertThat(result).satisfies(r -> {
                assertThat(r.matchingDocuments()).isEmpty();
                assertThat(r.errorVotes()).hasSize(1).first()
                        .satisfies(vote -> assertThat(vote.errors()).first().isInstanceOf(ErrorValue.class));
            });
        }

        @Test
        @DisplayName("error on one predicate does not affect independent formula")
        void whenErrorOnOnePredicateThenIndependentFormulaStillMatches() {
            val p1       = configurablePredicate(1L);
            val p2       = configurablePredicate(2L);
            val formula1 = new DisjunctiveFormula(new ConjunctiveClause(List.of(new Literal(p1, false))));
            val formula2 = new DisjunctiveFormula(new ConjunctiveClause(List.of(new Literal(p2, false))));
            val doc1     = stubDocument("policy1");
            val doc2     = stubDocument("policy2");

            val formulaMap = new LinkedHashMap<DisjunctiveFormula, List<CompiledDocument>>();
            formulaMap.put(formula1, List.of(doc1));
            formulaMap.put(formula2, List.of(doc2));
            val index = CanonicalPolicyIndex.createFromFormulas(formulaMap);

            PREDICATE_RESULTS.put(1L, new ErrorValue("broken"));
            PREDICATE_RESULTS.put(2L, Value.TRUE);
            val result = index.match(evaluationContext());

            assertThat(result).satisfies(r -> {
                assertThat(r.matchingDocuments()).extracting(CompiledDocument::metadata).extracting(m -> m.name())
                        .containsExactly("policy2");
                assertThat(r.errorVotes()).hasSize(1);
            });
        }
    }

    @Nested
    @DisplayName("complex scenarios")
    class ComplexScenarios {

        @Test
        @DisplayName("mixed positive and negative literals in same conjunction")
        void whenMixedLiteralsThenCorrectMatch() {
            val p1       = configurablePredicate(1L);
            val p2       = configurablePredicate(2L);
            val formula  = new DisjunctiveFormula(
                    new ConjunctiveClause(List.of(new Literal(p1, false), new Literal(p2, true))));
            val document = stubDocument("policy1");
            val index    = CanonicalPolicyIndex.createFromFormulas(Map.of(formula, List.of(document)));

            PREDICATE_RESULTS.put(1L, Value.TRUE);
            PREDICATE_RESULTS.put(2L, Value.FALSE);
            val result = index.match(evaluationContext());

            assertThat(result.matchingDocuments()).hasSize(1);
        }

        @Test
        @DisplayName("formula with two clauses sharing a predicate")
        void whenTwoClausesSharingPredicateThenCorrectMatch() {
            val p1       = configurablePredicate(1L);
            val p2       = configurablePredicate(2L);
            val p3       = configurablePredicate(3L);
            val formula  = new DisjunctiveFormula(
                    List.of(new ConjunctiveClause(List.of(new Literal(p1, false), new Literal(p2, false))),
                            new ConjunctiveClause(List.of(new Literal(p1, false), new Literal(p3, false)))));
            val document = stubDocument("policy1");
            val index    = CanonicalPolicyIndex.createFromFormulas(Map.of(formula, List.of(document)));

            PREDICATE_RESULTS.put(1L, Value.TRUE);
            PREDICATE_RESULTS.put(2L, Value.FALSE);
            PREDICATE_RESULTS.put(3L, Value.TRUE);
            val result = index.match(evaluationContext());

            assertThat(result.matchingDocuments()).hasSize(1);
        }

        @Test
        @DisplayName("non-boolean predicate result treated as false")
        void whenNonBooleanResultThenTreatedAsFalse() {
            val p1       = configurablePredicate(1L);
            val formula  = new DisjunctiveFormula(new ConjunctiveClause(List.of(new Literal(p1, false))));
            val document = stubDocument("policy1");
            val index    = CanonicalPolicyIndex.createFromFormulas(Map.of(formula, List.of(document)));

            PREDICATE_RESULTS.put(1L, Value.of("not a boolean"));
            val result = index.match(evaluationContext());

            assertThat(result.matchingDocuments()).isEmpty();
        }

        @Test
        @DisplayName("predicate skipped when not referenced by remaining candidates")
        void whenPredicateNotReferencedThenSkipped() {
            val p1       = configurablePredicate(1L);
            val formula  = new DisjunctiveFormula(new ConjunctiveClause(List.of(new Literal(p1, false))));
            val document = stubDocument("policy1");
            val index    = CanonicalPolicyIndex.createFromFormulas(Map.of(formula, List.of(document)));

            PREDICATE_RESULTS.put(1L, Value.TRUE);
            val result = index.match(evaluationContext());

            assertThat(result.matchingDocuments()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("document list factory")
    class DocumentListFactory {

        @Test
        @DisplayName("constant TRUE applicability document always matches")
        void whenConstantTrueThenAlwaysMatches() {
            val index  = CanonicalPolicyIndex.create(List.of(stubDocument("always")));
            val result = index.match(evaluationContext());

            assertThat(result.matchingDocuments()).extracting(CompiledDocument::metadata).extracting(m -> m.name())
                    .containsExactly("always");
        }

        @Test
        @DisplayName("constant FALSE applicability document is dropped")
        void whenConstantFalseThenDropped() {
            val doc    = IndexTestFixtures.stubDocumentWithConstantApplicability("never", Value.FALSE);
            val index  = CanonicalPolicyIndex.create(List.of(doc));
            val result = index.match(evaluationContext());

            assertThat(result.matchingDocuments()).isEmpty();
        }

        @Test
        @DisplayName("error applicability document produces error vote")
        void whenErrorApplicabilityThenErrorVote() {
            val doc    = IndexTestFixtures.stubDocumentWithConstantApplicability("broken", new ErrorValue("fail"));
            val index  = CanonicalPolicyIndex.create(List.of(doc));
            val result = index.match(evaluationContext());

            assertThat(result).satisfies(r -> {
                assertThat(r.matchingDocuments()).isEmpty();
                assertThat(r.errorVotes()).hasSize(1);
            });
        }

        @Test
        @DisplayName("PureOperator applicability document is indexed")
        void whenPureOperatorThenIndexed() {
            val p1    = configurablePredicate(1L);
            val doc   = IndexTestFixtures.stubDocumentWithApplicability("indexed", p1.operator());
            val index = CanonicalPolicyIndex.create(List.of(doc));

            PREDICATE_RESULTS.put(1L, Value.TRUE);
            val result = index.match(evaluationContext());

            assertThat(result.matchingDocuments()).hasSize(1);
        }

        @Test
        @DisplayName("mixed constant and indexed documents partitioned correctly")
        void whenMixedThenPartitionedCorrectly() {
            val p1         = configurablePredicate(1L);
            val alwaysDoc  = stubDocument("always");
            val indexedDoc = IndexTestFixtures.stubDocumentWithApplicability("indexed", p1.operator());
            val neverDoc   = IndexTestFixtures.stubDocumentWithConstantApplicability("never", Value.FALSE);
            val index      = CanonicalPolicyIndex.create(List.of(alwaysDoc, indexedDoc, neverDoc));

            PREDICATE_RESULTS.put(1L, Value.TRUE);
            val result = index.match(evaluationContext());

            assertThat(result.matchingDocuments()).extracting(CompiledDocument::metadata).extracting(m -> m.name())
                    .containsExactlyInAnyOrder("always", "indexed");
        }
    }

    @Nested
    @DisplayName("matchWhile")
    class MatchWhileTests {

        @Test
        @DisplayName("stops when consumer returns false")
        void whenConsumerStopsThenStops() {
            val p1    = configurablePredicate(1L);
            val doc1  = stubDocument("always1");
            val doc2  = stubDocument("always2");
            val doc3  = IndexTestFixtures.stubDocumentWithApplicability("indexed", p1.operator());
            val index = CanonicalPolicyIndex.create(List.of(doc1, doc2, doc3));

            PREDICATE_RESULTS.put(1L, Value.TRUE);
            val received = new ArrayList<String>();

            index.matchWhile(evaluationContext(), step -> {
                step.matchingDocuments().forEach(d -> received.add(d.metadata().name()));
                return received.size() < 2;
            });

            assertThat(received).hasSize(2);
        }

        @Test
        @DisplayName("yields all when consumer always continues")
        void whenAlwaysContinueThenAll() {
            val p1    = configurablePredicate(1L);
            val doc1  = stubDocument("always");
            val doc2  = IndexTestFixtures.stubDocumentWithApplicability("indexed", p1.operator());
            val index = CanonicalPolicyIndex.create(List.of(doc1, doc2));

            PREDICATE_RESULTS.put(1L, Value.TRUE);
            val received = new ArrayList<String>();

            index.matchWhile(evaluationContext(), step -> {
                step.matchingDocuments().forEach(d -> received.add(d.metadata().name()));
                return true;
            });

            assertThat(received).containsExactlyInAnyOrder("always", "indexed");
        }
    }

    @Nested
    @DisplayName("error handling with shared predicates")
    class ErrorWithSharedPredicates {

        @Test
        @DisplayName("shared predicate error produces error votes for all related documents")
        void whenSharedPredicateErrorsThenAllRelatedDocumentsGetErrorVotes() {
            val p1    = configurablePredicate(1L);
            val p2    = configurablePredicate(2L);
            val doc1  = IndexTestFixtures.stubDocumentWithApplicability("uses-p1", p1.operator());
            val doc2  = IndexTestFixtures.stubDocumentWithApplicability("uses-p1-and-p2",
                    new LazyAndPurePure(p1.operator(), p2.operator(), TEST_LOCATION, true, false));
            val doc3  = IndexTestFixtures.stubDocumentWithApplicability("uses-p2-only", p2.operator());
            val index = CanonicalPolicyIndex.create(List.of(doc1, doc2, doc3));

            PREDICATE_RESULTS.put(1L, new ErrorValue("p1 failed"));
            PREDICATE_RESULTS.put(2L, Value.TRUE);

            val matchResult = index.match(evaluationContext());

            assertThat(matchResult.errorVotes()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(matchResult.matchingDocuments()).extracting(CompiledDocument::metadata).extracting(m -> m.name())
                    .containsExactly("uses-p2-only");
        }

        @Test
        @DisplayName("match and matchWhile produce consistent results for error scenario")
        void whenErrorThenMatchAndMatchWhileConsistent() {
            val p1    = configurablePredicate(1L);
            val p2    = configurablePredicate(2L);
            val doc1  = IndexTestFixtures.stubDocumentWithApplicability("uses-p1", p1.operator());
            val doc2  = IndexTestFixtures.stubDocumentWithApplicability("uses-p2", p2.operator());
            val index = CanonicalPolicyIndex.create(List.of(doc1, doc2));

            PREDICATE_RESULTS.put(1L, new ErrorValue("broken"));
            PREDICATE_RESULTS.put(2L, Value.TRUE);

            val matchResult       = index.match(evaluationContext());
            val incrementalResult = accumulateMatchWhile(index, evaluationContext());

            assertThat(incrementalResult.matchingDocuments().stream().map(d -> d.metadata().name()).toList())
                    .containsExactlyInAnyOrderElementsOf(
                            matchResult.matchingDocuments().stream().map(d -> d.metadata().name()).toList());
            assertThat(incrementalResult.errorVotes()).hasSameSizeAs(matchResult.errorVotes());
        }

        @Test
        @DisplayName("document with errored predicate is not returned as match")
        void whenPredicateErrorsThenDocumentNotInMatches() {
            val p1    = configurablePredicate(1L);
            val doc1  = IndexTestFixtures.stubDocumentWithApplicability("erroring", p1.operator());
            val index = CanonicalPolicyIndex.create(List.of(doc1));

            PREDICATE_RESULTS.put(1L, new ErrorValue("fail"));

            val result = index.match(evaluationContext());
            assertThat(result).satisfies(r -> {
                assertThat(r.matchingDocuments()).isEmpty();
                assertThat(r.errorVotes()).hasSize(1);
            });

            val incrementalResult = accumulateMatchWhile(index, evaluationContext());
            assertThat(incrementalResult.matchingDocuments()).isEmpty();
            assertThat(incrementalResult.errorVotes()).hasSize(1);
        }
    }

    private static PolicyIndexResult accumulateMatchWhile(PolicyIndex index, EvaluationContext ctx) {
        val documents  = new ArrayList<CompiledDocument>();
        val errorVotes = new ArrayList<Vote>();
        index.matchWhile(ctx, step -> {
            documents.addAll(step.matchingDocuments());
            errorVotes.addAll(step.errorVotes());
            return true;
        });
        return new PolicyIndexResult(documents, errorVotes);
    }

}
