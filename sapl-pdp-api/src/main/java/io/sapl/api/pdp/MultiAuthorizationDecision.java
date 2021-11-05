/*
 * Copyright © 2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.pdp;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A multi-decision holds a map of authorization subscription IDs and corresponding
 * {@link AuthorizationDecision authorization decisions}. It provides methods to
 * {@link #setAuthorizationDecisionForSubscriptionWithId(String, AuthorizationDecision)}
 * add single authorization decisions related to an authorization subscription ID, to
 * {@link #getAuthorizationDecisionForSubscriptionWithId(String) get} a single
 * authorization decision for a given authorization subscription ID and to
 * {@link #iterator() iterate} over all the authorization decisions.
 *
 * @see AuthorizationDecision
 */
@Data
@NoArgsConstructor
public class MultiAuthorizationDecision implements Iterable<IdentifiableAuthorizationDecision> {

	@JsonInclude(NON_EMPTY)
	Map<String, AuthorizationDecision> authorizationDecisions = new HashMap<>();

	public static MultiAuthorizationDecision indeterminate() {
		final MultiAuthorizationDecision multiAuthzDecision = new MultiAuthorizationDecision();
		multiAuthzDecision.setAuthorizationDecisionForSubscriptionWithId("", AuthorizationDecision.INDETERMINATE);
		return multiAuthzDecision;
	}

	/**
	 * @return the number of {@link AuthorizationDecision authorization decisions}
	 * contained by this multi-decision.
	 */
	public int size() {
		return authorizationDecisions.size();
	}

	/**
	 * Adds the given tuple of subscription ID and related authorization decision to this
	 * multi-decision.
	 * @param subscriptionId the ID of the authorization subscription related to the given
	 * authorization decision.
	 * @param authzDecision the authorization decision related to the authorization
	 * subscription with the given ID.
	 */
	public void setAuthorizationDecisionForSubscriptionWithId(@NonNull String subscriptionId,
			@NonNull AuthorizationDecision authzDecision) {
		authorizationDecisions.put(subscriptionId, authzDecision);
	}

	/**
	 * Retrieves the authorization decision related to the subscription with the given ID.
	 * @param subscriptionId the ID of the subscription for which the related
	 * authorization decision has to be returned.
	 * @return the authorization decision related to the subscription with the given ID.
	 */
	public AuthorizationDecision getAuthorizationDecisionForSubscriptionWithId(@NonNull String subscriptionId) {
		return authorizationDecisions.get(subscriptionId);
	}

	/**
	 * Retrieves the decision related to the authorization subscription with the given ID.
	 * @param subscriptionId the ID of the authorization subscription for which the
	 * related decision has to be returned.
	 * @return the decision related to the authorization subscription with the given ID.
	 * Returns null if not present.
	 */
	public Decision getDecisionForSubscriptionWithId(@NonNull String subscriptionId) {
		var decision = authorizationDecisions.get(subscriptionId);
		return decision == null ? null : authorizationDecisions.get(subscriptionId).getDecision();
	}

	/**
	 * Returns {@code true} if the decision related to the authorization subscription with
	 * the given ID is {@link Decision#PERMIT}, {@code false} otherwise.
	 * @param subscriptionId the ID of the authorization subscription for which the
	 * related flag indicating whether the decision was PERMIT or not has to be returned.
	 * @return {@code true} if the decision related to the authorization subscription with
	 * the given ID is {@link Decision#PERMIT}, {@code false} otherwise.
	 */
	public boolean isAccessPermittedForSubscriptionWithId(@NonNull String subscriptionId) {
		var decision = authorizationDecisions.get(subscriptionId);
		return decision != null && decision.getDecision() == Decision.PERMIT;
	}

	/**
	 * @return an {@link Iterator iterator} providing access to the
	 * {@link IdentifiableAuthorizationDecision identifiable authorization decisions}
	 * created from the data held by this multi-decision.
	 */
	@Override
	public Iterator<IdentifiableAuthorizationDecision> iterator() {
		final Iterator<Map.Entry<String, AuthorizationDecision>> decisionIterator = authorizationDecisions.entrySet()
				.iterator();
		return new Iterator<IdentifiableAuthorizationDecision>() {
			@Override
			public boolean hasNext() {
				return decisionIterator.hasNext();
			}

			@Override
			public IdentifiableAuthorizationDecision next() {
				final Map.Entry<String, AuthorizationDecision> entry = decisionIterator.next();
				return new IdentifiableAuthorizationDecision(entry.getKey(), entry.getValue());
			}
		};
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("MultiAuthorizationDecision {");
		for (IdentifiableAuthorizationDecision iad : this) {
			sb.append("\n\t[").append("SUBSCRIPTION-ID: ").append(iad.getAuthorizationSubscriptionId()).append(" | ")
					.append("DECISION: ").append(iad.getAuthorizationDecision().getDecision()).append(" | ")
					.append("RESOURCE: ").append(iad.getAuthorizationDecision().getResource()).append(" | ")
					.append("OBLIGATIONS: ").append(iad.getAuthorizationDecision().getObligations()).append(" | ")
					.append("ADVICE: ").append(iad.getAuthorizationDecision().getAdvice()).append(']');
		}
		sb.append("\n}");
		return sb.toString();
	}

}
