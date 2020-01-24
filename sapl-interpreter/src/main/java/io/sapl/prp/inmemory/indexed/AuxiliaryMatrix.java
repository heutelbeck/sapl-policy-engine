/**
 * Copyright © 2017 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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

import java.util.Arrays;

import com.google.common.base.Preconditions;

public class AuxiliaryMatrix {

	private static final int EXPECTED_ROWS = 2;

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
		int indexOfUncheckedLiterals = 0;
		int indexOfUncheckedOccurrences = indexOfUncheckedLiterals + 1;
		int[] sizeOfClauses = matrix[indexOfUncheckedLiterals];
		int[] occurrencesOfClauses = matrix[indexOfUncheckedOccurrences];
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
		return uncheckedOccurrencesOfClause[index];
	}

	public int size() {
		return size;
	}

}
