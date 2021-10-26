/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.coverage.api.model;

import io.sapl.test.coverage.api.CoverageHitConfig;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
/**
 * Containing all necessary information of a Policy hit
 */
public class PolicyHit {

	/**
	 * Id of {@link io.sapl.grammar.sapl.PolicySet} of hit policy. Empty if
	 * {@link io.sapl.grammar.sapl.Policy} isn't in a
	 * {@link io.sapl.grammar.sapl.PolicySet}.
	 */
	private String policySetId;

	/**
	 * Id of hit {@link io.sapl.grammar.sapl.Policy}
	 */
	private String policyId;

	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(policySetId);
		stringBuilder.append(CoverageHitConfig.DELIMITER);
		stringBuilder.append(policyId);
		return stringBuilder.toString();
	}

	public static PolicyHit fromString(String policyToStringResult) {
		String[] splitted = policyToStringResult.split(CoverageHitConfig.DELIMITER_MATCH_REGEX);
		return new PolicyHit(splitted[0], splitted[1]);
	}
}