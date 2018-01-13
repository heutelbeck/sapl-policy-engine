package io.sapl.interpreter.combinators;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.grammar.sapl.Policy;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;

public class FirstApplicableCombinator implements PolicyCombinator {

	private SAPLInterpreter interpreter;

	public FirstApplicableCombinator(SAPLInterpreter interpreter) {
		this.interpreter = interpreter;
	}

	@Override
	public Response combinePolicies(List<Policy> policies, Request request, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables, Map<String, JsonNode> variables,
			Map<String, String> imports) {
		for (Policy policy : policies) {
			boolean matches;
			try {
				matches = interpreter.matches(request, policy, functionCtx, systemVariables, variables, imports);
			} catch (PolicyEvaluationException e) {
				return Response.indeterminate();
			}
			if (matches) {
				Response tempResponse = interpreter.evaluateRules(request, policy, attributeCtx, functionCtx,
						systemVariables, variables, imports);
				Decision tempDecision = tempResponse.getDecision();

				if (tempDecision != Decision.NOT_APPLICABLE) {
					return tempResponse;
				}
			}
		}
		return Response.notApplicable();
	}

}
