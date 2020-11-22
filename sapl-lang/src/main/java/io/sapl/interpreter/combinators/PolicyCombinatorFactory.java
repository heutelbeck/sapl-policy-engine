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
package io.sapl.interpreter.combinators;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PolicyCombinatorFactory {

	public static PolicyCombinator getCombinator(String algorithm) {
		switch (algorithm) {
		case "deny-unless-permit":
			return new DenyUnlessPermitCombinator();
		case "permit-unless-deny":
			return new PermitUnlessDenyCombinator();
		case "deny-overrides":
			return new DenyOverridesCombinator();
		case "permit-overrides":
			return new PermitOverridesCombinator();
		case "only-one-applicable":
			return new OnlyOneApplicableCombinator();
		default: // "first-applicable":
			return new FirstApplicableCombinator();
		}
	}
}
