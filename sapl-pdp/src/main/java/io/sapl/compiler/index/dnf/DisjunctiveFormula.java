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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A formula in Disjunctive Normal Form (DNF): an OR of conjunctive clauses.
 * <p>
 * Represents {@code c1 OR c2 OR ... OR cm}. Uses set semantics for equality:
 * two formulas are equal if they contain the same clauses regardless of order.
 * <p>
 * Constants:
 * <ul>
 * <li>{@link #TRUE} - always satisfied (single empty conjunction)</li>
 * <li>{@link #FALSE} - never satisfied (no clauses)</li>
 * </ul>
 *
 * @param clauses the disjuncts of this formula
 */
public record DisjunctiveFormula(List<ConjunctiveClause> clauses) {

    /** Always satisfied: a single empty conjunction (vacuous truth). */
    public static final DisjunctiveFormula TRUE = new DisjunctiveFormula(List.of(new ConjunctiveClause(List.of())));

    /** Never satisfied: no clauses at all. */
    public static final DisjunctiveFormula FALSE = new DisjunctiveFormula(List.of());

    /**
     * Creates a formula from the given clauses.
     *
     * @param clauses the disjuncts
     */
    public DisjunctiveFormula(List<ConjunctiveClause> clauses) {
        this.clauses = List.copyOf(clauses);
    }

    /**
     * Creates a single-clause formula.
     *
     * @param clause the single clause
     */
    public DisjunctiveFormula(ConjunctiveClause clause) {
        this(List.of(clause));
    }

    /**
     * @return true if this formula has no clauses (always false)
     */
    public boolean isFalse() {
        return clauses.isEmpty();
    }

    /**
     * @return true if this formula contains an empty clause (always true)
     */
    public boolean isTrue() {
        return clauses.stream().anyMatch(ConjunctiveClause::isEmpty);
    }

    /**
     * Returns a reduced copy of this formula with unsatisfiable, duplicate, and
     * subsumed clauses removed.
     * <ul>
     * <li>Unsatisfiable: a clause containing both {@code p} and {@code !p}</li>
     * <li>Duplicate: identical clauses (by set equality)</li>
     * <li>Subsumed: a clause that is a superset of another (the smaller clause
     * is more general and already covers its cases)</li>
     * </ul>
     *
     * @return reduced formula
     */
    public DisjunctiveFormula reduce() {
        var unique  = new ArrayList<>(new HashSet<>(clauses));
        var reduced = new ArrayList<ConjunctiveClause>(unique.size());
        for (var candidate : unique) {
            if (!candidate.isUnsatisfiable() && !isSubsumed(candidate, unique)) {
                reduced.add(candidate);
            }
        }
        return new DisjunctiveFormula(reduced);
    }

    private static boolean isSubsumed(ConjunctiveClause candidate, List<ConjunctiveClause> allClauses) {
        for (var other : allClauses) {
            if (other != candidate && candidate.size() > other.size() && other.isSubsetOf(candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return asSet().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof DisjunctiveFormula(var otherClauses) && asSet().equals(new HashSet<>(otherClauses));
    }

    private Set<ConjunctiveClause> asSet() {
        return new HashSet<>(clauses);
    }

}
