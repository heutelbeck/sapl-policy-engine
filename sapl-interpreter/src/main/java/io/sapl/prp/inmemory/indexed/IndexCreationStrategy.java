/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.prp.inmemory.indexed;

import io.sapl.grammar.sapl.SAPL;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public interface IndexCreationStrategy {

	IndexContainer construct(final Map<String, SAPL> documents, final Map<String, DisjunctiveFormula> targets);

	default Map<ConjunctiveClause, Set<DisjunctiveFormula>> mapClauseToFormulas(
			final Collection<DisjunctiveFormula> formulas) {
		Map<ConjunctiveClause, Set<DisjunctiveFormula>> result = new HashMap<>();
		for (DisjunctiveFormula formula : formulas) {
			for (ConjunctiveClause clause : formula.getClauses()) {
				Set<DisjunctiveFormula> set = result.computeIfAbsent(clause, k -> new HashSet<>());
				set.add(formula);
			}
		}
		return result;
	}

	default Map<DisjunctiveFormula, Set<SAPL>> mapFormulaToDocuments(final Map<String, DisjunctiveFormula> targets,
			final Map<String, SAPL> documents) {
		Map<DisjunctiveFormula, Set<SAPL>> result = new HashMap<>(targets.size(), 1.0F);

		for (Map.Entry<String, DisjunctiveFormula> entry : targets.entrySet()) {
			DisjunctiveFormula formula = entry.getValue();
			Set<SAPL> set = result.computeIfAbsent(formula, k -> new HashSet<>());
			set.add(documents.get(entry.getKey()));
		}
		return result;
	}

}
