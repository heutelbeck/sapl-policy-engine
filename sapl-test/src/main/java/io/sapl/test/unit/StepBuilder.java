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

import java.util.LinkedList;
import java.util.Map;
import java.util.function.Function;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DocumentEvaluationResult;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.prp.Document;
import io.sapl.test.mocking.attribute.MockingAttributeStreamBroker;
import io.sapl.test.mocking.function.MockingFunctionContext;
import io.sapl.test.steps.AttributeMockReturnValues;
import io.sapl.test.steps.GivenStep;
import io.sapl.test.steps.StepsDefaultImpl;
import io.sapl.test.steps.WhenStep;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

/**
 * Implementing a Step Builder Pattern to construct test cases.
 *
 */
class StepBuilder {

    /**
     * Create Builder starting at the Given-Step. Only for internal usage.
     *
     * @param document containing the {@link SAPL} policy to evaluate
     * @return {@link GivenStep} to start constructing the test case.
     */
    static GivenStep newBuilderAtGivenStep(Document document, AttributeStreamBroker attrCtx, FunctionContext funcCtx,
            Map<String, Val> variables) {
        return new Steps(document, attrCtx, funcCtx, variables);
    }

    /**
     * Create Builder starting at the When-Step. Only for internal usage.
     *
     * @param document containing the {@link SAPL} policy to evaluate
     * @return {@link WhenStep} to start constructing the test case.
     */
    static WhenStep newBuilderAtWhenStep(Document document, AttributeStreamBroker attrCtx, FunctionContext funcCtx,
            Map<String, Val> variables) {
        return new Steps(document, attrCtx, funcCtx, variables);
    }

    // disable default constructor
    private StepBuilder() {
    }

    /**
     * Implementing all step interfaces. Always returning \"this\" to enable
     * Builder-Pattern but as a step interface
     */
    private static class Steps extends StepsDefaultImpl {

        final Document document;

        Steps(Document document, AttributeStreamBroker attrCtx, FunctionContext funcCtx, Map<String, Val> variables) {
            this.document                     = document;
            this.mockingFunctionContext       = new MockingFunctionContext(funcCtx);
            this.mockingAttributeStreamBroker = new MockingAttributeStreamBroker(attrCtx);
            this.variables                    = variables;
            this.mockedAttributeValues        = new LinkedList<>();
        }

        @Override
        protected void createStepVerifier(AuthorizationSubscription authzSub) {
            Val matchResult = this.document.sapl().matches().contextWrite(setUpContext(authzSub)).block();
            if (matchResult != null && matchResult.isBoolean() && matchResult.getBoolean()) {
                if (this.withVirtualTime) {
                    this.steps = StepVerifier.withVirtualTime(() -> this.document.sapl().evaluate()
                            .map(DocumentEvaluationResult::getAuthorizationDecision)
                            .contextWrite(setUpContext(authzSub)));
                } else {
                    this.steps = StepVerifier.create(
                            this.document.sapl().evaluate().map(DocumentEvaluationResult::getAuthorizationDecision)
                                    .contextWrite(setUpContext(authzSub)));
                }

                for (AttributeMockReturnValues mock : this.mockedAttributeValues) {
                    String fullName = mock.getFullName();
                    for (Val val : mock.getMockReturnValues()) {
                        this.steps = this.steps.then(() -> this.mockingAttributeStreamBroker.mockEmit(fullName, val));
                    }
                }
            } else {
                this.steps = StepVerifier.create(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
            }
        }

        private Function<Context, Context> setUpContext(AuthorizationSubscription authzSub) {
            return ctx -> {
                ctx = AuthorizationContext.setAttributeStreamBroker(ctx, this.mockingAttributeStreamBroker);
                ctx = AuthorizationContext.setFunctionContext(ctx, this.mockingFunctionContext);
                ctx = AuthorizationContext.setVariables(ctx, this.variables);
                ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSub);
                return ctx;
            };
        }

    }

}
