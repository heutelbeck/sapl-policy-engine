package io.sapl.prp.inmemory.indexed.improved;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.math.DoubleMath;

import io.sapl.prp.inmemory.indexed.ConjunctiveClause;

public class PredicateInfo implements Comparable<PredicateInfo> {

	private final Predicate predicate;

	private final Set<ConjunctiveClause> unsatisfiableConjunctionsIfFalse = new HashSet<>();

	private final Set<ConjunctiveClause> unsatisfiableConjunctionsIfTrue = new HashSet<>();

	/* required for existing variable order */
	private int groupedNumberOfNegatives;

	private int groupedNumberOfPositives;

	private int numberOfNegatives;

	private int numberOfPositives;

	private double relevance;

	private final List<Double> relevanceList = new LinkedList<>();

	private double score;

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

	public List<Double> getClauseRelevanceList() {
		return Collections.unmodifiableList(relevanceList);
	}

	public double getScore() {
		return score;
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

	public void setRelevance(double relevance) {
		this.relevance = relevance;
	}

	public void setScore(double score) {
		this.score = score;
	}

	@Override
	public int compareTo(PredicateInfo o) {
		final double epsilon = 0.000000001;
		double lhs = getScore();
		double rhs = o.getScore();

		if (DoubleMath.fuzzyEquals(lhs, rhs, epsilon)) {
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
