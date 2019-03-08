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

public class FilterStatementImplCustom extends FilterStatementImpl {

	@Override
	public int hash(Map<String, String> imports) {
		int hash = 17;
		hash = 37 * hash + ((getArguments() == null) ? 0 : getArguments().hash(imports));
		hash = 37 * hash + ((getTarget() == null) ? 0 : getTarget().hash(imports));
		hash = 37 * hash + Objects.hashCode(getClass().getTypeName());
		for (String fStep : getFsteps()) {
			hash = 37 * hash + Objects.hashCode(fStep);
		}
		hash = 37 * hash + Objects.hashCode(isEach());
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
