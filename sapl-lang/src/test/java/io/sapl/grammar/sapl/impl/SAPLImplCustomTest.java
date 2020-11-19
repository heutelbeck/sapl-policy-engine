/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl;

import org.junit.Test;

import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class SAPLImplCustomTest {

	private final static EvaluationContext CTX = MockUtil.constructTestEnvironmentEvaluationContext();
	private final static SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	@Test
	public void detectErrorInTargetMatches() {
		var policy = INTERPRETER.parse("policy \"policy\" permit (10/0)");
		StepVerifier.create(policy.matches(CTX)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void detectErrorInTargetEvaluate() {
		var policy = INTERPRETER.parse("policy \"policy\" permit (10/0)");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(CTX)).expectNext(expected).verifyComplete();
	}

}
