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
}
