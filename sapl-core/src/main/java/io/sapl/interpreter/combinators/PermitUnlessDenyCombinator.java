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
import reactor.core.publisher.Flux;

public class PermitUnlessDenyCombinator implements DocumentsCombinator, PolicyCombinator {

	@Override
	public Flux<AuthDecision> combineMatchingDocuments(Collection<SAPL> matchingSaplDocuments, boolean errorsInTarget,
			AuthSubscription authSubscription, AttributeContext attributeCtx, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables) {

		if (matchingSaplDocuments == null || matchingSaplDocuments.isEmpty()) {
			return Flux.just(AuthDecision.PERMIT);
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
			authDecisionFluxes.add(document.evaluate(evaluationCtx));
		}

		final AuthDecisionAccumulator authDecisionAccumulator = new AuthDecisionAccumulator();
		return Flux.combineLatest(authDecisionFluxes, authDecisions -> {
			authDecisionAccumulator.addSingleDecisions(authDecisions);
			return authDecisionAccumulator.getCombinedAuthDecision();
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
			return Flux.just(AuthDecision.PERMIT);
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
			authDecision = AuthDecision.PERMIT;
		}

		void addSingleDecisions(Object... authDecisions) {
			init();
			for (Object decision : authDecisions) {
				addSingleDecision((AuthDecision) decision);
			}
		}

		private void addSingleDecision(AuthDecision newAuthDecision) {
			if (newAuthDecision.getDecision() == Decision.DENY) {
				obligationAdvice.add(Decision.DENY, newAuthDecision);
				authDecision = AuthDecision.DENY;
			}
			else if (newAuthDecision.getDecision() == Decision.PERMIT && authDecision.getDecision() != Decision.DENY) {
				permitCount += 1;
				if (newAuthDecision.getResource().isPresent()) {
					transformation = true;
				}

				obligationAdvice.add(Decision.PERMIT, newAuthDecision);
				authDecision = newAuthDecision;
			}
		}

		AuthDecision getCombinedAuthDecision() {
			if (authDecision.getDecision() == Decision.PERMIT) {
				if (permitCount > 1 && transformation) {
					return AuthDecision.DENY;
				}
				else {
					return new AuthDecision(Decision.PERMIT, authDecision.getResource(),
							obligationAdvice.get(Type.OBLIGATION, Decision.PERMIT),
							obligationAdvice.get(Type.ADVICE, Decision.PERMIT));
				}
			}
			else {
				return new AuthDecision(Decision.DENY, authDecision.getResource(),
						obligationAdvice.get(Type.OBLIGATION, Decision.DENY),
						obligationAdvice.get(Type.ADVICE, Decision.DENY));
			}
		}

	}

}
