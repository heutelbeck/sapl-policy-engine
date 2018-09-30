package io.sapl.api.prp;

import java.util.Collection;
import java.util.Iterator;

import io.sapl.grammar.sapl.SAPL;
import lombok.AllArgsConstructor;
import org.eclipse.emf.ecore.util.EcoreUtil;

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
		if (!(other instanceof PolicyRetrievalResult)) {
			return false;
		}
		final PolicyRetrievalResult otherResult = (PolicyRetrievalResult) other;
		if (! areEqual(this.getMatchingDocuments(), otherResult.getMatchingDocuments())) {
			return false;
		}
		return this.isErrorsInTarget() == otherResult.isErrorsInTarget();
	}

	private boolean areEqual(Collection<SAPL> thisMatchingDocuments, Collection<SAPL> otherMatchingDocuments) {
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
			if (! EcoreUtil.equals(thisIterator.next(), otherIterator.next())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Collection<SAPL> $matchingDocuments = getMatchingDocuments();
		result = result * PRIME + ($matchingDocuments == null ? 43 : $matchingDocuments.hashCode());
		result = result * PRIME + (isErrorsInTarget() ? 79 : 97);
		return result;
	}

	@Override
	public String toString() {
		return "PolicyRetrievalResult(" +
				"matchingDocuments=" + getMatchingDocuments() +
				", errorsInTarget=" + isErrorsInTarget() +
				")";
	}
}
