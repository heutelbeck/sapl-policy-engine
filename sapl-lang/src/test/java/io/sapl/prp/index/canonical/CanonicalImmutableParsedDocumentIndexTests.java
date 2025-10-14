/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.prp.Document;
import io.sapl.prp.DocumentMatch;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import io.sapl.prp.index.UpdateEventDrivenPolicyRetrievalPoint;
import io.sapl.prp.index.canonical.ordering.NoPredicateOrderStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.InputStream;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(5)
class CanonicalImmutableParsedDocumentIndexTests {

    private static final SAPLInterpreter INTERPERETER = new DefaultSAPLInterpreter();

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private Map<String, Boolean> bindings;

    private CanonicalImmutableParsedDocumentIndex emptyIndex;

    private Map<String, Val> variables;

    @BeforeEach
    void setUp() {
        bindings = Maps.newHashMapWithExpectedSize(getVariables().size());
        for (String variable : getVariables()) {
            bindings.put(variable, null);
        }

        emptyIndex = new CanonicalImmutableParsedDocumentIndex(new CachingAttributeStreamBroker(),
                new AnnotationFunctionContext());
        variables  = new HashMap<>();
    }

    @Test
    void return_empty_result_on_policy_evaluation_exception() {
        try (MockedStatic<CanonicalIndexAlgorithm> mock = mockStatic(CanonicalIndexAlgorithm.class)) {
            mock.when(() -> CanonicalIndexAlgorithm.match(any())).thenThrow(new PolicyEvaluationException());
            final var result = emptyIndex.retrievePolicies().block();

            assertNotNull(result);
            assertTrue(result.getMatchingDocuments().isEmpty());
            assertTrue(result.isRetrievalWithErrors());
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

        assertTrue(updatedIndex.retrievePolicies().block().isPrpInconsistent());

        /* EMPTY */
        prpUpdateEvent = new PrpUpdateEvent();
        spyIndex.apply(prpUpdateEvent);
        verify(spyIndex, times(0)).applyUpdate(any(), any());
        verify(spyIndex, times(1)).recreateIndex(argThat(Map::isEmpty), eq(false));
    }

    private Update update(Type type, String name) {
        return new Update(type, INTERPERETER.parseDocument("policy \"" + name + "\" permit"));
    }

    @Test
    void test_return_empty_result_when_error_occurs() {
        InputStream resourceAsStream = getClass().getClassLoader()
                .getResourceAsStream("it/error/policy_with_error.sapl");
        Document    withError        = INTERPERETER.parseDocument(resourceAsStream);

        List<Update> updates = new ArrayList<>(3);

        updates.add(new Update(Type.PUBLISH, withError));

        PrpUpdateEvent                        prpUpdateEvent = new PrpUpdateEvent(updates);
        UpdateEventDrivenPolicyRetrievalPoint updatedIndex   = emptyIndex.apply(prpUpdateEvent);

        // when
        PolicyRetrievalResult result = updatedIndex.retrievePolicies().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeStreamBroker(ctx, new CachingAttributeStreamBroker());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, variables);
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, createRequestObject());
            return ctx;
        }).block();

        // then
        assertNotNull(result);
        assertTrue(result.getMatchingDocuments().isEmpty());
        assertTrue(result.isRetrievalWithErrors());
    }

    // Test must be repeated a couple of times to test implementation of
    // "findOrphanedCandidates"
    // is this still a valid comment ?
    @Test
    void test_orphaned() {
        // given
        emptyIndex = new CanonicalImmutableParsedDocumentIndex(new NoPredicateOrderStrategy(),
                new CachingAttributeStreamBroker(), new AnnotationFunctionContext());
        List<Update> updates = new ArrayList<>(3);

        final var doc1 = INTERPERETER.parseDocument("policy \"p_0\" permit !resource.x1");
        updates.add(new Update(Type.PUBLISH, doc1));
        final var doc2 = INTERPERETER.parseDocument("policy \"p_1\" permit !(resource.x0 | resource.x1)");
        updates.add(new Update(Type.PUBLISH, doc2));
        final var doc3 = INTERPERETER.parseDocument("policy \"p_2\" permit (resource.x1 | resource.x2)");
        updates.add(new Update(Type.PUBLISH, doc3));

        PrpUpdateEvent                        prpUpdateEvent = new PrpUpdateEvent(updates);
        UpdateEventDrivenPolicyRetrievalPoint updatedIndex   = emptyIndex.apply(prpUpdateEvent);

        bindings.put("x0", Boolean.FALSE);
        bindings.put("x1", Boolean.FALSE);
        bindings.put("x2", Boolean.TRUE);

        // when
        PolicyRetrievalResult result = updatedIndex.retrievePolicies().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeStreamBroker(ctx, new CachingAttributeStreamBroker());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, variables);
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, createRequestObject());
            return ctx;
        }).block();

        // then
        assertNotNull(result);
        assertFalse(result.isRetrievalWithErrors());
        assertThat(result.getMatchingDocuments(), hasSize(3));
        assertTrue(contains(result.getMatchingDocuments(), doc1));
        assertTrue(contains(result.getMatchingDocuments(), doc2));
        assertTrue(contains(result.getMatchingDocuments(), doc3));
    }

    private boolean contains(Iterable<DocumentMatch> matches, Document doc) {
        for (var md : matches) {
            if (doc.equals(md.document())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void testPutSimple() {
        // given
        final var updates  = new ArrayList<Update>(3);
        final var document = INTERPERETER.parseDocument("policy \"p_0\" permit true");
        updates.add(new Update(Type.PUBLISH, document));
        final var prpUpdateEvent = new PrpUpdateEvent(updates);
        final var updatedIndex   = emptyIndex.apply(prpUpdateEvent);

        // when
        PolicyRetrievalResult result = updatedIndex.retrievePolicies().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeStreamBroker(ctx, new CachingAttributeStreamBroker());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, variables);
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, createRequestObject());
            return ctx;
        }).block();

        // then
        assertNotNull(result);
        assertFalse(result.isRetrievalWithErrors());
        assertThat(result.getMatchingDocuments(), hasSize(1));
        assertTrue(contains(result.getMatchingDocuments(), document));
    }

    @Test
    void testPut() {
        // given
        final var updates  = new ArrayList<Update>(3);
        final var document = INTERPERETER.parseDocument("policy \"p_0\" permit !(resource.x0 | resource.x1)");
        updates.add(new Update(Type.PUBLISH, document));
        final var prpUpdateEvent = new PrpUpdateEvent(updates);
        final var updatedIndex   = emptyIndex.apply(prpUpdateEvent);
        bindings.put("x0", Boolean.FALSE);
        bindings.put("x1", Boolean.FALSE);

        // when
        PolicyRetrievalResult result = updatedIndex.retrievePolicies().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeStreamBroker(ctx, new CachingAttributeStreamBroker());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, variables);
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, createRequestObject());
            return ctx;
        }).block();

        // then
        assertNotNull(result);
        assertFalse(result.isRetrievalWithErrors());
        assertThat(result.getMatchingDocuments(), hasSize(1));
        assertTrue(contains(result.getMatchingDocuments(), document));
    }

    @Test
    void testRemove() {
        // given
        List<Update> updates = new ArrayList<>(3);

        String    definition = "policy \"p_0\" permit resource.x0 & resource.x1";
        final var document   = INTERPERETER.parseDocument(definition);
        updates.add(new Update(Type.PUBLISH, document));

        PrpUpdateEvent                        prpUpdateEvent = new PrpUpdateEvent(updates);
        UpdateEventDrivenPolicyRetrievalPoint updatedIndex   = emptyIndex.apply(prpUpdateEvent);

        bindings.put("x0", Boolean.TRUE);
        bindings.put("x1", Boolean.TRUE);

        updates.clear();
        updates.add(new Update(Type.WITHDRAW, document));

        prpUpdateEvent = new PrpUpdateEvent(updates);
        updatedIndex   = updatedIndex.apply(prpUpdateEvent);

        // when
        PolicyRetrievalResult result = updatedIndex.retrievePolicies().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeStreamBroker(ctx, new CachingAttributeStreamBroker());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, variables);
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, createRequestObject());
            return ctx;
        }).block();

        // then
        assertNotNull(result);
        assertFalse(result.isRetrievalWithErrors());
        assertTrue(result.getMatchingDocuments().isEmpty());
    }

    @Test
    void testUpdateFunctionCtxNoTargetNoSchema() {
        // given
        List<Update> updates = new ArrayList<>();

        String    definition = "policy \"p_0\" permit";
        final var document   = INTERPERETER.parseDocument(definition);
        updates.add(new Update(Type.PUBLISH, document));

        PrpUpdateEvent                        prpUpdateEvent = new PrpUpdateEvent(updates);
        UpdateEventDrivenPolicyRetrievalPoint updatedIndex   = emptyIndex.apply(prpUpdateEvent);

        // when
        PolicyRetrievalResult result = updatedIndex.retrievePolicies().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeStreamBroker(ctx, new CachingAttributeStreamBroker());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, variables);
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, createRequestObject());
            return ctx;
        }).block();

        // then
        assertNotNull(result);
        assertFalse(result.isRetrievalWithErrors());
        assertThat(result.getMatchingDocuments(), hasSize(1));
        assertTrue(contains(result.getMatchingDocuments(), document));
    }

    @Test
    void testUpdateFunctionCtx() {
        // given
        List<Update> updates = new ArrayList<>();

        String    definition = "policy \"p_0\" permit !resource.x0";
        final var document   = INTERPERETER.parseDocument(definition);
        updates.add(new Update(Type.PUBLISH, document));

        PrpUpdateEvent                        prpUpdateEvent = new PrpUpdateEvent(updates);
        UpdateEventDrivenPolicyRetrievalPoint updatedIndex   = emptyIndex.apply(prpUpdateEvent);

        bindings.put("x0", Boolean.FALSE);

        // when
        PolicyRetrievalResult result = updatedIndex.retrievePolicies().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeStreamBroker(ctx, new CachingAttributeStreamBroker());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, variables);
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, createRequestObject());
            return ctx;
        }).block();

        // then
        assertNotNull(result);
        assertFalse(result.isRetrievalWithErrors());
        assertThat(result.getMatchingDocuments(), hasSize(1));
        assertTrue(contains(result.getMatchingDocuments(), document));
    }

    @Test
    void exception_while_retaining_Target() {
        final var document = INTERPERETER.parseDocument("id1", "policy \"p1\" permit");
        final var saplMap  = new HashMap<String, Document>();
        saplMap.put("p1", document);

        try (MockedConstruction<CanonicalIndexDataCreationStrategy> mocked = Mockito.mockConstruction(
                CanonicalIndexDataCreationStrategy.class,
                (mock, context) -> doReturn(null).when(mock).constructNew(any(), any()))) {

            emptyIndex.recreateIndex(saplMap, true);
            verify(mocked.constructed().get(0), times(1)).constructNew(any(), any());
        }
    }

    @Test
    void throw_exception_on_name_collision() {
        final var firstDocument = INTERPERETER.parseDocument("policy \"p1\" permit");
        final var saplMap       = new HashMap<String, Document>();
        saplMap.put("p1", firstDocument);

        final var secondDocument = INTERPERETER.parseDocument("policy \"p1\" permit");
        final var update         = new Update(Type.PUBLISH, secondDocument);

        assertThrows(RuntimeException.class, () -> emptyIndex.applyUpdate(saplMap, update));
    }

    private AuthorizationSubscription createRequestObject() {

        ObjectNode resource = JSON.objectNode();
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
