/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.google.common.base.Preconditions;
import com.google.common.math.DoubleMath;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class PredicateInfo implements Comparable<PredicateInfo> {

    private static final double EPSILON = 0.000000001;
    private final Predicate predicate;
    private final Set<ConjunctiveClause> unsatisfiableConjunctionsIfFalse = new HashSet<>();
    private final Set<ConjunctiveClause> unsatisfiableConjunctionsIfTrue = new HashSet<>();

    /* required for existing variable order */
    @Getter
    private int groupedNumberOfNegatives;
    @Getter
    private int groupedNumberOfPositives;
    @Getter
    private int numberOfNegatives;
    @Getter
    private int numberOfPositives;
    @Getter
    @Setter
    private double relevance;
    @Getter
    @Setter
    private double score;
    private final List<Double> relevanceList = new LinkedList<>();

    public PredicateInfo(final Predicate predicate) {
        this.predicate = Preconditions.checkNotNull(predicate);
    }

    public Set<ConjunctiveClause> getUnsatisfiableConjunctionsIfFalse() {
        return Collections.unmodifiableSet(unsatisfiableConjunctionsIfFalse);
    }

    public Set<ConjunctiveClause> getUnsatisfiableConjunctionsIfTrue() {
        return Collections.unmodifiableSet(unsatisfiableConjunctionsIfTrue);
    }

    public Predicate getPredicate() {
        return predicate;
    }

    public void addUnsatisfiableConjunctionIfFalse(ConjunctiveClause clause) {
        unsatisfiableConjunctionsIfFalse.add(clause);
    }

    public void addUnsatisfiableConjunctionIfTrue(ConjunctiveClause clause) {
        unsatisfiableConjunctionsIfTrue.add(clause);
    }


    public List<Double> getClauseRelevanceList() {
        return Collections.unmodifiableList(relevanceList);
    }


    public void addToClauseRelevanceList(double relevanceForClause) {
        relevanceList.add(relevanceForClause);
    }

    public void incGroupedNumberOfNegatives() {
        ++groupedNumberOfNegatives;
    }

    public void incGroupedNumberOfPositives() {
        ++groupedNumberOfPositives;
    }

    public void incNumberOfNegatives() {
        ++numberOfNegatives;
    }

    public void incNumberOfPositives() {
        ++numberOfPositives;
    }


    @Override
    public int compareTo(PredicateInfo o) {
        double lhs = getScore();
        double rhs = o.getScore();

        if (DoubleMath.fuzzyEquals(lhs, rhs, EPSILON)) {
            return 0;
        }
        if (lhs < rhs) {
            return -1;
        }
        return 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PredicateInfo that = (PredicateInfo) o;
        return compareTo(that) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(predicate, unsatisfiableConjunctionsIfFalse, unsatisfiableConjunctionsIfTrue,
                groupedNumberOfNegatives, groupedNumberOfPositives, numberOfNegatives, numberOfPositives, relevance,
                relevanceList, score);
    }

}
