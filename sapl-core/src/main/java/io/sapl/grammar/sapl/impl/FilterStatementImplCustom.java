package io.sapl.grammar.sapl.impl;

import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;


public class FilterStatementImplCustom extends io.sapl.grammar.sapl.impl.FilterStatementImpl {

	private static final int HASH_PRIME_06 = 37;
	private static final int INIT_PRIME_03 = 7;

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_03;
		hash = HASH_PRIME_06 * hash + ((getArguments() == null) ? 0 : getArguments().hash(imports));
		hash = HASH_PRIME_06 * hash + ((getTarget() == null) ? 0 : getTarget().hash(imports));
		hash = HASH_PRIME_06 * hash + Objects.hashCode(getClass().getTypeName());
		for (String fStep : getFsteps()) {
			hash = HASH_PRIME_06 * hash + Objects.hashCode(fStep);
		}
		hash = HASH_PRIME_06 * hash + Objects.hashCode(isEach());
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
		final FilterStatementImplCustom otherImpl = (FilterStatementImplCustom) other;
		if ((getArguments() == null) ? (getArguments() != otherImpl.getArguments())
				: !getArguments().isEqualTo(otherImpl.getArguments(), otherImports, imports)) {
			return false;
		}
		if ((getTarget() == null) ? (getTarget() != otherImpl.getTarget())
				: !getTarget().isEqualTo(otherImpl.getTarget(), otherImports, imports)) {
			return false;
		}
		if (!Objects.equals(isEach(), otherImpl.isEach())) {
			return false;
		}
		if (getFsteps().size() != otherImpl.getFsteps().size()) {
			return false;
		}
		ListIterator<String> left = getFsteps().listIterator();
		ListIterator<String> right = otherImpl.getFsteps().listIterator();
		while (left.hasNext()) {
			String lhs = left.next();
			String rhs = right.next();
			if (!Objects.equals(lhs, rhs)) {
				return false;
			}
		}
		return true;
	}

}
