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
package io.sapl.compiler.index.kleene;

import static io.sapl.compiler.index.kleene.KleeneDdIndex.Truth.ERROR;
import static io.sapl.compiler.index.kleene.KleeneDdIndex.Truth.FALSE;
import static io.sapl.compiler.index.kleene.KleeneDdIndex.Truth.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.compiler.index.kleene.KleeneDdIndex.And;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Atom;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Constant;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Formula;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Matches;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Not;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Or;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Truth;
import lombok.val;

@DisplayName("Plain Kleene diagrams retrieve true and error policy matches")
class KleeneDdIndexTests {

    private static final int NODE_LIMIT = 100_000;

    @Nested
    @DisplayName("Applicability follows all strong Kleene truth-table cases")
    class Applicability {

        @ParameterizedTest(name = "{0} and {1}: conjunction {2}, disjunction {3}")
        @CsvSource({ "TRUE,TRUE,TRUE,TRUE", "TRUE,FALSE,FALSE,TRUE", "TRUE,ERROR,ERROR,TRUE", "FALSE,TRUE,FALSE,TRUE",
                "FALSE,FALSE,FALSE,FALSE", "FALSE,ERROR,FALSE,ERROR", "ERROR,TRUE,ERROR,TRUE",
                "ERROR,FALSE,FALSE,ERROR", "ERROR,ERROR,ERROR,ERROR" })
        @DisplayName("a dominating operand resolves applicability despite an error")
        void whenPredicatesHaveAnyOutcomesThenTruthTablesDetermineMatches(Truth left, Truth right, Truth conjunction,
                Truth disjunction) {
            val operands = List.<Formula>of(new Atom(0), new Atom(1));
            val index    = build(new And(operands), new Or(operands));
            assertThat(index.lookup(predicate -> predicate == 0 ? left : right))
                    .isEqualTo(matches(conjunction, disjunction));
        }

        @ParameterizedTest(name = "not {0} yields {1}")
        @CsvSource({ "TRUE,FALSE", "FALSE,TRUE", "ERROR,ERROR" })
        @DisplayName("negation preserves errors")
        void whenApplicabilityNegatedThenErrorsRemainErrors(Truth input, Truth expected) {
            val index = build(new Not(new Atom(0)));
            assertThat(index.lookup(ignored -> input)).isEqualTo(matches(expected));
        }

        @ParameterizedTest(name = "predicate outcome {0}")
        @EnumSource(Truth.class)
        @DisplayName("excluded middle and contradiction are not Boolean constants on errors")
        void whenPredicateErrorsThenComplementaryOccurrencesRemainErrors(Truth outcome) {
            val operands = List.<Formula>of(new Atom(0), new Not(new Atom(0)));
            val index    = build(new Or(operands), new And(operands));
            val expected = outcome == ERROR ? matches(ERROR, ERROR) : matches(TRUE, FALSE);
            assertThat(index.lookup(ignored -> outcome)).isEqualTo(expected);
        }

        @Test
        @DisplayName("constant and empty formulas need no request predicates")
        void whenApplicabilityConstantThenLookupDoesNotEvaluatePredicates() {
            val index = build(new Constant(TRUE), new Constant(FALSE), new Constant(ERROR), new And(List.of()),
                    new Or(List.of()));
            assertThat(index.lookup(ignored -> {
                throw new AssertionError("Constant applicability must not evaluate predicates.");
            })).isEqualTo(matches(TRUE, FALSE, ERROR, TRUE, FALSE));
            assertThat(index.statistics()).satisfies(stats -> {
                assertThat(stats.decisionNodes()).isZero();
                assertThat(stats.terminals()).isOne();
            });
        }

        @ParameterizedTest(name = "shared applicability outcome {0}")
        @EnumSource(Truth.class)
        @DisplayName("documents with identical applicability retain distinct identities")
        void whenDocumentsShareAFormulaThenBothIdentitiesAreReturned(Truth outcome) {
            val index = build(new Atom(0), new Atom(0));
            assertThat(index.lookup(ignored -> outcome)).isEqualTo(matches(outcome, outcome));
        }

        @Test
        @DisplayName("a shared error can exclude one document and error-match another")
        void whenSharedPredicateErrorsThenEachDocumentResolvesIndependently() {
            val index = build(new And(List.of(new Atom(0), new Atom(1))), new Or(List.of(new Atom(0), new Atom(1))),
                    new Atom(0));
            assertThat(index.lookup(predicate -> predicate == 0 ? ERROR : FALSE))
                    .isEqualTo(matches(FALSE, ERROR, ERROR));
        }
    }

