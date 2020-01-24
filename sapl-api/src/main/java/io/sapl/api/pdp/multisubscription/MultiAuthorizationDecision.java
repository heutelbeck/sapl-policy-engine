/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
package io.sapl.api.pdp.multisubscription;

import static java.util.Objects.requireNonNull;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import lombok.Value;

/**
 * A multi-decision holds a map of authorization subscription IDs and corresponding
 * {@link AuthorizationDecision authorization decisions}. It provides methods to
 * {@link #setAuthorizationDecisionForSubscriptionWithId(String, AuthorizationDecision)}
 * add} single authorization decisions related to an authorization subscription ID, to
 * {@link #getAuthorizationDecisionForSubscriptionWithId(String) get} a single
 * authorization decision for a given authorization subscription ID and to
 * {@link #iterator() iterate} over all the authorization decisions.
 *
 * @see AuthorizationDecision
 */
@Value
public class MultiAuthorizationDecision implements Iterable<IdentifiableAuthorizationDecision> {

	@JsonInclude(NON_EMPTY)
	private Map<String, AuthorizationDecision> authorizationDecisions = new HashMap<>();

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
	public void setAuthorizationDecisionForSubscriptionWithId(String subscriptionId,
			AuthorizationDecision authzDecision) {
		requireNonNull(subscriptionId, "subscriptionId must not be null");
		requireNonNull(authzDecision, "authzDecision must not be null");
		authorizationDecisions.put(subscriptionId, authzDecision);
	}

	/**
	 * Retrieves the authorization decision related to the subscription with the given ID.
	 * @param subscriptionId the ID of the subscription for which the related
	 * authorization decision has to be returned.
	 * @return the authorization decision related to the subscription with the given ID.
	 */
	public AuthorizationDecision getAuthorizationDecisionForSubscriptionWithId(String subscriptionId) {
		requireNonNull(subscriptionId, "subscriptionId must not be null");
		return authorizationDecisions.get(subscriptionId);
	}

	/**
	 * Retrieves the decision related to the authorization subscription with the given ID.
	 * @param subscriptionId the ID of the authorization subscription for which the
	 * related decision has to be returned.
	 * @return the decision related to the authorization subscription with the given ID.
	 */
	public Decision getDecisionForSubscriptionWithId(String subscriptionId) {
		requireNonNull(subscriptionId, "subscriptionId must not be null");
		return authorizationDecisions.get(subscriptionId).getDecision();
	}

	/**
	 * Returns {@code true} if the decision related to the authorization subscription with
	 * the given ID is {@link Decision#PERMIT}, {@code false} otherwise.
	 * @param subscriptionId the ID of the authorization subscription for which the
	 * related flag indicating whether the decision was PERMIT or not has to be returned.
	 * @return {@code true} if the decision related to the authorization subscription with
	 * the given ID is {@link Decision#PERMIT}, {@code false} otherwise.
	 */
	public boolean isAccessPermittedForSubscriptionWithId(String subscriptionId) {
		requireNonNull(subscriptionId, "subscriptionId must not be null");
		return authorizationDecisions.get(subscriptionId).getDecision() == Decision.PERMIT;
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
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		final MultiAuthorizationDecision other = (MultiAuthorizationDecision) obj;

		final Map<String, AuthorizationDecision> otherDecisions = other.authorizationDecisions;
		if (authorizationDecisions.size() != otherDecisions.size()) {
			return false;
		}

		final Set<String> thisKeys = authorizationDecisions.keySet();
		final Set<String> otherKeys = otherDecisions.keySet();
		for (String key : thisKeys) {
			if (!otherKeys.contains(key)) {
				return false;
			}
			if (!Objects.equals(authorizationDecisions.get(key), otherDecisions.get(key))) {
				return false;
			}
		}

		return true;
	}

	@Override
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		for (AuthorizationDecision authzDecision : authorizationDecisions.values()) {
			result = result * PRIME + (authzDecision == null ? 43 : authzDecision.hashCode());
		}
		return result;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("MultiAuthorizationDecision {");
		for (IdentifiableAuthorizationDecision iad : this) {
			sb.append("\n\t[").append("SUBSCRIPTION-ID: ").append(iad.getAuthorizationSubscriptionId()).append(" | ")
					.append("DECISION: ").append(iad.getAuthorizationDecision().getDecision()).append(" | ")
					.append("RESOURCE: ").append(iad.getAuthorizationDecision().getResource()).append(" | ")
					.append("OBLIGATIONS: ").append(iad.getAuthorizationDecision().getObligations()).append(" | ")
					.append("ADVICE: ").append(iad.getAuthorizationDecision().getAdvices()).append(']');
		}
		sb.append("\n}");
		return sb.toString();
	}

}
