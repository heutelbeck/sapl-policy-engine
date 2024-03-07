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

import static io.sapl.interpreter.combinators.DenyOverrides.DENY_OVERRIDES;
import static io.sapl.interpreter.combinators.DenyUnlessPermit.DENY_UNLESS_PERMIT;
import static io.sapl.interpreter.combinators.FirstApplicable.FIRST_APPLICABLE;
import static io.sapl.interpreter.combinators.OnlyOneApplicable.ONLY_ONE_APPLICABLE;
import static io.sapl.interpreter.combinators.PermitOverrides.PERMIT_OVERRIDES;
import static io.sapl.interpreter.combinators.PermitUnlessDeny.PERMIT_UNLESS_DENY;

import io.sapl.api.interpreter.PolicyEvaluationException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CombiningAlgorithmFactory {

    public PolicySetCombiningAlgorithm policySetCombiningAlgorithm(String algorithmName) {
        return switch (algorithmName) {
        case DENY_OVERRIDES -> DenyOverrides::denyOverrides;
        case PERMIT_OVERRIDES -> PermitOverrides::permitOverrides;
        case FIRST_APPLICABLE -> FirstApplicable::firstApplicable;
        case ONLY_ONE_APPLICABLE -> OnlyOneApplicable::onlyOneApplicable;
        case DENY_UNLESS_PERMIT -> DenyUnlessPermit::denyUnlessPermit;
        case PERMIT_UNLESS_DENY -> PermitUnlessDeny::permitUnlessDeny;
        default -> throw new PolicyEvaluationException(
                String.format("Illegal PolicySetCombiningAlgorithm '%s'.", algorithmName));
        };
    }

    public DocumentsCombiningAlgorithm documentsCombiningAlgorithm(String algorithmName) {
        return switch (algorithmName) {
        case DENY_OVERRIDES -> DenyOverrides::denyOverrides;
        case PERMIT_OVERRIDES -> PermitOverrides::permitOverrides;
        case ONLY_ONE_APPLICABLE -> OnlyOneApplicable::onlyOneApplicable;
        case DENY_UNLESS_PERMIT -> DenyUnlessPermit::denyUnlessPermit;
        case PERMIT_UNLESS_DENY -> PermitUnlessDeny::permitUnlessDeny;
        default -> throw new PolicyEvaluationException(
                String.format("Illegal DocumentsCombiningAlgorithm '%s'.", algorithmName));
        };
    }

}
