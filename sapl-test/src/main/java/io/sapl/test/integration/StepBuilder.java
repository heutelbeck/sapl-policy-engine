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
package io.sapl.test.integration;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.pdp.config.fixed.FixedFunctionsAndAttributesPDPConfigurationProvider;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalPointSource;
import io.sapl.test.mocking.attribute.MockingAttributeContext;
import io.sapl.test.mocking.function.MockingFunctionContext;
import io.sapl.test.steps.AttributeMockReturnValues;
import io.sapl.test.steps.GivenStep;
import io.sapl.test.steps.StepsDefaultImpl;
import io.sapl.test.steps.WhenStep;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class StepBuilder {

    /**
     * Create Builder starting at the Given-Step. Only for internal usage.
     *
     * @return {@link GivenStep} to start constructing the test case.
     */
    static GivenStep newBuilderAtGivenStep(PolicyRetrievalPoint prp, VariablesAndCombinatorSource pdpConfig,
            AttributeContext attrCtx, FunctionContext funcCtx, Map<String, Val> variables) {
        return new Steps(prp, pdpConfig, attrCtx, funcCtx, variables);
    }

    /**
     * Create Builder starting at the When-Step. Only for internal usage.
     *
     * @return {@link WhenStep} to start constructing the test case.
     */
    static WhenStep newBuilderAtWhenStep(PolicyRetrievalPoint prp, VariablesAndCombinatorSource pdpConfig,
            AttributeContext attrCtx, FunctionContext funcCtx, Map<String, Val> variables) {
        return new Steps(prp, pdpConfig, attrCtx, funcCtx, variables);
    }

    // disable default constructor
    private StepBuilder() {
    }

    /**
     * Implementing all step interfaces. Always returning \"this\" to enable
     * Builder-Pattern but as a step interface
     */
    private static class Steps extends StepsDefaultImpl {

        private final PolicyRetrievalPoint prp;

        private final VariablesAndCombinatorSource pdpConfig;

        Steps(PolicyRetrievalPoint prp, VariablesAndCombinatorSource pdpConfig, AttributeContext attrCtx,
                FunctionContext funcCtx, Map<String, Val> variables) {
            this.prp                     = prp;
            this.pdpConfig               = pdpConfig;
            this.mockingFunctionContext  = new MockingFunctionContext(funcCtx);
            this.mockingAttributeContext = new MockingAttributeContext(attrCtx);
            this.variables               = variables;
            this.mockedAttributeValues   = new LinkedList<>();
        }

        @Override
        protected void createStepVerifier(AuthorizationSubscription authzSub) {
            var prpSource             = new PolicyRetrievalPointSource() {

                                          @Override
                                          public void dispose() {
                                              // NOOP
                                          }

                                          @Override
                                          public Flux<PolicyRetrievalPoint> policyRetrievalPoint() {
                                              return Flux.just(prp);
                                          }
                                      };
            var configurationProvider = new FixedFunctionsAndAttributesPDPConfigurationProvider(
                    this.mockingAttributeContext, this.mockingFunctionContext, this.pdpConfig, List.of(), List.of(),
                    prpSource);
            var pdp                   = new EmbeddedPolicyDecisionPoint(configurationProvider);

            if (this.withVirtualTime) {
                this.steps = StepVerifier.withVirtualTime(() -> pdp.decide(authzSub));
            } else {
                this.steps = StepVerifier.create(pdp.decide(authzSub));
            }

            for (AttributeMockReturnValues mock : this.mockedAttributeValues) {
                var fullName = mock.getFullName();
                for (Val val : mock.getMockReturnValues()) {
                    this.steps = this.steps.then(() -> this.mockingAttributeContext.mockEmit(fullName, val));
                }
            }
        }

    }

}
