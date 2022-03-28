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

/**
 * partly copied from package package org.axonframework.queryhandling;
 * copied parts will be marked
 */

package io.sapl.axon.client.gateway;

import static org.axonframework.common.BuilderUtils.assertNonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.BuilderUtils;
import org.axonframework.common.Registration;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.DefaultSubscriptionQueryResult;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;

import io.sapl.axon.client.exception.OriginalUpdateTypeRemoved;
import io.sapl.axon.client.exception.RecoverableException;
import io.sapl.axon.client.recoverable.RecoverableResponse;
import lombok.extern.slf4j.Slf4j;
import reactor.util.concurrent.Queues;

/**
 * Implementation of the QueryGateway Interface, with additional methods for
 * SubscriptionQueryMessages.
 */

@Slf4j
@SuppressWarnings("deprecation") // Inherited from Axon
public class SaplQueryGateway implements QueryGateway {
	private final QueryBus queryBus;
	private final List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors;

	protected SaplQueryGateway(QueryBus queryBus,
			List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors) {
		this.queryBus = queryBus;
		this.dispatchInterceptors = dispatchInterceptors;
	}

	/**
	 * Will return a new {@link SaplQueryGateway.Builder}.
	 */
	public static SaplQueryGateway.Builder builder() {
		return new SaplQueryGateway.Builder();
	}

	/**
	 * copied from org.axonframework.queryhandling.DefaultQueryGateway
	 */
	public <R, Q> CompletableFuture<R> query(String queryName, Q query, ResponseType<R> responseType) {
		CompletableFuture<QueryResponseMessage<R>> queryResponse = this.queryBus.query(this.processInterceptors(
				new GenericQueryMessage<>(GenericMessage.asMessage(query), queryName, responseType)));
		CompletableFuture<R> result = new CompletableFuture<>();
		queryResponse
				.exceptionally(cause -> GenericQueryResponseMessage
						.asResponseMessage(responseType.responseMessagePayloadType(), cause))
				.thenAccept((queryResponseMessage) -> {
					try {
						if (queryResponseMessage.isExceptional()) {
							result.completeExceptionally(queryResponseMessage.exceptionResult());
						} else {
							result.complete(queryResponseMessage.getPayload());
						}
					} catch (Exception var3) {
						result.completeExceptionally(var3);
					}

				});
		return result;
	}

	/**
	 * copied from org.axonframework.queryhandling.DefaultQueryGateway
	 */
	public <R, Q> Stream<R> scatterGather(String queryName, Q query, ResponseType<R> responseType, long timeout,
			TimeUnit timeUnit) {

		GenericQueryMessage<?, R> queryMessage = new GenericQueryMessage<>(GenericMessage.asMessage(query), queryName,
				responseType);
		return this.queryBus.scatterGather(this.processInterceptors(queryMessage), timeout, timeUnit)
				.map(Message::getPayload);
	}

	/** @deprecated */
	@Deprecated
	public <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(String queryName, Q query,
			ResponseType<I> initialResponseType, ResponseType<U> updateResponseType,
			SubscriptionQueryBackpressure backpressure, int updateBufferSize) {
		SubscriptionQueryMessage<?, I, U> interceptedQuery = this.getSubscriptionQueryMessage(queryName, query,
				initialResponseType, updateResponseType);
		SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> result = this.queryBus
				.subscriptionQuery(interceptedQuery, backpressure, updateBufferSize);
		return this.getSubscriptionQueryResult(result);
	}

	public <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(String queryName, Q query,
			ResponseType<I> initialResponseType, ResponseType<U> updateResponseType, int updateBufferSize) {
		SubscriptionQueryMessage<?, I, U> interceptedQuery = this.getSubscriptionQueryMessage(queryName, query,
				initialResponseType, updateResponseType);
		SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> result = this.queryBus
				.subscriptionQuery(interceptedQuery, updateBufferSize);
		return this.getSubscriptionQueryResult(result);
	}

	/**
	 * copied from org.axonframework.queryhandling.DefaultQueryGateway
	 */
	private <Q, I, U> SubscriptionQueryMessage<?, I, U> getSubscriptionQueryMessage(String queryName, Q query,
			ResponseType<I> initialResponseType, ResponseType<U> updateResponseType) {
		SubscriptionQueryMessage<?, I, U> subscriptionQueryMessage = new GenericSubscriptionQueryMessage<>(
				GenericMessage.asMessage(query), queryName, initialResponseType, updateResponseType);
		return this.processInterceptors(subscriptionQueryMessage);
	}

