package io.sapl.test.lang;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.PolicySetImplCustom;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import io.sapl.test.coverage.api.model.PolicySetHit;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class PolicySetImplCustomCoverage extends PolicySetImplCustom {
	private final CoverageHitRecorder hitRecorder;

	PolicySetImplCustomCoverage(CoverageHitRecorder recorder) {
		this.hitRecorder = recorder;
	}

	@Override
	public Mono<Val> matches(EvaluationContext ctx) {
		return super.matches(ctx).doOnNext(matches -> {
			if(matches.isBoolean() && matches.getBoolean()) {
				PolicySetHit hit = new PolicySetHit(getSaplName());
				log.trace("| | | | |-- Hit PolicySet: " + hit);
				this.hitRecorder.recordPolicySetHit(hit);
			}
		});
	}
}