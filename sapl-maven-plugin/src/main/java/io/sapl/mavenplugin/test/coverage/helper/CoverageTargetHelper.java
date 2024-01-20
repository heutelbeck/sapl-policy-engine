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
package io.sapl.mavenplugin.test.coverage.helper;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Named;
import javax.inject.Singleton;

import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.Statement;
import io.sapl.mavenplugin.test.coverage.SaplTestException;
import io.sapl.mavenplugin.test.coverage.model.CoverageTargets;
import io.sapl.mavenplugin.test.coverage.model.SaplDocument;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

@Named
@Singleton
public class CoverageTargetHelper {

    public CoverageTargets getCoverageTargets(Collection<SaplDocument> documents) {
        List<PolicySetHit>       availablePolicySetHitTargets       = new LinkedList<>();
        List<PolicyHit>          availablePolicyHitTargets          = new LinkedList<>();
        List<PolicyConditionHit> availablePolicyConditionHitTargets = new LinkedList<>();

        for (SaplDocument saplDoc : documents) {
            PolicyElement element = saplDoc.getDocument().getPolicyElement();

            if (element.eClass().equals(SaplPackage.Literals.POLICY_SET)) {
                addPolicySetToResult((PolicySet) element, availablePolicySetHitTargets, availablePolicyHitTargets,
                        availablePolicyConditionHitTargets);
            } else if (element.eClass().equals(SaplPackage.Literals.POLICY)) {
                addPolicyToResult((Policy) element, "", availablePolicyHitTargets, availablePolicyConditionHitTargets);
            } else {
                throw new SaplTestException("Error: Unknown Subtype of " + PolicyElement.class);
            }
        }

        return new CoverageTargets(List.copyOf(availablePolicySetHitTargets), List.copyOf(availablePolicyHitTargets),
                List.copyOf(availablePolicyConditionHitTargets));
    }

    private void addPolicySetToResult(PolicySet policySet, Collection<PolicySetHit> availablePolicySetHitTargets,
            Collection<PolicyHit> availablePolicyHitTargets,
            Collection<PolicyConditionHit> availablePolicyConditionHitTargets) {

        availablePolicySetHitTargets.add(new PolicySetHit(policySet.getSaplName()));

        for (Policy policy : policySet.getPolicies()) {
            addPolicyToResult(policy, policySet.getSaplName(), availablePolicyHitTargets,
                    availablePolicyConditionHitTargets);
        }
    }

    private void addPolicyToResult(Policy policy, String policySetId, Collection<PolicyHit> availablePolicyHitTargets,
            Collection<PolicyConditionHit> availablePolicyConditionHitTargets) {

        availablePolicyHitTargets.add(new PolicyHit(policySetId, policy.getSaplName()));

        if (policy.getBody() == null) {
            return;
        }

        for (int i = 0; i < policy.getBody().getStatements().size(); i++) {
            addPolicyConditionToResult(policy.getBody().getStatements().get(i), i, policySetId, policy.getSaplName(),
                    availablePolicyConditionHitTargets);
        }
    }

    private void addPolicyConditionToResult(Statement statement, int position, String policySetId, String policyId,
            Collection<PolicyConditionHit> availablePolicyConditionHitTargets) {

        if (statement instanceof Condition) {
            availablePolicyConditionHitTargets.add(new PolicyConditionHit(policySetId, policyId, position, true));
            availablePolicyConditionHitTargets.add(new PolicyConditionHit(policySetId, policyId, position, false));
        }
    }

}
