package io.sapl.grammar.sapl.impl;

import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

@RunWith(MockitoJUnitRunner.class)
public class DivImplCustomTest {

	EvaluationContext ctx = mock(EvaluationContext.class);

	@Test
	public void evaluationShouldFailWithNonNumberLeft() throws IOException {
		var expression = ParserUtil.expression("null/10");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED).log()).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void evaluationShouldFailWithNonNumberRight() throws IOException {
		var expression = ParserUtil.expression("10/null");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED).log()).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void evaluationShouldFailDivisionByZero() throws IOException {
		var expression = ParserUtil.expression("10/0");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED).log()).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void evaluationSucceed() throws IOException {
		var expression = ParserUtil.expression("10/2");
		var expected = Val.of(5);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED).log()).expectNext(expected).verifyComplete();
	}

}
