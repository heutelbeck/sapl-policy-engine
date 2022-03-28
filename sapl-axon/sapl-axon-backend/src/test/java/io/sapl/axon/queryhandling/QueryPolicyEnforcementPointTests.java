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

package io.sapl.axon.queryhandling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.AccessDeniedException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.client.exception.RecoverableException;
import io.sapl.axon.constraints.AxonConstraintHandlerBundle;
import io.sapl.axon.constraints.ConstraintHandlerService;
import io.sapl.axon.utilities.AuthorizationSubscriptionBuilderService;
import io.sapl.spring.method.metadata.EnforceDropWhileDenied;
import io.sapl.spring.method.metadata.EnforceRecoverableIfDenied;
import io.sapl.spring.method.metadata.EnforceTillDenied;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.PreEnforce;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;

public class QueryPolicyEnforcementPointTests {

	private final ConstraintHandlerService constraintHandlerService = mock(ConstraintHandlerService.class);
	private final PolicyDecisionPoint pdp = mock(PolicyDecisionPoint.class);
	private final ObjectMapper mapper = new ObjectMapper();
	private final AuthorizationSubscriptionBuilderService authSubscriptionService = new AuthorizationSubscriptionBuilderService(
			mapper);
	private final String DENY = "Denied by PDP";
	private final QueryPolicyEnforcementPoint testSubject = new QueryPolicyEnforcementPoint(pdp,
			authSubscriptionService, constraintHandlerService, mapper);

