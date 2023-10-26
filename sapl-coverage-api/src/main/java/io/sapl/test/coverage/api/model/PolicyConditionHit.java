/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
 * Containing all necessary information of a Policy Condition Hit
 */
@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class PolicyConditionHit {

    /**
     * Identifier of {@link io.sapl.grammar.sapl.PolicySet} of hit
     * {@link io.sapl.grammar.sapl.Policy}. Empty if
     * {@link io.sapl.grammar.sapl.Policy} isn't in a
     * {@link io.sapl.grammar.sapl.PolicySet}.
     */
    String policySetId;

    /**
     * Identifier of hit {@link io.sapl.grammar.sapl.Policy}
     */
    String policyId;

    /**
     * StatementId of {@link io.sapl.grammar.sapl.Condition} hit in
     * {@link io.sapl.grammar.sapl.Policy}
     */
    int conditionStatementId;

    /**
     * Result of evaluation {@link io.sapl.grammar.sapl.Condition}
     */
    boolean conditionResult;

    @Override
    public String toString() {
        return policySetId + CoverageHitConstants.DELIMITER + policyId + CoverageHitConstants.DELIMITER
                + conditionStatementId + CoverageHitConstants.DELIMITER + conditionResult;
    }

    /**
     * Converts a String to PolicyConditionHit.
     * 
     * @param policyConditionToStringResult a condition result expressed in a String
     * @return the expressed PolicyConditionHit
     */
    public static PolicyConditionHit fromString(String policyConditionToStringResult) {
        var split = policyConditionToStringResult.split(CoverageHitConstants.DELIMITER_MATCH_REGEX);
        return new PolicyConditionHit(split[0], split[1], Integer.parseInt(split[2]), Boolean.parseBoolean(split[3]));
    }

}
