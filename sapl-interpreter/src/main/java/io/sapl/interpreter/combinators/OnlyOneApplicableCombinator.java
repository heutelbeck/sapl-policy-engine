package io.sapl.interpreter.combinators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;

public class OnlyOneApplicableCombinator implements DocumentsCombinator, PolicyCombinator {

	private SAPLInterpreter interpreter;

	public OnlyOneApplicableCombinator(SAPLInterpreter interpreter) {
		this.interpreter = interpreter;
	}

	@Override
	public Response combineMatchingDocuments(Collection<SAPL> matchingSaplDocuments, boolean errorsInTarget,
			Request request, AttributeContext attributeCtx, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables) {
		if (errorsInTarget || matchingSaplDocuments.size() > 1) {
			return Response.indeterminate();
		} else if (matchingSaplDocuments.size() == 1) {
			return interpreter.evaluateRules(request, matchingSaplDocuments.iterator().next(), attributeCtx,
					functionCtx, systemVariables);
		} else {
			return Response.notApplicable();
		}
	}

	@Override
	public Response combinePolicies(List<Policy> policies, Request request, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables, Map<String, JsonNode> variables,
			Map<String, String> imports) {
		boolean errorsInTarget = false;
		List<Policy> matchingPolicies = new ArrayList<>();
		for (Policy policy : policies) {
			try {
				if (interpreter.matches(request, policy, functionCtx, systemVariables, variables, imports)) {
					matchingPolicies.add(policy);
				}
			} catch (PolicyEvaluationException e) {
				errorsInTarget = true;
			}
		}

		if (errorsInTarget || matchingPolicies.size() > 1) {
			return Response.indeterminate();
		} else if (matchingPolicies.size() == 1) {
			return interpreter.evaluateRules(request, matchingPolicies.get(0), attributeCtx, functionCtx,
					systemVariables, variables, imports);
		} else {
			return Response.notApplicable();
		}
	}
}
