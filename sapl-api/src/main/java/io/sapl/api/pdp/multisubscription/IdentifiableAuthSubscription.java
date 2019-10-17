package io.sapl.api.pdp.multisubscription;

import static java.util.Objects.requireNonNull;

import io.sapl.api.pdp.AuthSubscription;
import io.sapl.api.pdp.AuthDecision;
import lombok.Getter;
import lombok.ToString;

/**
 * Holds an {@link AuthSubscription SAPL authorization subscription} together with an ID
 * used to identify the authorization subscription and to assign the authorization
 * subscription its corresponding {@link AuthDecision SAPL authorization decision}.
 *
 * @see AuthSubscription
 * @see IdentifiableAuthDecision
 */
@Getter
@ToString
public class IdentifiableAuthSubscription {

	private String authSubscriptionId;

	private AuthSubscription authSubscription;

	/**
	 * Creates a new {@code IdentifiableAuthSubscription} instance holding the given
	 * authorization subscription ID and authorization subscription.
	 * @param authSubscriptionId the ID assigned to the given authorization subscription.
	 * Must not be {@code null}.
	 * @param authSubscription the authorization subscription assigned to the given ID.
	 * Must not be {@code null}.
	 */
	public IdentifiableAuthSubscription(String authSubscriptionId, AuthSubscription authSubscription) {
		requireNonNull(authSubscriptionId, "authSubscriptionId must not be null");
		requireNonNull(authSubscription, "authSubscription must not be null");

		this.authSubscriptionId = authSubscriptionId;
		this.authSubscription = authSubscription;
	}

}
