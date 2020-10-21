/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.prp;

import java.util.Collection;
import java.util.Iterator;

import org.eclipse.emf.ecore.util.EcoreUtil;

import io.sapl.grammar.sapl.SAPL;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class PolicyRetrievalResult {

	Collection<SAPL> matchingDocuments;

	boolean errorsInTarget;

	public Collection<SAPL> getMatchingDocuments() {
		return this.matchingDocuments;
	}

	public boolean isErrorsInTarget() {
		return this.errorsInTarget;
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}
		if (other == null || other.getClass() != this.getClass()) {
			return false;
		}
		final PolicyRetrievalResult otherResult = (PolicyRetrievalResult) other;
		if (!areEqual(this.getMatchingDocuments(), otherResult.getMatchingDocuments())) {
			return false;
		}
		return this.isErrorsInTarget() == otherResult.isErrorsInTarget();
	}

	private static boolean areEqual(Collection<SAPL> thisMatchingDocuments, Collection<SAPL> otherMatchingDocuments) {
		if (thisMatchingDocuments == null) {
			return otherMatchingDocuments == null;
		}
		if (otherMatchingDocuments == null) {
			return false;
		}
		if (thisMatchingDocuments.size() != otherMatchingDocuments.size()) {
			return false;
		}
		final Iterator<SAPL> thisIterator = thisMatchingDocuments.iterator();
		final Iterator<SAPL> otherIterator = otherMatchingDocuments.iterator();
		while (thisIterator.hasNext()) {
			if (!EcoreUtil.equals(thisIterator.next(), otherIterator.next())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Collection<SAPL> thisMatchingDocuments = getMatchingDocuments();
		result = result * PRIME + (thisMatchingDocuments == null ? 43 : thisMatchingDocuments.hashCode());
		result = result * PRIME + (isErrorsInTarget() ? 79 : 97);
		return result;
	}

	@Override
	public String toString() {
		return "PolicyRetrievalResult(" + "matchingDocuments=" + getMatchingDocuments() + ", errorsInTarget="
				+ isErrorsInTarget() + ")";
	}

}
