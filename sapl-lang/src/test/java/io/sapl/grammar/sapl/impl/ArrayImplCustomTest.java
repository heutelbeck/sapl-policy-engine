package io.sapl.grammar.sapl.impl;

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionErrors;
import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class ArrayImplCustomTest {

	EvaluationContext ctx = mock(EvaluationContext.class);

	@Test
	public void simpleArrayToVal() throws IOException {
		var expression = ParserUtil.expression("[true,false]");
		var expected = Val.ofJson("[true,false]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void arrayPropagatesErrors() throws IOException {
		var expression = ParserUtil.expression("[true,(1/0)]");
		expressionErrors(ctx, expression);
	}

	@Test
	public void emptyArray() throws IOException {
		var expression = ParserUtil.expression("[]");
		var expected = Val.ofJson("[]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void dropsUndefined() throws IOException {
		var expression = ParserUtil.expression("[true,undefined,false,undefined]");
		var expected = Val.ofJson("[true,false]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

}
