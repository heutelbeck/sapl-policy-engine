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
	 * A simple PERMIT decision.
	 */
	public static final AuthorizationDecision PERMIT = new AuthorizationDecision(Decision.PERMIT);

	/**
	 * A simple DENY decision.
	 */
	public static final AuthorizationDecision DENY = new AuthorizationDecision(Decision.DENY);

	/**
	 * A simple INDETERMINATE decision.
	 */
	public static final AuthorizationDecision INDETERMINATE = new AuthorizationDecision(Decision.INDETERMINATE);

	/**
	 * A simple NOT_APPLICABLE decision.
	 */
	public static final AuthorizationDecision NOT_APPLICABLE = new AuthorizationDecision(Decision.NOT_APPLICABLE);

	@NotNull
	Decision decision = Decision.INDETERMINATE;

	@JsonInclude(Include.NON_ABSENT)
	Optional<JsonNode> resource = Optional.empty();

	@JsonInclude(Include.NON_ABSENT)
	Optional<ArrayNode> obligations = Optional.empty();

	@JsonInclude(Include.NON_ABSENT)
	Optional<ArrayNode> advice = Optional.empty();

	/**
	 * @param decision Creates an immutable authorization decision with 'decision'
	 *                 as value, and without any resource, advice, or obligations.
	 *                 Must not be null.
	 */
	public AuthorizationDecision(@NonNull Decision decision) {
		this.decision = decision;
	}

	/**
	 * @param newObligations a JSON array containing obligations.
	 * @return new immutable decision object, replacing the obligations of the
	 *         original object with newObligations. If the array is empty, no
	 *         obligations will be present, not even an empty array.
	 */
	public AuthorizationDecision withObligations(@NonNull ArrayNode newObligations) {
		return new AuthorizationDecision(decision, resource,
				newObligations.isEmpty() ? Optional.empty() : Optional.of(newObligations), advice);
	}

	/**
	 * @param newAdvice a JSON array containing advice.
	 * @return new immutable decision object, replacing the advice of the original
	 *         object with newAdvice. If the array is empty, no advice will be
	 *         present, not even an empty array.
	 */
	public AuthorizationDecision withAdvice(@NonNull ArrayNode newAdvice) {
		return new AuthorizationDecision(decision, resource, obligations,
				newAdvice.isEmpty() ? Optional.empty() : Optional.of(newAdvice));
	}

	/**
	 * @param newResource a JSON object, must nor be null.
	 * @return new immutable decision object, replacing the resource with
	 *         newResource.
	 */
	public AuthorizationDecision withResource(@NonNull JsonNode newResource) {
		return new AuthorizationDecision(decision, Optional.of(newResource), obligations, advice);
	}

	/**
	 * @param newDecision a Decision value.
	 * @return new immutable decision object, replacing the resource with
	 *         newResource.
	 */
	public AuthorizationDecision withDecision(@NonNull Decision newDecision) {
		return new AuthorizationDecision(newDecision, resource, obligations, advice);
	}

}
