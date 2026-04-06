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
package io.sapl.compiler.index.dnf;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
import io.sapl.api.model.BooleanExpression.Constant;
import io.sapl.api.model.BooleanExpression.Not;
import io.sapl.api.model.BooleanExpression.Or;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.compiler.index.dnf.DnfNormalizer.normalize;
import static io.sapl.compiler.index.IndexTestFixtures.atom;
import static io.sapl.compiler.index.IndexTestFixtures.negativeLiteral;
import static io.sapl.compiler.index.IndexTestFixtures.positiveLiteral;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("DnfNormalizer")
class DnfNormalizerTests {

    @Nested
    @DisplayName("normalizes to expected DNF")
    class NormalizationTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenExpressionThenExpectedDnf(String description, BooleanExpression input, DisjunctiveFormula expected) {
            assertThat(normalize(input)).isEqualTo(expected);
        }

        static Stream<Arguments> whenExpressionThenExpectedDnf() {
            return Stream.of(
                    // Constants
                    arguments("true -> TRUE", new Constant(true), DisjunctiveFormula.TRUE),
                    arguments("false -> FALSE", new Constant(false), DisjunctiveFormula.FALSE),
                    arguments("!true -> FALSE", new Not(new Constant(true)), DisjunctiveFormula.FALSE),
                    arguments("!false -> TRUE", new Not(new Constant(false)), DisjunctiveFormula.TRUE),

                    // Atoms
                    arguments("p1 -> (p1)", atom(1L), formula(clause(positiveLiteral(1L)))),
                    arguments("!p1 -> (!p1)", new Not(atom(1L)), formula(clause(negativeLiteral(1L)))),

                    // Double/triple negation
                    arguments("!!p1 -> (p1)", new Not(new Not(atom(1L))), formula(clause(positiveLiteral(1L)))),
                    arguments("!!!p1 -> (!p1)", new Not(new Not(new Not(atom(1L)))),
                            formula(clause(negativeLiteral(1L)))),

                    // Disjunction
                    arguments("p1 OR p2 -> (p1) | (p2)", new Or(atom(1L), atom(2L)),
                            formula(clause(positiveLiteral(1L)), clause(positiveLiteral(2L)))),
                    arguments("p1 OR TRUE -> TRUE", new Or(atom(1L), new Constant(true)), DisjunctiveFormula.TRUE),
                    arguments("p1 OR FALSE -> (p1)", new Or(atom(1L), new Constant(false)),
                            formula(clause(positiveLiteral(1L)))),

                    // Conjunction
                    arguments("p1 AND p2 -> (p1 & p2)", new And(atom(1L), atom(2L)),
                            formula(clause(positiveLiteral(1L), positiveLiteral(2L)))),
                    arguments("p1 AND TRUE -> (p1)", new And(atom(1L), new Constant(true)),
                            formula(clause(positiveLiteral(1L)))),
                    arguments("p1 AND FALSE -> FALSE", new And(atom(1L), new Constant(false)),
                            DisjunctiveFormula.FALSE),

                    // Distribution
                    arguments("(p1 OR p2) AND p3 -> (p1 & p3) | (p2 & p3)",
                            new And(new Or(atom(1L), atom(2L)), atom(3L)),
                            formula(clause(positiveLiteral(1L), positiveLiteral(3L)),
                                    clause(positiveLiteral(2L), positiveLiteral(3L)))),

                    // De Morgan
                    arguments("!(p1 AND p2) -> (!p1) | (!p2)", new Not(new And(atom(1L), atom(2L))),
                            formula(clause(negativeLiteral(1L)), clause(negativeLiteral(2L)))),
                    arguments("!(p1 OR p2) -> (!p1 & !p2)", new Not(new Or(atom(1L), atom(2L))),
                            formula(clause(negativeLiteral(1L), negativeLiteral(2L)))),

                    // Deeply nested: !(p1 AND (p2 OR p3))
                    // = !p1 OR !(p2 OR p3) = !p1 OR (!p2 AND !p3)
                    arguments("!(p1 AND (p2 OR p3)) -> (!p1) | (!p2 & !p3)",
                            new Not(new And(atom(1L), new Or(atom(2L), atom(3L)))),
                            formula(clause(negativeLiteral(1L)), clause(negativeLiteral(2L), negativeLiteral(3L)))),

                    // Deeply nested: !((p1 OR p2) AND (p3 OR p4))
                    // = !(p1 OR p2) OR !(p3 OR p4)
                    // = (!p1 AND !p2) OR (!p3 AND !p4)
                    arguments("!((p1 OR p2) AND (p3 OR p4)) -> (!p1 & !p2) | (!p3 & !p4)",
                            new Not(new And(new Or(atom(1L), atom(2L)), new Or(atom(3L), atom(4L)))),
                            formula(clause(negativeLiteral(1L), negativeLiteral(2L)),
                                    clause(negativeLiteral(3L), negativeLiteral(4L)))),

                    // Triple nesting: (p1 AND p2) OR (p3 AND (p4 OR p5))
                    // = (p1 & p2) | (p3 & p4) | (p3 & p5)
                    arguments("(p1 AND p2) OR (p3 AND (p4 OR p5)) -> (p1&p2) | (p3&p4) | (p3&p5)",
                            new Or(new And(atom(1L), atom(2L)), new And(atom(3L), new Or(atom(4L), atom(5L)))),
                            formula(clause(positiveLiteral(1L), positiveLiteral(2L)),
                                    clause(positiveLiteral(3L), positiveLiteral(4L)),
                                    clause(positiveLiteral(3L), positiveLiteral(5L)))),

                    // Negation inside distribution: !(p1 OR p2) AND p3
                    // = (!p1 AND !p2) AND p3 = (!p1 & !p2 & p3)
                    arguments("!(p1 OR p2) AND p3 -> (!p1 & !p2 & p3)",
                            new And(new Not(new Or(atom(1L), atom(2L))), atom(3L)),
                            formula(clause(negativeLiteral(1L), negativeLiteral(2L), positiveLiteral(3L)))),

                    // N-ary: three-way AND
                    arguments("p1 AND p2 AND p3 -> (p1 & p2 & p3)", new And(atom(1L), atom(2L), atom(3L)),
                            formula(clause(positiveLiteral(1L), positiveLiteral(2L), positiveLiteral(3L)))),

                    // N-ary: three-way OR
                    arguments("p1 OR p2 OR p3 -> (p1) | (p2) | (p3)", new Or(atom(1L), atom(2L), atom(3L)), formula(
                            clause(positiveLiteral(1L)), clause(positiveLiteral(2L)), clause(positiveLiteral(3L)))));
        }
    }

    @Nested
    @DisplayName("reduction after normalization")
    class ReductionTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenRedundantThenReduced(String description, BooleanExpression input, int expectedClauseCount) {
            assertThat(normalize(input).clauses()).hasSize(expectedClauseCount);
        }

        static Stream<Arguments> whenRedundantThenReduced() {
            return Stream.of(arguments("p1 OR p1 deduplicates to 1 clause", new Or(atom(1L), atom(1L)), 1),
                    arguments("p1 OR (p1 AND p2) subsumes to 1 clause", new Or(atom(1L), new And(atom(1L), atom(2L))),
                            1),
                    arguments("(p1 AND !p1) OR p2 eliminates unsatisfiable, leaves 1 clause",
                            new Or(new And(atom(1L), new Not(atom(1L))), atom(2L)), 1),
                    arguments("(p1 OR p2) AND (p3 OR p4) produces 4 independent clauses",
                            new And(new Or(atom(1L), atom(2L)), new Or(atom(3L), atom(4L))), 4));
        }
    }

    private static ConjunctiveClause clause(Literal... literals) {
        return new ConjunctiveClause(List.of(literals));
    }

    private static DisjunctiveFormula formula(ConjunctiveClause... clauses) {
        return new DisjunctiveFormula(List.of(clauses));
    }

}
