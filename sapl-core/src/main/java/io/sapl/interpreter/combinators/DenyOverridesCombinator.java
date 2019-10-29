package io.sapl.interpreter.combinators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.combinators.ObligationAdviceCollector.Type;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.variables.VariableContext;
import reactor.core.publisher.Flux;

public class DenyOverridesCombinator implements DocumentsCombinator, PolicyCombinator {

	@Override
	public Flux<AuthorizationDecision> combineMatchingDocuments(Collection<SAPL> matchingSaplDocuments,
			boolean errorsInTarget, AuthorizationSubscription authzSubscription, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables) {

		if (matchingSaplDocuments == null || matchingSaplDocuments.isEmpty()) {
			return errorsInTarget ? Flux.just(AuthorizationDecision.INDETERMINATE)
					: Flux.just(AuthorizationDecision.NOT_APPLICABLE);
		}

		final VariableContext variableCtx;
		try {
			variableCtx = new VariableContext(authzSubscription, systemVariables);
		}
		catch (PolicyEvaluationException e) {
			return Flux.just(AuthorizationDecision.INDETERMINATE);
		}
		final EvaluationContext evaluationCtx = new EvaluationContext(attributeCtx, functionCtx, variableCtx);

		final List<Flux<AuthorizationDecision>> authzDecisionFluxes = new ArrayList<>(matchingSaplDocuments.size());
		for (SAPL document : matchingSaplDocuments) {
			authzDecisionFluxes.add(document.evaluate(evaluationCtx));
		}

		final AuthorizationDecisionAccumulator accumulator = new AuthorizationDecisionAccumulator(errorsInTarget);
		return Flux.combineLatest(authzDecisionFluxes, authzDecisions -> {
			accumulator.addSingleDecisions(authzDecisions);
			return accumulator.getCombinedAuthorizationDecision();
		}).distinctUntilChanged();
	}

	@Override
	public Flux<AuthorizationDecision> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
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
			return errorsInTarget ? Flux.just(AuthorizationDecision.INDETERMINATE)
					: Flux.just(AuthorizationDecision.NOT_APPLICABLE);
		}

		final List<Flux<AuthorizationDecision>> authzDecisionFluxes = new ArrayList<>(matchingPolicies.size());
		for (Policy policy : matchingPolicies) {
			authzDecisionFluxes.add(policy.evaluate(ctx));
		}
		final AuthorizationDecisionAccumulator accumulator = new AuthorizationDecisionAccumulator(errorsInTarget);
		return Flux.combineLatest(authzDecisionFluxes, authzDecisions -> {
			accumulator.addSingleDecisions(authzDecisions);
			return accumulator.getCombinedAuthorizationDecision();
		}).distinctUntilChanged();
	}

	private static class AuthorizationDecisionAccumulator {

		private boolean errorsInTarget;

		private AuthorizationDecision authzDecision;

		private int permitCount;

		private boolean transformation;

		private ObligationAdviceCollector obligationAdvice;

		AuthorizationDecisionAccumulator(boolean errorsInTarget) {
			this.errorsInTarget = errorsInTarget;
			init();
		}

		private void init() {
			permitCount = 0;
			transformation = false;
			obligationAdvice = new ObligationAdviceCollector();
			authzDecision = errorsInTarget ? AuthorizationDecision.INDETERMINATE : AuthorizationDecision.NOT_APPLICABLE;
		}

		void addSingleDecisions(Object... authzDecisions) {
			init();
			for (Object decision : authzDecisions) {
				addSingleDecision((AuthorizationDecision) decision);
			}
		}

		private void addSingleDecision(AuthorizationDecision newAuthzDecision) {
			Decision newDecision = newAuthzDecision.getDecision();
			if (newDecision == Decision.DENY) {
				obligationAdvice.add(Decision.DENY, newAuthzDecision);
				authzDecision = AuthorizationDecision.DENY;
			}
			else if (newDecision == Decision.INDETERMINATE && authzDecision.getDecision() != Decision.DENY) {
				authzDecision = AuthorizationDecision.INDETERMINATE;
			}
			else if (newDecision == Decision.PERMIT) {
				permitCount += 1;
				if (newAuthzDecision.getResource().isPresent()) {
					transformation = true;
				}

				obligationAdvice.add(Decision.PERMIT, newAuthzDecision);
				if (authzDecision.getDecision() != Decision.DENY
						&& authzDecision.getDecision() != Decision.INDETERMINATE) {
					authzDecision = newAuthzDecision;
				}
			}
		}

		AuthorizationDecision getCombinedAuthorizationDecision() {
			if (authzDecision.getDecision() == Decision.PERMIT) {
				if (permitCount > 1 && transformation) {
					return AuthorizationDecision.INDETERMINATE;
				}
				else {
					return new AuthorizationDecision(Decision.PERMIT, authzDecision.getResource(),
							obligationAdvice.get(Type.OBLIGATION, Decision.PERMIT),
							obligationAdvice.get(Type.ADVICE, Decision.PERMIT));
				}
			}
			else if (authzDecision.getDecision() == Decision.DENY) {
				return new AuthorizationDecision(Decision.DENY, authzDecision.getResource(),
						obligationAdvice.get(Type.OBLIGATION, Decision.DENY),
						obligationAdvice.get(Type.ADVICE, Decision.DENY));
			}
			else {
				return authzDecision;
			}
		}

	}

}
