/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
public class ConjunctiveClauseReductionSupport {

	static void reduceConstants(final List<Literal> data) {
		ListIterator<Literal> iter = data.listIterator();
		while (iter.hasNext() && data.size() > 1) {
			Literal literal = iter.next();
			if (literal.isImmutable()) {
				if (!literal.evaluate()) {
					data.clear();
					data.add(literal);
					return;
				} else {
					iter.remove();
				}
			}
		}
	}

	static void reduceFormula(final List<Literal> data) {
		ListIterator<Literal> pointer = data.listIterator();
		while (pointer.hasNext()) {
			Literal lhs = pointer.next();
			if (lhs != null && reduceFormulaStep(data, pointer, lhs)) {
				break;
			}
		}
		data.removeIf(Objects::isNull);
	}

	private static boolean reduceFormulaStep(final List<Literal> data, final ListIterator<Literal> pointer,
			final Literal value) {
		ListIterator<Literal> forward = data.listIterator(pointer.nextIndex());
		while (forward.hasNext()) {
			Literal rhs = forward.next();
			if (value.sharesBool(rhs)) {
				if (value.sharesNegation(rhs)) {
					forward.set(null);
				} else {
					data.clear();
					data.add(new Literal(new Bool(false)));
					return true;
				}
			}
		}
		return false;
	}

}
