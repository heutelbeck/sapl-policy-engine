package io.sapl.test.lang;

import org.eclipse.emf.ecore.EObject;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.impl.PolicyImplCustom;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import io.sapl.test.coverage.api.model.PolicyHit;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class PolicyImplCustomCoverage extends PolicyImplCustom {

	private final CoverageHitRecorder hitRecorder;

	PolicyImplCustomCoverage(CoverageHitRecorder recorder) {
		this.hitRecorder = recorder;
	}

	@Override
	public Mono<Val> matches(EvaluationContext ctx) {
		//if this is the policy under unit test return "normal" result
		return super.matches(ctx).doOnNext(matches -> {
			//and record policy hit if policy matches
			if(matches.isBoolean() && matches.getBoolean()) {
				String policySetId = "";
				EObject eContainer = eContainer();
				if(eContainer.eClass().equals(SaplPackage.Literals.POLICY_SET)) {
					policySetId = ((PolicySet) eContainer()).getSaplName();
				}
				PolicyHit hit = new PolicyHit(policySetId, getSaplName());
				log.trace("Hit Policy: " + hit);
				this.hitRecorder.recordPolicyHit(hit);
			}
		});
		
	}
}
