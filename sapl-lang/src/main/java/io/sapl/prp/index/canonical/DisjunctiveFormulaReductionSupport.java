/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DisjunctiveFormulaReductionSupport {

	static void reduceConstants(final List<ConjunctiveClause> data) {
		ListIterator<ConjunctiveClause> iter = data.listIterator();
		while (iter.hasNext() && data.size() > 1) {
			ConjunctiveClause clause = iter.next();
			if (clause.isImmutable()) {
				if (clause.evaluate()) {
					data.clear();
					data.add(clause);
					return;
				}
				else {
					iter.remove();
				}
			}
		}
	}

	static void reduceFormula(final List<ConjunctiveClause> data) {
		ListIterator<ConjunctiveClause> pointer = data.listIterator();
		while (pointer.hasNext()) {
			ConjunctiveClause lhs = pointer.next();
			if (lhs != null) {
				reduceFormulaStep(data, pointer, lhs);
			}
		}
		data.removeIf(Objects::isNull);
	}

	static void reduceFormulaStep(final List<ConjunctiveClause> data, final ListIterator<ConjunctiveClause> pointer,
			final ConjunctiveClause value) {
		ListIterator<ConjunctiveClause> forward = data.listIterator(pointer.nextIndex());
		while (forward.hasNext()) {
			ConjunctiveClause rhs = forward.next();
			if (rhs == null) {
				continue;
			}
			if (value.isSubsetOf(rhs)) {
				forward.set(null);
			}
			else if (rhs.isSubsetOf(value)) {
				pointer.set(null);
				return;
			}
		}
	}

}
