package io.sapl.test.steps;

import java.util.LinkedList;
import java.util.Map;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.test.mocking.MockingAttributeContext;
import io.sapl.test.mocking.MockingFunctionContext;

import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class StepsDefaultImplTestImpl extends StepsDefaultImpl {
	SAPL document;

	StepsDefaultImplTestImpl(String document, AttributeContext attrCtx, FunctionContext funcCtx, Map<String, JsonNode> variables) {
		this.document = new DefaultSAPLInterpreter().parse(document);
		this.mockingFunctionContext = new MockingFunctionContext(funcCtx);
		this.mockingAttributeContext = new MockingAttributeContext(attrCtx, this.numberOfExpectSteps);
		this.variables = variables;
		this.mockedAttributeValues = new LinkedList<>();
	}


	@Override
	protected void createStepVerifier(AuthorizationSubscription authzSub) {
		EvaluationContext ctx = new EvaluationContext(this.mockingAttributeContext, this.mockingFunctionContext,
				this.variables).forAuthorizationSubscription(authzSub);

		Val matchResult = this.document.matches(ctx).block();
		
		if(matchResult.isBoolean() && matchResult.getBoolean()) {
			
			this.steps = StepVerifier.create(this.document.evaluate(ctx));
			
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
