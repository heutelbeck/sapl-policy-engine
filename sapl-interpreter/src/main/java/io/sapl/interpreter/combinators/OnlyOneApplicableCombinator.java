package io.sapl.interpreter.combinators;

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
import reactor.core.publisher.Flux;

public class OnlyOneApplicableCombinator
		implements DocumentsCombinator, PolicyCombinator {

	private SAPLInterpreter interpreter;

	public OnlyOneApplicableCombinator(SAPLInterpreter interpreter) {
		this.interpreter = interpreter;
	}

	@Override
	public Flux<Response> combineMatchingDocuments(Collection<SAPL> matchingSaplDocuments,
			boolean errorsInTarget, Request request, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables) {

		if (errorsInTarget || matchingSaplDocuments.size() > 1) {
			return Flux.just(Response.indeterminate());
		}
		else if (matchingSaplDocuments.size() == 1) {
			final SAPL matchingDocument = matchingSaplDocuments.iterator().next();
			return interpreter.evaluateRules(request, matchingDocument, attributeCtx,
					functionCtx, systemVariables);
		}
		else {
			return Flux.just(Response.notApplicable());
		}
	}

	@Override
	public Flux<Response> combinePolicies(List<Policy> policies, Request request,
			AttributeContext attributeCtx, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables, Map<String, JsonNode> variables,
			Map<String, String> imports) {

		Policy matchingPolicy = null;
		for (Policy policy : policies) {
			try {
				if (interpreter.matches(request, policy, functionCtx, systemVariables,
						variables, imports)) {
					if (matchingPolicy != null) {
						return Flux.just(Response.indeterminate());
					}
					matchingPolicy = policy;
				}
			}
			catch (PolicyEvaluationException e) {
				return Flux.just(Response.indeterminate());
			}
		}

		if (matchingPolicy == null) {
			return Flux.just(Response.notApplicable());
		}

		return interpreter.evaluateRules(request, matchingPolicy, attributeCtx,
				functionCtx, systemVariables, variables, imports);
	}

}
