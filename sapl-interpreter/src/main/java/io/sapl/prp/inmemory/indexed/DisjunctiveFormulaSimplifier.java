package io.sapl.prp.inmemory.indexed;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public class DisjunctiveFormulaSimplifier implements Simplifier<DisjunctiveFormula> {

	@Override
	public DisjunctiveFormula reduce(final DisjunctiveFormula obj) {
		List<ConjunctiveClause> clauses = obj.getClauses();
		List<ConjunctiveClause> result = new ArrayList<>(clauses.size());
		for (ConjunctiveClause clause : clauses) {
			result.add(clause.reduce());
		}
		if (result.size() > 1) {
			reduceConstants(result);
			reduceFormula(result);
		}
		return new DisjunctiveFormula(result);
	}

	private static void reduceConstants(final List<ConjunctiveClause> data) {
		ListIterator<ConjunctiveClause> iter = data.listIterator();
		while (iter.hasNext() && data.size() > 1) {
			ConjunctiveClause clause = iter.next();
			if (clause.isImmutable()) {
				if (clause.evaluate()) {
					data.clear();
					data.add(clause);
					return;
				} else {
					iter.remove();
				}
			}
		}
	}

	private static void reduceFormula(final List<ConjunctiveClause> data) {
		ListIterator<ConjunctiveClause> pointer = data.listIterator();
		while (pointer.hasNext()) {
			ConjunctiveClause lhs = pointer.next();
			if (lhs != null) {
				reduceFormulaStep(data, pointer, lhs);
			}
		}
		data.removeIf(Objects::isNull);
	}

	private static void reduceFormulaStep(final List<ConjunctiveClause> data,
			final ListIterator<ConjunctiveClause> pointer, final ConjunctiveClause value) {
		ListIterator<ConjunctiveClause> forward = data.listIterator(pointer.nextIndex());
		while (forward.hasNext()) {
			ConjunctiveClause rhs = forward.next();
			if (rhs == null) {
				continue;
			}
			if (value.isSubsetOf(rhs)) {
				forward.set(null);
			} else if (rhs.isSubsetOf(value)) {
				pointer.set(null);
				return;
			}
		}
	}
}
