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
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class DenyUnlessPermitCombinator implements DocumentsCombinator, PolicyCombinator {

	@Override
	public Flux<Response> combineMatchingDocuments(Collection<SAPL> matchingSaplDocuments, boolean errorsInTarget,
			Request request, AttributeContext attributeCtx, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables) {
		LOGGER.trace("|-- Combining matching documents");
		if (matchingSaplDocuments == null || matchingSaplDocuments.isEmpty()) {
			LOGGER.trace("| |-- No matches. Default to DENY");
			return Flux.just(Response.DENY);
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
			LOGGER.trace("| |-- Evaluate: {} ({})", document.getPolicyElement().getSaplName(),
					document.getPolicyElement().getClass().getName());
			// do not first check match again. directly evaluate the rules
			responseFluxes.add(document.evaluate(evaluationCtx));
		}

		final ResponseAccumulator responseAccumulator = new ResponseAccumulator();
		return Flux.combineLatest(responseFluxes, responses -> {
			responseAccumulator.addSingleResponses(responses);
			Response result = responseAccumulator.getCombinedResponse();
			LOGGER.trace("| |-- {} Combined Response: {}", result.getDecision(), result);
			return result;
		}).distinctUntilChanged();
	}

	@Override
	public Flux<Response> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
		final List<Policy> matchingPolicies = new ArrayList<>();
		for (Policy policy : policies) {
			try {
				if (policy.matches(ctx)) {
					matchingPolicies.add(policy);
				}
			}
			catch (PolicyEvaluationException e) {
				// we won't further evaluate this policy
			}
		}

		if (matchingPolicies.isEmpty()) {
			return Flux.just(Response.DENY);
		}

		final List<Flux<Response>> responseFluxes = new ArrayList<>(matchingPolicies.size());
		for (Policy policy : matchingPolicies) {
			responseFluxes.add(policy.evaluate(ctx));
		}
		final ResponseAccumulator responseAccumulator = new ResponseAccumulator();
		return Flux.combineLatest(responseFluxes, responses -> {
			responseAccumulator.addSingleResponses(responses);
			return responseAccumulator.getCombinedResponse();
		}).distinctUntilChanged();
	}

	private static class ResponseAccumulator {

		private Response response;

		private int permitCount;

		private boolean transformation;

		private ObligationAdviceCollector obligationAdvice;

		ResponseAccumulator() {
			init();
		}

		private void init() {
			permitCount = 0;
			transformation = false;
			obligationAdvice = new ObligationAdviceCollector();
			response = Response.DENY;
		}

		void addSingleResponses(Object... responses) {
			init();
			for (Object resp : responses) {
				addSingleResponse((Response) resp);
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
			}
			else if (newResponse.getDecision() == Decision.DENY && response.getDecision() != Decision.PERMIT) {
				obligationAdvice.add(Decision.DENY, newResponse);
			}
		}

		Response getCombinedResponse() {
			if (response.getDecision() == Decision.PERMIT) {
				if (permitCount > 1 && transformation) {
					// Multiple applicable permit policies with at least one
					// transformation not
					// allowed.
					return Response.DENY;
				}

				return new Response(Decision.PERMIT, response.getResource(),
						obligationAdvice.get(Type.OBLIGATION, Decision.PERMIT),
						obligationAdvice.get(Type.ADVICE, Decision.PERMIT));
			}
			else {
				return new Response(Decision.DENY, response.getResource(),
						obligationAdvice.get(Type.OBLIGATION, Decision.DENY),
						obligationAdvice.get(Type.ADVICE, Decision.DENY));
			}
		}

	}

}
