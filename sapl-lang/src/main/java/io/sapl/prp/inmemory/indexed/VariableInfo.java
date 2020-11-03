/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.inmemory.indexed;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.math.DoubleMath;

@Deprecated
public class VariableInfo implements Comparable<VariableInfo> {

	private int groupedNumberOfNegatives;

	private int groupedNumberOfPositives;

	private int numberOfNegatives;

	private int numberOfPositives;

	private double relevance;

	private final List<Double> relevances = new LinkedList<>();

	private double score;

	private final Set<ConjunctiveClause> setOfUnsatisfiableClausesIfFalse = new HashSet<>();

	private final Set<ConjunctiveClause> setOfUnsatisfiableClausesIfTrue = new HashSet<>();

	private final Variable variable;

	public VariableInfo(final Variable variable) {
		this.variable = Preconditions.checkNotNull(variable);
	}

	public void addToClauseRelevanceList(double relevanceForClause) {
		relevances.add(relevanceForClause);
	}

	public void addToSetOfUnsatisfiableClausesIfFalse(final ConjunctiveClause clause) {
		setOfUnsatisfiableClausesIfFalse.add(clause);
	}

	public void addToSetOfUnsatisfiableClausesIfTrue(final ConjunctiveClause clause) {
		setOfUnsatisfiableClausesIfTrue.add(clause);
	}

	@Override
	public int compareTo(VariableInfo o) {
		final double epsilon = 0.000000001;
		double lhs = getEnergyScore();
		double rhs = o.getEnergyScore();

		if (DoubleMath.fuzzyEquals(lhs, rhs, epsilon)) {
			return 0;
		}
		if (lhs < rhs) {
			return -1;
		}
		return 1;
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
		final VariableInfo other = (VariableInfo) obj;
		return compareTo(other) == 0;
	}

	public List<Double> getClauseRelevancesList() {
		return Collections.unmodifiableList(relevances);
	}

	public double getEnergyScore() {
		return score;
	}

	public int getGroupedNumberOfNegatives() {
		return groupedNumberOfNegatives;
	}

	public int getGroupedNumberOfPositives() {
		return groupedNumberOfPositives;
	}

	public int getNumberOfNegatives() {
		return numberOfNegatives;
	}

	public int getNumberOfPositives() {
		return numberOfPositives;
	}

	public double getRelevance() {
		return relevance;
	}

	public Set<ConjunctiveClause> getSetOfUnsatisfiableClausesIfFalse() {
		return Collections.unmodifiableSet(setOfUnsatisfiableClausesIfFalse);
	}

	public Set<ConjunctiveClause> getSetOfUnsatisfiableClausesIfTrue() {
		return Collections.unmodifiableSet(setOfUnsatisfiableClausesIfTrue);
	}

	public Variable getVariable() {
		return variable;
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 31 * hash + Objects.hashCode(score);
		return hash;
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

	public void setEnergyScore(double score) {
		this.score = score;
	}

	public void setRelevance(double relevance) {
		this.relevance = relevance;
	}

}
