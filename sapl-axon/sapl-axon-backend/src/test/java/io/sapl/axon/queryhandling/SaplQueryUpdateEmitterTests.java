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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandlerRegistration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentMatchers;

import io.sapl.axon.client.exception.RecoverableException;
import io.sapl.axon.client.recoverable.RecoverableResponse;
import reactor.test.StepVerifier;

@Timeout(2)
@SuppressWarnings("deprecation") // Inherited from Axon
public class SaplQueryUpdateEmitterTests {

	/**
	 * Test class validating the {@link SaplQueryUpdateEmitter}.
	 * 
	 * copied TestCases starting with "test" from Axon Framework
	 *
	 */

	private SaplQueryUpdateEmitter testSubject;
	private QueryPolicyEnforcementPoint pep;
	final String TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE = "some-text";

	@BeforeEach
	void setUp() {
		pep = mock(QueryPolicyEnforcementPoint.class);
		testSubject = SaplQueryUpdateEmitter.builder().policyEnforcementPoint(pep)
				.updateMessageMonitor(NoOpMessageMonitor.INSTANCE).build();
	}

	void when_DispatchInterceptorRegistered_Then_NoException() {
		testSubject.registerDispatchInterceptor(
				list -> (integer, updateMessage) -> GenericSubscriptionQueryUpdateMessage.asUpdateMessage(5));// GenericSubscriptionQueryUpdateMessage.asUpdateMessage(5));
	}