	private <I, U> DefaultSubscriptionQueryResult<I, U> getSubscriptionQueryResult(
			SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> result) {
		return new DefaultSubscriptionQueryResult<>(
				result.initialResult().filter((initialResult) -> Objects.nonNull(initialResult.getPayload()))
						.map(Message::getPayload)
						.onErrorMap((e) -> e instanceof IllegalPayloadAccessException ? e.getCause() : e),

				result.updates().filter(update -> Objects.nonNull(update.getPayload())).map(Message::getPayload),

				result);
	}

	/**
	 * Gets the QueryMessage, InitialResponseType, UpdateResponseType and
	 * RecoverableExcpetion. It returns a SubscriptionQueryResult.
	 * 
	 * @param <Q>                 PayloadType of the Query
	 * @param <I>                 Initial Response Type
	 * @param <U>                 Update Response Type
	 * @param query               QueryMessage
	 * @param initialResponseType the Type of the initial Response
	 * @param updateResponseType  the Type of the update Response
	 * @param errorConsumer       Consumer which is called on RecoverableException
	 * @return SubscriptionQueryResult
	 */
	public <Q, I, U> SubscriptionQueryResult<I, U> recoverableSubscriptionQuery(Q query, Class<I> initialResponseType,
			Class<U> updateResponseType, Consumer<RecoverableException> errorConsumer) {
		return this.recoverableSubscriptionQuery(QueryMessage.queryName(query), query, initialResponseType,
				updateResponseType, errorConsumer);
	}

	/**
	 * Gets the QueryName, QueryMessage, InitialResponseType, UpdateResponseType and
	 * RecoverableExcpetion. It returns a SubscriptionQueryResult.
	 * 
	 * @param <Q>                 PayloadType of the Query
	 * @param <I>                 Initial Response Type
	 * @param <U>                 Update Response Type
	 * @param queryName           the Name of the Query
	 * @param query               QueryMessage
	 * @param initialResponseType the Type of the initial Response
	 * @param updateResponseType  the Type of the update Response
	 * @param errorConsumer       Consumer which is called on RecoverableException
	 * @return SubscriptionQueryResult
	 */
	public <Q, I, U> SubscriptionQueryResult<I, U> recoverableSubscriptionQuery(String queryName, Q query,
			Class<I> initialResponseType, Class<U> updateResponseType, Consumer<RecoverableException> errorConsumer) {
		return this.recoverableSubscriptionQuery(queryName, query, ResponseTypes.instanceOf(initialResponseType),
				ResponseTypes.instanceOf(updateResponseType), errorConsumer);
	}

	/**
	 * Gets the QueryMessage, InitialResponseType, UpdateResponseType and
	 * RecoverableExcpetion. It returns a SubscriptionQueryResult.
	 * 
	 * @param <Q>                 PayloadType of the Query
	 * @param <I>                 Initial Response Type
	 * @param <U>                 Update Response Type
	 * @param query               QueryMessage
	 * @param initialResponseType the Type of the initial Response
	 * @param updateResponseType  the Type of the update Response
	 * @param errorConsumer       Consumer which is called on RecoverableException
	 * @return SubscriptionQueryResult
	 */

	public <Q, I, U> SubscriptionQueryResult<I, U> recoverableSubscriptionQuery(Q query,
			ResponseType<I> initialResponseType, ResponseType<U> updateResponseType,
			Consumer<RecoverableException> errorConsumer) {
		return this.recoverableSubscriptionQuery(QueryMessage.queryName(query), query, initialResponseType,
				updateResponseType, errorConsumer);
	}

	/**
	 * Gets the QueryName, QueryMessage, InitialResponseType, UpdateResponseType and
	 * RecoverableExcpetion. It returns a SubscriptionQueryResult.
	 * 
	 * @param <Q>                 PayloadType of the Query
	 * @param <I>                 Initial Response Type
	 * @param <U>                 Update Response Type
	 * @param queryName           the Name of the Query
	 * @param query               QueryMessage
	 * @param initialResponseType the Type of the initial Response
	 * @param updateResponseType  the Type of the update Response
	 * @param errorConsumer       Consumer which is called on RecoverableException
	 * @return SubscriptionQueryResult
	 */
	public <Q, I, U> SubscriptionQueryResult<I, U> recoverableSubscriptionQuery(String queryName, Q query,
			ResponseType<I> initialResponseType, ResponseType<U> updateResponseType,
			Consumer<RecoverableException> errorConsumer) {
		return this.recoverableSubscriptionQuery(queryName, query, initialResponseType, updateResponseType,
				Queues.SMALL_BUFFER_SIZE, errorConsumer);
	}

