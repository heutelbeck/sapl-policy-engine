/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.test.mocking.attribute.MockingAttributeContext;
import io.sapl.test.mocking.function.MockingFunctionContext;
import io.sapl.test.steps.AttributeMockReturnValues;
import io.sapl.test.steps.GivenStep;
import io.sapl.test.steps.StepsDefaultImpl;
import io.sapl.test.steps.WhenStep;

import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

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
	static GivenStep newBuilderAtGivenStep(SAPL document, AttributeContext attrCtx, FunctionContext funcCtx,
			Map<String, JsonNode> variables) {
		return new Steps(document, attrCtx, funcCtx, variables);
	}

	/**
	 * Create Builder starting at the When-Step. Only for internal usage.
	 * 
	 * @param document containing the {@link SAPL} policy to evaluate
	 * @return {@link WhenStep} to start constructing the test case.
	 */
	static WhenStep newBuilderAtWhenStep(SAPL document, AttributeContext attrCtx, FunctionContext funcCtx,
			Map<String, JsonNode> variables) {
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

		
		SAPL document;

		Steps(SAPL document, AttributeContext attrCtx, FunctionContext funcCtx, Map<String, JsonNode> variables) {
			this.document = document;
			this.mockingFunctionContext = new MockingFunctionContext(funcCtx);
			this.mockingAttributeContext = new MockingAttributeContext(attrCtx);
			this.variables = variables;
			this.mockedAttributeValues = new LinkedList<>();
		}


		@Override
		protected void createStepVerifier(AuthorizationSubscription authzSub) {
			EvaluationContext ctx = new EvaluationContext(this.mockingAttributeContext, this.mockingFunctionContext,
					this.variables).forAuthorizationSubscription(authzSub);

			Val matchResult = this.document.matches(ctx).block();
			if(matchResult.isBoolean() && matchResult.getBoolean()) {
				if (this.withVirtualTime) {
					this.steps = StepVerifier
							.withVirtualTime(() -> this.document.evaluate(ctx));
				} else {	
					this.steps = StepVerifier.create(this.document.evaluate(ctx));
				}
	
				for (AttributeMockReturnValues mock : this.mockedAttributeValues) {
					String fullname = mock.getFullname();
					for (Val val : mock.getMockReturnValues()) {
						this.steps = this.steps.then(() -> this.mockingAttributeContext.mockEmit(fullname, val));
					}
				}
			} else {
				this.steps = StepVerifier.create(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
				
			}
		}
	}
}
