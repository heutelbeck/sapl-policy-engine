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
package io.sapl.test.steps;

import java.util.LinkedList;
import java.util.Map;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.DocumentEvaluationResult;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.test.mocking.attribute.MockingAttributeContext;
import io.sapl.test.mocking.function.MockingFunctionContext;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class StepsDefaultImplTestsImpl extends StepsDefaultImpl {

    final SAPL document;

    StepsDefaultImplTestsImpl(String document, AttributeContext attrCtx, FunctionContext funcCtx,
            Map<String, Val> variables) {
        this.document                = new DefaultSAPLInterpreter().parse(document);
        this.mockingFunctionContext  = new MockingFunctionContext(funcCtx);
        this.mockingAttributeContext = new MockingAttributeContext(attrCtx);
        this.variables               = variables;
        this.mockedAttributeValues   = new LinkedList<>();
    }

    @Override
    protected void createStepVerifier(AuthorizationSubscription authzSub) {

        final var matchResult = this.document.matches().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeContext(ctx, this.mockingAttributeContext);
            ctx = AuthorizationContext.setFunctionContext(ctx, this.mockingFunctionContext);
            ctx = AuthorizationContext.setVariables(ctx, this.variables);
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSub);
            return ctx;
        }).block();

        if (matchResult.isBoolean() && matchResult.getBoolean()) {

            this.steps = StepVerifier.create(this.document.evaluate()
                    .map(DocumentEvaluationResult::getAuthorizationDecision).contextWrite(ctx -> {
                        ctx = AuthorizationContext.setAttributeContext(ctx, this.mockingAttributeContext);
                        ctx = AuthorizationContext.setFunctionContext(ctx, this.mockingFunctionContext);
                        ctx = AuthorizationContext.setVariables(ctx, this.variables);
                        ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSub);
                        return ctx;
                    }));

            for (AttributeMockReturnValues mock : this.mockedAttributeValues) {
                String fullName = mock.getFullName();
                for (var val : mock.getMockReturnValues()) {
                    this.steps = this.steps.then(() -> this.mockingAttributeContext.mockEmit(fullName, val));
                }
            }
        } else {
            this.steps = StepVerifier.create(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
        }
    }

}
