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

import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SaplFactoryImplCoverage extends SaplFactoryImpl {

	private final CoverageHitRecorder recorder;

	public SaplFactoryImplCoverage(CoverageHitRecorder recorder) {
		this.recorder = recorder;
	}

	@Override
	public PolicySet createPolicySet() {
		log.trace("Creating PolicySet Subclass for test mode");
		return new PolicySetImplCustomCoverage(this.recorder);
	}

	@Override
	public Policy createPolicy() {
		log.trace("Creating Policy Subclass for test mode");
		return new PolicyImplCustomCoverage(this.recorder);
	}

	@Override
	public PolicyBody createPolicyBody() {
		log.trace("Creating PolicyBody Subclass for test mode");
		return new PolicyBodyImplCustomCoverage(this.recorder);
	}

}