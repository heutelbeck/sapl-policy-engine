package io.sapl.api.pdp.multisubscription;

import static java.util.Objects.requireNonNull;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.sapl.api.pdp.AuthDecision;
import io.sapl.api.pdp.Decision;
import lombok.Value;

/**
 * A multi-decision holds a map of authorization subscription IDs and corresponding
 * {@link AuthDecision authorization decisions}. It provides methods to
 * {@link #setAuthDecisionForSubscriptionWithId(String, AuthDecision)} add} single
 * authorization decisions related to an authorization subscription ID, to
 * {@link #getAuthDecisionForSubscriptionWithId(String) get} a single authorization
 * decision for a given authorization subscription ID and to {@link #iterator() iterate}
 * over all the authorization decisions.
 *
 * @see AuthDecision
 */
@Value
public class MultiAuthDecision implements Iterable<IdentifiableAuthDecision> {

	@JsonInclude(NON_EMPTY)
	private Map<String, AuthDecision> authDecisions = new HashMap<>();

	public static MultiAuthDecision indeterminate() {
		final MultiAuthDecision multiAuthDecision = new MultiAuthDecision();
		multiAuthDecision.setAuthDecisionForSubscriptionWithId("", AuthDecision.INDETERMINATE);
		return multiAuthDecision;
	}

	/**
	 * @return the number of {@link AuthDecision authorization decisions} contained by
	 * this multi-decision.
	 */
	public int size() {
		return authDecisions.size();
	}

	/**
	 * Adds the given tuple of subscription ID and related authorization decision to this
	 * multi-decision.
	 * @param subscriptionId the ID of the authorization subscription related to the given
	 * authorization decision.
	 * @param authDecision the authorization decision related to the authorization
	 * subscription with the given ID.
	 */
	public void setAuthDecisionForSubscriptionWithId(String subscriptionId, AuthDecision authDecision) {
		requireNonNull(subscriptionId, "subscriptionId must not be null");
		requireNonNull(authDecision, "authDecision must not be null");
		authDecisions.put(subscriptionId, authDecision);
	}

	/**
	 * Retrieves the authorization decision related to the subscription with the given ID.
	 * @param subscriptionId the ID of the subscription for which the related
	 * authorization decision has to be returned.
	 * @return the authorization decision related to the subscription with the given ID.
	 */
	public AuthDecision getAuthDecisionForSubscriptionWithId(String subscriptionId) {
		requireNonNull(subscriptionId, "subscriptionId must not be null");
		return authDecisions.get(subscriptionId);
	}

	/**
	 * Retrieves the decision related to the authorization subscription with the given ID.
	 * @param subscriptionId the ID of the authorization subscription for which the
	 * related decision has to be returned.
	 * @return the decision related to the authorization subscription with the given ID.
	 */
	public Decision getDecisionForSubscriptionWithId(String subscriptionId) {
		requireNonNull(subscriptionId, "subscriptionId must not be null");
		return authDecisions.get(subscriptionId).getDecision();
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
		return authDecisions.get(subscriptionId).getDecision() == Decision.PERMIT;
	}

	/**
	 * @return an {@link Iterator iterator} providing access to the
	 * {@link IdentifiableAuthDecision identifiable authorization decisions} created from
	 * the data held by this multi-decision.
	 */
	@Override
	public Iterator<IdentifiableAuthDecision> iterator() {
		final Iterator<Map.Entry<String, AuthDecision>> decisionIterator = authDecisions.entrySet()
				.iterator();
		return new Iterator<IdentifiableAuthDecision>() {
			@Override
			public boolean hasNext() {
				return decisionIterator.hasNext();
			}

			@Override
			public IdentifiableAuthDecision next() {
				final Map.Entry<String, AuthDecision> entry = decisionIterator.next();
				return new IdentifiableAuthDecision(entry.getKey(), entry.getValue());
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
		final MultiAuthDecision other = (MultiAuthDecision) obj;

		final Map<String, AuthDecision> otherDecisions = other.authDecisions;
		if (authDecisions.size() != otherDecisions.size()) {
			return false;
		}

		final Set<String> thisKeys = authDecisions.keySet();
		final Set<String> otherKeys = otherDecisions.keySet();
		for (String key : thisKeys) {
			if (!otherKeys.contains(key)) {
				return false;
			}
			if (!Objects.equals(authDecisions.get(key), otherDecisions.get(key))) {
				return false;
			}
		}

		return true;
	}

	@Override
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		for (AuthDecision authDecision : authDecisions.values()) {
			result = result * PRIME + (authDecision == null ? 43 : authDecision.hashCode());
		}
		return result;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("MultiAuthDecision {");
		for (IdentifiableAuthDecision iad : this) {
			sb.append("\n\t[").append("SUBSCRIPTION-ID: ").append(iad.getAuthSubscriptionId()).append(" | ").append("DECISION: ")
					.append(iad.getAuthDecision().getDecision()).append(" | ").append("RESOURCE: ")
					.append(iad.getAuthDecision().getResource()).append(" | ").append("OBLIGATIONS: ")
					.append(iad.getAuthDecision().getObligations()).append(" | ").append("ADVICE: ")
					.append(iad.getAuthDecision().getAdvices()).append(']');
		}
		sb.append("\n}");
		return sb.toString();
	}

}
