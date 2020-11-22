/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.reimpl.prp.index.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.prp.inmemory.indexed.improved.ordering.NoPredicateOrderStrategy;
import io.sapl.reimpl.prp.ImmutableParsedDocumentIndex;
import io.sapl.reimpl.prp.PrpUpdateEvent;
import io.sapl.reimpl.prp.PrpUpdateEvent.Type;
import io.sapl.reimpl.prp.PrpUpdateEvent.Update;

public class CanonicalImmutableParsedDocumentIndexTest {
	private static final EvaluationContext PDP_SCOPED_EVALUATION_CONTEXT = new EvaluationContext(
			new AnnotationAttributeContext(), new AnnotationFunctionContext(), new HashMap<>());

	@Rule
	public Timeout globalTimeout = Timeout.seconds(3);

	private Map<String, Boolean> bindings;

	private SAPLInterpreter interpreter;

	private JsonNodeFactory json;

	private CanonicalImmutableParsedDocumentIndex emptyIndex;

	private Map<String, JsonNode> variables;

	@Before
	public void setUp() {
		bindings = new HashMap<>();
		for (String variable : getVariables()) {
			bindings.put(variable, null);
		}
		interpreter = new DefaultSAPLInterpreter();
		json = JsonNodeFactory.instance;
		emptyIndex = new CanonicalImmutableParsedDocumentIndex(PDP_SCOPED_EVALUATION_CONTEXT);
		variables = new HashMap<>();
	}

	@Test
	public void test_return_empty_result_when_error_occurs() throws Exception {
		InputStream resourceAsStream = getClass().getClassLoader()
				.getResourceAsStream("it/error/policy_with_error.sapl");
		SAPL withError = interpreter.parse(resourceAsStream);

		List<Update> updates = new ArrayList<>(3);

		updates.add(new Update(Type.PUBLISH, withError, withError.toString()));

		PrpUpdateEvent prpUpdateEvent = new PrpUpdateEvent(updates);
		ImmutableParsedDocumentIndex updatedIndex = emptyIndex.apply(prpUpdateEvent);

		AuthorizationSubscription authzSubscription = createRequestObject();
		var subscriptionScopedEvaluationCtx = new EvaluationContext(new AnnotationAttributeContext(),
				new AnnotationFunctionContext(), variables);
		subscriptionScopedEvaluationCtx = subscriptionScopedEvaluationCtx
				.forAuthorizationSubscription(authzSubscription);
		// when
		PolicyRetrievalResult result = updatedIndex.retrievePolicies(subscriptionScopedEvaluationCtx).block();

		// then
		assertThat(result).isNotNull();
		assertThat(result.getMatchingDocuments()).isEmpty();
		assertThat(result.isErrorsInTarget()).isTrue();
	}

	// Test must be repeated a couple of times to test implementation of
	// "findOrphanedCandidates"
	@Test
	public void test_orphaned() {
		// given
		emptyIndex = new CanonicalImmutableParsedDocumentIndex(new NoPredicateOrderStrategy(),
				PDP_SCOPED_EVALUATION_CONTEXT);
		List<Update> updates = new ArrayList<>(3);

		String def1 = "policy \"p_0\" permit !resource.x1";
		SAPL doc1 = interpreter.parse(def1);
		updates.add(new Update(Type.PUBLISH, doc1, def1));

		String def2 = "policy \"p_1\" permit !(resource.x0 | resource.x1)";
		SAPL doc2 = interpreter.parse(def2);
		updates.add(new Update(Type.PUBLISH, doc2, def2));

		String def3 = "policy \"p_2\" permit (resource.x1 | resource.x2)";
		SAPL doc3 = interpreter.parse(def3);
		updates.add(new Update(Type.PUBLISH, doc3, def3));

		PrpUpdateEvent prpUpdateEvent = new PrpUpdateEvent(updates);
		ImmutableParsedDocumentIndex updatedIndex = emptyIndex.apply(prpUpdateEvent);

		bindings.put("x0", false);
		bindings.put("x1", false);
		bindings.put("x2", true);

		AuthorizationSubscription authzSubscription = createRequestObject();
		var subscriptionScopedEvaluationCtx = new EvaluationContext(new AnnotationAttributeContext(),
				new AnnotationFunctionContext(), variables);
		subscriptionScopedEvaluationCtx = subscriptionScopedEvaluationCtx
				.forAuthorizationSubscription(authzSubscription);

		// when
		PolicyRetrievalResult result = updatedIndex.retrievePolicies(subscriptionScopedEvaluationCtx).block();

		// then
		assertThat(result).isNotNull();
		assertThat(result.isErrorsInTarget()).isFalse();
		assertThat(result.getMatchingDocuments()).hasSize(3);
		assertTrue(result.getMatchingDocuments().contains(doc1));
		assertTrue(result.getMatchingDocuments().contains(doc2));
	}

