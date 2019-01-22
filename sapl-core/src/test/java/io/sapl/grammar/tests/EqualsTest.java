package io.sapl.grammar.tests;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.Equals;
import io.sapl.grammar.sapl.Regex;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.Value;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import org.junit.Test;

import java.util.HashMap;

import static io.sapl.grammar.tests.BasicValueHelper.basicValueFrom;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EqualsTest {

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static EvaluationContext ctx = new EvaluationContext(null, null, null, new HashMap<>());

	@Test
	public void testEvaluateEqualsWithNullLeftAndStringRightIsFalse() throws PolicyEvaluationException {
		Equals equals = factory.createEquals();
		equals.setRight(basicValueFrom(factory.createStringLiteral()));
		equals.setLeft(basicValueFrom(factory.createNullLiteral()));

		JsonNode result = equals.evaluate(ctx, false, null);
		assertFalse(result.asBoolean());
	}

	@Test
	public void testEvaluateEqualsWithNullLeftAndNullRightIsTrue() throws PolicyEvaluationException {
		Equals equals = factory.createEquals();
		equals.setRight(basicValueFrom(factory.createNullLiteral()));
		equals.setLeft(basicValueFrom(factory.createNullLiteral()));

		JsonNode result = equals.evaluate(ctx, false, null);
		assertTrue(result.asBoolean());
	}

}
