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

import java.util.HashMap;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

public class PolicySetImplCustomCoverageTest {

	CoverageHitRecorder recorder;

	private SAPLInterpreter INTERPRETER;

	private EvaluationContext ctx;

	@BeforeEach
	void setup() {
		this.recorder = Mockito.mock(CoverageHitRecorder.class);
		this.INTERPRETER = new TestSaplInterpreter(this.recorder);
		var attributeCtx = new AnnotationAttributeContext();
		var functionCtx = new AnnotationFunctionContext();
		ctx = new EvaluationContext(attributeCtx, functionCtx, new HashMap<>());
	}

	@Test
	void test_match() {
		var policy = INTERPRETER.parse("set \"set\" deny-overrides for action == \"read\" policy \"set.p1\" permit");
		AuthorizationSubscription authzSub = AuthorizationSubscription.of("willi", "read", "something");
		Assertions.assertThat(policy.matches(ctx.forAuthorizationSubscription(authzSub)).block().getBoolean()).isTrue();
		Mockito.verify(this.recorder, Mockito.times(1)).recordPolicySetHit(Mockito.isA(PolicySetHit.class));
	}

	@Test
	void test_NotMatching() {
		var policy = INTERPRETER.parse("set \"set\" deny-overrides for action == \"read\" policy \"set.p1\" permit");
		AuthorizationSubscription authzSub = AuthorizationSubscription.of("willi", "write", "something");
		Assertions.assertThat(policy.matches(ctx.forAuthorizationSubscription(authzSub)).block().getBoolean())
				.isFalse();
		Mockito.verify(this.recorder, Mockito.never()).recordPolicyHit(Mockito.isA(PolicyHit.class));
	}

	@Test
	void test_matchesThrowsError() {
		var policy = INTERPRETER
				.parse("set \"set\" deny-overrides for action.<pip.attr> == \"test\" policy \"set.p1\" permit");
		AuthorizationSubscription authzSub = AuthorizationSubscription.of("willi", "write", "something");
		Assertions.assertThat(policy.matches(ctx.forAuthorizationSubscription(authzSub)).block().isBoolean()).isFalse();
		Mockito.verify(this.recorder, Mockito.never()).recordPolicyHit(Mockito.isA(PolicyHit.class));
	}

}
