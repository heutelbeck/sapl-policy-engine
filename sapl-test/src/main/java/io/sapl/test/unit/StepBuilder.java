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
package io.sapl.test.unit;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.api.pdp.TraceLevel;
import io.sapl.api.pdp.internal.TracedDecision;
import io.sapl.api.pdp.internal.TracedPdpDecision;
import io.sapl.pdp.ConfigurationRegister;
import io.sapl.pdp.DynamicPolicyDecisionPoint;
import io.sapl.pdp.ThreadLocalRandomIdFactory;
import io.sapl.test.mocking.attribute.MockingAttributeBroker;
import io.sapl.test.mocking.function.MockingFunctionBroker;
import io.sapl.test.steps.AttributeMockReturnValues;
import io.sapl.test.steps.GivenStep;
import io.sapl.test.steps.StepsDefaultImpl;
import io.sapl.test.steps.WhenStep;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.test.StepVerifier;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Implementing a Step Builder Pattern to construct test cases.
 * <p>
 * Uses a PDP with ONLY_ONE_APPLICABLE combining algorithm to evaluate single
 * documents. This ensures proper context propagation, streaming attribute
 * handling, and coverage tracking through the full PDP infrastructure.
 */
@UtilityClass
class StepBuilder {

    private static final String DEFAULT_PDP_ID    = "test-pdp";
    private static final String DEFAULT_CONFIG_ID = "test-config";

    /**
     * Create Builder starting at the Given-Step. Only for internal usage.
     *
     * @param policySource the SAPL policy source code to evaluate
     * @param attributeBroker the attribute broker
     * @param functionBroker the function broker
     * @param variables the variables map
     * @return {@link GivenStep} to start constructing the test case.
     */
    static GivenStep newBuilderAtGivenStep(String policySource, AttributeBroker attributeBroker,
            FunctionBroker functionBroker, Map<String, Value> variables) {
        return new Steps(policySource, attributeBroker, functionBroker, variables);
    }

    /**
     * Create Builder starting at the When-Step. Only for internal usage.
     *
     * @param policySource the SAPL policy source code to evaluate
     * @param attributeBroker the attribute broker
     * @param functionBroker the function broker
     * @param variables the variables map
     * @return {@link WhenStep} to start constructing the test case.
     */
    static WhenStep newBuilderAtWhenStep(String policySource, AttributeBroker attributeBroker,
            FunctionBroker functionBroker, Map<String, Value> variables) {
        return new Steps(policySource, attributeBroker, functionBroker, variables);
    }

    /**
     * Implementing all step interfaces. Always returning "this" to enable
     * Builder-Pattern but as a step interface.
     */
    private static class Steps extends StepsDefaultImpl {

        private final String               policySource;
        private DynamicPolicyDecisionPoint pdp;

        Steps(String policySource,
                AttributeBroker attributeBroker,
                FunctionBroker functionBroker,
                Map<String, Value> variables) {
            this.policySource           = policySource;
            this.mockingFunctionBroker  = new MockingFunctionBroker(functionBroker);
            this.mockingAttributeBroker = new MockingAttributeBroker(attributeBroker);
            this.variables              = variables;
            this.mockedAttributeValues  = new LinkedList<>();
        }

        @Override
        protected void createStepVerifier(AuthorizationSubscription authzSub) {
            val configurationRegister = new ConfigurationRegister(mockingFunctionBroker, mockingAttributeBroker,
                    TraceLevel.STANDARD);

            val pdpConfiguration = new PDPConfiguration(DEFAULT_PDP_ID, DEFAULT_CONFIG_ID,
                    CombiningAlgorithm.ONLY_ONE_APPLICABLE, TraceLevel.STANDARD, List.of(policySource), variables);

            configurationRegister.loadConfiguration(pdpConfiguration, false);

            this.pdp = new DynamicPolicyDecisionPoint(configurationRegister, new ThreadLocalRandomIdFactory());

            val decisionFlux = pdp.decideTraced(authzSub).map(TracedDecision::originalTrace)
                    .map(this::valueToAuthorizationDecision);

            if (this.withVirtualTime) {
                this.steps = StepVerifier.withVirtualTime(() -> decisionFlux);
            } else {
                this.steps = StepVerifier.create(decisionFlux);
            }

            for (AttributeMockReturnValues mock : this.mockedAttributeValues) {
                var fullName = mock.getFullName();
                for (Value value : mock.getMockReturnValues()) {
                    this.steps = this.steps.then(() -> this.mockingAttributeBroker.emitToAttribute(fullName, value));
                }
            }
        }

        private AuthorizationDecision valueToAuthorizationDecision(Value value) {
            return TracedPdpDecision.toAuthorizationDecision(value);
        }

    }

}
