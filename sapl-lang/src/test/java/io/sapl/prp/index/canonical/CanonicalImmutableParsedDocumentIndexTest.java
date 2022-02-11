/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.index.canonical;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import io.sapl.prp.index.ImmutableParsedDocumentIndex;
import io.sapl.prp.index.canonical.ordering.NoPredicateOrderStrategy;

@Timeout(5)
class CanonicalImmutableParsedDocumentIndexTest {

	private Map<String, Boolean> bindings;

	private static SAPLInterpreter interpreter;

	private static JsonNodeFactory json;

	private CanonicalImmutableParsedDocumentIndex emptyIndex;

	private Map<String, JsonNode> variables;

	@BeforeAll
	static void beforeClass() {
		interpreter = new DefaultSAPLInterpreter();
		json        = JsonNodeFactory.instance;
	}

	@BeforeEach
	void setUp() {
		bindings = new HashMap<>();
		for (String variable : getVariables()) {
			bindings.put(variable, null);
		}

		emptyIndex = new CanonicalImmutableParsedDocumentIndex(new AnnotationAttributeContext(),
				new AnnotationFunctionContext());
		variables  = new HashMap<>();
	}

	@Test
	void return_empty_result_on_policy_evaluation_exception() {
		try (MockedStatic<CanonicalIndexAlgorithm> mock = mockStatic(CanonicalIndexAlgorithm.class)) {
			mock.when(() -> CanonicalIndexAlgorithm.match(any())).thenThrow(new PolicyEvaluationException());
			var result = emptyIndex.retrievePolicies().block();

			assertNotNull(result);
			assertTrue(result.getMatchingDocuments().isEmpty());
			assertTrue(result.isErrorsInTarget());
		}
	}

	@Test
	void test_apply_update_events() {
		var spyIndex = spy(emptyIndex);

		/* PUBLISH + CONSISTENT */
		var prpUpdateEvent = new PrpUpdateEvent(update(Type.PUBLISH, "p1"), update(Type.PUBLISH, "p2"),
				update(Type.CONSISTENT, null));

		var updatedIndex = spyIndex.apply(prpUpdateEvent);
		verify(spyIndex, times(2)).applyUpdate(any(), argThat(e -> e.getType() == Type.PUBLISH));
		verify(spyIndex, times(1)).recreateIndex(argThat(map -> map.size() == 2), eq(true));
		spyIndex = (CanonicalImmutableParsedDocumentIndex) spy(updatedIndex);

		/* WITHDRAW + INCONSISTENT */
		prpUpdateEvent = new PrpUpdateEvent(update(Type.WITHDRAW, "p1"), update(Type.WITHDRAW, "p2"),
				update(Type.INCONSISTENT, null));
		updatedIndex   = spyIndex.apply(prpUpdateEvent);
		verify(spyIndex, times(2)).applyUpdate(any(), argThat(e -> e.getType() == Type.WITHDRAW));
		verify(spyIndex, times(1)).recreateIndex(argThat(Map::isEmpty), eq(false));
		spyIndex = (CanonicalImmutableParsedDocumentIndex) spy(updatedIndex);

		assertFalse(updatedIndex.retrievePolicies().block().isPrpValidState());

		/* EMPTY */
		prpUpdateEvent = new PrpUpdateEvent();
		updatedIndex   = spyIndex.apply(prpUpdateEvent);
		verify(spyIndex, times(0)).applyUpdate(any(), any());
		verify(spyIndex, times(1)).recreateIndex(argThat(Map::isEmpty), eq(false));
	}

	private Update update(Type type, String name) {
		var mockSapl = mock(SAPL.class, RETURNS_DEEP_STUBS);
		when(mockSapl.getPolicyElement().getSaplName()).thenReturn(name);
		return new Update(type, mockSapl, null);
	}

	@Test
	void test_return_empty_result_when_error_occurs() throws Exception {
		InputStream resourceAsStream = getClass().getClassLoader()
				.getResourceAsStream("it/error/policy_with_error.sapl");
		SAPL        withError        = interpreter.parse(resourceAsStream);

		List<Update> updates = new ArrayList<>(3);

		updates.add(new Update(Type.PUBLISH, withError, withError.toString()));

		PrpUpdateEvent               prpUpdateEvent = new PrpUpdateEvent(updates);
		ImmutableParsedDocumentIndex updatedIndex   = emptyIndex.apply(prpUpdateEvent);

		// when
		PolicyRetrievalResult result = updatedIndex.retrievePolicies()
				.contextWrite(ctx -> {
					ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
					ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
					ctx = AuthorizationContext.setVariables(ctx, variables);
					ctx = AuthorizationContext.setSubscriptionVariables(ctx, createRequestObject());
					return ctx;
				}).block();

		// then
		assertNotNull(result);
		assertTrue(result.getMatchingDocuments().isEmpty());
		assertTrue(result.isErrorsInTarget());
	}

