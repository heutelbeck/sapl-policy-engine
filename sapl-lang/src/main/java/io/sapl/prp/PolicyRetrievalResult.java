/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.eclipse.emf.ecore.util.EcoreUtil;

import io.sapl.grammar.sapl.AuthorizationDecisionEvaluable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class PolicyRetrievalResult {

	Collection<? extends AuthorizationDecisionEvaluable> matchingDocuments = new ArrayList<>();
	@Getter
	boolean errorsInTarget = false;
	@Getter
	boolean prpValidState = true;
	
	public Collection<? extends AuthorizationDecisionEvaluable> getMatchingDocuments() {
		return this.matchingDocuments;
	}

	public PolicyRetrievalResult withMatch(AuthorizationDecisionEvaluable match) {
		var matches = new ArrayList<AuthorizationDecisionEvaluable>(matchingDocuments);
		matches.add(match);
		return new PolicyRetrievalResult(matches, errorsInTarget, prpValidState);
	}

	public PolicyRetrievalResult withError() {
		return new PolicyRetrievalResult(new ArrayList<AuthorizationDecisionEvaluable>(matchingDocuments), true, prpValidState);
	}

	public PolicyRetrievalResult withInvalidState() {
		return new PolicyRetrievalResult(new ArrayList<AuthorizationDecisionEvaluable>(matchingDocuments), errorsInTarget, false);
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

	private static boolean areEqual(Collection<? extends AuthorizationDecisionEvaluable> thisMatchingDocuments,
			Collection<? extends AuthorizationDecisionEvaluable> otherMatchingDocuments) {
		if (thisMatchingDocuments == null) {
			return otherMatchingDocuments == null;
		}
		if (otherMatchingDocuments == null) {
			return false;
		}
		if (thisMatchingDocuments.size() != otherMatchingDocuments.size()) {
			return false;
		}
		final Iterator<? extends AuthorizationDecisionEvaluable> thisIterator = thisMatchingDocuments.iterator();
		final Iterator<? extends AuthorizationDecisionEvaluable> otherIterator = otherMatchingDocuments.iterator();
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
		final Collection<? extends AuthorizationDecisionEvaluable> thisMatchingDocuments = getMatchingDocuments();
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
