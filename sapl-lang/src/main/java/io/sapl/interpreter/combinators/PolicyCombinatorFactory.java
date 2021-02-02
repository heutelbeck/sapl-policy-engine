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

import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.grammar.sapl.DenyOverridesCombiningAlgorithm;
import io.sapl.grammar.sapl.DenyUnlessPermitCombiningAlgorithm;
import io.sapl.grammar.sapl.OnlyOneApplicableCombiningAlgorithm;
import io.sapl.grammar.sapl.PermitOverridesCombiningAlgorithm;
import io.sapl.grammar.sapl.PermitUnlessDenyCombiningAlgorithm;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PolicyCombinatorFactory {

	public static PolicyCombinator getCombinator(CombiningAlgorithm algorithm) {
		if (algorithm instanceof DenyUnlessPermitCombiningAlgorithm) {
			return new DenyUnlessPermitCombinator();
		} else if (algorithm instanceof PermitUnlessDenyCombiningAlgorithm) {
			return new PermitUnlessDenyCombinator();
		} else if (algorithm instanceof DenyOverridesCombiningAlgorithm) {
			return new DenyOverridesCombinator();
		} else if (algorithm instanceof PermitOverridesCombiningAlgorithm) {
			return new PermitOverridesCombinator();
		} else if (algorithm instanceof OnlyOneApplicableCombiningAlgorithm) {
			return new OnlyOneApplicableCombinator();
		} else { // "first-applicable":
			return new FirstApplicableCombinator();
		}
	}
}
