/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES;
import static io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE;
import static io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES;
import static io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY;

import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.grammar.sapl.impl.DenyOverridesCombiningAlgorithmImplCustom;
import io.sapl.grammar.sapl.impl.DenyUnlessPermitCombiningAlgorithmImplCustom;
import io.sapl.grammar.sapl.impl.OnlyOneApplicableCombiningAlgorithmImplCustom;
import io.sapl.grammar.sapl.impl.PermitOverridesCombiningAlgorithmImplCustom;
import io.sapl.grammar.sapl.impl.PermitUnlessDenyCombiningAlgorithmImplCustom;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CombiningAlgorithmFactory {

	public static CombiningAlgorithm getCombiningAlgorithm(PolicyDocumentCombiningAlgorithm algorithm) {
		if (algorithm == PERMIT_UNLESS_DENY)
			return new PermitUnlessDenyCombiningAlgorithmImplCustom();
		if (algorithm == PERMIT_OVERRIDES)
			return new PermitOverridesCombiningAlgorithmImplCustom();
		if (algorithm == DENY_OVERRIDES)
			return new DenyOverridesCombiningAlgorithmImplCustom();
		if (algorithm == ONLY_ONE_APPLICABLE)
			return new OnlyOneApplicableCombiningAlgorithmImplCustom();

		return new DenyUnlessPermitCombiningAlgorithmImplCustom();
	}

}
