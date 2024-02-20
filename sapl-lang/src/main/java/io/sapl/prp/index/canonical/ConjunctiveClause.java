/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

public class ConjunctiveClause {

    static final String CONSTRUCTION_FAILED = "Failed to create instance, empty collection provided.";

    private int hash;

    private boolean hasHashCode;

    private final List<Literal> literals;

    public ConjunctiveClause(@NonNull Collection<Literal> literals) {
        if (literals.isEmpty())
            throw new IllegalArgumentException(CONSTRUCTION_FAILED);

        this.literals = new ArrayList<>(literals);
    }

    public ConjunctiveClause(Literal... literals) {
        this(Arrays.asList(literals));
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
        final ConjunctiveClause other = (ConjunctiveClause) obj;
        if (literals.size() != other.literals.size()) {
            return false;
        }

        return (new HashSet<>(literals).containsAll(other.literals)
                && new HashSet<>(other.literals).containsAll(literals));
    }

    public boolean evaluate() {
        ListIterator<Literal> iter   = literals.listIterator();
        Literal               first  = iter.next();
        boolean               result = first.evaluate();
        while (iter.hasNext()) {
            if (!result) {
                return false;
            }
            result = iter.next().evaluate();
        }
        return result;
    }

    public List<Literal> getLiterals() {
        return Collections.unmodifiableList(literals);
    }

    @Override
    public int hashCode() {
        if (!hasHashCode) {
            int h = 7;
            h           = 23 * h + literals.stream().mapToInt(Objects::hashCode).sum();
            hash        = h;
            hasHashCode = true;
        }
        return hash;
    }

    public boolean isImmutable() {
        for (Literal literal : literals) {
            if (!literal.isImmutable()) {
                return false;
            }
        }
        return true;
    }

    public boolean isSubsetOf(final ConjunctiveClause other) {
        for (Literal lhs : literals) {
            boolean match = false;
            for (Literal rhs : other.literals) {
                if (lhs.sharesBool(rhs) && lhs.sharesNegation(rhs)) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                return false;
            }
        }
        return true;
    }

    public List<ConjunctiveClause> negate() {
        ListIterator<Literal>   iter   = literals.listIterator();
        List<ConjunctiveClause> result = new ArrayList<>(literals.size());
        while (iter.hasNext()) {
            Literal literal = iter.next();
            result.add(new ConjunctiveClause(literal.negate()));
        }
        return Collections.unmodifiableList(result);
    }

    public ConjunctiveClause reduce() {
        if (size() > 1) {
            List<Literal> result = new ArrayList<>(getLiterals());
            ConjunctiveClauseReductionSupport.reduceConstants(result);
            ConjunctiveClauseReductionSupport.reduceFormula(result);
            return new ConjunctiveClause(result);
        }
        return this;
    }

    public int size() {
        return literals.size();
    }

}