	@Test
	public void when_preEnforceSingleQueryHandler_and_DecisionDeny_then_AccessDeniedException()
			throws NoSuchMethodException, SecurityException {
		var payload = new QueryPolicyEnforcementPointTests.TestPreQuery();
		QueryMessage<QueryPolicyEnforcementPointTests.TestPreQuery, ?> query = new GenericQueryMessage<>(payload, null,
				ResponseTypes.instanceOf(TestResource.class));
		var method = QueryPolicyEnforcementPointTests.TestPreQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(PreEnforce.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var exception = assertThrows(Exception.class,
				() -> (testSubject.preEnforceSingleQuery(query, annotation, method)).get().getT1());
		assertEquals(DENY, exception.getCause().getMessage());
	}

	@Test
	public void when_preEnforceSingleQueryHandler_and_DecisionNull_then_AccessDeniedException()
			throws NoSuchMethodException, SecurityException {
		var payload = new QueryPolicyEnforcementPointTests.TestPreQuery();
		QueryMessage<QueryPolicyEnforcementPointTests.TestPreQuery, ?> query = new GenericQueryMessage<>(payload, null,
				ResponseTypes.instanceOf(TestResource.class));
		var method = QueryPolicyEnforcementPointTests.TestPreQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(PreEnforce.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just());
		var exception = assertThrows(Exception.class,
				() -> (testSubject.preEnforceSingleQuery(query, annotation, method)).get().getT1());
		assertEquals(DENY, exception.getCause().getMessage());
	}

	@Test
	public void when_preEnforceSingleQueryHandler_and_DecisionPermit_then_ReturnMappedMessage()
			throws NoSuchMethodException, SecurityException, InterruptedException, ExecutionException {

		var payload = new QueryPolicyEnforcementPointTests.TestPreQuery();
		QueryMessage<QueryPolicyEnforcementPointTests.TestPreQuery, String> query = new GenericQueryMessage<>(payload,
				null, ResponseTypes.instanceOf(String.class));
		var method = QueryPolicyEnforcementPointTests.TestPreQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(PreEnforce.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var bundlee = new AxonConstraintHandlerBundle<QueryPolicyEnforcementPointTests.TestPreQuery, String, QueryMessage<QueryPolicyEnforcementPointTests.TestPreQuery, String>>();
		when(constraintHandlerService.createQueryBundle(any(), ArgumentMatchers.same(query),
				ArgumentMatchers.same(query.getResponseType()))).thenReturn(bundlee);

		var result = (testSubject.preEnforceSingleQuery(query, annotation, method)).get().getT2();
		assertEquals(query, result);
	}

	@Test
	public void when_PreEnforceQuery_and_DecisionPermitWithResource_then_ReturnNewResource()
			throws JsonProcessingException {
		var decisionResource = new QueryPolicyEnforcementPointTests.TestResource();
		decisionResource.number = 42;
		var decisionResourceAsString = mapper.writeValueAsString(decisionResource);
		var decisionResourceNode = mapper.readTree(decisionResourceAsString);

		var rootNode = mapper.createObjectNode().set("queryResult", decisionResourceNode);

		var resource = new QueryPolicyEnforcementPointTests.TestResource();
		resource.text = "oldText";
		resource.binary = true;

		var bundle = new AxonConstraintHandlerBundle<QueryPolicyEnforcementPointTests.TestPreQuery, QueryPolicyEnforcementPointTests.TestResource, QueryMessage<QueryPolicyEnforcementPointTests.TestPreQuery, QueryPolicyEnforcementPointTests.TestResource>>();
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var newResource = testSubject.executeResultHandling(resource, bundle, Optional.of(rootNode),
				QueryPolicyEnforcementPointTests.TestResource.class);

		assertNull(newResource.text);
		assertFalse(newResource.binary);
		assertEquals(42, newResource.number);
	}

	@Test
	public void when_PreEnforceQuery_and_DecisionPermitWithFalseResource_then_AccessDenied()
			throws JsonProcessingException {
		var decisionResource = "falseResource";
		var decisionResourceAsString = mapper.writeValueAsString(decisionResource);

		var decisionResourceNode = mapper.readTree(decisionResourceAsString);
		var rootNode = mapper.createObjectNode().set("queryResult", decisionResourceNode);

		var resource = new QueryPolicyEnforcementPointTests.TestResource();
		resource.text = "oldText";
		resource.binary = true;

		var bundle = new AxonConstraintHandlerBundle<QueryPolicyEnforcementPointTests.TestPreQuery, QueryPolicyEnforcementPointTests.TestResource, QueryMessage<QueryPolicyEnforcementPointTests.TestPreQuery, QueryPolicyEnforcementPointTests.TestResource>>();
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		assertThrows(org.springframework.security.access.AccessDeniedException.class,
				() -> testSubject.executeResultHandling(resource, bundle, Optional.of(rootNode),
						QueryPolicyEnforcementPointTests.TestResource.class));

	}

	@Test
	public void when_postEnforceSingleQuery_and_DecisionDeny_then_AccessDeniedException()
			throws NoSuchMethodException, SecurityException {
		var payload = new QueryPolicyEnforcementPointTests.TestPostQuery();
		var query = new GenericQueryMessage<>(payload, null, ResponseTypes.instanceOf(String.class));
		var method = QueryPolicyEnforcementPointTests.TestPostQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(PostEnforce.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var exception = assertThrows(Exception.class,
				() -> (testSubject.postEnforceSingleQuery(query, "Test", annotation, method)).get());
		assertEquals(DENY, exception.getCause().getMessage());
	}

	@Test
	public void when_postEnforceSingleQuery_and_DecisionNull_then_AccessDeniedException()
			throws NoSuchMethodException, SecurityException {
		var payload = new QueryPolicyEnforcementPointTests.TestPostQuery();
		var query = new GenericQueryMessage<>(payload, null, ResponseTypes.instanceOf(String.class));
		var method = QueryPolicyEnforcementPointTests.TestPostQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(PostEnforce.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just());

		var exception = assertThrows(Exception.class,
				() -> (testSubject.postEnforceSingleQuery(query, "Test", annotation, method)).get());
		assertEquals(DENY, exception.getCause().getMessage());
	}

	@Test
	public void when_postEnforceSingleQuery_and_DecisionPermit_then_ReturnResult() throws Exception {
		var payload = new QueryPolicyEnforcementPointTests.TestPostQuery();
		QueryMessage<QueryPolicyEnforcementPointTests.TestPostQuery, String> query = new GenericQueryMessage<>(payload,
				null, ResponseTypes.instanceOf(String.class));
		var method = QueryPolicyEnforcementPointTests.TestPostQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(PostEnforce.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var bundle = new AxonConstraintHandlerBundle<QueryPolicyEnforcementPointTests.TestPostQuery, String, QueryMessage<QueryPolicyEnforcementPointTests.TestPostQuery, String>>();
		when(constraintHandlerService.createQueryBundle(any(), ArgumentMatchers.same(query),
				ArgumentMatchers.same(query.getResponseType()))).thenReturn(bundle);
		var result = (testSubject.postEnforceSingleQuery(query, "Test", annotation, method)).get();
		assertEquals("Test", result);
	}

	/**
	 * ------------------<Tests on SubscriptionQuery-Side>--------------------
	 */

	@Test
	public void when_preEnforceSubscriptionQueryHandler_and_DecisionDeny_then_AccessDeniedException()
			throws NoSuchMethodException, SecurityException {
		var payload = new QueryPolicyEnforcementPointTests.TestPreQuery();
		var query = new GenericSubscriptionQueryMessage<>(payload, ResponseTypes.instanceOf(TestResource.class),
				ResponseTypes.instanceOf(TestResource.class));
		var method = QueryPolicyEnforcementPointTests.TestPreQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(EnforceTillDenied.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var exception = assertThrows(Exception.class,
				() -> (testSubject.enforceSubscriptionQuery(query, annotation, method)).get().getT1());
		assertEquals(DENY, exception.getCause().getMessage());
	}

	@Test
	public void when_preEnforceSubscriptionQueryHandler_and_DecisionNull_then_AccessDeniedException()
			throws NoSuchMethodException, SecurityException {
		var payload = new QueryPolicyEnforcementPointTests.TestPreQuery();
		var query = new GenericSubscriptionQueryMessage<>(payload, ResponseTypes.instanceOf(TestResource.class),
				ResponseTypes.instanceOf(TestResource.class));
		var method = QueryPolicyEnforcementPointTests.TestPreQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(EnforceTillDenied.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just());

		var exception = assertThrows(Exception.class,
				() -> (testSubject.enforceSubscriptionQuery(query, annotation, method)).get().getT1());
		assertEquals(DENY, exception.getCause().getMessage());
	}

	@Test
	public void when_EnforceSubscriptionTillDeniedQueryHandler_and_DecisionDeny_andThenPermit_then_AccessDeniedException_andThen_Result()
			throws NoSuchMethodException, SecurityException {
		var payload = new QueryPolicyEnforcementPointTests.TestPreQuery();
		var query = new GenericSubscriptionQueryMessage<>(payload, ResponseTypes.instanceOf(TestResource.class),
				ResponseTypes.instanceOf(TestResource.class));
		var method = QueryPolicyEnforcementPointTests.TestPreQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(EnforceTillDenied.class);
		var duration = Duration.ofMillis(1000);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(
				Flux.just(AuthorizationDecision.DENY, AuthorizationDecision.PERMIT).delayElements(duration));

		var result = testSubject.enforceSubscriptionQuery(query, annotation, method)
				.whenComplete((authorisation, exception) -> {
					assertNull(authorisation);
					assertEquals(AccessDeniedException.class, exception.getClass());
				});
		assertThrows(ExecutionException.class, result::get);
	}

	@Test
	public void when_EnforceDropSubscriptionUpdatesWhileDeniedQueryHandler_and_DecisionDeny_andThenPermit_then_AccessDeniedException_andThen_Result()
			throws NoSuchMethodException, SecurityException, InterruptedException, ExecutionException {
		var payload = new QueryPolicyEnforcementPointTests.TestPreQuery();
		SubscriptionQueryMessage<QueryPolicyEnforcementPointTests.TestPreQuery, TestResource, TestResource> query = new GenericSubscriptionQueryMessage<>(
				payload, ResponseTypes.instanceOf(TestResource.class), ResponseTypes.instanceOf(TestResource.class));
		var method = QueryPolicyEnforcementPointTests.TestSubscriptionQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(EnforceDropWhileDenied.class);
		var duration = Duration.ofMillis(1000);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(
				Flux.just(AuthorizationDecision.DENY, AuthorizationDecision.PERMIT).delayElements(duration));

		var bundle = new AxonConstraintHandlerBundle<QueryPolicyEnforcementPointTests.TestPreQuery, TestResource, SubscriptionQueryMessage<QueryPolicyEnforcementPointTests.TestPreQuery, TestResource, TestResource>>();
		when(constraintHandlerService.createQueryBundle(any(), ArgumentMatchers.same(query),
				ArgumentMatchers.same(query.getResponseType()))).thenReturn(bundle);

		var result = testSubject.enforceSubscriptionQuery(query, annotation, method)
				.whenComplete((authorisation, exception) -> {
					assertEquals(bundle, authorisation.getT1());
					assertNull(exception);
				});
		result.get();
	}

	@Test
	public void when_EnforceDropSubscriptionUpdatesWhileDeniedQueryHandler_and_DecisionDeny_andThen2Permit_then_AccessDeniedException_andThen_Result()
			throws NoSuchMethodException, SecurityException, InterruptedException, ExecutionException {
		var payload = new QueryPolicyEnforcementPointTests.TestPreQuery();
		SubscriptionQueryMessage<QueryPolicyEnforcementPointTests.TestPreQuery, TestResource, TestResource> query = new GenericSubscriptionQueryMessage<>(
				payload, ResponseTypes.instanceOf(TestResource.class), ResponseTypes.instanceOf(TestResource.class));
		var method = QueryPolicyEnforcementPointTests.TestSubscriptionQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(EnforceDropWhileDenied.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(
				Flux.just(AuthorizationDecision.DENY, AuthorizationDecision.PERMIT, AuthorizationDecision.PERMIT));

		var bundle = new AxonConstraintHandlerBundle<QueryPolicyEnforcementPointTests.TestPreQuery, TestResource, SubscriptionQueryMessage<QueryPolicyEnforcementPointTests.TestPreQuery, TestResource, TestResource>>();
		when(constraintHandlerService.createQueryBundle(any(), ArgumentMatchers.same(query),
				ArgumentMatchers.same(query.getResponseType()))).thenReturn(bundle);
		var result = testSubject.enforceSubscriptionQuery(query, annotation, method)
				.whenComplete((authorisation, exception) -> {
					assertEquals(bundle, authorisation.getT1());
					assertNull(exception);
				});
		result.get();
	}

	@Test
	public void when_EnforceSubscriptionTillDeniedQueryHandler_and_DecisionDeny_then_SubscriptionRemoved()
			throws NoSuchMethodException, SecurityException {
		var payload = new QueryPolicyEnforcementPointTests.TestPreQuery();
		var query = new GenericSubscriptionQueryMessage<>(payload, ResponseTypes.instanceOf(TestResource.class),
				ResponseTypes.instanceOf(TestResource.class));
		var method = QueryPolicyEnforcementPointTests.TestPreQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(EnforceTillDenied.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var exception = assertThrows(Exception.class,
				() -> (testSubject.enforceSubscriptionQuery(query, annotation, method)).get().getT1());
		assertEquals(DENY, exception.getCause().getMessage());
	}

	@Test
	public void when_EnforceSubscriptionUpdatesWhileDeniedQueryHandler_and_DecisionDeny_andThenError_then_CompleteExceptionally()
			throws NoSuchMethodException, SecurityException {
		var payload = new QueryPolicyEnforcementPointTests.TestSubscriptionQuery();
		var query = new GenericSubscriptionQueryMessage<>(payload, ResponseTypes.instanceOf(TestResource.class),
				ResponseTypes.instanceOf(TestResource.class));
		var method = QueryPolicyEnforcementPointTests.TestSubscriptionQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(EnforceDropWhileDenied.class);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.concat(Flux.just(AuthorizationDecision.DENY), Flux.error(new Exception(""))));

		var exception = assertThrows(Exception.class,
				() -> (testSubject.enforceSubscriptionQuery(query, annotation, method)).get().getT1());
		assertEquals(DENY, exception.getCause().getMessage());
	}

	@Test
	public void when_EnforceSubscriptionUpdatesWhileDeniedQueryHandler_and_ThenError_then_CompleteExceptionally()
			throws NoSuchMethodException, SecurityException {
		var payload = new QueryPolicyEnforcementPointTests.TestSubscriptionQuery();
		var query = new GenericSubscriptionQueryMessage<>(payload, ResponseTypes.instanceOf(TestResource.class),
				ResponseTypes.instanceOf(TestResource.class));
		var method = QueryPolicyEnforcementPointTests.TestSubscriptionQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(EnforceDropWhileDenied.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.error(new Exception("Exception")));

		var exception = assertThrows(Exception.class,
				() -> (testSubject.enforceSubscriptionQuery(query, annotation, method)).get().getT1());
		assertEquals(DENY, exception.getCause().getMessage());
	}

	@Test
	public void when_annotationIsNull_then_dropMessage() throws Exception {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var query = new GenericSubscriptionQueryMessage<>(new QueryPolicyEnforcementPointTests.TestSubscriptionQuery(),
				ResponseTypes.instanceOf(String.class), ResponseTypes.instanceOf(String.class));

		var update = new GenericSubscriptionQueryUpdateMessage<>("Test");
		assertNull(testSubject.handleSubscriptionQueryUpdateMessage(query, update));
	}

	@Test
	public void when_MessageIsUnenforced_then_returnOriginalUpdateMessage() throws Exception {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var query = new GenericSubscriptionQueryMessage<>(new QueryPolicyEnforcementPointTests.TestSubscriptionQuery(),
				ResponseTypes.instanceOf(String.class), ResponseTypes.instanceOf(String.class));
		testSubject.addUnenforcedMessage(query.getIdentifier());
		var update = new GenericSubscriptionQueryUpdateMessage<>("Test");
		assertEquals(update, testSubject.handleSubscriptionQueryUpdateMessage(query, update));
	}

	@Test
	public void when_SubscriptionQueryUpdateReceived_Before_Decision_then_return_null() throws Exception {
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT).delaySequence(Duration.ofMillis(1000)));

		var method = QueryPolicyEnforcementPointTests.TestSubscriptionQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(EnforceDropWhileDenied.class);
		var query = new GenericSubscriptionQueryMessage<>(new QueryPolicyEnforcementPointTests.TestSubscriptionQuery(),
				ResponseTypes.instanceOf(String.class), ResponseTypes.instanceOf(String.class));

		testSubject.enforceSubscriptionQuery(query, annotation, method);
		var update = new GenericSubscriptionQueryUpdateMessage<>("Test");
		assertNull(testSubject.handleSubscriptionQueryUpdateMessage(query, update));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void when_SubscriptionQueryUpdateReceived_then_ReturnSamePayloadType() throws Exception {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var query = new GenericSubscriptionQueryMessage<>(new QueryPolicyEnforcementPointTests.TestSubscriptionQuery(),
				ResponseTypes.instanceOf(String.class), ResponseTypes.instanceOf(String.class));
		var update = new GenericSubscriptionQueryUpdateMessage<>("Test");
		var method = QueryPolicyEnforcementPointTests.TestSubscriptionQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(EnforceDropWhileDenied.class);
		var bundle = new AxonConstraintHandlerBundle<QueryPolicyEnforcementPointTests.TestSubscriptionQuery, String, SubscriptionQueryMessage<QueryPolicyEnforcementPointTests.TestSubscriptionQuery, String, String>>();
		when(constraintHandlerService.createQueryBundle(any(), any(Message.class), any(ResponseType.class)))
				.thenReturn(bundle);

		testSubject.enforceSubscriptionQuery(query, annotation, method);
		assertEquals(update.getPayloadType(),
				testSubject.handleSubscriptionQueryUpdateMessage(query, update).getPayloadType());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void when_SubscriptionQueryUpdateReceived_and_DecisionDeny_then_returnNull() throws Exception {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var query = new GenericSubscriptionQueryMessage<>(new QueryPolicyEnforcementPointTests.TestSubscriptionQuery(),
				ResponseTypes.instanceOf(String.class), ResponseTypes.instanceOf(String.class));
		var update = new GenericSubscriptionQueryUpdateMessage<>("Test");
		var method = QueryPolicyEnforcementPointTests.TestSubscriptionQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(EnforceDropWhileDenied.class);
		var bundle = new AxonConstraintHandlerBundle<QueryPolicyEnforcementPointTests.TestSubscriptionQuery, String, SubscriptionQueryMessage<QueryPolicyEnforcementPointTests.TestSubscriptionQuery, String, String>>();
		when(constraintHandlerService.createQueryBundle(any(), any(Message.class), any(ResponseType.class)))
				.thenReturn(bundle);

		testSubject.enforceSubscriptionQuery(query, annotation, method);
		assertNull(testSubject.handleSubscriptionQueryUpdateMessage(query, update));

	}

	@SuppressWarnings("unchecked")
	@Test
	public void when_SubscriptionQueryUpdateReceived_and_DecisionDeny_and_EnforceRecoverable_then_RecoverableException()
			throws Exception {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var query = new GenericSubscriptionQueryMessage<>(
				new QueryPolicyEnforcementPointTests.TestSubscriptionQueryRecoverable(),
				ResponseTypes.instanceOf(String.class), ResponseTypes.instanceOf(String.class));
		var update = new GenericSubscriptionQueryUpdateMessage<>("Test");
		var method = QueryPolicyEnforcementPointTests.TestSubscriptionQueryRecoverable.class
				.getDeclaredMethod("method");
		var annotation = method.getAnnotation(EnforceRecoverableIfDenied.class);
		var bundle = new AxonConstraintHandlerBundle<QueryPolicyEnforcementPointTests.TestSubscriptionQueryRecoverable, String, SubscriptionQueryMessage<QueryPolicyEnforcementPointTests.TestSubscriptionQueryRecoverable, String, String>>();
		when(constraintHandlerService.createQueryBundle(any(), any(Message.class), any(ResponseType.class)))
				.thenReturn(bundle);

		testSubject.enforceSubscriptionQuery(query, annotation, method);
		assertThrows(RecoverableException.class,
				() -> (testSubject.handleSubscriptionQueryUpdateMessage(query, update)).optionalExceptionResult());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void when_SubscriptionQueryUpdateReceived_and_DecisionDeny_and_preEnforce_then_AccessDenied()
			throws Exception {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var query = new GenericSubscriptionQueryMessage<>(new QueryPolicyEnforcementPointTests.TestPreQuery(),
				ResponseTypes.instanceOf(String.class), ResponseTypes.instanceOf(String.class));
		var update = new GenericSubscriptionQueryUpdateMessage<>("Test");
		var method = QueryPolicyEnforcementPointTests.TestPreQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(PreEnforce.class);
		var bundle = new AxonConstraintHandlerBundle<QueryPolicyEnforcementPointTests.TestPreQuery, String, SubscriptionQueryMessage<QueryPolicyEnforcementPointTests.TestPreQuery, String, String>>();
		when(constraintHandlerService.createQueryBundle(any(), any(Message.class), any(ResponseType.class)))
				.thenReturn(bundle);

		testSubject.enforceSubscriptionQuery(query, annotation, method);
		var exception = assertThrows(Exception.class,
				() -> (testSubject.handleSubscriptionQueryUpdateMessage(query, update)).optionalExceptionResult());
		assertEquals("DENIED", exception.getMessage());
	}

	@Test
	public void when_HandleObjectNull_then_ReturnNull() {
		assertNull(testSubject.executeResultHandling(null,
                new AxonConstraintHandlerBundle<>(), Optional.empty(), null));
	}

	@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
	private static class TestPreQuery {
		@EnforceTillDenied(subject = "'Subject'", action = "'Action'", resource = "'Resource'", environment = "'Environment'")
		@PreEnforce(subject = "'Subject'", action = "'Action'", resource = "'Resource'", environment = "'Environment'")
		@QueryHandler
		public void method() {
		}
	}

	@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
	private static class TestPostQuery {
		@PostEnforce(subject = "'Subject'", action = "'Action'", resource = "'Resource'", environment = "'Environment'")
		@QueryHandler
		public void method() {
		}
	}

	@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
	private static class TestSubscriptionQuery {
		@EnforceDropWhileDenied(subject = "'Subject'", action = "'Action'", resource = "'Resource'", environment = "'Environment'")
		@QueryHandler
		public void method() {
		}
	}

	@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
	private static class TestSubscriptionQueryRecoverable {
		@EnforceRecoverableIfDenied(subject = "'Subject'", action = "'Action'", resource = "'Resource'", environment = "'Environment'")
		@QueryHandler
		public void method() {
		}
	}

	@Data
	@Aggregate
	@NoArgsConstructor
	private static class TestResource {
		
		@AggregateIdentifier
        private String identifier;
		
		String text;
		int number;
		boolean binary;

//		TestResource() {
//		}
	}

}
