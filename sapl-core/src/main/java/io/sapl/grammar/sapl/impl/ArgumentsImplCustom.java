package io.sapl.grammar.sapl.impl;

import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import io.sapl.grammar.sapl.Expression;

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
