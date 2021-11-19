/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.test.lang;

import java.util.function.Function;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.impl.PolicyBodyImplCustom;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import io.sapl.test.coverage.api.model.PolicyConditionHit;

import org.eclipse.emf.ecore.EObject;
import org.reactivestreams.Publisher;

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
		return super.evaluateStatements(statementId);
	}

	@Override
	protected Flux<Tuple2<Val, EvaluationContext>> evaluateCondition(Val previousResult, Condition condition,
			EvaluationContext ctx) {
		return super.evaluateCondition(previousResult, condition, ctx).doOnNext(result -> {
			if (result.getT1().isBoolean()) {
				String policySetId = "";
				String policyId = "";
				EObject eContainer1 = eContainer();
				// A PolicyBody outside of a Policy is not allowed -> thus a pre-cast if
				// statement like the following
				// if (eContainer1.eClass().equals(SaplPackage.Literals.POLICY)) {
				// cannot be false -> cannot be tested
				policyId = ((Policy) eContainer1).getSaplName();
				EObject eContainer2 = eContainer1.eContainer();
				if (eContainer2.eClass().equals(SaplPackage.Literals.POLICY_SET)) {
					policySetId = ((PolicySet) eContainer2).getSaplName();
				}
				// because of implementation of super method and switchMap -> this is
				// executed on the actual statementId-1
				int actualStatementId = this.currentStatementId - 1;
				PolicyConditionHit hit = new PolicyConditionHit(policySetId, policyId, actualStatementId,
						result.getT1().getBoolean());
				log.trace("| | | | |-- Hit PolicyCondition: " + hit);
				this.hitRecorder.recordPolicyConditionHit(hit);
			}
		});
	}

}
