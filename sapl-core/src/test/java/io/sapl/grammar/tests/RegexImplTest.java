package io.sapl.grammar.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.Regex;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.Value;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertFalse;

public class RegexImplTest {

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static EvaluationContext ctx = new EvaluationContext(null, null, null, new HashMap<>());

	@Test
	public void testEvaluateRegexWithNullLeft() throws PolicyEvaluationException {
		Regex regex = factory.createRegex();
		regex.setRight(basicValueFrom(factory.createStringLiteral()));
		regex.setLeft(basicValueFrom(factory.createNullLiteral()));

		JsonNode result = regex.evaluate(ctx, false, null);
		assertFalse(result.asBoolean());
	}

	private static BasicValue basicValueFrom(Value value) {
		BasicValue basicValue = factory.createBasicValue();
		basicValue.setValue(value);
		return basicValue;
	}
}
