package io.sapl.grammar.tests;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.BasicFunction;
import io.sapl.grammar.sapl.BasicGroup;
import io.sapl.grammar.sapl.BasicIdentifier;
import io.sapl.grammar.sapl.BasicRelative;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;

public class SAPLEvaluationBasicExpressionsTest {
	private static final String EXCEPTION = "EXCEPTION";
	private static final String PARAMETERS = "PARAMETERS";
	private static final String LONG = "long";
	private static final String SHORT = "short";
	private static final String KEY_ANOTHER = "another key";
	private static final String KEY = "key";

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;
	
	private static VariableContext variableCtx = new VariableContext();
	private static FunctionContext functionCtx = new MockFunctionContext();
	private static EvaluationContext ctx;
	
	@Before
	public void prepare() throws PolicyEvaluationException {
		Map<String, String> imports = new HashMap<>();
		imports.put(SHORT, LONG);
		ctx = new EvaluationContext(null, functionCtx, variableCtx, imports);
		variableCtx.put(KEY, JSON.booleanNode(true));
	}
	
	@Test
	public void evaluateBasicValue() throws PolicyEvaluationException {
		BasicValue expression = factory.createBasicValue();
		expression.setValue(factory.createNullLiteral());

		JsonNode result = expression.evaluate(ctx, true, null);

		assertEquals("BasicValueExpression with NullLiteral should evaluate to NullNode", JSON.nullNode(), result);
	}
	
	@Test
	public void evaluateBasicIdentifierExisting() throws PolicyEvaluationException {
		BasicIdentifier expression = factory.createBasicIdentifier();
		expression.setIdentifier(KEY);
		
		JsonNode result = expression.evaluate(ctx, true, null);

		assertEquals("BasicIdentifierExpression should return the corresponding variable value", JSON.booleanNode(true), result);
	}
	
	@Test(expected = PolicyEvaluationException.class)
	public void evaluateBasicIdentifierNonExisting() throws PolicyEvaluationException {
		BasicIdentifier expression = factory.createBasicIdentifier();
		expression.setIdentifier(KEY_ANOTHER);
		
		expression.evaluate(ctx, true, null);
	}
	
	@Test
	public void evaluateBasicRelative() throws PolicyEvaluationException {
		BasicRelative expression = factory.createBasicRelative();
		
		JsonNode result = expression.evaluate(ctx, true, JSON.nullNode());

		assertEquals("BasicRelativeExpression without selection steps should evaluate to relative node", JSON.nullNode(), result);
	}
	
	@Test(expected = PolicyEvaluationException.class)
	public void evaluateBasicRelativeNotAllowed() throws PolicyEvaluationException {
		BasicRelative expression = factory.createBasicRelative();
		expression.evaluate(ctx, true, null);
	}

	@Test
	public void evaluateBasicGroup() throws PolicyEvaluationException {
		BasicGroup expression = factory.createBasicGroup();
		BasicValue value = factory.createBasicValue();
		value.setValue(factory.createNullLiteral());
		expression.setExpression(value);
		
		JsonNode result = expression.evaluate(ctx, true, null);

		assertEquals("BasicGroupExpression should evaluate to the result of evaluating its expression", JSON.nullNode(), result);
	}
	
	@Test
	public void evaluateBasicFunctionNoArgs1() throws PolicyEvaluationException {
		BasicFunction expression = factory.createBasicFunction();
		expression.getFsteps().add(KEY);

		JsonNode result = expression.evaluate(ctx, true, null);

		assertEquals("BasicFunctionExpression should evaluate to the result of evaluating the function",
				JSON.textNode(KEY), result);
	}

	@Test
	public void evaluateBasicFunctionNoArgs2() throws PolicyEvaluationException {
		BasicFunction expression = factory.createBasicFunction();
		expression.setArguments(factory.createArguments());
		expression.getFsteps().add(KEY);
			
		JsonNode result = expression.evaluate(ctx, true, null);
		
		assertEquals("BasicFunctionExpression should evaluate to the result of evaluating the function", JSON.textNode(KEY), result);
	}
	
	@Test
	public void evaluateBasicFunctionOneArg() throws PolicyEvaluationException {
		BasicFunction expression = factory.createBasicFunction();
		
		Arguments arguments = factory.createArguments();
		BasicValue value = factory.createBasicValue();
		value.setValue(factory.createNullLiteral());
		arguments.getArgs().add(value);
		expression.setArguments(arguments);
		expression.getFsteps().add(PARAMETERS);
			
		JsonNode result = expression.evaluate(ctx, true, null);
		
		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.nullNode());
		assertEquals("BasicFunctionExpression should evaluate to the result of evaluating the function", expectedResult, result);
	}
	
	@Test
	public void evaluateBasicFunctionTwoArgs() throws PolicyEvaluationException {
		BasicFunction expression = factory.createBasicFunction();
		
		Arguments arguments = factory.createArguments();

		BasicValue value1 = factory.createBasicValue();
		value1.setValue(factory.createNullLiteral());
		BasicValue value2 = factory.createBasicValue();
		value2.setValue(factory.createNullLiteral());

		arguments.getArgs().add(value1);
		arguments.getArgs().add(value2);
		expression.setArguments(arguments);
		expression.getFsteps().add(PARAMETERS);
			
		JsonNode result = expression.evaluate(ctx, true, null);
		
		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.nullNode());
		expectedResult.add(JSON.nullNode());
		assertEquals("BasicFunctionExpression should evaluate to the result of evaluating the function", expectedResult, result);
	}
	
	@Test
	public void evaluateBasicFunctionImport() throws PolicyEvaluationException {
		BasicFunction expression = factory.createBasicFunction();
		expression.setArguments(factory.createArguments());
		expression.getFsteps().add(SHORT);
			
		JsonNode result = expression.evaluate(ctx, true, null);
		
		assertEquals("BasicFunctionExpression should evaluate to the result of evaluating the function", JSON.textNode(LONG), result);
	}
	
	@Test(expected = PolicyEvaluationException.class)
	public void evaluateBasicFunctionException() throws PolicyEvaluationException {
		BasicFunction expression = factory.createBasicFunction();
		expression.setArguments(factory.createArguments());
		expression.getFsteps().add(EXCEPTION);
			
		expression.evaluate(ctx, true, null);
	}

}