	// Test must be repeated a couple of times to test implementation of
	// "findOrphanedCandidates"
	@Test
	void test_orphaned() {
		// given
		emptyIndex = new CanonicalImmutableParsedDocumentIndex(new NoPredicateOrderStrategy(),
				new AnnotationAttributeContext(), new AnnotationFunctionContext());
		List<Update> updates = new ArrayList<>(3);

		String def1 = "policy \"p_0\" permit !resource.x1";
		SAPL   doc1 = interpreter.parse(def1);
		updates.add(new Update(Type.PUBLISH, doc1, def1));

		String def2 = "policy \"p_1\" permit !(resource.x0 | resource.x1)";
		SAPL   doc2 = interpreter.parse(def2);
		updates.add(new Update(Type.PUBLISH, doc2, def2));

		String def3 = "policy \"p_2\" permit (resource.x1 | resource.x2)";
		SAPL   doc3 = interpreter.parse(def3);
		updates.add(new Update(Type.PUBLISH, doc3, def3));

		PrpUpdateEvent               prpUpdateEvent = new PrpUpdateEvent(updates);
		ImmutableParsedDocumentIndex updatedIndex   = emptyIndex.apply(prpUpdateEvent);

		bindings.put("x0", false);
		bindings.put("x1", false);
		bindings.put("x2", true);

		// when
		PolicyRetrievalResult result = updatedIndex.retrievePolicies()
				.contextWrite(ctx -> {
					ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
					ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
					ctx = AuthorizationContext.setVariables(ctx, variables);
					ctx = AuthorizationContext.setSubscriptionVariables(ctx, createRequestObject());
					return ctx;
				}).block();

		// then
		assertNotNull(result);
		assertFalse(result.isErrorsInTarget());
		assertThat(result.getMatchingDocuments(), hasSize(3));
		assertTrue(result.getMatchingDocuments().contains(doc1));
		assertTrue(result.getMatchingDocuments().contains(doc2));
	}

	@Test
	void testPutSimple() {
		// given
		List<Update> updates = new ArrayList<>(3);

		String definition = "policy \"p_0\" permit true";
		SAPL   document   = interpreter.parse(definition);
		updates.add(new Update(Type.PUBLISH, document, definition));

		PrpUpdateEvent               prpUpdateEvent = new PrpUpdateEvent(updates);
		ImmutableParsedDocumentIndex updatedIndex   = emptyIndex.apply(prpUpdateEvent);

		// when
		PolicyRetrievalResult result = updatedIndex.retrievePolicies()
				.contextWrite(ctx -> {
					ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
					ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
					ctx = AuthorizationContext.setVariables(ctx, variables);
					ctx = AuthorizationContext.setSubscriptionVariables(ctx, createRequestObject());
					return ctx;
				}).block();

		// then
		assertNotNull(result);
		assertFalse(result.isErrorsInTarget());
		assertThat(result.getMatchingDocuments(), hasSize(1));
		assertTrue(result.getMatchingDocuments().contains(document));
	}

	@Test
	void testPut() {
		// given
		List<Update> updates = new ArrayList<>(3);

		String definition = "policy \"p_0\" permit !(resource.x0 | resource.x1)";
		SAPL   document   = interpreter.parse(definition);
		updates.add(new Update(Type.PUBLISH, document, definition));

		PrpUpdateEvent               prpUpdateEvent = new PrpUpdateEvent(updates);
		ImmutableParsedDocumentIndex updatedIndex   = emptyIndex.apply(prpUpdateEvent);

		bindings.put("x0", false);
		bindings.put("x1", false);

		// when
		PolicyRetrievalResult result = updatedIndex.retrievePolicies()
				.contextWrite(ctx -> {
					ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
					ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
					ctx = AuthorizationContext.setVariables(ctx, variables);
					ctx = AuthorizationContext.setSubscriptionVariables(ctx, createRequestObject());
					return ctx;
				}).block();

		// then
		assertNotNull(result);
		assertFalse(result.isErrorsInTarget());
		assertThat(result.getMatchingDocuments(), hasSize(1));
		assertTrue(result.getMatchingDocuments().contains(document));
	}