	@Test
	public void testPutSimple() {
		// given
		List<Update> updates = new ArrayList<>(3);

		String definition = "policy \"p_0\" permit true";
		SAPL document = interpreter.parse(definition);
		updates.add(new Update(Type.PUBLISH, document, definition));

		PrpUpdateEvent prpUpdateEvent = new PrpUpdateEvent(updates);
		ImmutableParsedDocumentIndex updatedIndex = emptyIndex.apply(prpUpdateEvent);

		// bindings.put("x0", false);
		// bindings.put("x1", false);
		AuthorizationSubscription authzSubscription = createRequestObject();
		var subscriptionScopedEvaluationCtx = new EvaluationContext(new AnnotationAttributeContext(),
				new AnnotationFunctionContext(), variables).forAuthorizationSubscription(authzSubscription);

		// when
		PolicyRetrievalResult result = updatedIndex.retrievePolicies(subscriptionScopedEvaluationCtx).block();

		// then
		assertThat(result).isNotNull();
		assertThat(result.isErrorsInTarget()).isFalse();
		assertThat(result.getMatchingDocuments()).hasSize(1);
		assertTrue(result.getMatchingDocuments().contains(document));

	}

	@Test
	public void testPut() {
		// given
		List<Update> updates = new ArrayList<>(3);

		String definition = "policy \"p_0\" permit !(resource.x0 | resource.x1)";
		SAPL document = interpreter.parse(definition);
		updates.add(new Update(Type.PUBLISH, document, definition));

		PrpUpdateEvent prpUpdateEvent = new PrpUpdateEvent(updates);
		ImmutableParsedDocumentIndex updatedIndex = emptyIndex.apply(prpUpdateEvent);

		bindings.put("x0", false);
		bindings.put("x1", false);
		AuthorizationSubscription authzSubscription = createRequestObject();
		var subscriptionScopedEvaluationCtx = new EvaluationContext(new AnnotationAttributeContext(),
				new AnnotationFunctionContext(), variables).forAuthorizationSubscription(authzSubscription);

		// when
		PolicyRetrievalResult result = updatedIndex.retrievePolicies(subscriptionScopedEvaluationCtx).block();

		// then
		assertThat(result).isNotNull();
		assertThat(result.isErrorsInTarget()).isFalse();
		assertThat(result.getMatchingDocuments()).hasSize(1);
		assertTrue(result.getMatchingDocuments().contains(document));

	}

	@Test
	public void testRemove() {
		// given
		List<Update> updates = new ArrayList<>(3);

		String definition = "policy \"p_0\" permit resource.x0 & resource.x1";
		SAPL document = interpreter.parse(definition);
		// prp.updateFunctionContext(functionCtx);
		updates.add(new Update(Type.PUBLISH, document, definition));

		PrpUpdateEvent prpUpdateEvent = new PrpUpdateEvent(updates);
		ImmutableParsedDocumentIndex updatedIndex = emptyIndex.apply(prpUpdateEvent);

		bindings.put("x0", true);
		bindings.put("x1", true);

		updates.clear();
		updates.add(new Update(Type.UNPUBLISH, document, definition));

		prpUpdateEvent = new PrpUpdateEvent(updates);
		updatedIndex = updatedIndex.apply(prpUpdateEvent);

		AuthorizationSubscription authzSubscription = createRequestObject();

		var subscriptionScopedEvaluationCtx = new EvaluationContext(new AnnotationAttributeContext(),
				new AnnotationFunctionContext(), variables);
		subscriptionScopedEvaluationCtx = subscriptionScopedEvaluationCtx
				.forAuthorizationSubscription(authzSubscription);

		// when
		PolicyRetrievalResult result = updatedIndex.retrievePolicies(subscriptionScopedEvaluationCtx).block();
		// then
		assertThat(result).isNotNull();
		assertThat(result.isErrorsInTarget()).isFalse();
		assertThat(result.getMatchingDocuments()).isEmpty();
	}

	@Test
	public void testUpdateFunctionCtx() {
		// given
		List<Update> updates = new ArrayList<>(3);

		String definition = "policy \"p_0\" permit !resource.x0";
		SAPL document = interpreter.parse(definition);
		updates.add(new Update(Type.PUBLISH, document, definition));

		PrpUpdateEvent prpUpdateEvent = new PrpUpdateEvent(updates);
		ImmutableParsedDocumentIndex updatedIndex = emptyIndex.apply(prpUpdateEvent);

		bindings.put("x0", false);
		AuthorizationSubscription authzSubscription = createRequestObject();
		var subscriptionScopedEvaluationCtx = new EvaluationContext(new AnnotationAttributeContext(),
				new AnnotationFunctionContext(), variables);
		subscriptionScopedEvaluationCtx = subscriptionScopedEvaluationCtx
				.forAuthorizationSubscription(authzSubscription);

		// when
		PolicyRetrievalResult result = updatedIndex.retrievePolicies(subscriptionScopedEvaluationCtx).block();
		// then
		assertThat(result).isNotNull();
		assertThat(result.isErrorsInTarget()).isFalse();
		assertThat(result.getMatchingDocuments()).hasSize(1);
		assertTrue(result.getMatchingDocuments().contains(document));
	}

	private AuthorizationSubscription createRequestObject() {

		ObjectNode resource = json.objectNode();
		for (Map.Entry<String, Boolean> entry : bindings.entrySet()) {
			Boolean value = entry.getValue();
			if (value != null) {
				resource.put(entry.getKey(), value);
			}
		}

		return new AuthorizationSubscription(NullNode.getInstance(), NullNode.getInstance(),
				bindings.isEmpty() ? NullNode.getInstance() : resource, NullNode.getInstance());
	}

	private static Set<String> getVariables() {
		HashSet<String> variables = new HashSet<>();
		for (int i = 0; i < 10; ++i) {
			variables.add("x" + i);
		}
		return variables;
	}
}
