package io.sapl.prp.inmemory.indexed;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import com.anarsoft.vmlens.concurrent.junit.ConcurrentTestRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.Request;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;

@RunWith(ConcurrentTestRunner.class)
public class ConcurrencyTest {

	@Rule
	public Timeout globalTimeout = Timeout.seconds(60);

	private SAPLInterpreter interpreter;

	private JsonNodeFactory json;

	private FastParsedDocumentIndex prp;

	private Map<String, JsonNode> variables;

	@Before
	public void setUp() {
		interpreter = new DefaultSAPLInterpreter();
		json = JsonNodeFactory.instance;
		prp = new FastParsedDocumentIndex();
		prp.setLiveMode();
		variables = new HashMap<>();
	}

	@Test
	public void testConcurrency() throws PolicyEvaluationException {
		for (int i = 0; i < 10; ++i) {
			testUpdateFunctionCtx();
			testPut();
			testRemove();
		}
	}

	@Test
	public void testPut() throws PolicyEvaluationException {
		// given
		FunctionContext functionCtx = new AnnotationFunctionContext();
		String definition = "policy \"p_0\" permit (resource.x0 | !resource.x1)";
		SAPL document = interpreter.parse(definition);
		prp.put("1", document);
		Map<String, Boolean> bindings = createBindings();
		bindings.put("x0", true);
		bindings.put("x1", false);
		Request request = createRequestObject(bindings);

		// when
		PolicyRetrievalResult result = prp.retrievePolicies(request, functionCtx,
				variables);

		// then
		Assertions.assertThat(result).isNotNull();
	}

	@Test
	public void testRemove() throws PolicyEvaluationException {
		// given
		FunctionContext functionCtx = new AnnotationFunctionContext();
		String definition = "policy \"p_0\" permit resource.x0 & !resource.x1";
		SAPL document = interpreter.parse(definition);
		prp.updateFunctionContext(functionCtx);
		prp.put("1", document);
		Map<String, Boolean> bindings = createBindings();
		bindings.put("x0", true);
		bindings.put("x1", false);
		prp.remove("1");
		Request request = createRequestObject(bindings);

		// when
		PolicyRetrievalResult result = prp.retrievePolicies(request, functionCtx,
				variables);

		// then
		Assertions.assertThat(result).isNotNull();
	}

	@Test
	public void testUpdateFunctionCtx() throws PolicyEvaluationException {
		// given
		FunctionContext functionCtx = new AnnotationFunctionContext();
		String definition = "policy \"p_0\" permit resource.x0";
		SAPL document = interpreter.parse(definition);
		prp.put("1", document);
		Map<String, Boolean> bindings = createBindings();
		bindings.put("x0", true);
		Request request = createRequestObject(bindings);

		// when
		prp.updateFunctionContext(functionCtx);
		PolicyRetrievalResult result = prp.retrievePolicies(request, functionCtx,
				variables);

		// then
		Assertions.assertThat(result).isNotNull();
	}

	private Map<String, Boolean> createBindings() {
		Map<String, Boolean> result = new HashMap<>();
		for (String variable : getVariables()) {
			result.put(variable, null);
		}
		return result;
	}

	private Request createRequestObject(Map<String, Boolean> assignments) {
		ObjectNode resource = json.objectNode();
		for (Map.Entry<String, Boolean> entry : assignments.entrySet()) {
			Boolean value = entry.getValue();
			if (value != null) {
				resource.put(entry.getKey(), value);
			}
		}
		return new Request(NullNode.getInstance(), NullNode.getInstance(), resource,
				NullNode.getInstance());
	}

	private static Set<String> getVariables() {
		HashSet<String> variables = new HashSet<>();
		for (int i = 0; i < 10; ++i) {
			variables.add("x" + i);
		}
		return variables;
	}

}
