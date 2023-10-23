/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Holds a {@link AuthorizationDecision SAPL authorization decision} together
 * with the ID of the corresponding {@link AuthorizationSubscription SAPL
 * authorization subscription}.
 *
 * @see AuthorizationDecision
 * @see IdentifiableAuthorizationSubscription
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(NON_NULL)
public class IdentifiableAuthorizationDecision {
	/**
	 * A simple INDETERMINATE decision.
	 */
	public static final IdentifiableAuthorizationDecision INDETERMINATE = new IdentifiableAuthorizationDecision(null,
			AuthorizationDecision.INDETERMINATE);

	@JsonProperty(required = true)
	String authorizationSubscriptionId;

	@JsonProperty(required = true)
	AuthorizationDecision authorizationDecision;

}
