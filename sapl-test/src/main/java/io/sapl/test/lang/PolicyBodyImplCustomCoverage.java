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
package io.sapl.test.lang;

import org.eclipse.emf.ecore.EObject;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.impl.PolicyBodyImplCustom;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class PolicyBodyImplCustomCoverage extends PolicyBodyImplCustom {

    private final CoverageHitRecorder hitRecorder;

    private int currentStatementId = 0;

    PolicyBodyImplCustomCoverage(CoverageHitRecorder recorder) {
        this.hitRecorder = recorder;
    }

    @Override

    protected Flux<Val> evaluateStatements(Val previousResult, int statementId) {
        this.currentStatementId = statementId;
        return super.evaluateStatements(previousResult, statementId);
    }

    @Override
    protected Flux<Val> evaluateCondition(Val previousResult, Condition condition) {
        return super.evaluateCondition(previousResult, condition).doOnNext(result -> {
            if (result.isBoolean()) {
                String  policySetId = "";
                String  policyId;
                EObject eContainer1 = eContainer();
                /*
                 * A PolicyBody outside a Policy is not allowed -> thus a pre-cast if statement
                 * like if(eContainer1.eClass().equals(SaplPackage.Literals.POLICY)) cannot be
                 * false and therefore cannot be tested
                 */
                policyId = ((Policy) eContainer1).getSaplName();
                EObject eContainer2 = eContainer1.eContainer();
                if (eContainer2.eClass().equals(SaplPackage.Literals.POLICY_SET)) {
                    policySetId = ((PolicySet) eContainer2).getSaplName();
                }
                PolicyConditionHit hit = new PolicyConditionHit(policySetId, policyId, currentStatementId,
                        result.getBoolean());
                log.trace("| | | | |-- Hit PolicyCondition: " + hit);
                this.hitRecorder.recordPolicyConditionHit(hit);
            }
        });
    }

}
