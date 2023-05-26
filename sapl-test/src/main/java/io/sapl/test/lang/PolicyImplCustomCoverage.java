/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.eclipse.emf.ecore.EObject;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.impl.PolicyImplCustom;
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
	public Mono<Val> matches() {
		return super.matches().doOnNext(matches -> {
			if (matches.isBoolean() && matches.getBoolean()) {
				String  policySetId = "";
				EObject eContainer  = eContainer();
				if (eContainer.eClass().equals(SaplPackage.Literals.POLICY_SET)) {
					policySetId = ((PolicySet) eContainer()).getSaplName();
				}
				PolicyHit hit = new PolicyHit(policySetId, getSaplName());
				log.trace("| | | | |-- Hit Policy: " + hit);
				this.hitRecorder.recordPolicyHit(hit);
			}
		});

	}

}
