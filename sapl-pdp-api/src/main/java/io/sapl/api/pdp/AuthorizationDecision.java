/*
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
package io.sapl.api.pdp;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

/**
 * Container for a decision
 */
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationDecision {

	/**
	 * Premade PERMIT decision object
	 */
	public static final AuthorizationDecision PERMIT = new AuthorizationDecision(Decision.PERMIT);

	/**
	 * Premade DENY decision object
	 */
	public static final AuthorizationDecision DENY = new AuthorizationDecision(Decision.DENY);

	/**
	 * Premade INDETERMINATE decision object
	 */
	public static final AuthorizationDecision INDETERMINATE = new AuthorizationDecision(Decision.INDETERMINATE);

	/**
	 * Premade NOT_APPLICABLE decision object
	 */
	public static final AuthorizationDecision NOT_APPLICABLE = new AuthorizationDecision(Decision.NOT_APPLICABLE);

	@NotNull
	Decision decision = Decision.INDETERMINATE;

	@JsonInclude(Include.NON_ABSENT)
	Optional<JsonNode> resource = Optional.empty();

	@JsonInclude(Include.NON_ABSENT)
	Optional<ArrayNode> obligations = Optional.empty();

	@JsonInclude(Include.NON_ABSENT)
	Optional<ArrayNode> advices = Optional.empty();

	/**
	 * @param decision Creates an immuatable authorization decision with 'decision'
	 *                 as value, and without any resource, advices, or obligations.
	 *                 Must not be null.
	 */
	public AuthorizationDecision(@NonNull Decision decision) {
		this.decision = decision;
	}

	/**
	 * @param newObligations a JSON array containing obligations.
	 * @return new immuatable decision object, replacing the obligations of the
	 *         original object with newObligations. If the array is empty, no
	 *         obligations will be present, not even an empty array.
	 */
	public AuthorizationDecision withObligations(@NonNull ArrayNode newObligations) {
		return new AuthorizationDecision(decision, resource,
				newObligations.isEmpty() ? Optional.empty() : Optional.of(newObligations), advices);
	}

	/**
	 * @param newAdvices a JSON array containing advices.
	 * @return new immuatable decision object, replacing the advices of the original
	 *         object with newAdvices. If the array is empty, no advices will be
	 *         present, not even an empty array.
	 */
	public AuthorizationDecision withAdvices(@NonNull ArrayNode newAdvices) {
		return new AuthorizationDecision(decision, resource, obligations,
				newAdvices.isEmpty() ? Optional.empty() : Optional.of(newAdvices));
	}

	/**
	 * @param newResource a JSON object, must nor be null.
	 * @return new immuatable decision object, replacing the resource with
	 *         newResource.
	 */
	public AuthorizationDecision withResource(@NonNull JsonNode newResource) {
		return new AuthorizationDecision(decision, Optional.of(newResource), obligations, advices);
	}

	/**
	 * @param newResource a JSON object, must nor be null.
	 * @return new immuatable decision object, replacing the resource with
	 *         newResource.
	 */
	public AuthorizationDecision withDecision(@NonNull Decision newDecision) {
		return new AuthorizationDecision(newDecision, resource, obligations, advices);
	}

}
