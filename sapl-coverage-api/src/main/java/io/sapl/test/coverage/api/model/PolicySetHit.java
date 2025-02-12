/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.coverage.api.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * A set of policy hits.
 */
@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class PolicySetHit {

    /**
     * PolicySetId of hit {@link io.sapl.grammar.sapl.PolicySet}
     */
    private String policySetId;

    @Override
    public String toString() {
        return policySetId;
    }

    /**
     * Loads a {@link PolicySetHit} from a String.
     *
     * @param policySetToStringResult input String
     * @return the PolicySetHit.
     */
    public static PolicySetHit fromString(String policySetToStringResult) {
        return new PolicySetHit(policySetToStringResult);
    }

}