    @ParameterizedTest(name = "predicate order {0}")
    @MethodSource("predicateOrders")
    @DisplayName("nested applicability matches an independent interpreter for every predicate valuation")
    void whenFormulasNestedThenEveryValuationAgreesWithDirectEvaluation(List<Integer> order) {
        val random   = new Random(73029L);
        val formulas = new ArrayList<Formula>();
        for (var document = 0; document < 32; document++) {
            formulas.add(randomFormula(random, 3));
        }
        val index = KleeneDdIndex.build(formulas, order, NODE_LIMIT);
        for (val first : Truth.values()) {
            for (val second : Truth.values()) {
                for (val third : Truth.values()) {
                    val valuation = List.of(first, second, third);
                    val expected  = formulas.stream().map(formula -> interpret(formula, valuation))
                            .toArray(Truth[]::new);
                    val visited   = new ArrayList<Integer>();
                    assertThat(index.lookup(predicate -> {
                        visited.add(predicate);
                        return valuation.get(predicate);
                    })).as("valuation %s", valuation).isEqualTo(matches(expected));
                    assertThat(visited).doesNotHaveDuplicates();
                    assertThat(visited.stream().map(order::indexOf).toList()).isSorted();
                }
            }
        }
    }

    @Test
    @DisplayName("each insertion preserves all earlier document classifications")
    void whenDocumentsInsertedThenEveryPrefixSatisfiesTheRetrievalContract() {
        val formulas = List.<Formula>of(new Atom(0), new Not(new Atom(0)), new Constant(ERROR), new Constant(TRUE),
                new Atom(0));
        for (var size = 0; size <= formulas.size(); size++) {
            val prefix = formulas.subList(0, size);
            val index  = KleeneDdIndex.build(prefix, List.of(0), NODE_LIMIT);
            for (val outcome : Truth.values()) {
                val valuation = List.of(outcome);
                val expected  = prefix.stream().map(formula -> interpret(formula, valuation)).toArray(Truth[]::new);
                assertThat(index.lookup(ignored -> outcome)).as("prefix %d, outcome %s", size, outcome)
                        .isEqualTo(matches(expected));
            }
        }
    }

    @Test
    @DisplayName("irrelevant predicates are removed from the lookup path")
    void whenApplicabilityHasADominatingConstantThenLookupSkipsThePredicate() {
        val index = build(new Or(List.of(new Atom(0), new Constant(TRUE))),
                new And(List.of(new Atom(0), new Constant(FALSE))));
        assertThat(index.lookup(ignored -> {
            throw new AssertionError("The result does not depend on this predicate.");
        })).isEqualTo(matches(TRUE, FALSE));
    }

    @Nested
    @DisplayName("Construction and reuse have explicit limits")
    class ConstructionContract {

        @Test
        @DisplayName("an empty policy collection is one empty terminal")
        void whenCollectionEmptyThenOneNodeSuffices() {
            val index = KleeneDdIndex.build(List.of(), List.of(), 1);
            assertThat(index.lookup(ignored -> TRUE)).isEqualTo(matches());
            assertThat(index.statistics()).isEqualTo(new KleeneDdIndex.Statistics(0, 1, 1));
        }

        @Test
        @DisplayName("the allocation limit includes temporary formula and index nodes")
        void whenLimitBelowRequiredAllocationsThenConstructionStops() {
            val documents = List.<Formula>of(new Atom(0), new Atom(1));
            val order     = List.of(0, 1);
            val required  = KleeneDdIndex.build(documents, order, NODE_LIMIT).statistics().allocatedNodes();
            val below     = required - 1;
            assertThat(KleeneDdIndex.build(documents, order, required).statistics().allocatedNodes())
                    .isEqualTo(required);
            assertThatThrownBy(() -> KleeneDdIndex.build(documents, order, below))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Kleene DD node limit reached: " + below);
        }

