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
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class Response {

	Decision decision;

	// Optional fields initialized as Optional.empty to allow comparing with JSON marshalling/unmarshalling
	// Without initialization, fields would be null after JSON marshalling/unmarshalling
	@JsonInclude(Include.NON_ABSENT)
	Optional<JsonNode> resource = Optional.empty();

	@JsonInclude(Include.NON_ABSENT)
	Optional<ArrayNode> obligation = Optional.empty();

	@JsonInclude(Include.NON_ABSENT)
	Optional<ArrayNode> advice = Optional.empty();

	public static Response deny() {
		return new Response(Decision.DENY, Optional.empty(), Optional.empty(), Optional.empty());
	}

	public static Response indeterminate() {
		return new Response(Decision.INDETERMINATE, Optional.empty(), Optional.empty(), Optional.empty());
	}

	public static Response notApplicable() {
		return new Response(Decision.NOT_APPLICABLE, Optional.empty(), Optional.empty(), Optional.empty());
	}


	public Decision getDecision() {
		return this.decision;
	}

	public void setDecision(Decision decision) {
		this.decision = decision;
	}

	public Optional<JsonNode> getResource() {
		return this.resource;
	}

	public void setResource(Optional<JsonNode> resource) {
		this.resource = resource;
	}

	public Optional<ArrayNode> getObligation() {
		return this.obligation;
	}

	public void setObligation(Optional<ArrayNode> obligation) {
		this.obligation = obligation;
	}

	public Optional<ArrayNode> getAdvice() {
		return this.advice;
	}

	public void setAdvice(Optional<ArrayNode> advice) {
		this.advice = advice;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o.getClass() != this.getClass()) {
			return false;
		}
		final Response other = (Response) o;
		if (! Objects.equals(this.getDecision(), other.getDecision())) {
			return false;
		}
		if (! areEqual(this.getResource(), other.getResource())) {
			return false;
		}
		if (! areEqual(this.getObligation(), other.getObligation())) {
			return false;
		}
		return areEqual(this.getAdvice(), other.getAdvice());
	}

	private static boolean areEqual(Optional<?> thisOptional, Optional<?> otherOptional) {
		if (! thisOptional.isPresent()) {
			return ! otherOptional.isPresent();
		}
		if (! otherOptional.isPresent()) {
			return false;
		}
		return thisOptional.get().equals(otherOptional.get());
	}

	@Override
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Object decision = this.getDecision();
		result = result * PRIME + (decision == null ? 43 : decision.hashCode());
		final Object resource = this.getResource();
		result = result * PRIME + (resource == null ? 43 : resource.hashCode());
		final Object obligation = this.getObligation();
		result = result * PRIME + (obligation == null ? 43 : obligation.hashCode());
		final Object advice = this.getAdvice();
		result = result * PRIME + (advice == null ? 43 : advice.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "Response(" +
				"decision=" + this.getDecision() +
				", resource=" + this.getResource() +
				", obligation=" + this.getObligation() +
				", advice=" + this.getAdvice() +
				")";
	}
}
