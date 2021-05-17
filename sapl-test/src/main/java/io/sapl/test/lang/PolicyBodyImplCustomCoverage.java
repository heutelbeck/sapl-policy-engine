package io.sapl.test.lang;

import java.util.function.Function;

import org.eclipse.emf.ecore.EObject;
import org.reactivestreams.Publisher;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.impl.PolicyBodyImplCustom;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;

@Slf4j
public class PolicyBodyImplCustomCoverage extends PolicyBodyImplCustom {

	private final CoverageHitRecorder hitRecorder;

	private int currentStatementId = 0;

	PolicyBodyImplCustomCoverage(CoverageHitRecorder recorder) {
		this.hitRecorder = recorder;
	}

	@Override
	protected Function<? super Tuple2<Val, EvaluationContext>, Publisher<? extends Tuple2<Val, EvaluationContext>>> evaluateStatements(
			int statementId) {
		this.currentStatementId = statementId;
		// System.out.println("StatementId: " + statementId);
		return super.evaluateStatements(statementId);
	}

	@Override
	protected Flux<Tuple2<Val, EvaluationContext>> evaluateCondition(Val previousResult, Condition condition,
			EvaluationContext ctx) {
		return super.evaluateCondition(previousResult, condition, ctx).doOnNext(result -> {
			// record policy condition hit
			if(result.getT1().isBoolean()) {
				// System.out.println("Condition: " + condition.getExpression().toString() + " evaluated to " + result.getT1().getBoolean());
				String policySetId = "";
				String policyId = "";		
				EObject eContainer1 = eContainer();
				if(eContainer1 instanceof Policy) {
					policyId = ((Policy) eContainer1).getSaplName();
					EObject eContainer2 = eContainer1.eContainer();
					if(eContainer2 instanceof PolicySet) {
						policySetId = ((PolicySet) eContainer2).getSaplName();
					}
				}
				// because of implementation of super method and switchMap -> this is executed on the actual statementId-1 
				int actualStatementId = this.currentStatementId - 1;
				PolicyConditionHit hit = new PolicyConditionHit(policySetId, policyId, actualStatementId, result.getT1().getBoolean());
				log.trace("hit PolicyCondition: " + hit);
				this.hitRecorder.recordPolicyConditionHit(hit);
			}
		});
	}
}