	/**
	 * Gets the QueryName, the QueryMessage, InitialResponseRype,
	 * UpdateResponseType, UpdateBufferSize, and RecoverableExcpetion. It transforms
	 * the SubscriptionQueryMessage into a RecoverableSubscriptionQueryMessage and
	 * returns the RecoverableSubscriptionQuery Result.
	 * 
	 * @param <Q>                 PayloadType of the Query
	 * @param <I>                 Initial Response Type
	 * @param <U>                 Update Response Type
	 * @param queryName           the Name of the Query
	 * @param query               QueryMessage
	 * @param initialResponseType the Type of the initial Response
	 * @param updateResponseType  the Type of the update Response
	 * @param updateBufferSize    the Buffer Size for Updates
	 * @param errorConsumer       Consumer which is called on RecoverableException
	 * @return the RecoverableSubscriptionQuery Result
	 */
	public <Q, I, U> SubscriptionQueryResult<I, U> recoverableSubscriptionQuery(String queryName, Q query,
			ResponseType<I> initialResponseType, ResponseType<U> updateResponseType, int updateBufferSize,
			Consumer<RecoverableException> errorConsumer) {
		SubscriptionQueryMessage<?, I, U> interceptedQuery = this.getSubscriptionQueryMessage(queryName, query,
				initialResponseType, updateResponseType);

		SubscriptionQueryMessage<?, I, RecoverableResponse> subscriptionQueryMessage = transformToRecoverableSubscriptionQueryMessage(
				interceptedQuery, queryName, initialResponseType, ResponseTypes.instanceOf(RecoverableResponse.class));

		SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<RecoverableResponse>> result = this.queryBus
				.subscriptionQuery(subscriptionQueryMessage, updateBufferSize);

		return this.getRecoverableSubscriptionQueryResult(result, errorConsumer, updateResponseType);
	}

	private <I, U> SubscriptionQueryMessage<?, I, U> transformToRecoverableSubscriptionQueryMessage(
			SubscriptionQueryMessage<?, I, ?> interceptedQuery, String queryName, ResponseType<I> initialResponseType,
			ResponseType<U> recoverableResponseType) {

		MetaData originalMetaData = interceptedQuery.getMetaData();
		Map<String, Object> typeKeyToResponseType = Map.of(RecoverableResponse.RECOVERABLE_UPDATE_TYPE_KEY,
				interceptedQuery.getUpdateResponseType());

		MetaData mergedMetadata = originalMetaData.mergedWith(typeKeyToResponseType);
		Message<?> genericMessage = new GenericMessage<>(interceptedQuery.getIdentifier(),
				interceptedQuery.getPayload(), mergedMetadata);
		return new GenericSubscriptionQueryMessage<>(genericMessage, queryName, initialResponseType,
				recoverableResponseType);

	}

	private <I, U> DefaultSubscriptionQueryResult<I, U> getRecoverableSubscriptionQueryResult(
			SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<RecoverableResponse>> result,
			Consumer<RecoverableException> errorConsumer, ResponseType<U> originalUpdateResponseType) {

		return new DefaultSubscriptionQueryResult<>(
				result.initialResult()
				.filter((initialResult) -> Objects.nonNull(initialResult.getPayload()))
				.map(Message::getPayload)
				.onErrorMap(e -> e instanceof IllegalPayloadAccessException ? e.getCause() : e),

				result.updates()
				.filter(Objects::nonNull)
				.mapNotNull(recoverableResponse -> recoverableResponse(originalUpdateResponseType, recoverableResponse)).onErrorContinue(RecoverableException.class,
						((throwable, o) -> errorConsumer.accept((RecoverableException) throwable)))
						.doOnError(OriginalUpdateTypeRemoved.class, e -> {
							log.error("updateResponseType:  {} was erased from Metadata",
									originalUpdateResponseType.responseMessagePayloadType());
							log.error("Verify that you don't delete MetaData on the AxonServer with an Interceptor");
						}), result);
	}

