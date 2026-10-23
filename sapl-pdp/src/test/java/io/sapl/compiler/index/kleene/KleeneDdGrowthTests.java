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

import static io.sapl.compiler.index.kleene.KleeneDdIndex.Truth.FALSE;
import static io.sapl.compiler.index.kleene.KleeneDdIndex.Truth.TRUE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.compiler.index.IndexTestFixtures;
import io.sapl.compiler.index.dnf.DnfNormalizer;
import io.sapl.compiler.index.kleene.KleeneDdIndex.And;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Atom;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Formula;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Matches;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Or;
import lombok.val;

@DisplayName("Bounded examples separate DNF expansion from decision-diagram growth")
class KleeneDdGrowthTests {

    private static final int NODE_LIMIT = 100_000;

    @ParameterizedTest(name = "{0} independent alternatives")
    @ValueSource(ints = { 1, 2, 4, 6, 8 })
    @DisplayName("conjoined alternatives expand exponentially in DNF but have a linear ordered diagram")
    void whenConjunctionContainsIndependentAlternativesThenDnfAndDiagramGrowDifferently(int groups) {
        val alternatives = new ArrayList<Formula>();
        for (var group = 0; group < groups; group++) {
            alternatives.add(new Or(List.of(new Atom(2 * group), new Atom(2 * group + 1))));
        }
        val index = KleeneDdIndex.build(List.of(new And(alternatives)), order(2 * groups), NODE_LIMIT);
        val dnf   = DnfNormalizer.normalize(IndexTestFixtures.andOfOrs(groups));
        assertThat(dnf.clauses()).hasSize(1 << groups);
        assertThat(index.statistics()).satisfies(stats -> {
            assertThat(stats.decisionNodes()).isEqualTo(5 * groups - 2);
            assertThat(stats.terminals()).isEqualTo(3);
            assertThat(stats.allocatedNodes()).isGreaterThan(stats.decisionNodes() + stats.terminals());
        });
        assertThat(index.lookup(predicate -> predicate % 2 == 0 ? TRUE : FALSE))
                .isEqualTo(new Matches(Set.of(0), Set.of()));
        assertThat(index.lookup(predicate -> predicate < 2 ? FALSE : TRUE)).isEqualTo(new Matches(Set.of(), Set.of()));
    }

    @ParameterizedTest(name = "{0} independently applicable documents")
    @ValueSource(ints = { 1, 2, 3, 4, 5, 6, 7 })
    @DisplayName("independent document outcomes require exponentially many distinct result terminals")
    void whenDocumentsMatchIndependentlyThenEveryOutcomeVectorHasADistinctTerminal(int documents) {
        val formulas = IntStream.range(0, documents).mapToObj(predicate -> (Formula) new Atom(predicate)).toList();
        val index    = KleeneDdIndex.build(formulas, order(documents), NODE_LIMIT);
        val outcomes = powerOfThree(documents);
        assertThat(index.statistics()).satisfies(stats -> {
            assertThat(stats.terminals()).isEqualTo(outcomes);
            assertThat(stats.decisionNodes()).isEqualTo((outcomes - 1) / 2);
        });
        val booleanResults = new HashSet<Matches>();
        for (var assignment = 0; assignment < (1 << documents); assignment++) {
            val bits = assignment;
            booleanResults.add(index.lookup(predicate -> (bits & (1 << predicate)) == 0 ? FALSE : TRUE));
        }
        assertThat(booleanResults).hasSize(1 << documents)
                .allSatisfy(result -> assertThat(result.errorMatches()).isEmpty());
    }

    @ParameterizedTest(name = "{0} mutually exclusive role predicates")
    @ValueSource(ints = { 2, 3, 4, 5, 6 })
    @DisplayName("opaque role predicates retain outcome combinations that no single-valued role can produce")
    void whenRolesMutuallyExclusiveThenMostStructuralTerminalsAreUnreachableByTheseRequests(int roles) {
        val formulas      = IntStream.range(0, roles).mapToObj(predicate -> (Formula) new Atom(predicate)).toList();
        val index         = KleeneDdIndex.build(formulas, order(roles), NODE_LIMIT);
        val actualResults = new HashSet<Matches>();
        for (var role = 0; role <= roles; role++) {
            val selected = role;
            val result   = index.lookup(predicate -> predicate == selected ? TRUE : FALSE);
            val expected = role < roles ? Set.of(role) : Set.<Integer>of();
            assertThat(result).isEqualTo(new Matches(expected, Set.of()));
            actualResults.add(result);
        }
        assertThat(actualResults).hasSize(roles + 1);
        assertThat(index.statistics().terminals()).isEqualTo(powerOfThree(roles)).isGreaterThan(actualResults.size());
    }

    @ParameterizedTest(name = "{0} predicate pairs")
    @ValueSource(ints = { 3, 4, 5 })
    @DisplayName("predicate ordering changes graph size without changing applicability")
    void whenRelatedPredicatesSeparatedThenTheSameFormulaNeedsALargerDiagram(int pairs) {
        val alternatives = new ArrayList<Formula>();
        for (var pair = 0; pair < pairs; pair++) {
            alternatives.add(new And(List.of(new Atom(2 * pair), new Atom(2 * pair + 1))));
        }
        val formulas       = List.<Formula>of(new Or(alternatives));
        val interleaved    = KleeneDdIndex.build(formulas, order(2 * pairs), NODE_LIMIT);
        val separatedOrder = new ArrayList<Integer>();
        for (var offset = 0; offset < 2; offset++) {
            for (var pair = 0; pair < pairs; pair++) {
                separatedOrder.add(2 * pair + offset);
            }
        }
        val separated = KleeneDdIndex.build(formulas, separatedOrder, NODE_LIMIT);
        assertThat(separated.statistics().decisionNodes()).isGreaterThan(interleaved.statistics().decisionNodes());
        for (var assignment = 0; assignment < (1 << (2 * pairs)); assignment++) {
            val bits = assignment;
            assertThat(separated.lookup(predicate -> (bits & (1 << predicate)) == 0 ? FALSE : TRUE))
                    .isEqualTo(interleaved.lookup(predicate -> (bits & (1 << predicate)) == 0 ? FALSE : TRUE));
        }
    }

    private static List<Integer> order(int predicates) {
        return IntStream.range(0, predicates).boxed().toList();
    }

    private static int powerOfThree(int exponent) {
        var result = 1;
        for (var factor = 0; factor < exponent; factor++) {
            result *= 3;
        }
        return result;
    }
}
