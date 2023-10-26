/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.index.canonical;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import lombok.NonNull;

public class DisjunctiveFormula {

    static final String CONSTRUCTION_FAILED     = "Failed to create instance, empty collection provided.";
    static final String EVALUATION_NOT_POSSIBLE = "Evaluation Error: Attempting to evaluate empty formula.";

    private final List<ConjunctiveClause> clauses;

    private int hash;

    private boolean hasHashCode;

    public DisjunctiveFormula(@NonNull Collection<ConjunctiveClause> clauses) {
        if (clauses.isEmpty())
            throw new IllegalArgumentException(CONSTRUCTION_FAILED);

        this.clauses = new ArrayList<>(clauses);
    }

    public DisjunctiveFormula(ConjunctiveClause... clauses) {
        this(Arrays.asList(clauses));
    }

    public DisjunctiveFormula combine(final DisjunctiveFormula formula) {
        List<ConjunctiveClause> result = new ArrayList<>(clauses);
        result.addAll(formula.clauses);
        return new DisjunctiveFormula(result).reduce();
    }

    public DisjunctiveFormula distribute(final DisjunctiveFormula formula) {
        List<ConjunctiveClause> result = new ArrayList<>(clauses.size() * formula.clauses.size());
        for (ConjunctiveClause lhs : clauses) {
            for (ConjunctiveClause rhs : formula.clauses) {
                List<Literal> literals = new ArrayList<>(lhs.size() + rhs.size());
                literals.addAll(lhs.getLiterals());
                literals.addAll(rhs.getLiterals());
                result.add(new ConjunctiveClause(literals));
            }
        }
        return new DisjunctiveFormula(result).reduce();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DisjunctiveFormula other = (DisjunctiveFormula) obj;
        if (clauses.size() != other.clauses.size()) {
            return false;
        }
        return (new HashSet<>(clauses).containsAll(other.clauses) && new HashSet<>(other.clauses).containsAll(clauses));
    }

    public boolean evaluate() {
        ListIterator<ConjunctiveClause> iter   = clauses.listIterator();
        ConjunctiveClause               first  = iter.next();
        boolean                         result = first.evaluate();
        while (iter.hasNext()) {
            if (result) {
                return true;
            }
            result = iter.next().evaluate();
        }
        return result;
    }

    public List<ConjunctiveClause> getClauses() {
        return Collections.unmodifiableList(clauses);
    }

    @Override
    public int hashCode() {
        if (!hasHashCode) {
            int h = 5;
            h           = 17 * h + clauses.stream().mapToInt(Objects::hashCode).sum();
            hash        = h;
            hasHashCode = true;
        }
        return hash;
    }

    public boolean isImmutable() {
        for (ConjunctiveClause clause : clauses) {
            if (!clause.isImmutable()) {
                return false;
            }
        }
        return true;
    }

    public DisjunctiveFormula negate() {
        ListIterator<ConjunctiveClause> iter   = clauses.listIterator();
        ConjunctiveClause               first  = iter.next();
        DisjunctiveFormula              result = new DisjunctiveFormula(first.negate());
        while (iter.hasNext()) {
            ConjunctiveClause clause = iter.next();
            result = result.distribute(new DisjunctiveFormula(clause.negate()));
        }
        return result.reduce();
    }

    public DisjunctiveFormula reduce() {
        List<ConjunctiveClause> clauseList = getClauses();
        List<ConjunctiveClause> result     = new ArrayList<>(clauseList.size());
        for (ConjunctiveClause clause : clauseList) {
            result.add(clause.reduce());
        }
        if (result.size() > 1) {
            DisjunctiveFormulaReductionSupport.reduceConstants(result);
            DisjunctiveFormulaReductionSupport.reduceFormula(result);
        }
        return new DisjunctiveFormula(result);
    }

    public int size() {
        return clauses.size();
    }

}
