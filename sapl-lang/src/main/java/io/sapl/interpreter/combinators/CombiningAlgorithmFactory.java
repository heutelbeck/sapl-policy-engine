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

import static io.sapl.interpreter.combinators.algorithms.DenyOverrides.DENY_OVERRIDES;
import static io.sapl.interpreter.combinators.algorithms.FirstApplicable.FIRST_APPLICABLE;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.combinators.algorithms.DenyOverrides;
import io.sapl.interpreter.combinators.algorithms.FirstApplicable;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CombiningAlgorithmFactory {
    private static final String PERMIT_OVERRIDES    = "permit-overrides";
    private static final String ONLY_ONE_APPLICABLE = "only-one-applicable";
    private static final String DENY_UNLESS_PERMIT  = "deny-unless-permit";
    private static final String PERMIT_UNLESS_DENY  = "permit-unless-deny";

    public PolicySetCombiningAlgorithm policySetCombiningAlgorithm(String algorithmName) {
        return switch (algorithmName) {
        case DENY_OVERRIDES -> DenyOverrides::denyOverrides;
        case PERMIT_OVERRIDES -> null;
        case FIRST_APPLICABLE -> FirstApplicable::firstApplicable;
        case ONLY_ONE_APPLICABLE -> null;
        case DENY_UNLESS_PERMIT -> null;
        case PERMIT_UNLESS_DENY -> null;
        default -> throw new PolicyEvaluationException(
                String.format("Illegal PolicySetCombiningAlgorithm '%s'.", algorithmName));
        };
    }

    public DocumentsCombiningAlgorithm documentsCombiningAlgorithm(String algorithmName) {
        return switch (algorithmName) {
        case DENY_OVERRIDES -> DenyOverrides::denyOverrides;
        case PERMIT_OVERRIDES -> null;
        case ONLY_ONE_APPLICABLE -> null;
        case DENY_UNLESS_PERMIT -> null;
        case PERMIT_UNLESS_DENY -> null;
        default -> throw new PolicyEvaluationException(
                String.format("Illegal DocumentsCombiningAlgorithm '%s'.", algorithmName));
        };
    }

}
