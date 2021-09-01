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

public class PolicyImplCustomCoverageTest {

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
	void test_Match() {
		var policy = INTERPRETER.parse("policy \"p\" permit action == \"read\"");
		AuthorizationSubscription authzSub = AuthorizationSubscription.of("willi", "read", "something");
		Assertions.assertThat(policy.matches(ctx.forAuthorizationSubscription(authzSub)).block().getBoolean()).isTrue();
		Mockito.verify(this.recorder, Mockito.times(1)).recordPolicyHit(Mockito.isA(PolicyHit.class));
	}
	
	
	@Test
	void test_NotMatching() {
		var policy = INTERPRETER.parse("policy \"p\" permit action == \"read\"");
		AuthorizationSubscription authzSub = AuthorizationSubscription.of("willi", "write", "something");
		Assertions.assertThat(policy.matches(ctx.forAuthorizationSubscription(authzSub)).block().getBoolean()).isFalse();
		Mockito.verify(this.recorder, Mockito.never()).recordPolicyHit(Mockito.isA(PolicyHit.class));
	}
	
	@Test
	void test_matchesThrowsError() {
		var policy = INTERPRETER.parse("policy \"p\" permit action.<pip.attr> == \"test\"");
		AuthorizationSubscription authzSub = AuthorizationSubscription.of("willi", "write", "something");
		Assertions.assertThat(policy.matches(ctx.forAuthorizationSubscription(authzSub)).block().isBoolean()).isFalse();
		Mockito.verify(this.recorder, Mockito.never()).recordPolicyHit(Mockito.isA(PolicyHit.class));
	}

	
}
