package io.sapl.grammar.sapl.impl.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.DocumentEvaluationResult;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class CombiningAlgorithmUtil {

	public static Flux<CombinedDecision> eagerlyCombinePolicyElements(List<PolicyElement> policyElements,
			Function<DocumentEvaluationResult[], CombinedDecision> combinator, String algorithmName) {
		if (policyElements.isEmpty())
			return Flux.just(CombinedDecision.of(AuthorizationDecision.NOT_APPLICABLE, algorithmName));
		var policyDecisions = eagerPolicyElementDecisionFluxes(policyElements);
		return Flux.combineLatest(policyDecisions, decisionObjects -> combinator
				.apply(Arrays.copyOf(decisionObjects, decisionObjects.length, DocumentEvaluationResult[].class)));
	}

	private static List<Flux<DocumentEvaluationResult>> eagerPolicyElementDecisionFluxes(
			Collection<PolicyElement> policyElements) {
		var policyDecsions = new ArrayList<Flux<DocumentEvaluationResult>>(policyElements.size());
		for (var policyElement : policyElements) {
			policyDecsions.add(evaluatePolicyElementTargetAndPolicyIfApplicable(policyElement));
		}
		return policyDecsions;
	}

	private static Flux<DocumentEvaluationResult> evaluatePolicyElementTargetAndPolicyIfApplicable(
			PolicyElement policyElement) {
		var matches = policyElement.matches().map(CombiningAlgorithmUtil::requireTargetExpressionEvaluatesToBoolean);
		return matches.flatMapMany(evaluatePolicyIfApplicable(policyElement));
	}

	private static Function<Val, Flux<DocumentEvaluationResult>> evaluatePolicyIfApplicable(
			PolicyElement policyElement) {
		return targetExpressionResult -> {
			if (targetExpressionResult.isError() || !targetExpressionResult.getBoolean()) {
				return Flux.just(policyElement.targetResult(targetExpressionResult));
			}
			return policyElement.evaluate().map(result -> result.withTargetResult(targetExpressionResult));
		};
	}

	private static Val requireTargetExpressionEvaluatesToBoolean(Val targetExpressionResult) {
		if (targetExpressionResult.isBoolean())
			return targetExpressionResult;

		return Val
				.error("Type mismatch. Target expression must evaluate to Boolean. Was: %s",
						targetExpressionResult.getValType())
				.withTrace(CombiningAlgorithm.class, targetExpressionResult);
	}

}
