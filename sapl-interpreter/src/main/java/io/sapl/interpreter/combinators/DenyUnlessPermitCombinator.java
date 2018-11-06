package io.sapl.interpreter.combinators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.combinators.ObligationAdviceCollector.Type;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import reactor.core.publisher.Flux;

public class DenyUnlessPermitCombinator implements DocumentsCombinator, PolicyCombinator {

	private SAPLInterpreter interpreter;

	public DenyUnlessPermitCombinator(SAPLInterpreter interpreter) {
		this.interpreter = interpreter;
	}

	@Override
	public Flux<Response> combineMatchingDocuments(Collection<SAPL> matchingSaplDocuments, boolean errorsInTarget,
												   Request request, AttributeContext attributeCtx, FunctionContext functionCtx,
												   Map<String, JsonNode> systemVariables) {

		if (matchingSaplDocuments == null || matchingSaplDocuments.isEmpty()) {
			return Flux.just(Response.deny());
		}

		final List<Flux<Response>> responseFluxes = new ArrayList<>();
		for (SAPL document : matchingSaplDocuments) {
			responseFluxes.add(Flux.just(interpreter.evaluate(request, document, attributeCtx, functionCtx, systemVariables)));
		}

		final ResponseAccumulator responseAccumulator = new ResponseAccumulator(errorsInTarget);
		return Flux.combineLatest(responseFluxes, responses -> {
			responseAccumulator.addSingleResponses(responses);
			return responseAccumulator.getCombinedResponse();
		}).distinctUntilChanged();
	}

	@Override
	public Flux<Response> combinePolicies(List<Policy> policies, Request request, AttributeContext attributeCtx,
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

		if (matchingPolicies.isEmpty()) {
			return Flux.just(Response.deny());
		}

		final List<Flux<Response>> responseFluxes = new ArrayList<>();
		for (Policy policy : matchingPolicies) {
			responseFluxes.add(Flux.just(interpreter.evaluateRules(request, policy, attributeCtx, functionCtx,
					systemVariables, variables, imports)));
		}
		final ResponseAccumulator responseAccumulator = new ResponseAccumulator(errorsInTarget);
		return Flux.combineLatest(responseFluxes, responses -> {
			responseAccumulator.addSingleResponses(responses);
			return responseAccumulator.getCombinedResponse();
		}).distinctUntilChanged();
	}


	private static class ResponseAccumulator {

		private boolean errorsInTarget;
		private Response response;
		private int permitCount;
		private boolean transformation;
		private ObligationAdviceCollector obligationAdvice;

		ResponseAccumulator(boolean errorsInTarget) {
			this.errorsInTarget = errorsInTarget;
			init();
		}

		private void init() {
			permitCount = 0;
			transformation = false;
			obligationAdvice = new ObligationAdviceCollector();
			response = Response.deny();
		}

		void addSingleResponses(Object[] responses) {
			init();
			for (Object response : responses) {
				addSingleResponse((Response) response);
			}
		}

		private void addSingleResponse(Response newResponse) {
			if (newResponse.getDecision() == Decision.PERMIT) {
				permitCount += 1;
				if (newResponse.getResource().isPresent()) {
					transformation = true;
				}
				obligationAdvice.add(Decision.PERMIT, newResponse);
				response = newResponse;
			} else if (newResponse.getDecision() == Decision.DENY && response.getDecision() != Decision.PERMIT) {
				obligationAdvice.add(Decision.DENY, newResponse);
			}
		}

		Response getCombinedResponse() {
			if (response.getDecision() == Decision.PERMIT) {
				if (permitCount > 1 && transformation) {
					// Multiple applicable permit policies with at least one transformation not
					// allowed.
					return Response.deny();
				}

				return new Response(Decision.PERMIT, response.getResource(),
						obligationAdvice.get(Type.OBLIGATION, Decision.PERMIT),
						obligationAdvice.get(Type.ADVICE, Decision.PERMIT));
			} else {
				return new Response(Decision.DENY, response.getResource(),
						obligationAdvice.get(Type.OBLIGATION, Decision.DENY),
						obligationAdvice.get(Type.ADVICE, Decision.DENY));
			}
		}
	}
}
