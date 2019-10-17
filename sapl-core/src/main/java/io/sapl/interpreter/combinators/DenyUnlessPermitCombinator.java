package io.sapl.interpreter.combinators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthDecision;
import io.sapl.api.pdp.AuthSubscription;
import io.sapl.api.pdp.Decision;
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
	public Flux<AuthDecision> combineMatchingDocuments(Collection<SAPL> matchingSaplDocuments, boolean errorsInTarget,
			AuthSubscription authSubscription, AttributeContext attributeCtx, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables) {
		LOGGER.trace("|-- Combining matching documents");
		if (matchingSaplDocuments == null || matchingSaplDocuments.isEmpty()) {
			LOGGER.trace("| |-- No matches. Default to DENY");
			return Flux.just(AuthDecision.DENY);
		}

		final VariableContext variableCtx;
		try {
			variableCtx = new VariableContext(authSubscription, systemVariables);
		}
		catch (PolicyEvaluationException e) {
			return Flux.just(AuthDecision.INDETERMINATE);
		}
		final EvaluationContext evaluationCtx = new EvaluationContext(attributeCtx, functionCtx, variableCtx);

		final List<Flux<AuthDecision>> authDecisionFluxes = new ArrayList<>(matchingSaplDocuments.size());
		for (SAPL document : matchingSaplDocuments) {
			LOGGER.trace("| |-- Evaluate: {} ({})", document.getPolicyElement().getSaplName(),
					document.getPolicyElement().getClass().getName());
			// do not first check match again. directly evaluate the rules
			authDecisionFluxes.add(document.evaluate(evaluationCtx));
		}

		final AuthDecisionAccumulator authDecisionAccumulator = new AuthDecisionAccumulator();
		return Flux.combineLatest(authDecisionFluxes, authDecisions -> {
			authDecisionAccumulator.addSingleDecisions(authDecisions);
			AuthDecision result = authDecisionAccumulator.getCombinedAuthDecision();
			LOGGER.trace("| |-- {} Combined AuthDecision: {}", result.getDecision(), result);
			return result;
		}).distinctUntilChanged();
	}

	@Override
	public Flux<AuthDecision> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
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
			return Flux.just(AuthDecision.DENY);
		}

		final List<Flux<AuthDecision>> authDecisionFluxes = new ArrayList<>(matchingPolicies.size());
		for (Policy policy : matchingPolicies) {
			authDecisionFluxes.add(policy.evaluate(ctx));
		}
		final AuthDecisionAccumulator authDecisionAccumulator = new AuthDecisionAccumulator();
		return Flux.combineLatest(authDecisionFluxes, authDecisions -> {
			authDecisionAccumulator.addSingleDecisions(authDecisions);
			return authDecisionAccumulator.getCombinedAuthDecision();
		}).distinctUntilChanged();
	}

	private static class AuthDecisionAccumulator {

		private AuthDecision authDecision;

		private int permitCount;

		private boolean transformation;

		private ObligationAdviceCollector obligationAdvice;

		AuthDecisionAccumulator() {
			init();
		}

		private void init() {
			permitCount = 0;
			transformation = false;
			obligationAdvice = new ObligationAdviceCollector();
			authDecision = AuthDecision.DENY;
		}

		void addSingleDecisions(Object... authDecisions) {
			init();
			for (Object decision : authDecisions) {
				addSingleDecision((AuthDecision) decision);
			}
		}

		private void addSingleDecision(AuthDecision newAuthDecision) {
			if (newAuthDecision.getDecision() == Decision.PERMIT) {
				permitCount += 1;
				if (newAuthDecision.getResource().isPresent()) {
					transformation = true;
				}
				obligationAdvice.add(Decision.PERMIT, newAuthDecision);
				authDecision = newAuthDecision;
			}
			else if (newAuthDecision.getDecision() == Decision.DENY && authDecision.getDecision() != Decision.PERMIT) {
				obligationAdvice.add(Decision.DENY, newAuthDecision);
			}
		}

		AuthDecision getCombinedAuthDecision() {
			if (authDecision.getDecision() == Decision.PERMIT) {
				if (permitCount > 1 && transformation) {
					// Multiple applicable permit policies with at least one
					// transformation not
					// allowed.
					return AuthDecision.DENY;
				}

				return new AuthDecision(Decision.PERMIT, authDecision.getResource(),
						obligationAdvice.get(Type.OBLIGATION, Decision.PERMIT),
						obligationAdvice.get(Type.ADVICE, Decision.PERMIT));
			}
			else {
				return new AuthDecision(Decision.DENY, authDecision.getResource(),
						obligationAdvice.get(Type.OBLIGATION, Decision.DENY),
						obligationAdvice.get(Type.ADVICE, Decision.DENY));
			}
		}

	}

}