        @ParameterizedTest(name = "node limit {0}")
        @ValueSource(ints = { 0, -1 })
        @DisplayName("growth experiments must supply a positive node limit")
        void whenNodeLimitInvalidThenConstructionRejectsIt(int limit) {
            val empty = List.<Formula>of();
            val order = List.<Integer>of();
            assertThatThrownBy(() -> KleeneDdIndex.build(empty, order, limit))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Node limit must be positive.");
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("io.sapl.compiler.index.kleene.KleeneDdIndexTests#invalidOrders")
        @DisplayName("the predicate order covers every atom without duplicates")
        void whenOrderInvalidThenConstructionRejectsIt(List<Integer> order, String message) {
            val documents = List.<Formula>of(new Atom(0));
            assertThatThrownBy(() -> KleeneDdIndex.build(documents, order, NODE_LIMIT))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage(message);
        }

        @Test
        @DisplayName("lookup results cannot mutate a shared terminal")
        void whenCallerChangesResultThenSharedIndexRemainsUnchanged() {
            val index  = build(new Atom(0), new Constant(ERROR));
            val result = index.lookup(ignored -> TRUE);
            assertThatThrownBy(() -> result.trueMatches().add(3)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> result.errorMatches().add(3)).isInstanceOf(UnsupportedOperationException.class);
            assertThat(index.lookup(ignored -> FALSE)).isEqualTo(matches(FALSE, ERROR));
            assertThat(index.lookup(ignored -> TRUE)).isEqualTo(matches(TRUE, ERROR));
        }

        @ParameterizedTest(name = "null collection: documents={0}, order={1}")
        @MethodSource("io.sapl.compiler.index.kleene.KleeneDdIndexTests#nullInputs")
        @DisplayName("null input collections are rejected")
        void whenInputCollectionNullThenConstructionRejectsIt(List<Formula> documents, List<Integer> order) {
            assertThatThrownBy(() -> KleeneDdIndex.build(documents, order, NODE_LIMIT))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("predicate evaluation must supply a defined Kleene outcome")
        void whenPredicateOutcomeNullThenLookupRejectsIt() {
            val index = build(new Atom(0));
            assertThatThrownBy(() -> index.lookup(ignored -> null)).isInstanceOf(NullPointerException.class);
        }
    }

    static Stream<List<Integer>> predicateOrders() {
        return Stream.of(List.of(0, 1, 2), List.of(0, 2, 1), List.of(1, 0, 2), List.of(1, 2, 0), List.of(2, 0, 1),
                List.of(2, 1, 0));
    }

    static Stream<Arguments> invalidOrders() {
        return Stream.of(arguments(List.of(0, 0), "Duplicate predicate in order: 0"),
                arguments(List.of(1), "Predicate missing from order: 0"));
    }

    static Stream<Arguments> nullInputs() {
        return Stream.of(arguments(null, List.of()), arguments(List.of(), null));
    }

    private static KleeneDdIndex build(Formula... formulas) {
        return KleeneDdIndex.build(List.of(formulas), List.of(0, 1, 2), NODE_LIMIT);
    }

    private static Matches matches(Truth... outcomes) {
        val matched = new HashSet<Integer>();
        val errored = new HashSet<Integer>();
        for (var document = 0; document < outcomes.length; document++) {
            if (outcomes[document] == TRUE) {
                matched.add(document);
            } else if (outcomes[document] == ERROR) {
                errored.add(document);
            }
        }
        return new Matches(matched, errored);
    }

    private static Formula randomFormula(Random random, int depth) {
        if (depth == 0) {
            return random.nextBoolean() ? new Atom(random.nextInt(3)) : new Constant(Truth.values()[random.nextInt(3)]);
        }
        return switch (random.nextInt(4)) {
        case 0  -> new Atom(random.nextInt(3));
        case 1  -> new Not(randomFormula(random, depth - 1));
        case 2  -> new And(List.of(randomFormula(random, depth - 1), randomFormula(random, depth - 1)));
        default -> new Or(List.of(randomFormula(random, depth - 1), randomFormula(random, depth - 1)));
        };
    }

    // Independent truth-table interpreter, without DD truth operations or reduction.
    private static Truth interpret(Formula formula, List<Truth> valuation) {
        return switch (formula) {
        case Constant(var value) -> value;
        case Atom(var predicate) -> valuation.get(predicate);
        case Not(var operand)    -> switch (interpret(operand, valuation)) {
                             case TRUE      -> FALSE;
                             case FALSE     -> TRUE;
                             case ERROR     -> ERROR;
                             };
        case And(var operands)   -> {
            val outcomes = operands.stream().map(operand -> interpret(operand, valuation)).toList();
            if (outcomes.contains(FALSE)) {
                yield FALSE;
            }
            yield outcomes.contains(ERROR) ? ERROR : TRUE;
        }
        case Or(var operands)    -> {
            val outcomes = operands.stream().map(operand -> interpret(operand, valuation)).toList();
            if (outcomes.contains(TRUE)) {
                yield TRUE;
            }
            yield outcomes.contains(ERROR) ? ERROR : FALSE;
        }
        };
    }
}
