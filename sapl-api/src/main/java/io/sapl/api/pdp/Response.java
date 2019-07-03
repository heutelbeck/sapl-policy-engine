/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.api.pdp;

import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class Response {

	public static final Response PERMIT = new Response(Decision.PERMIT,
			Optional.empty(), Optional.empty(), Optional.empty());

	public static final Response DENY = new Response(Decision.DENY,
			Optional.empty(), Optional.empty(), Optional.empty());

	public static final Response INDETERMINATE = new Response(Decision.INDETERMINATE,
			Optional.empty(), Optional.empty(), Optional.empty());

	public static final Response NOT_APPLICABLE = new Response(Decision.NOT_APPLICABLE,
			Optional.empty(), Optional.empty(), Optional.empty());


	Decision decision;

	// Optional fields initialized as Optional.empty to allow comparing with JSON
	// marshalling/unmarshalling
	// Without initialization, fields would be null after JSON
	// marshalling/unmarshalling
	@JsonInclude(Include.NON_ABSENT)
	Optional<JsonNode> resource = Optional.empty();

	@JsonInclude(Include.NON_ABSENT)
	Optional<ArrayNode> obligations = Optional.empty();

	@JsonInclude(Include.NON_ABSENT)
	Optional<ArrayNode> advices = Optional.empty();

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o == null || o.getClass() != this.getClass()) {
			return false;
		}
		final Response other = (Response) o;
		if (!Objects.equals(getDecision(), other.getDecision())) {
			return false;
		}
		if (!areEqual(getResource(), other.getResource())) {
			return false;
		}
		if (!areEqual(getObligations(), other.getObligations())) {
			return false;
		}
		return areEqual(getAdvices(), other.getAdvices());
	}

	private static boolean areEqual(Optional<?> thisOptional, Optional<?> otherOptional) {
		if (!thisOptional.isPresent()) {
			return !otherOptional.isPresent();
		}
		if (!otherOptional.isPresent()) {
			return false;
		}
		return thisOptional.get().equals(otherOptional.get());
	}

	@Override
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Object thisDecision = getDecision();
		result = result * PRIME + (thisDecision == null ? 43 : thisDecision.hashCode());
		final Optional<JsonNode> thisResource = getResource();
		result = result * PRIME + thisResource.map(Object::hashCode).orElseGet(() -> 43);
		final Optional<ArrayNode> thisObligation = getObligations();
		result = result * PRIME
				+ thisObligation.map(Object::hashCode).orElseGet(() -> 43);
		final Optional<ArrayNode> thisAdvice = getAdvices();
		result = result * PRIME + thisAdvice.map(Object::hashCode).orElseGet(() -> 43);
		return result;
	}

}
