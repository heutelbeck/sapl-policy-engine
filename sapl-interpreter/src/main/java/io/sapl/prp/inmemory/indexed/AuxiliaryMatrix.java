package io.sapl.prp.inmemory.indexed;

import java.util.Arrays;

import com.google.common.base.Preconditions;

public class AuxiliaryMatrix {

	private final int size;
	private final int[] uncheckedLiteralsOfClause;
	private final int[] uncheckedOccurrencesOfClause;

	public AuxiliaryMatrix(final AuxiliaryMatrix matrix) {
		size = matrix.size;
		uncheckedLiteralsOfClause = Arrays.copyOf(matrix.uncheckedLiteralsOfClause, size);
		uncheckedOccurrencesOfClause = Arrays.copyOf(matrix.uncheckedOccurrencesOfClause, size);
	}

	public AuxiliaryMatrix(final int[] sizeOfClauses, final int[] occurrencesOfClauses) {
		Preconditions.checkArgument(sizeOfClauses.length == occurrencesOfClauses.length);
		size = sizeOfClauses.length;
		uncheckedLiteralsOfClause = Arrays.copyOf(sizeOfClauses, size);
		uncheckedOccurrencesOfClause = Arrays.copyOf(occurrencesOfClauses, size);
	}

	public int decrementAndGetRemainingLiteralsOfClause(int index) {
		uncheckedLiteralsOfClause[index] -= 1;
		return uncheckedLiteralsOfClause[index];
	}

	public int decrementAndGetRemainingOccurrencesOfClause(int index) {
		uncheckedOccurrencesOfClause[index] -= 1;
		return --uncheckedOccurrencesOfClause[index];
	}

	public int size() {
		return size;
	}
}
