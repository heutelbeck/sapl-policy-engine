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

import io.sapl.test.coverage.api.CoverageHitConstants;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Documents a policy hit during testing.
 */
@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class PolicyHit {

    /**
     * Identifier of {@link io.sapl.grammar.sapl.PolicySet} of hit policy. Empty if
     * {@link io.sapl.grammar.sapl.Policy} isn't in a
     * {@link io.sapl.grammar.sapl.PolicySet}.
     */
    private String policySetId;

    /**
     * Identifier of hit {@link io.sapl.grammar.sapl.Policy}
     */
    private String policyId;

    @Override
    public String toString() {
        return policySetId + CoverageHitConstants.DELIMITER + policyId;
    }

    /**
     * Converts a String to PolicyHit.
     *
     * @param policyToStringResult a condition result expressed in a String
     * @return the expressed PolicyHit
     */
    public static PolicyHit fromString(String policyToStringResult) {
        String[] split = policyToStringResult.split(CoverageHitConstants.DELIMITER_MATCH_REGEX);
        return new PolicyHit(split[0], split[1]);
    }

}