	@SuppressWarnings("unchecked")
	@Test
	void when_Registration_Permitting_Updates_then_ReturnNext() throws Exception {
		SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));

		when(pep.handleSubscriptionQueryUpdateMessage(any(SubscriptionQueryMessage.class),
				any(SubscriptionQueryUpdateMessage.class))).thenReturn(
				new GenericSubscriptionQueryUpdateMessage<>(TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));

		UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(queryMessage, 1024);
		result.getUpdates().subscribe();
		testSubject.emit(any -> true, TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE);
		result.complete();

		StepVerifier.create(result.getUpdates().map(Message::getPayload)).expectNext(TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE)
				.verifyComplete();
	}

	@SuppressWarnings("unchecked")
	@Test
	void when_DispatchInterceptorRegisteredEmit_Then_ReturnInterceptedUpdateMessage() throws Exception {
		var interceptedUpdateMessage = new GenericSubscriptionQueryUpdateMessage<>("intercepted");
		MessageDispatchInterceptor<SubscriptionQueryUpdateMessage<?>> interceptor = messages -> (integer, update) -> interceptedUpdateMessage;

		testSubject.registerDispatchInterceptor(interceptor);
		SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));

		when(pep.handleSubscriptionQueryUpdateMessage(any(SubscriptionQueryMessage.class),
				ArgumentMatchers.eq(interceptedUpdateMessage))).thenReturn(interceptedUpdateMessage);

		UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(queryMessage, 1024);
		result.getUpdates().subscribe();
		testSubject.emit(any -> true, TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE);
		result.complete();
		StepVerifier.create(result.getUpdates().map(Message::getPayload)).expectNext("intercepted").verifyComplete();
	}

	@Test
	@SuppressWarnings("unchecked")
	void testCompletingRegistrationOldApi() throws Exception {
		SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));

		when(pep.handleSubscriptionQueryUpdateMessage(any(SubscriptionQueryMessage.class),
				any(SubscriptionQueryUpdateMessage.class))).thenReturn(
				new GenericSubscriptionQueryUpdateMessage<>(TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));

		UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(queryMessage,
				SubscriptionQueryBackpressure.defaultBackpressure(), 1024);

		testSubject.emit(any -> true, TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE);
		result.complete();

		StepVerifier.create(result.getUpdates().map(Message::getPayload)).expectNext(TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE)
				.verifyComplete();
	}

	@Test
	@SuppressWarnings("unchecked")
	void testConcurrentUpdateEmitting() throws Exception {
		SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));

		when(pep.handleSubscriptionQueryUpdateMessage(any(SubscriptionQueryMessage.class),
				any(SubscriptionQueryUpdateMessage.class))).thenReturn(
				new GenericSubscriptionQueryUpdateMessage<>(TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));

		UpdateHandlerRegistration<Object> registration = testSubject.registerUpdateHandler(queryMessage, 128);

		ExecutorService executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		for (int i = 0; i < 20; i++) {
			executors.submit(() -> testSubject.emit(q -> true, TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));
		}
		executors.shutdown();
		StepVerifier.create(registration.getUpdates()).expectNextCount(20).then(() -> testSubject.complete(q -> true))
				.verifyComplete();
	}

	@Test
	@SuppressWarnings("unchecked")
	void testConcurrentUpdateEmitting_WithBackpressure() throws Exception {
		SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));

		when(pep.handleSubscriptionQueryUpdateMessage(any(SubscriptionQueryMessage.class),
				any(SubscriptionQueryUpdateMessage.class))).thenReturn(
				new GenericSubscriptionQueryUpdateMessage<>(TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));

		UpdateHandlerRegistration<Object> registration = testSubject.registerUpdateHandler(queryMessage,
				SubscriptionQueryBackpressure.defaultBackpressure(), 128);

		ExecutorService executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		for (int i = 0; i < 20; i++) {
			executors.submit(() -> testSubject.emit(q -> true, TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));
		}
		executors.shutdown();
		StepVerifier.create(registration.getUpdates()).expectNextCount(20).then(() -> testSubject.complete(q -> true))
				.verifyComplete();
	}

	@Test
	@SuppressWarnings("unchecked")
	void testCancelingRegistrationDoesNotCompleteFluxOfUpdates() throws Exception {
		SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));

		when(pep.handleSubscriptionQueryUpdateMessage(any(SubscriptionQueryMessage.class),
				any(SubscriptionQueryUpdateMessage.class))).thenReturn(
				new GenericSubscriptionQueryUpdateMessage<>(TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));

		UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(queryMessage, 1024);

		result.getUpdates().subscribe();
		testSubject.emit(any -> true, TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE);
		result.getRegistration().cancel();

		StepVerifier.create(result.getUpdates().map(Message::getPayload)).expectNext(TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE)
				.verifyTimeout(Duration.ofMillis(500));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testCancelingRegistrationDoesNotCompleteFluxOfUpdatesOldApi() throws Exception {
		SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));

		when(pep.handleSubscriptionQueryUpdateMessage(any(SubscriptionQueryMessage.class),
				any(SubscriptionQueryUpdateMessage.class))).thenReturn(
				new GenericSubscriptionQueryUpdateMessage<>(TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));

		UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(queryMessage,
				SubscriptionQueryBackpressure.defaultBackpressure(), 1024);

		testSubject.emit(any -> true, TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE);
		result.getRegistration().cancel();

		StepVerifier.create(result.getUpdates().map(Message::getPayload)).expectNext(TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE)
				.verifyTimeout(Duration.ofMillis(500));
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_RecoverableSubscriptionThrowsRecoverableExceptionWithMetaData_Then_RecoverableExceptionInEmittedResult()
			throws Exception {
		SubscriptionQueryMessage<String, List<String>, String> subscriptionQueryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));
		SubscriptionQueryMessage<String, List<String>, String> recoverableSubscriptionQueryMessage = subscriptionQueryMessage
				.withMetaData(Map.of(RecoverableResponse.RECOVERABLE_UPDATE_TYPE_KEY,
						ResponseTypes.instanceOf(String.class)));
		when(pep.handleSubscriptionQueryUpdateMessage(any(SubscriptionQueryMessage.class),
				any(SubscriptionQueryUpdateMessage.class))).thenThrow(new RecoverableException());

		UpdateHandlerRegistration<Object> result = testSubject
				.registerUpdateHandler(recoverableSubscriptionQueryMessage, 1024);

		ExecutorService executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		for (int i = 0; i < 5; i++) {
			executors.submit(() -> testSubject.emit(q -> true, TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));
		}
		executors.shutdown();

		StepVerifier.create(result.getUpdates().map(Message::getPayload)).expectNextCount(5)
				.then(() -> testSubject.complete(q -> true)).verifyComplete();
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_RecoverableSubscriptionWithoutMetaData_Then_RecoverableException() throws Exception {
		SubscriptionQueryMessage<String, List<String>, String> subscriptionQueryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));
		SubscriptionQueryMessage<String, List<String>, String> recoverableSubscriptionQueryMessage = subscriptionQueryMessage
				.withMetaData(Map.of("test", ResponseTypes.instanceOf(String.class)));
		when(pep.handleSubscriptionQueryUpdateMessage(any(SubscriptionQueryMessage.class),
				any(SubscriptionQueryUpdateMessage.class))).thenThrow(new RecoverableException());

		UpdateHandlerRegistration<Object> result = testSubject
				.registerUpdateHandler(recoverableSubscriptionQueryMessage, 1024);

		ExecutorService executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		executors.submit(() -> testSubject.emit(q -> true, TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));
		executors.shutdown();

		StepVerifier.create(result.getUpdates().map(Message::getPayload)).expectError(RecoverableException.class)
				.verify();
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_RecoverableSubscriptionThrowsException_Then_EmitException() throws Exception {
		SubscriptionQueryMessage<String, List<String>, String> subscriptionQueryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));
		SubscriptionQueryMessage<String, List<String>, String> recoverableSubscriptionQueryMessage = subscriptionQueryMessage;
		when(pep.handleSubscriptionQueryUpdateMessage(any(SubscriptionQueryMessage.class),
				any(SubscriptionQueryUpdateMessage.class))).thenThrow(new Exception());

		UpdateHandlerRegistration<Object> result = testSubject
				.registerUpdateHandler(recoverableSubscriptionQueryMessage, 1024);

		ExecutorService executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		executors.submit(() -> testSubject.emit(q -> true, TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));
		executors.shutdown();
		StepVerifier.create(result.getUpdates().map(Message::getPayload)).expectError(Exception.class).verify();
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_ErrorEmitOnCompletedUpdateFlux_Then_NoErrorEmitted() throws Exception {
		SubscriptionQueryMessage<String, List<String>, String> subscriptionQueryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));
		SubscriptionQueryMessage<String, List<String>, String> recoverableSubscriptionQueryMessage = subscriptionQueryMessage;
		when(pep.handleSubscriptionQueryUpdateMessage(any(SubscriptionQueryMessage.class),
				any(SubscriptionQueryUpdateMessage.class))).thenThrow(new Exception());
		UpdateHandlerRegistration<Object> result = testSubject
				.registerUpdateHandler(recoverableSubscriptionQueryMessage, 1024);
		result.complete();
		testSubject.emit(q -> true, TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE);
		StepVerifier.create(result.getUpdates().map(Message::getPayload)).expectComplete().verify();
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_RecoverableSubscription_Then_ReturnRecoverableSubscription() throws Exception {
		SubscriptionQueryMessage<String, List<String>, String> subscriptionQueryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));
		SubscriptionQueryMessage<String, List<String>, String> recoverableSubscriptionQueryMessage = subscriptionQueryMessage
				.withMetaData(Map.of(RecoverableResponse.RECOVERABLE_UPDATE_TYPE_KEY,
						ResponseTypes.instanceOf(String.class)));
		when(pep.handleSubscriptionQueryUpdateMessage(any(SubscriptionQueryMessage.class),
				any(SubscriptionQueryUpdateMessage.class))).thenReturn(
				new GenericSubscriptionQueryUpdateMessage<>(TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));

		UpdateHandlerRegistration<Object> result = testSubject
				.registerUpdateHandler(recoverableSubscriptionQueryMessage, 1024);

		ExecutorService executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		for (int i = 0; i < 5; i++) {
			executors.submit(() -> testSubject.emit(q -> true, TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));
		}
		executors.shutdown();

		assertTrue(testSubject.queryUpdateHandlerRegistered(recoverableSubscriptionQueryMessage));

		StepVerifier.create(result.getUpdates().map(Message::getPayload)).expectNextCount(5)
				.then(() -> testSubject.complete(q -> true)).verifyComplete();
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_RecoverableSubscriptionCompleteExceptionally_Then_ReturnError() throws Exception {
		Throwable expected = new Throwable("oops");
		SubscriptionQueryMessage<String, List<String>, String> subscriptionQueryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));
		SubscriptionQueryMessage<String, List<String>, String> recoverableSubscriptionQueryMessage = subscriptionQueryMessage
				.withMetaData(Map.of(RecoverableResponse.RECOVERABLE_UPDATE_TYPE_KEY,
						ResponseTypes.instanceOf(String.class)));
		when(pep.handleSubscriptionQueryUpdateMessage(any(SubscriptionQueryMessage.class),
				any(SubscriptionQueryUpdateMessage.class))).thenReturn(
				new GenericSubscriptionQueryUpdateMessage<>(TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));

		UpdateHandlerRegistration<Object> result = testSubject
				.registerUpdateHandler(recoverableSubscriptionQueryMessage, 1024);

		ExecutorService executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		executors.submit(() -> testSubject.emit(q -> true, TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE));
		executors.shutdown();

		StepVerifier.create(result.getUpdates()).expectNextCount(1)
				.then(() -> testSubject.completeExceptionally(q -> true, expected)).verifyError();
	}

	@Test
	void when_UnitOfWorkIsStartedAndTaskIsAdded_Then_TaskIsQueued() {
		SubscriptionQueryMessage<String, List<String>, String> subscriptionQueryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));
		var uow = DefaultUnitOfWork.startAndGet(subscriptionQueryMessage);

		var unitOfWorkTasks = uow.resources();
		assertEquals(0, unitOfWorkTasks.size());
		testSubject.complete(q -> true);
		assertEquals(1, unitOfWorkTasks.size());
		uow.commit();// Cleanup to not interfere with other tests

	}

	@Test
	void when_UpdateHandlerRegistered_Then_ActiveSubscriptionsAreReturned() {
		SubscriptionQueryMessage<String, List<String>, String> subscriptionQueryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));
		testSubject.registerUpdateHandler(subscriptionQueryMessage, 1024);

		assertTrue(testSubject.activeSubscriptions().contains(subscriptionQueryMessage));
	}
}