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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import reactor.test.StepVerifier;

public class PolicyBodyImplCustomCoverageTest {

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
	void trueReturnsEntitlement() {
		var policy = INTERPRETER.parse("policy \"p\" permit true where true; true; true;");
		var expected = AuthorizationDecision.PERMIT;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();

		ArgumentCaptor<PolicyConditionHit> captor = ArgumentCaptor.forClass(PolicyConditionHit.class);
		Mockito.verify(this.recorder, Mockito.times(3)).recordPolicyConditionHit(captor.capture());
		Assertions.assertThat(captor.getAllValues().get(0).getConditionStatementId()).isEqualTo(0);
		Assertions.assertThat(captor.getAllValues().get(1).getConditionStatementId()).isEqualTo(1);
		Assertions.assertThat(captor.getAllValues().get(2).getConditionStatementId()).isEqualTo(2);
	}

	@Test
	void trueReturnsEntitlementInSet() {
		var policy = INTERPRETER.parse("set \"set\" deny-overrides policy \"p\" permit true where true; true; true;");
		var expected = AuthorizationDecision.PERMIT;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
		
		ArgumentCaptor<PolicyConditionHit> captor = ArgumentCaptor.forClass(PolicyConditionHit.class);
		Mockito.verify(this.recorder, Mockito.times(3)).recordPolicyConditionHit(captor.capture());
		Assertions.assertThat(captor.getAllValues().get(0).getConditionStatementId()).isEqualTo(0);
		Assertions.assertThat(captor.getAllValues().get(1).getConditionStatementId()).isEqualTo(1);
		Assertions.assertThat(captor.getAllValues().get(2).getConditionStatementId()).isEqualTo(2);
	}
	
	@Test
	void test_evaluateConditionThrowsError() {
		var policy = INTERPRETER.parse("set \"set\" deny-overrides policy \"p\" permit true where true == subject.<pip.attr>; true; true;");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
		Mockito.verify(this.recorder, Mockito.never()).recordPolicyConditionHit(Mockito.any());
	}
}
