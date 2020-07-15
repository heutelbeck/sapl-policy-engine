/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.prp.inmemory.indexed.improved;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class ImprovedDocumentIndexTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    private Map<String, Boolean> bindings;

    private SAPLInterpreter interpreter;

    private JsonNodeFactory json;

    private ImprovedDocumentIndex prp;

    private Map<String, JsonNode> variables;

    @Before
    public void setUp() {
        bindings = new HashMap<>();
        for (String variable : getVariables()) {
            bindings.put(variable, null);
        }
        interpreter = new DefaultSAPLInterpreter();
        json = JsonNodeFactory.instance;
        prp = new ImprovedDocumentIndex();
        prp.setLiveMode();
        variables = new HashMap<>();
    }


    //TODO orphaned {3} -> false
	@Test
	public void test_orphaned() throws PolicyEvaluationException {
		// given
		FunctionContext functionCtx = new AnnotationFunctionContext();

		String def1 = "policy \"p_0\" permit !resource.x1";
		SAPL doc1 = interpreter.parse(def1);
		prp.put("1", doc1);

		String def2 = "policy \"p_1\" permit !(resource.x0 | resource.x1)";
		SAPL doc2 = interpreter.parse(def2);
		prp.put("2", doc2);

		String def3 = "policy \"p_2\" permit (resource.x1 | resource.x2)";
		SAPL doc3 = interpreter.parse(def3);
		prp.put("3", doc3);

		bindings.put("x0", false);
		bindings.put("x1", false);
        bindings.put("x2", true);

		AuthorizationSubscription authzSubscription = createRequestObject();

		// when
		PolicyRetrievalResult result = prp.retrievePolicies(authzSubscription, functionCtx, variables);

		// then
		Assertions.assertThat(result.isErrorsInTarget()).isFalse();
		Assertions.assertThat(result.getMatchingDocuments()).hasSize(3).contains(doc1, doc2);
	}

    @Test
    public void testPut() throws PolicyEvaluationException {
        // given
        FunctionContext functionCtx = new AnnotationFunctionContext();
        String definition = "policy \"p_0\" permit !(resource.x0 | resource.x1)";
        SAPL document = interpreter.parse(definition);
        prp.put("1", document);
        bindings.put("x0", false);
        bindings.put("x1", false);
        AuthorizationSubscription authzSubscription = createRequestObject();

        // when
        PolicyRetrievalResult result = prp.retrievePolicies(authzSubscription, functionCtx, variables);

        // then
        Assertions.assertThat(result.isErrorsInTarget()).isFalse();
        Assertions.assertThat(result.getMatchingDocuments()).hasSize(1).contains(document);
    }

    @Test
    public void testRemove() throws PolicyEvaluationException {
        // given
        FunctionContext functionCtx = new AnnotationFunctionContext();
        String definition = "policy \"p_0\" permit resource.x0 & resource.x1";
        SAPL document = interpreter.parse(definition);
        prp.updateFunctionContext(functionCtx);
        prp.put("1", document);
        bindings.put("x0", true);
        bindings.put("x1", true);
        prp.remove("1");
        AuthorizationSubscription authzSubscription = createRequestObject();

        // when
        PolicyRetrievalResult result = prp.retrievePolicies(authzSubscription, functionCtx, variables);

        // then
        Assertions.assertThat(result.isErrorsInTarget()).isFalse();
        Assertions.assertThat(result.getMatchingDocuments()).hasSize(0);
    }

    @Test
    public void testUpdateFunctionCtx() throws PolicyEvaluationException {
        // given
        FunctionContext functionCtx = new AnnotationFunctionContext();
        String definition = "policy \"p_0\" permit !resource.x0";
        SAPL document = interpreter.parse(definition);
        prp.put("1", document);
        bindings.put("x0", false);
        AuthorizationSubscription authzSubscription = createRequestObject();

        // when
        prp.updateFunctionContext(functionCtx);
        PolicyRetrievalResult result = prp.retrievePolicies(authzSubscription, functionCtx, variables);

        // then
        Assertions.assertThat(result.isErrorsInTarget()).isFalse();
        Assertions.assertThat(result.getMatchingDocuments()).hasSize(1).contains(document);
    }

    private AuthorizationSubscription createRequestObject() {
        ObjectNode resource = json.objectNode();
        for (Map.Entry<String, Boolean> entry : bindings.entrySet()) {
            Boolean value = entry.getValue();
            if (value != null) {
                resource.put(entry.getKey(), value);
            }
        }
        return new AuthorizationSubscription(NullNode.getInstance(), NullNode.getInstance(), resource,
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
