package io.sapl.test.lang;

import java.util.HashMap;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import io.sapl.test.coverage.api.model.PolicySetHit;

public class PolicySetImplCustomCoverageTest {
	
	// extends PolicySetImplCustomTest

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
	void test() {
		var policy = INTERPRETER.parse("set \"set\" deny-overrides policy \"set.p1\" permit");

		Assertions.assertThat(policy.matches(ctx).block().getBoolean()).isTrue();
		
		Mockito.verify(this.recorder, Mockito.times(1)).recordPolicySetHit(Mockito.isA(PolicySetHit.class));
	}
}
