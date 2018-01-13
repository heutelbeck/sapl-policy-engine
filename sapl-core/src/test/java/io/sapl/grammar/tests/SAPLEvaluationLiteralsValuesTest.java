package io.sapl.grammar.tests;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.HashMap;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Array;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.NumberLiteral;
import io.sapl.grammar.sapl.Pair;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.StringLiteral;
import io.sapl.grammar.sapl.Value;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;

public class SAPLEvaluationLiteralsValuesTest {
	private static final String TEST_STRING = "a test string";
	private static final BigDecimal TEST_NUMBER = BigDecimal.valueOf(100.50);
	private static final String PAIR1_KEY = "pair1 key";
	private static final String PAIR2_KEY = "pair2 key";

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static EvaluationContext ctx = new EvaluationContext(null, null, null, new HashMap<>());

	@Test
	public void evaluateNullLiteral() throws PolicyEvaluationException {
		Value value = factory.createNullLiteral();
		JsonNode result = value.evaluate(ctx, true, null);

		assertEquals("NullLiteral should evaluate to NullNode", JSON.nullNode(), result);
	}

	@Test
	public void evaluateTrueLiteral() throws PolicyEvaluationException {
		Value value = factory.createTrueLiteral();
		JsonNode result = value.evaluate(ctx, true, null);

		assertEquals("TrueLiteral should evaluate to BooleanNode(true)", JSON.booleanNode(true), result);
	}

	@Test
	public void evaluateFalseLiteral() throws PolicyEvaluationException {
		Value value = factory.createFalseLiteral();
		JsonNode result = value.evaluate(ctx, true, null);

		assertEquals("FalseLiteral should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	@Test
	public void evaluateStringLiteral() throws PolicyEvaluationException {
		StringLiteral literal = factory.createStringLiteral();
		literal.setString(TEST_STRING);

		JsonNode result = literal.evaluate(ctx, true, null);

		assertEquals("String should evaluate to TextNode", JSON.textNode(TEST_STRING), result);
	}

	@Test
	public void evaluateNumberLiteral() throws PolicyEvaluationException {
		NumberLiteral literal = factory.createNumberLiteral();
		literal.setNumber(TEST_NUMBER);

		JsonNode result = literal.evaluate(ctx, true, null);

		assertEquals("NumberLiteral should evaluate to ValueNode", JSON.numberNode(TEST_NUMBER), result);
	}

	@Test
	public void evaluateEmptyObject() throws PolicyEvaluationException {
		io.sapl.grammar.sapl.Object saplObject = factory.createObject();
		JsonNode result = saplObject.evaluate(ctx, true, null);

		assertEquals("Empty Object should evaluate to ObjectNode", JSON.objectNode(), result);
	}

	@Test
	public void evaluateObject() throws PolicyEvaluationException {
		io.sapl.grammar.sapl.Object saplObject = factory.createObject();

		Pair pair1 = factory.createPair();
		pair1.setKey(PAIR1_KEY);
		pair1.setValue(basicValueOf(factory.createNullLiteral()));
		saplObject.getMembers().add(pair1);

		Pair pair2 = factory.createPair();
		pair2.setKey(PAIR2_KEY);
		pair2.setValue(basicValueOf(factory.createTrueLiteral()));
		saplObject.getMembers().add(pair2);

		JsonNode result = saplObject.evaluate(ctx, true, null);

		ObjectNode expectedResult = JSON.objectNode();
		expectedResult.set(PAIR1_KEY, JSON.nullNode());
		expectedResult.set(PAIR2_KEY, JSON.booleanNode(true));

		assertEquals("Object should evaluate to ObjectNode", expectedResult, result);
	}

	@Test
	public void evaluateEmptyArray() throws PolicyEvaluationException {
		Array saplArray = factory.createArray();
		JsonNode result = saplArray.evaluate(ctx, true, null);

		assertEquals("Empty Array should evaluate to ArrayNode", JSON.arrayNode(), result);
	}

	@Test
	public void evaluateArray() throws PolicyEvaluationException {
		Array saplArray = factory.createArray();

		saplArray.getItems().add(basicValueOf(factory.createNullLiteral()));
		saplArray.getItems().add(basicValueOf(factory.createTrueLiteral()));
		saplArray.getItems().add(basicValueOf(factory.createFalseLiteral()));

		JsonNode result = saplArray.evaluate(ctx, true, null);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.nullNode());
		expectedResult.add(JSON.booleanNode(true));
		expectedResult.add(JSON.booleanNode(false));

		assertEquals("Array should evaluate to Array", expectedResult, result);
	}

	private static BasicValue basicValueOf(Value value) {
		BasicValue basicValue = factory.createBasicValue();
		basicValue.setValue(value);
		return basicValue;
	}

}
