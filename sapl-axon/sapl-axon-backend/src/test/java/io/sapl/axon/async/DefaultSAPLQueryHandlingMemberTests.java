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

package io.sapl.axon.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMember;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.axon.constraints.AxonConstraintHandlerBundle;
import io.sapl.axon.queryhandling.QueryPolicyEnforcementPoint;
import io.sapl.spring.method.metadata.EnforceDropWhileDenied;
import io.sapl.spring.method.metadata.EnforceRecoverableIfDenied;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.PreEnforce;
import reactor.util.function.Tuples;

public class DefaultSAPLQueryHandlingMemberTests {

	private final static String PDP_DENY = "PDP DENY";
	private final QueryPolicyEnforcementPoint pep = mock(QueryPolicyEnforcementPoint.class);
	private final TestQuery payload = new TestQuery();

	@Test
	void when_NoQueryMessage_then_Exception() throws NoSuchMethodException, SecurityException {
		var executable = QueryProjection.class.getDeclaredMethod("handle");
		var delegate = new AnnotatedMessageHandlingMember<QueryProjection>(executable, null, null, null);
		var member = new DefaultSAPLQueryHandlingMember<>(delegate, pep);
		var message = new GenericCommandMessage<>(new TestQuery());
		var exception = assertThrows(Exception.class, () -> member.handle(message, new QueryProjection()));
		assertEquals("Message must be a QueryMessage", exception.getMessage());
	}

	@Test
	void when_NoSource_then_Exception() throws NoSuchMethodException, SecurityException {
		var executable = ExceptionalProjection.class.getDeclaredMethod("handle");
		var delegate = new AnnotatedMessageHandlingMember<ExceptionalProjection>(executable, null, null, null);
		var member = new DefaultSAPLQueryHandlingMember<>(delegate, pep);
		var message = new GenericQueryMessage<>(new TestQuery(), null);
		var exception = assertThrows(Exception.class, () -> member.handle(message, new ExceptionalProjection()));
		assertEquals("Exception Handler: ", exception.getMessage());
	}

	/**
	 * ------------------<Single Query Tests>--------------------
	 */

