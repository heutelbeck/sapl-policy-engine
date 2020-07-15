/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.grammar.tests;

import static io.sapl.grammar.tests.BasicValueHelper.basicValueFrom;
import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.grammar.sapl.Array;
import io.sapl.grammar.sapl.NumberLiteral;
import io.sapl.grammar.sapl.Pair;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.StringLiteral;
import io.sapl.grammar.sapl.Value;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.grammar.sapl.impl.Val;
import io.sapl.interpreter.EvaluationContext;

public class EvaluateLiteralsValuesTest {

	private static final String TEST_STRING = "a test string";

	private static final BigDecimal TEST_NUMBER = BigDecimal.valueOf(100.50);

	private static final String PAIR1_KEY = "pair1 key";

	private static final String PAIR2_KEY = "pair2 key";

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static EvaluationContext ctx = new EvaluationContext();

	@Test
	public void evaluateNullLiteral() {
		Value value = factory.createNullLiteral();
		value.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("NullLiteral should evaluate to NullNode", Val.ofNull(), result));
	}

	@Test
	public void evaluateTrueLiteral() {
		Value value = factory.createTrueLiteral();
		value.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("TrueLiteral should evaluate to BooleanNode(true)", Val.ofTrue(), result));
	}

	@Test
	public void evaluateFalseLiteral() {
		Value value = factory.createFalseLiteral();
		value.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("FalseLiteral should evaluate to BooleanNode(false)", Val.ofFalse(), result));
	}

	@Test
	public void evaluateStringLiteral() {
		StringLiteral literal = factory.createStringLiteral();
		literal.setString(TEST_STRING);

		literal.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("String should evaluate to TextNode", Val.of(TEST_STRING), result));
	}

	@Test
	public void evaluateNumberLiteral() {
		NumberLiteral literal = factory.createNumberLiteral();
		literal.setNumber(TEST_NUMBER);

		literal.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("NumberLiteral should evaluate to ValueNode", Val.of(TEST_NUMBER), result));
	}

	@Test
	public void evaluateEmptyObject() {
		io.sapl.grammar.sapl.Object saplObject = factory.createObject();
		saplObject.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("Empty Object should evaluate to ObjectNode",
						Val.of(JSON.objectNode()), result));
	}

	@Test
	public void evaluateObject() {
		io.sapl.grammar.sapl.Object saplObject = factory.createObject();

		Pair pair1 = factory.createPair();
		pair1.setKey(PAIR1_KEY);
		pair1.setValue(basicValueFrom(factory.createNullLiteral()));
		saplObject.getMembers().add(pair1);

		Pair pair2 = factory.createPair();
		pair2.setKey(PAIR2_KEY);
		pair2.setValue(basicValueFrom(factory.createTrueLiteral()));
		saplObject.getMembers().add(pair2);

		ObjectNode expectedResult = JSON.objectNode();
		expectedResult.set(PAIR1_KEY, JSON.nullNode());
		expectedResult.set(PAIR2_KEY, JSON.booleanNode(true));

		saplObject.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("Object should evaluate to ObjectNode", Val.of(expectedResult), result));
	}

	@Test
	public void evaluateEmptyArray() {
		Array saplArray = factory.createArray();
		saplArray.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("Empty Array should evaluate to ArrayNode", Val.of(JSON.arrayNode()), result));
	}

	@Test
	public void evaluateArray() {
		Array saplArray = factory.createArray();

		saplArray.getItems().add(basicValueFrom(factory.createNullLiteral()));
		saplArray.getItems().add(basicValueFrom(factory.createTrueLiteral()));
		saplArray.getItems().add(basicValueFrom(factory.createFalseLiteral()));

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.nullNode());
		expectedResult.add(JSON.booleanNode(true));
		expectedResult.add(JSON.booleanNode(false));

		saplArray.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("Array should evaluate to Array", Val.of(expectedResult), result));
	}

}