	private <U> U recoverableResponse(ResponseType<U> originalUpdateResponseType,
			SubscriptionQueryUpdateMessage<RecoverableResponse> recoverableResponse) {
		RecoverableResponse response = recoverableResponse.getPayload();
		U originalUpdateResponseTypepayload = originalUpdateResponseType
				.convert(response.getOriginalUpdateResponseTypePayload());

		if (originalUpdateResponseTypepayload != null)
			return originalUpdateResponseTypepayload;
		throw response.getRecoverableException();
	}

	/**
	 * copied from org.axonframework.queryhandling.DefaultQueryGateway
	 */
	public Registration registerDispatchInterceptor(
			MessageDispatchInterceptor<? super QueryMessage<?, ?>> interceptor) {
		this.dispatchInterceptors.add(interceptor);
		return () -> this.dispatchInterceptors.remove(interceptor);
	}

	/**
	 * copied from org.axonframework.queryhandling.DefaultQueryGateway
	 */
	@SuppressWarnings("unchecked")
	private <T extends QueryMessage<?, ?>> T processInterceptors(T query) {
		for (MessageDispatchInterceptor<? super QueryMessage<?, ?>> interceptor : dispatchInterceptors) {
			query = (T) interceptor.handle(query);
		}
		return query;
	}

	/**
	 * Builder class to instantiate a {@link SaplQueryGateway}.
	 * <p>
	 * The {@code dispatchInterceptors} is defaulted to an empty list. The
	 * {@link QueryBus} is a <b>hard requirement</b> and as such should be provided.
	 */
	public static class Builder {
		private QueryBus queryBus;
		private List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();

		public Builder() {
		}

		/**
		 * Sets the {@link QueryBus} to deliver {@link QueryMessage}s on received in
		 * this {@link QueryGateway} implementation. Will assert that the
		 * {@link QueryBus} is not {@code null}, and will throw an
		 * {@link AxonConfigurationException} if it is {@code null}.
		 *
		 * @param queryBus a {@link QueryBus} to deliver {@link QueryMessage}s on
		 *                 received in this {@link QueryGateway} implementation
		 * @return the current Builder instance, for fluent interfacing
		 */
		public SaplQueryGateway.Builder queryBus(QueryBus queryBus) {
			BuilderUtils.assertNonNull(queryBus, "QueryBus may not be null");
			this.queryBus = queryBus;
			return this;
		}

		/**
		 * Sets the {@link List} of {@link MessageDispatchInterceptor}s for
		 * {@link QueryMessage}s. Are invoked when a query is being dispatched.
		 *
		 * @param dispatchInterceptors which are invoked when a query is being
		 *                             dispatched
		 * @return the current Builder instance, for fluent interfacing
		 */
		@SafeVarargs
		public final SaplQueryGateway.Builder dispatchInterceptors(
				MessageDispatchInterceptor<? super QueryMessage<?, ?>>... dispatchInterceptors) {
			return this.dispatchInterceptors(Arrays.asList(dispatchInterceptors));
		}

		/**
		 * Sets the {@link List} of {@link MessageDispatchInterceptor}s for
		 * {@link QueryMessage}s. Are invoked when a query is being dispatched.
		 *
		 * @param dispatchInterceptors which are invoked when a query is being
		 *                             dispatched
		 * @return the current Builder instance, for fluent interfacing
		 */
		public SaplQueryGateway.Builder dispatchInterceptors(
				List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors) {
			assertNonNull(dispatchInterceptors, "dispatchinterceptors may not be null, submit empty list instead");
			this.dispatchInterceptors = new CopyOnWriteArrayList<>(dispatchInterceptors);
			return this;
		}

		/**
		 * Initializes a {@link SaplQueryGateway} as specified through this Builder.
		 *
		 * @return a {@link SaplQueryGateway} as specified through this Builder
		 */
		public SaplQueryGateway build() {
			validate();
			return new SaplQueryGateway(queryBus, dispatchInterceptors);
		}

		/**
		 * Validates whether the fields contained in this Builder are set accordingly.
		 *
		 * @throws AxonConfigurationException if one field is asserted to be incorrect
		 *                                    according to the Builder's specifications
		 */
		protected void validate() throws AxonConfigurationException {
			assertNonNull(queryBus, "The QueryBus is a hard requirement and should be provided");
		}
	}
}
