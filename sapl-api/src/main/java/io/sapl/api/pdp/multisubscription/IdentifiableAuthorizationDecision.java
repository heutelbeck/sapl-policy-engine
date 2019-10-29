package io.sapl.api.pdp.multisubscription;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Holds a {@link AuthorizationDecision SAPL authorization decision} together with the ID
 * of the corresponding {@link AuthorizationSubscription SAPL authorization subscription}.
 *
 * @see AuthorizationDecision
 * @see IdentifiableAuthorizationSubscription
 */
@Value
@AllArgsConstructor
@JsonInclude(NON_NULL)
public class IdentifiableAuthorizationDecision {

	private String authorizationSubscriptionId;

	private AuthorizationDecision authorizationDecision;

	public IdentifiableAuthorizationDecision() {
		authorizationSubscriptionId = null;
		authorizationDecision = null;
	}

	public static IdentifiableAuthorizationDecision INDETERMINATE = new IdentifiableAuthorizationDecision(null,
			AuthorizationDecision.INDETERMINATE);

}
