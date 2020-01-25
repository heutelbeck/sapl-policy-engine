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
package io.sapl.api.pdp.multisubscription;

import static java.util.Objects.requireNonNull;

import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.Getter;
import lombok.ToString;

/**
 * Holds an {@link AuthorizationSubscription SAPL authorization subscription} together
 * with an ID used to identify the authorization subscription and to assign the
 * authorization subscription its corresponding {@link AuthorizationDecision SAPL
 * authorization decision}.
 *
 * @see AuthorizationSubscription
 * @see IdentifiableAuthorizationDecision
 */
@Getter
@ToString
public class IdentifiableAuthorizationSubscription {

	private String authorizationSubscriptionId;

	private AuthorizationSubscription authorizationSubscription;

	/**
	 * Creates a new {@code IdentifiableAuthorizationSubscription} instance holding the
	 * given authorization subscription ID and authorization subscription.
	 * @param authzSubscriptionId the ID assigned to the given authorization subscription.
	 * Must not be {@code null}.
	 * @param authzSubscription the authorization subscription assigned to the given ID.
	 * Must not be {@code null}.
	 */
	public IdentifiableAuthorizationSubscription(String authzSubscriptionId,
			AuthorizationSubscription authzSubscription) {
		requireNonNull(authzSubscriptionId, "authzSubscriptionId must not be null");
		requireNonNull(authzSubscription, "authzSubscription must not be null");

		this.authorizationSubscriptionId = authzSubscriptionId;
		this.authorizationSubscription = authzSubscription;
	}

}
