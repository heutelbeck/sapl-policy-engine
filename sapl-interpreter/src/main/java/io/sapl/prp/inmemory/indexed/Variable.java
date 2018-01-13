package io.sapl.prp.inmemory.indexed;

import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.google.common.base.Objects;
import com.google.common.math.DoubleMath;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;

public class Variable implements Comparable<Variable> {

	private final BitSet bitSetOfOccurencesInCandidates = new BitSet();
	private final BitSet bitSetOfUnsatisfiableCandidatesWhenFalse = new BitSet();
	private final BitSet bitSetOfUnsatisfiableCandidatesWhenTrue = new BitSet();
	private final Bool bool;
	private int groupedNumberOfNegatives;
	private int groupedNumberOfPositives;
	private int numberOfNegatives;
	private int numberOfPositives;
	private double relevance;
	private final List<Double> relevances = new LinkedList<>();
	private double score;
	private final Set<Integer> setOfSatisfiableCandidatesWhenFalse = new HashSet<>();
	private final Set<Integer> setOfSatisfiableCandidatesWhenTrue = new HashSet<>();
	private final Set<ConjunctiveClause> setOfUnsatisfiableClausesIfFalse = new HashSet<>();
	private final Set<ConjunctiveClause> setOfUnsatisfiableClausesIfTrue = new HashSet<>();

	public Variable(final Bool bool) {
		this.bool = bool;
	}

	public void addToClauseRelevanceList(double relevanceForClause) {
		relevances.add(relevanceForClause);
	}

	public void addToSetOfSatisfiableCandidatesWhenFalse(Integer index) {
		setOfSatisfiableCandidatesWhenFalse.add(index);
	}

	public void addToSetOfSatisfiableCandidatesWhenTrue(Integer index) {
		setOfSatisfiableCandidatesWhenTrue.add(index);
	}

	public void addToSetOfUnsatisfiableClausesIfFalse(final ConjunctiveClause clause) {
		setOfUnsatisfiableClausesIfFalse.add(clause);
	}

	public void addToSetOfUnsatisfiableClausesIfTrue(final ConjunctiveClause clause) {
		setOfUnsatisfiableClausesIfTrue.add(clause);
	}

	@Override
	public int compareTo(Variable o) {
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

	public void eliminateUnsatisfiableCandidates(final BitSet candidates, boolean value) {
		candidates.andNot((value) ? getUnsatisfiedCandidatesWhenTrue() : getUnsatisfiedCandidatesWhenFalse());
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
		final Variable other = (Variable) obj;
		if (!Objects.equal(bitSetOfOccurencesInCandidates, other.bitSetOfOccurencesInCandidates)) {
			return false;
		}
		if (!Objects.equal(bitSetOfUnsatisfiableCandidatesWhenFalse, other.bitSetOfUnsatisfiableCandidatesWhenFalse)) {
			return false;
		}
		if (!Objects.equal(bitSetOfUnsatisfiableCandidatesWhenTrue, other.bitSetOfUnsatisfiableCandidatesWhenTrue)) {
			return false;
		}
		if (!Objects.equal(bool, other.bool)) {
			return false;
		}
		if (!Objects.equal(groupedNumberOfNegatives, other.groupedNumberOfNegatives)) {
			return false;
		}
		if (!Objects.equal(groupedNumberOfPositives, other.groupedNumberOfPositives)) {
			return false;
		}
		if (!Objects.equal(numberOfNegatives, other.numberOfNegatives)) {
			return false;
		}
		if (!Objects.equal(numberOfPositives, other.numberOfPositives)) {
			return false;
		}
		if (!Objects.equal(relevance, other.relevance)) {
			return false;
		}
		if (!Objects.equal(relevances, other.relevances)) {
			return false;
		}
		if (!Objects.equal(score, other.score)) {
			return false;
		}
		if (!Objects.equal(setOfUnsatisfiableClausesIfFalse, other.setOfUnsatisfiableClausesIfFalse)) {
			return false;
		}
		return Objects.equal(setOfUnsatisfiableClausesIfTrue, other.setOfUnsatisfiableClausesIfTrue);
	}

	public boolean evaluate(final FunctionContext functionCtx, final VariableContext variableCtx)
			throws PolicyEvaluationException {
		return getBool().evaluate(functionCtx, variableCtx);
	}

	public Bool getBool() {
		return bool;
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

	public BitSet getOccurences() {
		return bitSetOfOccurencesInCandidates;
	}

	public double getRelevance() {
		return relevance;
	}

	public Set<Integer> getSatisfiableCandidates(boolean value) {
		return (value) ? getSatisfiableCandidatesWhenTrue() : getSatisfiableCandidatesWhenFalse();
	}

	public Set<Integer> getSatisfiableCandidatesWhenFalse() {
		return Collections.unmodifiableSet(setOfSatisfiableCandidatesWhenFalse);
	}

	public Set<Integer> getSatisfiableCandidatesWhenTrue() {
		return Collections.unmodifiableSet(setOfSatisfiableCandidatesWhenTrue);
	}

	public Set<ConjunctiveClause> getSetOfUnsatisfiableClausesIfFalse() {
		return Collections.unmodifiableSet(setOfUnsatisfiableClausesIfFalse);
	}

	public Set<ConjunctiveClause> getSetOfUnsatisfiableClausesIfTrue() {
		return Collections.unmodifiableSet(setOfUnsatisfiableClausesIfTrue);
	}

	public BitSet getUnsatisfiedCandidatesWhenFalse() {
		return bitSetOfUnsatisfiableCandidatesWhenFalse;
	}

	public BitSet getUnsatisfiedCandidatesWhenTrue() {
		return bitSetOfUnsatisfiableCandidatesWhenTrue;
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 31 * hash + Objects.hashCode(bitSetOfOccurencesInCandidates);
		hash = 31 * hash + Objects.hashCode(bitSetOfUnsatisfiableCandidatesWhenFalse);
		hash = 31 * hash + Objects.hashCode(bitSetOfUnsatisfiableCandidatesWhenTrue);
		hash = 31 * hash + Objects.hashCode(bool);
		hash = 31 * hash + Objects.hashCode(groupedNumberOfNegatives);
		hash = 31 * hash + Objects.hashCode(groupedNumberOfPositives);
		hash = 31 * hash + Objects.hashCode(numberOfNegatives);
		hash = 31 * hash + Objects.hashCode(numberOfPositives);
		hash = 31 * hash + Objects.hashCode(relevance);
		hash = 31 * hash + Objects.hashCode(relevances);
		hash = 31 * hash + Objects.hashCode(score);
		hash = 31 * hash + Objects.hashCode(setOfUnsatisfiableClausesIfFalse);
		hash = 31 * hash + Objects.hashCode(setOfUnsatisfiableClausesIfTrue);
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

	public boolean isPartOfCandidates(final BitSet candidates) {
		return bitSetOfOccurencesInCandidates.intersects(candidates);
	}

	public void setEnergyScore(double score) {
		this.score = score;
	}

	public void setRelevance(double relevance) {
		this.relevance = relevance;
	}
}
