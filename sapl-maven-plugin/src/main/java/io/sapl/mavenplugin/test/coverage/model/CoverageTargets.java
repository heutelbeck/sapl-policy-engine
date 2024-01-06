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
package io.sapl.mavenplugin.test.coverage.model;

import java.util.Collection;

import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CoverageTargets {

    private Collection<PolicySetHit> policySets;

    private Collection<PolicyHit> policies;

    private Collection<PolicyConditionHit> policyConditions;

    public boolean isPolicySetHit(PolicySetHit possibleHit) {
        return this.policySets.contains(possibleHit);
    }

    public boolean isPolicyHit(PolicyHit possibleHit) {
        return this.policies.contains(possibleHit);
    }

    public boolean isPolicyConditionHit(PolicyConditionHit possibleHit) {
        return this.policyConditions.contains(possibleHit);
    }

}
