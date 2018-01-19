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

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Response {
	Decision decision;

	@JsonInclude(Include.NON_ABSENT)
	Optional<JsonNode> resource;

	@JsonInclude(Include.NON_ABSENT)
	Optional<ArrayNode> obligation;

	@JsonInclude(Include.NON_ABSENT)
	Optional<ArrayNode> advice;

	public static Response deny() {
		return new Response(Decision.DENY, Optional.empty(), Optional.empty(), Optional.empty());
	}

	public static Response indeterminate() {
		return new Response(Decision.INDETERMINATE, Optional.empty(), Optional.empty(), Optional.empty());
	}

	public static Response notApplicable() {
		return new Response(Decision.NOT_APPLICABLE, Optional.empty(), Optional.empty(), Optional.empty());
	}
}