	@SuppressWarnings("unchecked")
	@Test
	void when_SingleQueryPreEnforceDeny_then_AccessDenied() throws Exception {
		var executable = QueryProjection.class.getDeclaredMethod("handle");
		var delegate = new AnnotatedMessageHandlingMember<QueryProjection>(executable, null, null, null);
		when(pep.preEnforceSingleQuery(any(QueryMessage.class), any(Annotation.class),any()))
				.thenReturn(CompletableFuture.failedFuture(new AccessDeniedException(PDP_DENY)));
		var member = new DefaultSAPLQueryHandlingMember<>(delegate, pep);
		QueryMessage<TestQuery, ?> query = new GenericQueryMessage<>(payload, null,
				ResponseTypes.instanceOf(QueryResponse.class));
		var comp = member.handle(query, new QueryProjection());
		assertEquals(CompletableFuture.class,comp.getClass());
		Exception exception = assertThrows(Exception.class, () -> ((CompletableFuture<Object>)comp).get());
		assertEquals(PDP_DENY, exception.getCause().getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void when_SingleQueryPreEnforcePermit_then_QueryResponse() throws Exception {
		var executable = QueryProjection.class.getDeclaredMethod("handle");
		var delegate = new AnnotatedMessageHandlingMember<QueryProjection>(executable, null, null, null);
		when(pep.preEnforceSingleQuery(any(QueryMessage.class), any(Annotation.class),any()))
				.thenReturn(CompletableFuture.completedFuture(
						Tuples.of(new AxonConstraintHandlerBundle<>(),new GenericQueryMessage<>(new TestQuery(), null), Optional.empty())));//AuthorizationDecision.PERMIT, new GenericQueryMessage<>(new TestQuery(), null))));
		when(pep.executeResultHandling(any(),any(),any(),any())).thenReturn(new QueryResponse());
		var member = new DefaultSAPLQueryHandlingMember<>(delegate, pep);
		QueryMessage<?, ?> query = new GenericQueryMessage<>(payload, null,
				ResponseTypes.instanceOf(QueryResponse.class));
		var comp = member.handle(query, new QueryProjection());
		assertEquals(CompletableFuture.class,comp.getClass());
		assertEquals("QueryResponse", ((CompletableFuture<Object>)comp).get().getClass().getSimpleName());
	}

	@SuppressWarnings("unchecked")
	@Test
	void when_SingleQueryPostEnforceDeny_then_AccessDenied() throws Exception {
		var executable = QueryProjectionPostEnforce.class.getDeclaredMethod("handle");
		var delegate = new AnnotatedMessageHandlingMember<QueryProjectionPostEnforce>(executable, null, null, null);
		when(pep.postEnforceSingleQuery(any(QueryMessage.class), any(), any(Annotation.class),any()))
				.thenReturn(CompletableFuture.failedFuture(new AccessDeniedException(PDP_DENY)));
		var member = new DefaultSAPLQueryHandlingMember<>(delegate, pep);
		QueryMessage<TestQuery, ?> query = new GenericQueryMessage<>(payload, null,
				ResponseTypes.instanceOf(QueryResponse.class));
		var comp =  member.handle(query, new QueryProjectionPostEnforce());
		assertEquals(CompletableFuture.class,comp.getClass());
		Exception exception = assertThrows(Exception.class, () -> ((CompletableFuture<Object>)comp).get());
		assertEquals(PDP_DENY, exception.getCause().getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void when_SingleQueryPostEnforcePermit_then_QueryResponse() throws Exception {
		var executable = QueryProjectionPostEnforce.class.getDeclaredMethod("handle");
		var delegate = new AnnotatedMessageHandlingMember<QueryProjectionPostEnforce>(executable, null, null, null);
		when(pep.postEnforceSingleQuery(any(QueryMessage.class), any(), any(Annotation.class),any()))
				.thenReturn(CompletableFuture.completedFuture(new QueryResponse()));
		DefaultSAPLQueryHandlingMember<QueryProjectionPostEnforce> member = new DefaultSAPLQueryHandlingMember<>(
				delegate, pep);
		QueryMessage<TestQuery, ?> query = new GenericQueryMessage<>(payload, null,
				ResponseTypes.instanceOf(QueryResponse.class));
		var comp = member.handle(query, new QueryProjectionPostEnforce());
		assertEquals(CompletableFuture.class,comp.getClass());
		assertEquals("QueryResponse", ((CompletableFuture<Object>)comp).get().getClass().getSimpleName());
	}

	@SuppressWarnings("unchecked")
	@Test
	void when_SingleQueryPreEnforcePermit_and_SingleQueryPostEnforceDeny_then_AccessDenied() throws Exception {
		var executable = QueryProjectionPrePostEnforce.class.getDeclaredMethod("handle");
		var delegate = new AnnotatedMessageHandlingMember<QueryProjectionPrePostEnforce>(executable, null, null, null);
		when(pep.preEnforceSingleQuery(any(QueryMessage.class), any(Annotation.class),any()))
				.thenReturn(CompletableFuture.completedFuture(
						Tuples.of(new AxonConstraintHandlerBundle<>(),new GenericQueryMessage<>(new TestQuery(), null), Optional.empty())));//AuthorizationDecision.PERMIT, new GenericQueryMessage<>(new TestQuery(), null))));
		when(pep.executeResultHandling(any(),any(),any(),any())).thenReturn(new QueryResponse());
		when(pep.postEnforceSingleQuery(any(QueryMessage.class), any(), any(Annotation.class),any()))
				.thenReturn(CompletableFuture.failedFuture(new AccessDeniedException(PDP_DENY)));
		var member = new DefaultSAPLQueryHandlingMember<>(delegate, pep);
		QueryMessage<TestQuery, ?> query = new GenericQueryMessage<>(payload, null,
				ResponseTypes.instanceOf(QueryResponse.class));
		var comp = member.handle(query, new QueryProjectionPrePostEnforce());
		assertEquals(CompletableFuture.class,comp.getClass());
		Exception exception = assertThrows(Exception.class, () -> ((CompletableFuture<Object>)comp).get());
		assertEquals(PDP_DENY, exception.getCause().getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	void when_SingleQueryPreEnforcePermit_and_SingleQueryPostEnforcePermit_then_QueryResponse() throws Exception {
		var executable = QueryProjectionPrePostEnforce.class.getDeclaredMethod("handle");
		var delegate = new AnnotatedMessageHandlingMember<QueryProjectionPrePostEnforce>(executable, null, null, null);
		when(pep.preEnforceSingleQuery(any(QueryMessage.class), any(Annotation.class),any()))
				.thenReturn(CompletableFuture.completedFuture(
						Tuples.of(new AxonConstraintHandlerBundle<>(),new GenericQueryMessage<>(new TestQuery(), null), Optional.empty())));//AuthorizationDecision.PERMIT, new GenericQueryMessage<>(new TestQuery(), null))));
		when(pep.executeResultHandling(any(),any(),any(),any())).thenReturn(new QueryResponse());
		when(pep.postEnforceSingleQuery(any(QueryMessage.class), any(), any(Annotation.class),any()))
				.thenReturn(CompletableFuture.completedFuture(new QueryResponse()));
		var member = new DefaultSAPLQueryHandlingMember<>(delegate, pep);
		QueryMessage<TestQuery, ?> query = new GenericQueryMessage<>(payload, null,
				ResponseTypes.instanceOf(QueryResponse.class));
		var comp = member.handle(query, new QueryProjectionPrePostEnforce());
		assertEquals(CompletableFuture.class,comp.getClass());
		assertEquals("QueryResponse", ((CompletableFuture<Object>)comp).get().getClass().getSimpleName());
	}

	/**
	 * Test of Method handleQuery without Annotation - if (annotations.length == 0)
	 * return super.handle(message,source)
	 * 
	 * @throws Exception
	 */

	@Test
	void when_QueryWithoutAnnotation_then_QueryResponse() throws Exception {
		var executable = QueryProjectionMissingAnnotation.class.getDeclaredMethod("handle");
		var delegate = new AnnotatedMessageHandlingMember<QueryProjectionMissingAnnotation>(executable, null, null,
				null);
		var member = new DefaultSAPLQueryHandlingMember<>(delegate, pep);
		QueryMessage<TestQuery, ?> query = new GenericQueryMessage<>(payload, null,
				ResponseTypes.instanceOf(QueryResponse.class));
		var comp = member.handle(query, new QueryProjectionMissingAnnotation());
		assertEquals("QueryResponse", comp.getClass().getSimpleName());
	}

	/**
	 * ------------------------<Subscription Query Tests>-------------------------
	 */

	@Test
	void when_SubscriptionQueryDeny_then_AccessDenied() throws Exception {

		var executable = QueryProjection.class.getDeclaredMethod("handle");
		var delegate = new AnnotatedMessageHandlingMember<QueryProjection>(executable, null, null, null);
		when(pep.enforceSubscriptionQuery(any(SubscriptionQueryMessage.class), any(Annotation.class),any()))
				.thenReturn(CompletableFuture.failedFuture(new AccessDeniedException(PDP_DENY)));
		DefaultSAPLQueryHandlingMember<QueryProjection> member = new DefaultSAPLQueryHandlingMember<>(delegate, pep);
		SubscriptionQueryMessage<TestQuery, ?, ?> query = new GenericSubscriptionQueryMessage<>(payload,
				ResponseTypes.instanceOf(QueryResponse.class), ResponseTypes.instanceOf(QueryResponse.class));
		doReturn(query).when(pep).getCurrentSubscriptionQueryMessage(anyString());
		Exception exception = assertThrows(Exception.class,
				() -> ((CompletableFuture<?>) member.handle(query, new QueryProjection())).get());
		assertEquals(PDP_DENY, exception.getCause().getMessage());

	}

	@SuppressWarnings("unchecked")
	@Test
	void when_SubscriptionQueryPermit_then_QueryResponse() throws Exception {
		var executable = QueryProjection.class.getDeclaredMethod("handle");
		SubscriptionQueryMessage<TestQuery, QueryResponse, QueryResponse> query = new GenericSubscriptionQueryMessage<>(payload,
				ResponseTypes.instanceOf(QueryResponse.class), ResponseTypes.instanceOf(QueryResponse.class));
		doReturn(query).when(pep).getCurrentSubscriptionQueryMessage(anyString());
		var bundle = new AxonConstraintHandlerBundle<TestQuery,QueryResponse,SubscriptionQueryMessage<TestQuery,QueryResponse,QueryResponse>>();

		var delegate = new AnnotatedMessageHandlingMember<QueryProjection>(executable, null, null, null);
		when(pep.enforceSubscriptionQuery(ArgumentMatchers.same(query), any(Annotation.class),any()))
				.thenReturn(CompletableFuture.completedFuture(
						Tuples.of(bundle,query, Optional.empty())));//AuthorizationDecision.PERMIT, new GenericQueryMessage<>(new TestQuery(), null))));
		when(pep.executeResultHandling(any(),any(),any(),any())).thenReturn(new QueryResponse());
		var member = new DefaultSAPLQueryHandlingMember<>(delegate, pep);


		var comp = member.handle(query, new QueryProjection());
		assertEquals(CompletableFuture.class,comp.getClass());
		assertEquals("QueryResponse", ((CompletableFuture<Object>)comp).get().getClass().getSimpleName());

	}

	/**
	 * Test of Method handleSubscriptionQuery without Annotation - if (annotation ==
	 * null) (...) return super.handle(message,source);
	 * 
	 * @throws Exception
	 */
	@Test
	void when_SubscriptionQueryMissingAnnotation_then_QueryResponse() throws Exception {
		var executable = QueryProjectionMissingAnnotation.class.getDeclaredMethod("handle");
		var delegate = new AnnotatedMessageHandlingMember<QueryProjectionMissingAnnotation>(executable, null, null,
				null);
		var member = new DefaultSAPLQueryHandlingMember<>(delegate, pep);
		SubscriptionQueryMessage<TestQuery, ?, ?> query = new GenericSubscriptionQueryMessage<>(payload,
				ResponseTypes.instanceOf(QueryResponse.class), ResponseTypes.instanceOf(QueryResponse.class));
		doReturn(query).when(pep).getCurrentSubscriptionQueryMessage(anyString());
		Object comp = member.handle(query,
				new QueryProjectionMissingAnnotation());
		assertEquals("QueryResponse", comp.getClass().getSimpleName());
	}

	@Test
	void when_SubscriptionQueryMultiAnnotation_then_Exception() throws Exception {
		var executable = QuerySubscriptionProjection.class.getDeclaredMethod("handle");
		var delegate = new AnnotatedMessageHandlingMember<QuerySubscriptionProjection>(executable, null, null, null);
		
		var member = new DefaultSAPLQueryHandlingMember<>(delegate, pep);
		SubscriptionQueryMessage<TestQuery, ?, ?> query = new GenericSubscriptionQueryMessage<>(payload,
				ResponseTypes.instanceOf(QueryResponse.class), ResponseTypes.instanceOf(QueryResponse.class));
		doReturn(query).when(pep).getCurrentSubscriptionQueryMessage(anyString());
		Exception e = assertThrows(Exception.class, () -> member.handle(query, new QuerySubscriptionProjection()));
		assertEquals("Multiple Subscription Annotations are not allowed", e.getMessage());
	}
	/**
	 * Test-Query
	 */
	private static class TestQuery {
	}

	/**
	 * Query Test-Response
	 */
	private static class QueryResponse {
	}

	/**
	 * Query Test-Projection with PreEnforce and
	 * EnforceDropSubscriptionUpdatesWhileDenied
	 * 
	 */
	private static class QueryProjection {
		@QueryHandler
		@PreEnforce(subject = "Subject", action = "Action", resource = "Resource", environment = "Environment")
		@EnforceDropWhileDenied(subject = "Subject", action = "Action", resource = "Resource", environment = "Environment")
		private QueryResponse handle() {
			return new QueryResponse();
		}
	}

	private static class QueryProjectionPrePostEnforce {
		@QueryHandler
		@PreEnforce(subject = "Subject", action = "Action", resource = "Resource", environment = "Environment")
		@PostEnforce(subject = "Subject", action = "Action", resource = "Resource", environment = "Environment")
		private QueryResponse handle() {
			return new QueryResponse();
		}
	}

	private static class ExceptionalProjection {
		@QueryHandler
		private QueryResponse handle() throws Exception {
			throw new Exception("Exception");
		}
	}

	/**
	 * Query Test-Projection with PostEnforce
	 */
	private static class QueryProjectionPostEnforce {
		@QueryHandler
		@PostEnforce(subject = "Subject", action = "Action", resource = "Resource", environment = "Environment")
		private QueryResponse handle() {
			return new QueryResponse();
		}
	}

	/**
	 * Query Test-Projection with EnforceDropSubscriptionUpdatesWhileDenied
	 */
	private static class QuerySubscriptionProjection {
		@QueryHandler
		@EnforceRecoverableIfDenied(subject = "Subject", action = "Action", resource = "Resource", environment = "Environment")
		@EnforceDropWhileDenied(subject = "Subject", action = "Action", resource = "Resource", environment = "Environment")
		private QueryResponse handle() {
			return new QueryResponse();
		}
	}

	/**
	 * Query Test-Projection without Annotation
	 */
	private static class QueryProjectionMissingAnnotation {
		@QueryHandler
		private QueryResponse handle() {
			return new QueryResponse();
		}
	}

}
