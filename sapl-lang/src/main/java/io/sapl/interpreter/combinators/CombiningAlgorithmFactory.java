/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

    private static final CombiningAlgorithm PERMIT_UNLESS_DENY_ALGORITHM  = new PermitUnlessDenyCombiningAlgorithmImplCustom();
    private static final CombiningAlgorithm PPERMIT_OVERRIDES_ALGORITHM   = new PermitOverridesCombiningAlgorithmImplCustom();
    private static final CombiningAlgorithm DENY_OVERRIDES_ALGORITHM      = new DenyOverridesCombiningAlgorithmImplCustom();
    private static final CombiningAlgorithm ONLY_ONE_APPLICABLE_ALGORITHM = new OnlyOneApplicableCombiningAlgorithmImplCustom();
    private static final CombiningAlgorithm DENY_UNLESS_PERMIT_ALGORITHM  = new DenyUnlessPermitCombiningAlgorithmImplCustom();

    public static CombiningAlgorithm getCombiningAlgorithm(PolicyDocumentCombiningAlgorithm algorithm) {
        if (algorithm == PERMIT_UNLESS_DENY)
            return PERMIT_UNLESS_DENY_ALGORITHM;
        if (algorithm == PERMIT_OVERRIDES)
            return PPERMIT_OVERRIDES_ALGORITHM;
        if (algorithm == DENY_OVERRIDES)
            return DENY_OVERRIDES_ALGORITHM;
        if (algorithm == ONLY_ONE_APPLICABLE)
            return ONLY_ONE_APPLICABLE_ALGORITHM;

        return DENY_UNLESS_PERMIT_ALGORITHM;
    }

}