	@Test
	void testRemove() {
		// given
		List<Update> updates = new ArrayList<>(3);

		String definition = "policy \"p_0\" permit resource.x0 & resource.x1";
		SAPL   document   = interpreter.parse(definition);
		// prp.updateFunctionContext(functionCtx);
		updates.add(new Update(Type.PUBLISH, document, definition));

		PrpUpdateEvent               prpUpdateEvent = new PrpUpdateEvent(updates);
		ImmutableParsedDocumentIndex updatedIndex   = emptyIndex.apply(prpUpdateEvent);

		bindings.put("x0", true);
		bindings.put("x1", true);

		updates.clear();
		updates.add(new Update(Type.WITHDRAW, document, definition));

		prpUpdateEvent = new PrpUpdateEvent(updates);
		updatedIndex   = updatedIndex.apply(prpUpdateEvent);

		// when
		PolicyRetrievalResult result = updatedIndex.retrievePolicies()
				.contextWrite(ctx -> {
					ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
					ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
					ctx = AuthorizationContext.setVariables(ctx, variables);
					ctx = AuthorizationContext.setSubscriptionVariables(ctx, createRequestObject());
					return ctx;
				}).block();

		// then
		assertNotNull(result);
		assertFalse(result.isErrorsInTarget());
		assertTrue(result.getMatchingDocuments().isEmpty());
	}

	@Test
	void testUpdateFunctionCtx() {
		// given
		List<Update> updates = new ArrayList<>();

		String definition = "policy \"p_0\" permit !resource.x0";
		SAPL   document   = interpreter.parse(definition);
		updates.add(new Update(Type.PUBLISH, document, definition));

		PrpUpdateEvent               prpUpdateEvent = new PrpUpdateEvent(updates);
		ImmutableParsedDocumentIndex updatedIndex   = emptyIndex.apply(prpUpdateEvent);

		bindings.put("x0", false);

		// when
		PolicyRetrievalResult result = updatedIndex.retrievePolicies()
				.contextWrite(ctx -> {
					ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
					ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
					ctx = AuthorizationContext.setVariables(ctx, variables);
					ctx = AuthorizationContext.setSubscriptionVariables(ctx, createRequestObject());
					return ctx;
				}).block();

		// then
		assertNotNull(result);
		assertFalse(result.isErrorsInTarget());
		assertThat(result.getMatchingDocuments(), hasSize(1));
		assertTrue(result.getMatchingDocuments().contains(document));
	}

	@Test
	void exception_while_retaining_Target() {
		SAPL mockDocument = mock(SAPL.class, RETURNS_DEEP_STUBS);
		when(mockDocument.getPolicyElement().getSaplName()).thenReturn("SAPL");
		when(mockDocument.getPolicyElement().getTargetExpression()).thenThrow(new PolicyEvaluationException());

		Map<String, SAPL> saplMap = new HashMap<>();
		saplMap.put("p1", mockDocument);

		try (MockedConstruction<CanonicalIndexDataCreationStrategy> mocked = Mockito.mockConstruction(
				CanonicalIndexDataCreationStrategy.class,
				(mock, context) -> doReturn(null).when(mock).constructNew(any(), any()))) {

			emptyIndex.recreateIndex(saplMap, true);
			verify(mocked.constructed().get(0), times(1)).constructNew(any(), any());
		}
	}

	@Test
	void null_while_retaining_Target() {
		SAPL mockDocument = mock(SAPL.class, RETURNS_DEEP_STUBS);
		when(mockDocument.getPolicyElement().getSaplName()).thenReturn("SAPL");
		when(mockDocument.getPolicyElement().getTargetExpression()).thenReturn(null);

		Map<String, SAPL> saplMap = new HashMap<>();
		saplMap.put("p1", mockDocument);

		try (MockedConstruction<CanonicalIndexDataCreationStrategy> mocked = Mockito.mockConstruction(
				CanonicalIndexDataCreationStrategy.class,
				(mock, context) -> doReturn(null).when(mock).constructNew(any(), any()))) {

			emptyIndex.recreateIndex(saplMap, true);
			verify(mocked.constructed().get(0), times(1)).constructNew(any(), any());
		}
	}

	@Test
	void throw_exception_on_name_collision() {
		var mockDocument = mock(SAPL.class, RETURNS_DEEP_STUBS);
		when(mockDocument.getPolicyElement().getSaplName()).thenReturn("SAPL");
		when(mockDocument.getPolicyElement().getTargetExpression()).thenReturn(null);

		var updateMock = mock(Update.class, RETURNS_DEEP_STUBS);
		when(updateMock.getType()).thenReturn(Type.PUBLISH);
		when(updateMock.getDocument().getPolicyElement().getSaplName()).thenReturn("p1");

		Map<String, SAPL> saplMap = new HashMap<>();
		saplMap.put("p1", mockDocument);

		assertThrows(RuntimeException.class, () -> emptyIndex.applyUpdate(saplMap, updateMock));
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
