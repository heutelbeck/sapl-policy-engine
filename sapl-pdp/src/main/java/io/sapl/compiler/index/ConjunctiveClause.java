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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A conjunction (AND) of literals in a DNF formula.
 * <p>
 * Represents {@code l1 AND l2 AND ... AND ln}. Uses set semantics for equality:
 * two clauses are equal if they contain the same literals regardless of order.
 *
 * @param literals the literals in this conjunction
 */
public record ConjunctiveClause(List<Literal> literals) {

    /**
     * Creates a clause from the given literals.
     *
     * @param literals the literals forming this conjunction
     */
    public ConjunctiveClause(List<Literal> literals) {
        this.literals = List.copyOf(literals);
    }

    /**
     * Creates a single-literal clause.
     *
     * @param literal the single literal
     */
    public ConjunctiveClause(Literal literal) {
        this(List.of(literal));
    }

    /**
     * @return the number of literals in this clause
     */
    public int size() {
        return literals.size();
    }

    /**
     * @return true if this clause is empty (a tautology in conjunction)
     */
    public boolean isEmpty() {
        return literals.isEmpty();
    }

    /**
     * Returns true if this clause contains complementary literals, i.e., both
     * {@code p} and {@code !p} for some predicate. Such a clause is
     * unsatisfiable and can be removed from a DNF formula.
     *
     * @return true if this clause contains a literal and its negation
     */
    public boolean isUnsatisfiable() {
        for (var literal : literals) {
            if (literals.contains(literal.negate())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if every literal in this clause is also in the other clause.
     *
     * @param other the clause to check against
     * @return true if this is a subset of other
     */
    public boolean isSubsetOf(ConjunctiveClause other) {
        return asSet(other.literals).containsAll(asSet(literals));
    }

    private static Set<Literal> asSet(List<Literal> list) {
        return new HashSet<>(list);
    }

    @Override
    public int hashCode() {
        return asSet(literals).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof ConjunctiveClause(var otherLiterals) && asSet(literals).equals(asSet(otherLiterals));
    }

}
