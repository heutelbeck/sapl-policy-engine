/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.grammar.sapl.impl;

import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import io.sapl.grammar.sapl.Expression;

/**
 * This class contains a collection of arguments to be passed to function calls.
 *
 * Grammar:
 * 
 * Arguments:
 *	{Arguments} '(' (args+=Expression (',' args+=Expression)*)? ')';
 *
 */
public class ArgumentsImplCustom extends io.sapl.grammar.sapl.impl.ArgumentsImpl {

	private static final int HASH_PRIME_02 = 19;
	private static final int INIT_PRIME_03 = 7;

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_03;
		hash = HASH_PRIME_02 * hash + Objects.hashCode(getClass().getTypeName());
		for (Expression expression : getArgs()) {
			hash = HASH_PRIME_02 * hash + ((expression == null) ? 0 : expression.hash(imports));
		}
		return hash;
	}

	@Override
	public boolean isEqualTo(EObject other, Map<String, String> otherImports, Map<String, String> imports) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		final ArgumentsImplCustom otherImpl = (ArgumentsImplCustom) other;
		if (getArgs().size() != otherImpl.getArgs().size()) {
			return false;
		}
		ListIterator<Expression> left = getArgs().listIterator();
		ListIterator<Expression> right = otherImpl.getArgs().listIterator();
		while (left.hasNext()) {
			Expression lhs = left.next();
			Expression rhs = right.next();
			if (!lhs.isEqualTo(rhs, otherImports, imports)) {
				return false;
			}
		}
		return true;
	}

}
