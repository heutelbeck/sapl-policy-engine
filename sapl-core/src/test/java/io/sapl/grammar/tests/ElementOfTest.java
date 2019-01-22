package io.sapl.grammar.tests;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Array;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.ElementOf;
import io.sapl.grammar.sapl.Equals;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import org.junit.Test;

import java.util.HashMap;

import static io.sapl.grammar.tests.BasicValueHelper.basicValueFrom;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ElementOfTest {

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static EvaluationContext ctx = new EvaluationContext(null, null, null, new HashMap<>());

	@Test
	public void testEvaluateElementOfWithNullLeftAndEmptyArrayIsFalse() throws PolicyEvaluationException {
		ElementOf elementOf = factory.createElementOf();
		elementOf.setRight(basicValueFrom(factory.createArray()));
		elementOf.setLeft(basicValueFrom(factory.createNullLiteral()));

		JsonNode result = elementOf.evaluate(ctx, false, null);
		assertFalse(result.asBoolean());
	}

	@Test
	public void testEvaluateElementOfWithNullLeftAndArrayWithNullElementIsTrue() throws PolicyEvaluationException {
		ElementOf elementOf = factory.createElementOf();
		Array array = factory.createArray();
		array.getItems().add(basicValueFrom(factory.createNullLiteral()));
		elementOf.setRight(basicValueFrom(array));
		elementOf.setLeft(basicValueFrom(factory.createNullLiteral()));

		JsonNode result = elementOf.evaluate(ctx, false, null);
		assertTrue(result.asBoolean());
	}

}
