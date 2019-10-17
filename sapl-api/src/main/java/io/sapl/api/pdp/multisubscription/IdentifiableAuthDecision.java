package io.sapl.api.pdp.multisubscription;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.sapl.api.pdp.AuthDecision;
import io.sapl.api.pdp.AuthSubscription;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Holds a {@link AuthDecision SAPL authorization decision} together with the ID of the
 * corresponding {@link AuthSubscription SAPL authorization subscription}.
 *
 * @see AuthDecision
 * @see IdentifiableAuthSubscription
 */
@Value
@AllArgsConstructor
@JsonInclude(NON_NULL)
public class IdentifiableAuthDecision {

	private String authSubscriptionId;

	private AuthDecision authDecision;

	public IdentifiableAuthDecision() {
		authSubscriptionId = null;
		authDecision = null;
	}

	public static IdentifiableAuthDecision INDETERMINATE = new IdentifiableAuthDecision(null,
			AuthDecision.INDETERMINATE);

}
