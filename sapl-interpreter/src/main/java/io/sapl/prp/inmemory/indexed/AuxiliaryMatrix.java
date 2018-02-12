package io.sapl.prp.inmemory.indexed;

import java.util.Arrays;

import com.google.common.base.Preconditions;

public class AuxiliaryMatrix {

	private static final int EXPECTED_ROWS = 2;
	private static final int INDEX_OF_UNCHECKED_LITERALS = 0;
	private static final int INDEX_OF_UNCHECKED_OCCURRENCES = 1;

	private final int size;
	private final int[] uncheckedLiteralsOfClause;
	private final int[] uncheckedOccurrencesOfClause;

	public AuxiliaryMatrix(final AuxiliaryMatrix matrix) {
		size = matrix.size;
		uncheckedLiteralsOfClause = Arrays.copyOf(matrix.uncheckedLiteralsOfClause, size);
		uncheckedOccurrencesOfClause = Arrays.copyOf(matrix.uncheckedOccurrencesOfClause, size);
	}

	public AuxiliaryMatrix(final int[]... matrix) {
		Preconditions.checkArgument(matrix.length == EXPECTED_ROWS);
		int[] sizeOfClauses = matrix[INDEX_OF_UNCHECKED_LITERALS];
		int[] occurrencesOfClauses = matrix[INDEX_OF_UNCHECKED_OCCURRENCES];
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
