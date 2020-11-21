package io.sapl.grammar.sapl.impl;

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionErrors;
import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionEvaluatesTo;

import org.junit.Test;

import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.EvaluationContext;

public class ArrayImplCustomTest {

	private final static EvaluationContext CTX = MockUtil.constructTestEnvironmentPdpScopedEvaluationContext();

	@Test
	public void simpleArrayToVal() {
		var expression = "[true,false]";
		var expected = "[true,false]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void arrayPropagatesErrors() {
		expressionErrors(CTX, "[true,(1/0)]");
	}

	@Test
	public void emptyArray() {
		expressionEvaluatesTo(CTX, "[]", "[]");
	}

	@Test
	public void dropsUndefined() {
		var expression = "[true,undefined,false,undefined]";
		var expected = "[true,false]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

}
