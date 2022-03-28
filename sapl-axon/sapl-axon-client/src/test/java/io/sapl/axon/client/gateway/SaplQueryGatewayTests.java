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

package io.sapl.axon.client.gateway;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.InstanceResponseType;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.DefaultSubscriptionQueryResult;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;

import io.sapl.axon.client.exception.OriginalUpdateTypeRemoved;
import io.sapl.axon.client.exception.RecoverableException;
import io.sapl.axon.client.recoverable.RecoverableResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings("deprecation") // Inherited from Axon
public class SaplQueryGatewayTests {

	/**
	 * Test class verifying correct workings of the {@link DefaultQueryGateway}.
	 *
	 * copied from Axon Framework
	 */
	private QueryBus mockBus;
	private SaplQueryGateway testSubject;
	private QueryResponseMessage<String> answer;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		answer = new GenericQueryResponseMessage<>("answer");
		MessageDispatchInterceptor<QueryMessage<?, ?>> mockDispatchInterceptor = mock(MessageDispatchInterceptor.class);
		mockBus = mock(QueryBus.class);

		testSubject = SaplQueryGateway.builder().queryBus(mockBus).dispatchInterceptors(mockDispatchInterceptor)
				.build();
		when(mockDispatchInterceptor.handle(isA(QueryMessage.class))).thenAnswer(i -> i.getArguments()[0]);
	}

	@Test
	void buildSaplQueryGatewayWithNullDispatchInterceptor() {
		SaplQueryGateway.builder().queryBus(mockBus).dispatchInterceptors(new CopyOnWriteArrayList<>()).build();
	}

	@SuppressWarnings("unchecked")
	@Test
	void testPointToPointQuery() throws Exception {
		when(mockBus.query(anyMessage(String.class, String.class))).thenReturn(completedFuture(answer));

		CompletableFuture<String> queryResponse = testSubject.query("query", String.class);
		assertEquals("answer", queryResponse.get());

		ArgumentCaptor<QueryMessage<String, String>> queryMessageCaptor = ArgumentCaptor.forClass(QueryMessage.class);

		verify(mockBus).query(queryMessageCaptor.capture());

		QueryMessage<String, String> result = queryMessageCaptor.getValue();
		assertEquals("query", result.getPayload());
		assertEquals(String.class, result.getPayloadType());
		assertEquals(String.class.getName(), result.getQueryName());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
		assertEquals(String.class, result.getResponseType().getExpectedResponseType());
		assertEquals(MetaData.emptyInstance(), result.getMetaData());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testPointToPointQuerySpecifyingQueryName() throws Exception {
		String expectedQueryName = "myQueryName";

		when(mockBus.query(anyMessage(String.class, String.class))).thenReturn(completedFuture(answer));

		CompletableFuture<String> queryResponse = testSubject.query(expectedQueryName, "query", String.class);
		assertEquals("answer", queryResponse.get());

		ArgumentCaptor<QueryMessage<String, String>> queryMessageCaptor = ArgumentCaptor.forClass(QueryMessage.class);

		verify(mockBus).query(queryMessageCaptor.capture());

		QueryMessage<String, String> result = queryMessageCaptor.getValue();
		assertEquals("query", result.getPayload());
		assertEquals(String.class, result.getPayloadType());
		assertEquals(expectedQueryName, result.getQueryName());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
		assertEquals(String.class, result.getResponseType().getExpectedResponseType());
		assertEquals(MetaData.emptyInstance(), result.getMetaData());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testPointToPointQueryWithMetaData() throws Exception {
		String expectedMetaDataKey = "key";
		String expectedMetaDataValue = "value";

		when(mockBus.query(anyMessage(String.class, String.class))).thenReturn(completedFuture(answer));

		GenericMessage<String> testQuery = new GenericMessage<>("query",
				MetaData.with(expectedMetaDataKey, expectedMetaDataValue));

		CompletableFuture<String> queryResponse = testSubject.query(testQuery, instanceOf(String.class));
		assertEquals("answer", queryResponse.get());

		ArgumentCaptor<QueryMessage<String, String>> queryMessageCaptor = ArgumentCaptor.forClass(QueryMessage.class);

		verify(mockBus).query(queryMessageCaptor.capture());

		QueryMessage<String, String> result = queryMessageCaptor.getValue();
		assertEquals("query", result.getPayload());
		assertEquals(String.class, result.getPayloadType());
		assertEquals(String.class.getName(), result.getQueryName());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
		assertEquals(String.class, result.getResponseType().getExpectedResponseType());
		MetaData resultMetaData = result.getMetaData();
		assertTrue(resultMetaData.containsKey(expectedMetaDataKey));
		assertTrue(resultMetaData.containsValue(expectedMetaDataValue));
	}

	@Test
	void testPointToPointQueryWhenQueryBusReportsAnError() throws Exception {
		Throwable expected = new Throwable("oops");
		when(mockBus.query(anyMessage(String.class, String.class)))
				.thenReturn(completedFuture(new GenericQueryResponseMessage<>(String.class, expected)));

		CompletableFuture<String> result = testSubject.query("query", String.class);

		assertTrue(result.isDone());
		assertTrue(result.isCompletedExceptionally());
		assertEquals(expected.getMessage(), result.exceptionally(Throwable::getMessage).get());
	}

	@Test
	void testPointToPointQueryWhenQueryBusThrowsException() throws Exception {
		Throwable expected = new Throwable("oops");
		CompletableFuture<QueryResponseMessage<String>> queryResponseCompletableFuture = new CompletableFuture<>();
		queryResponseCompletableFuture.completeExceptionally(expected);
		when(mockBus.query(anyMessage(String.class, String.class))).thenReturn(queryResponseCompletableFuture);

		CompletableFuture<String> result = testSubject.query("query", String.class);

		assertTrue(result.isDone());
		assertTrue(result.isCompletedExceptionally());
		assertEquals(expected.getMessage(), result.exceptionally(Throwable::getMessage).get());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testScatterGatherQuery() {
		long expectedTimeout = 1L;
		TimeUnit expectedTimeUnit = TimeUnit.SECONDS;

		when(mockBus.scatterGather(anyMessage(String.class, String.class), anyLong(), any()))
				.thenReturn(Stream.of(answer));

		Stream<String> queryResponse = testSubject.scatterGather("scatterGather", instanceOf(String.class),
				expectedTimeout, expectedTimeUnit);
		Optional<String> firstResult = queryResponse.findFirst();
		assertTrue(firstResult.isPresent());
		assertEquals("answer", firstResult.get());

		// noinspection unchecked
		ArgumentCaptor<QueryMessage<String, String>> queryMessageCaptor = ArgumentCaptor.forClass(QueryMessage.class);

		verify(mockBus).scatterGather(queryMessageCaptor.capture(), eq(expectedTimeout), eq(expectedTimeUnit));

		QueryMessage<String, String> result = queryMessageCaptor.getValue();
		assertEquals("scatterGather", result.getPayload());
		assertEquals(String.class, result.getPayloadType());
		assertEquals(String.class.getName(), result.getQueryName());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
		assertEquals(String.class, result.getResponseType().getExpectedResponseType());
		assertEquals(MetaData.emptyInstance(), result.getMetaData());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testScatterGatherQuerySpecifyingQueryName() {
		String expectedQueryName = "myQueryName";
		long expectedTimeout = 1L;
		TimeUnit expectedTimeUnit = TimeUnit.SECONDS;

		when(mockBus.scatterGather(anyMessage(String.class, String.class), anyLong(), any()))
				.thenReturn(Stream.of(answer));

		Stream<String> queryResponse = testSubject.scatterGather(expectedQueryName, "scatterGather",
				instanceOf(String.class), expectedTimeout, expectedTimeUnit);
		Optional<String> firstResult = queryResponse.findFirst();
		assertTrue(firstResult.isPresent());
		assertEquals("answer", firstResult.get());

		// noinspection unchecked
		ArgumentCaptor<QueryMessage<String, String>> queryMessageCaptor = ArgumentCaptor.forClass(QueryMessage.class);

		verify(mockBus).scatterGather(queryMessageCaptor.capture(), eq(expectedTimeout), eq(expectedTimeUnit));

		QueryMessage<String, String> result = queryMessageCaptor.getValue();
		assertEquals("scatterGather", result.getPayload());
		assertEquals(String.class, result.getPayloadType());
		assertEquals(expectedQueryName, result.getQueryName());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
		assertEquals(String.class, result.getResponseType().getExpectedResponseType());
		assertEquals(MetaData.emptyInstance(), result.getMetaData());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testScatterGatherQueryWithMetaData() {
		String expectedMetaDataKey = "key";
		String expectedMetaDataValue = "value";
		long expectedTimeout = 1L;
		TimeUnit expectedTimeUnit = TimeUnit.SECONDS;

		when(mockBus.scatterGather(anyMessage(String.class, String.class), anyLong(), any()))
				.thenReturn(Stream.of(answer));

		GenericMessage<String> testQuery = new GenericMessage<>("scatterGather",
				MetaData.with(expectedMetaDataKey, expectedMetaDataValue));

		Stream<String> queryResponse = testSubject.scatterGather(testQuery, instanceOf(String.class), expectedTimeout,
				expectedTimeUnit);
		Optional<String> firstResult = queryResponse.findFirst();
		assertTrue(firstResult.isPresent());
		assertEquals("answer", firstResult.get());

		// noinspection unchecked
		ArgumentCaptor<QueryMessage<String, String>> queryMessageCaptor = ArgumentCaptor.forClass(QueryMessage.class);

		verify(mockBus).scatterGather(queryMessageCaptor.capture(), eq(expectedTimeout), eq(expectedTimeUnit));

		QueryMessage<String, String> result = queryMessageCaptor.getValue();
		assertEquals("scatterGather", result.getPayload());
		assertEquals(String.class, result.getPayloadType());
		assertEquals(String.class.getName(), result.getQueryName());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
		assertEquals(String.class, result.getResponseType().getExpectedResponseType());
		MetaData resultMetaData = result.getMetaData();
		assertTrue(resultMetaData.containsKey(expectedMetaDataKey));
		assertTrue(resultMetaData.containsValue(expectedMetaDataValue));
	}

	@SuppressWarnings("unchecked")
	@Test
	void testSubscriptionQuery() {
		when(mockBus.subscriptionQuery(any(), anyInt()))
				.thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.empty(), () -> true));

		testSubject.subscriptionQuery("subscription", instanceOf(String.class), instanceOf(String.class));

		// noinspection unchecked
		ArgumentCaptor<SubscriptionQueryMessage<String, String, String>> queryMessageCaptor = ArgumentCaptor
				.forClass(SubscriptionQueryMessage.class);

		verify(mockBus).subscriptionQuery(queryMessageCaptor.capture(), anyInt());

		SubscriptionQueryMessage<String, String, String> result = queryMessageCaptor.getValue();
		assertEquals("subscription", result.getPayload());
		assertEquals(String.class, result.getPayloadType());
		assertEquals(String.class.getName(), result.getQueryName());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
		assertEquals(String.class, result.getResponseType().getExpectedResponseType());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getUpdateResponseType().getClass()));
		assertEquals(String.class, result.getUpdateResponseType().getExpectedResponseType());
		assertEquals(MetaData.emptyInstance(), result.getMetaData());
	}

	@Test
	@SuppressWarnings("unchecked")
	void testDeprecatedSubscriptionQuery() {
		when(mockBus.subscriptionQuery(any(), any(), anyInt()))
				.thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.empty(), () -> true));

		testSubject.subscriptionQuery("testQuery", "subscription", instanceOf(String.class), instanceOf(String.class),
				SubscriptionQueryBackpressure.defaultBackpressure());

		// noinspection unchecked
		ArgumentCaptor<SubscriptionQueryMessage<String, String, String>> queryMessageCaptor = ArgumentCaptor
				.forClass(SubscriptionQueryMessage.class);

		verify(mockBus).subscriptionQuery(queryMessageCaptor.capture(), any(), anyInt());

		SubscriptionQueryMessage<String, String, String> result = queryMessageCaptor.getValue();
		assertEquals("subscription", result.getPayload());
		assertEquals(String.class, result.getPayloadType());
		assertEquals("testQuery", result.getQueryName());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
		assertEquals(String.class, result.getResponseType().getExpectedResponseType());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getUpdateResponseType().getClass()));
		assertEquals(String.class, result.getUpdateResponseType().getExpectedResponseType());
		assertEquals(MetaData.emptyInstance(), result.getMetaData());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testSubscriptionQuerySpecifyingQueryName() {
		String expectedQueryName = "myQueryName";

		when(mockBus.subscriptionQuery(any(), anyInt()))
				.thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.empty(), () -> true));

		testSubject.subscriptionQuery(expectedQueryName, "subscription", String.class, String.class);

		// noinspection unchecked
		ArgumentCaptor<SubscriptionQueryMessage<String, String, String>> queryMessageCaptor = ArgumentCaptor
				.forClass(SubscriptionQueryMessage.class);

		verify(mockBus).subscriptionQuery(queryMessageCaptor.capture(), anyInt());

		SubscriptionQueryMessage<String, String, String> result = queryMessageCaptor.getValue();
		assertEquals("subscription", result.getPayload());
		assertEquals(String.class, result.getPayloadType());
		assertEquals(expectedQueryName, result.getQueryName());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
		assertEquals(String.class, result.getResponseType().getExpectedResponseType());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getUpdateResponseType().getClass()));
		assertEquals(String.class, result.getUpdateResponseType().getExpectedResponseType());
		assertEquals(MetaData.emptyInstance(), result.getMetaData());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testSubscriptionQueryWithMetaData() {
		String expectedMetaDataKey = "key";
		String expectedMetaDataValue = "value";

		when(mockBus.subscriptionQuery(any(), anyInt()))
				.thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.empty(), () -> true));

		GenericMessage<String> testQuery = new GenericMessage<>("subscription",
				MetaData.with(expectedMetaDataKey, expectedMetaDataValue));
		testSubject.subscriptionQuery(testQuery, instanceOf(String.class), instanceOf(String.class));

		// noinspection unchecked
		ArgumentCaptor<SubscriptionQueryMessage<String, String, String>> queryMessageCaptor = ArgumentCaptor
				.forClass(SubscriptionQueryMessage.class);

		verify(mockBus).subscriptionQuery(queryMessageCaptor.capture(), anyInt());

		SubscriptionQueryMessage<String, String, String> result = queryMessageCaptor.getValue();
		assertEquals("subscription", result.getPayload());
		assertEquals(String.class, result.getPayloadType());
		assertEquals(String.class.getName(), result.getQueryName());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
		assertEquals(String.class, result.getResponseType().getExpectedResponseType());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getUpdateResponseType().getClass()));
		assertEquals(String.class, result.getUpdateResponseType().getExpectedResponseType());
		MetaData resultMetaData = result.getMetaData();
		assertTrue(resultMetaData.containsKey(expectedMetaDataKey));
		assertTrue(resultMetaData.containsValue(expectedMetaDataValue));
	}

	@SuppressWarnings("unchecked")
	@Test
	void when_RecoverableSubscriptionQuery_then_RecoverableSubscriptionRegistered() {
		when(mockBus.subscriptionQuery(any(), anyInt()))
				.thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.empty(), () -> true));

		testSubject.recoverableSubscriptionQuery("subscription", instanceOf(String.class), instanceOf(String.class),
				Throwable::printStackTrace);

		// noinspection unchecked
		ArgumentCaptor<SubscriptionQueryMessage<String, String, String>> queryMessageCaptor = ArgumentCaptor
				.forClass(SubscriptionQueryMessage.class);

		verify(mockBus).subscriptionQuery(queryMessageCaptor.capture(), anyInt());

		SubscriptionQueryMessage<String, String, String> result = queryMessageCaptor.getValue();
		assertEquals("subscription", result.getPayload());
		assertEquals(String.class, result.getPayloadType());
		assertEquals(String.class.getName(), result.getQueryName());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
		assertEquals(String.class, result.getResponseType().getExpectedResponseType());
		assertTrue(InstanceResponseType.class.isAssignableFrom(result.getUpdateResponseType().getClass()));
		assertEquals(RecoverableResponse.class, result.getUpdateResponseType().getExpectedResponseType());
		assertEquals(MetaData.with(RecoverableResponse.RECOVERABLE_UPDATE_TYPE_KEY, instanceOf(String.class)),
				result.getMetaData());
	}

	@Test
	void when_RecoverableSubscriptionQueryServerReturnPayload_then_ReturnPayload() {
		var res = new RecoverableResponse("responseValue", null);
		SubscriptionQueryUpdateMessage<Object> subscriptionQueryUpdateMessage = GenericSubscriptionQueryUpdateMessage
				.asUpdateMessage(res);

		var flux = Flux.just(subscriptionQueryUpdateMessage, subscriptionQueryUpdateMessage)
				.delayElements(Duration.ofMillis(100));
		when(mockBus.subscriptionQuery(any(), anyInt()))
				.thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), flux, () -> true));
		var subscriptionQueryResult = testSubject.recoverableSubscriptionQuery("testQuery", instanceOf(String.class),
				instanceOf(String.class), null);

		subscriptionQueryResult.updates().subscribe();
		StepVerifier.withVirtualTime(() -> flux).expectSubscription().expectNext(subscriptionQueryUpdateMessage)
				.expectNoEvent(Duration.ofMillis(100)).expectNext(subscriptionQueryUpdateMessage).verifyComplete();
	}

	@Test
	void when_RecoverableSubscriptionQueryServerReturnRecoverableException_then_DoOnErrorContinue() {

		var res = new RecoverableResponse(null, new RecoverableException("recoverableException"));
		SubscriptionQueryUpdateMessage<Object> subscriptionQueryUpdateMessage = GenericSubscriptionQueryUpdateMessage
				.asUpdateMessage(res);

		var flux = Flux.just(subscriptionQueryUpdateMessage, subscriptionQueryUpdateMessage)
				.delayElements(Duration.ofMillis(100));
		when(mockBus.subscriptionQuery(any(), anyInt()))
				.thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), flux, () -> true));
		var subscriptionQueryResult = testSubject.recoverableSubscriptionQuery("testQuery", instanceOf(String.class),
				instanceOf(String.class), Throwable::getMessage);

		subscriptionQueryResult.updates().subscribe();

		StepVerifier.withVirtualTime(() -> flux).expectSubscription().expectNext(subscriptionQueryUpdateMessage)
				.expectNoEvent(Duration.ofMillis(100)).expectNext(subscriptionQueryUpdateMessage).verifyComplete();
	}

	@SuppressWarnings("unused")
	@Test
	void when_RecoverableSubscriptionQueryServerReturnsOriginalTypeRemove_then_DoOnError() {
		var res = new RecoverableResponse(null, new RecoverableException("recoverableException"));
		SubscriptionQueryUpdateMessage<Object> subscriptionQueryUpdateMessage = GenericSubscriptionQueryUpdateMessage
				.asUpdateMessage(res);

		Flux<SubscriptionQueryUpdateMessage<Object>> flux = Flux
				.error(new OriginalUpdateTypeRemoved("originalUpdateTypeRemoved"));
		when(mockBus.subscriptionQuery(any(), anyInt()))
				.thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), flux, () -> true));
		var subscriptionQueryResult = testSubject.recoverableSubscriptionQuery("testQuery", instanceOf(String.class),
				instanceOf(String.class), Throwable::printStackTrace);

		subscriptionQueryResult.updates().subscribe();

		StepVerifier.withVirtualTime(() -> flux).expectSubscription().expectError(OriginalUpdateTypeRemoved.class)
				.verify();
	}

	@Test
	void testDispatchInterceptor() {
		when(mockBus.query(anyMessage(String.class, String.class))).thenReturn(completedFuture(answer));
		testSubject.registerDispatchInterceptor(messages -> (integer, queryMessage) -> new GenericQueryMessage<>(
				"dispatch-" + queryMessage.getPayload(), queryMessage.getQueryName(), queryMessage.getResponseType()));
		testSubject.query("query", String.class).join();

		verify(mockBus).query(
				argThat((ArgumentMatcher<QueryMessage<String, String>>) x -> "dispatch-query".equals(x.getPayload())));
	}

	@Test
	void when_ExceptionInInitialResultOfSubscriptionQuery_then_ThrowException() {
		when(mockBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), anyInt()))
				.thenReturn(new DefaultSubscriptionQueryResult<>(Mono.error(new IllegalArgumentException()),
						Flux.empty(), () -> true));

		SubscriptionQueryResult<String, String> subscriptionQueryResult = testSubject.subscriptionQuery("Test",
				instanceOf(String.class), instanceOf(String.class));
		// noinspection NullableInLambdaInTransform
		assertThrows(IllegalArgumentException.class, () -> subscriptionQueryResult.initialResult().block());
	}

	@Test
	void when_IllegalPayloadAccessExceptionInInitialResultOfSubscriptionQuery_Then_ThrowCause() {
		when(mockBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), anyInt()))
				.thenReturn(new DefaultSubscriptionQueryResult<>(
						Mono.error(new IllegalPayloadAccessException("", new IllegalArgumentException())), Flux.empty(),
						() -> true));

		SubscriptionQueryResult<String, String> subscriptionQueryResult = testSubject.subscriptionQuery("Test",
				instanceOf(String.class), instanceOf(String.class));
		// noinspection NullableInLambdaInTransform
		assertThrows(IllegalArgumentException.class, () -> subscriptionQueryResult.initialResult().block());
	}

	@Test
	void when_IllegalPayloadAccessExceptionInInitialResultOfRecoverableSubscriptionQuery_then_ThrowCause() {

		when(mockBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), anyInt()))
				.thenReturn(new DefaultSubscriptionQueryResult<>(
						Mono.error(new IllegalPayloadAccessException("", new IllegalArgumentException())), Flux.empty(),
						() -> true));
		var subscriptionQueryResult = testSubject.recoverableSubscriptionQuery("test", String.class, String.class,
				Throwable::printStackTrace);

		assertThrows(IllegalArgumentException.class, () -> subscriptionQueryResult.initialResult().block());
	}

	@Test
	void when_ExceptionInInitialResultOfRecoverableSubscriptionQuery_then_ThrowException() {

		when(mockBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), anyInt()))
				.thenReturn(new DefaultSubscriptionQueryResult<>(Mono.error(new IllegalArgumentException("")),
						Flux.empty(), () -> true));
		var subscriptionQueryResult = testSubject.recoverableSubscriptionQuery("test", String.class, String.class,
				Throwable::printStackTrace);

		assertThrows(IllegalArgumentException.class, () -> subscriptionQueryResult.initialResult().block());
	}

	@Test
	void testNullInitialResultOfSubscriptionQueryReportedAsEmptyMono() {
		when(mockBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), anyInt()))
				.thenReturn(new DefaultSubscriptionQueryResult<>(
						Mono.just(new GenericQueryResponseMessage<>(String.class, (String) null)), Flux.empty(),
						() -> true));

		SubscriptionQueryResult<String, String> actual = testSubject.subscriptionQuery("Test", instanceOf(String.class),
				instanceOf(String.class));

		assertNull(actual.initialResult().block());
	}

	@Test
	void when_NullInitialResultOfRecoverableSubscriptionQuery_then_ReportedAsEmptyMono() {
		when(mockBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), anyInt()))
				.thenReturn(new DefaultSubscriptionQueryResult<>(
						Mono.just(new GenericQueryResponseMessage<>(String.class, (String) null)), Flux.empty(),
						() -> true));

		SubscriptionQueryResult<String, String> actual = testSubject.recoverableSubscriptionQuery("Test",
				instanceOf(String.class), instanceOf(String.class), Throwable::printStackTrace);

		assertNull(actual.initialResult().block());
	}

	@Test
	void testPayloadExtractionProblemsReportedInException() throws ExecutionException, InterruptedException {
		when(mockBus.query(anyMessage(String.class, String.class)))
				.thenReturn(completedFuture(new GenericQueryResponseMessage<>("test") {
					@Override
					public String getPayload() {
						throw new MockException("Faking serialization problem");
					}
				}));

		CompletableFuture<String> actual = testSubject.query("query", String.class);
		assertTrue(actual.isDone());
		assertTrue(actual.isCompletedExceptionally());
		assertEquals("Faking serialization problem", actual.exceptionally(Throwable::getMessage).get());
	}


	private <Q, R> QueryMessage<Q, R> anyMessage(Class<Q> queryType, Class<R> responseType) {
		return any();
	}

	private <Q, R> SubscriptionQueryMessage<Q, R, R> anySubscriptionMessage(Class<Q> queryType, Class<R> responseType) {
		return any();
	}
}
