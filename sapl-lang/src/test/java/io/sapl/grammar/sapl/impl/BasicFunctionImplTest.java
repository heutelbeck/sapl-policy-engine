package io.sapl.grammar.sapl.impl;

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionErrors;
import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionEvaluatesTo;

import org.junit.Test;

import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.EvaluationContext;

public class BasicFunctionImplTest {
	private static EvaluationContext CTX = MockUtil.constructTestEnvironmentEvaluationContext();

	@Test
	public void basicSuccessfullEvaluationNull() {
		expressionEvaluatesTo(CTX, "mock.nil()", "null");
	}

	@Test
	public void basicSuccessfullEvaluationError() {
		expressionErrors(CTX, "mock.error()");
	}

	@Test
	public void basicSuccessfullEvaluationExceptionToError() {
		expressionErrors(CTX, "mock.exception()");
	}
}
