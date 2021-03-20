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
package io.sapl.grammar.sapl.impl;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.EObjectUtil;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.SAPLInterpreter;
import reactor.test.StepVerifier;

class PolicyElementImplCustomTest {

	private final static EvaluationContext CTX = MockUtil.constructTestEnvironmentPdpScopedEvaluationContext();
	private final static SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	@Test
	void emptyTargetMatches() {
		var policy = INTERPRETER.parse("policy \"p\" permit");
		StepVerifier.create(policy.matches(CTX)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	void undefinedTargetErrors() {
		var policy = INTERPRETER.parse("policy \"p\" permit undefined");
		EObjectUtil.dump(policy);
		StepVerifier.create(policy.matches(CTX)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	void errorTargetErrors() {
		var policy = INTERPRETER.parse("policy \"p\" permit (10/0)");
		StepVerifier.create(policy.matches(CTX)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	void nonBooleanTargetErrors() {
		var policy = INTERPRETER.parse("policy \"p\" permit \"abc\"");
		StepVerifier.create(policy.matches(CTX)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	void falseTargetDosNotMatch() {
		var policy = INTERPRETER.parse("policy \"p\" permit false");
		StepVerifier.create(policy.matches(CTX)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	void trueTargetDosMatch() {
		var policy = INTERPRETER.parse("policy \"p\" permit true");
		StepVerifier.create(policy.matches(CTX)).expectNext(Val.TRUE).verifyComplete();
	}

}
