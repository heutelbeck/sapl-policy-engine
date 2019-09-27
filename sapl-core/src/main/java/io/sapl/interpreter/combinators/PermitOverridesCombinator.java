package io.sapl.interpreter.combinators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.combinators.ObligationAdviceCollector.Type;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.variables.VariableContext;
import reactor.core.publisher.Flux;

public class PermitOverridesCombinator implements DocumentsCombinator, PolicyCombinator {

	@Override
	public Flux<Response> combineMatchingDocuments(Collection<SAPL> matchingSaplDocuments, boolean errorsInTarget,
			Request request, AttributeContext attributeCtx, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables) {

		if (matchingSaplDocuments == null || matchingSaplDocuments.isEmpty()) {
			return errorsInTarget ? Flux.just(Response.INDETERMINATE) : Flux.just(Response.NOT_APPLICABLE);
		}

		final VariableContext variableCtx;
		try {
			variableCtx = new VariableContext(request, systemVariables);
		}
		catch (PolicyEvaluationException e) {
			return Flux.just(Response.INDETERMINATE);
		}
		final EvaluationContext evaluationCtx = new EvaluationContext(attributeCtx, functionCtx, variableCtx);

		final List<Flux<Response>> responseFluxes = new ArrayList<>(matchingSaplDocuments.size());
		for (SAPL document : matchingSaplDocuments) {
			responseFluxes.add(document.evaluate(evaluationCtx));
		}

		final ResponseAccumulator responseAccumulator = new ResponseAccumulator(errorsInTarget);
		return Flux.combineLatest(responseFluxes, responses -> {
			responseAccumulator.addSingleResponses(responses);
			return responseAccumulator.getCombinedResponse();
		}).distinctUntilChanged();
	}

	@Override
	public Flux<Response> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
		boolean errorsInTarget = false;
		final List<Policy> matchingPolicies = new ArrayList<>();
		for (Policy policy : policies) {
			try {
				if (policy.matches(ctx)) {
					matchingPolicies.add(policy);
				}
			}
			catch (PolicyEvaluationException e) {
				errorsInTarget = true;
			}
		}

		if (matchingPolicies.isEmpty()) {
			return errorsInTarget ? Flux.just(Response.INDETERMINATE) : Flux.just(Response.NOT_APPLICABLE);
		}

		final List<Flux<Response>> responseFluxes = new ArrayList<>(matchingPolicies.size());
		for (Policy policy : matchingPolicies) {
			responseFluxes.add(policy.evaluate(ctx));
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
			response = errorsInTarget ? Response.INDETERMINATE : Response.NOT_APPLICABLE;
		}

		void addSingleResponses(Object... responses) {
			init();
			for (Object resp : responses) {
				addSingleResponse((Response) resp);
			}
		}

		private void addSingleResponse(Response newResponse) {
			Decision newDecision = newResponse.getDecision();
			if (newDecision == Decision.PERMIT) {
				permitCount += 1;
				if (newResponse.getResource().isPresent()) {
					transformation = true;
				}

				obligationAdvice.add(Decision.PERMIT, newResponse);
				response = newResponse;
			}
			else if (newDecision == Decision.INDETERMINATE && response.getDecision() != Decision.PERMIT) {
				response = Response.INDETERMINATE;
			}
			else if (newDecision == Decision.DENY && response.getDecision() != Decision.INDETERMINATE
					&& response.getDecision() != Decision.PERMIT) {
				obligationAdvice.add(Decision.DENY, newResponse);
				response = Response.DENY;
			}
		}

		Response getCombinedResponse() {
			if (response.getDecision() == Decision.PERMIT) {
				if (permitCount > 1 && transformation)
					return Response.INDETERMINATE;

				return new Response(Decision.PERMIT, response.getResource(),
						obligationAdvice.get(Type.OBLIGATION, Decision.PERMIT),
						obligationAdvice.get(Type.ADVICE, Decision.PERMIT));
			}
			else if (response.getDecision() == Decision.DENY) {
				return new Response(Decision.DENY, response.getResource(),
						obligationAdvice.get(Type.OBLIGATION, Decision.DENY),
						obligationAdvice.get(Type.ADVICE, Decision.DENY));
			}
			else {
				return response;
			}
		}

	}

}
