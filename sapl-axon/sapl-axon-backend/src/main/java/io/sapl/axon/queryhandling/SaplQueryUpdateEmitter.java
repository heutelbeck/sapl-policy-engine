

/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sapl.axon.queryhandling;

import static org.axonframework.common.BuilderUtils.assertNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.queryhandling.SinkWrapper;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandlerRegistration;

import io.sapl.axon.client.exception.RecoverableException;
import io.sapl.axon.client.recoverable.RecoverableResponse;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Sinks;



/**
 * copy of SimpleQueryUpdateEmitter from axon framework with some extensions for SAPL uses
 *
 * changes in registerUpdateHandler(SubscriptionQueryMessage<?, ?, ?> query, int updateBufferSize)
 * 		  and doEmit(SubscriptionQueryMessage<?, ?, ?> query, SinkWrapper<?> updateHandler,
 *	                            SubscriptionQueryUpdateMessage<U> update)
 *
 * Implementation of {@link QueryUpdateEmitter} that uses Project Reactor to implement Update Handlers.
 */
@Slf4j
@SuppressWarnings("deprecation") // Enherited from Axon
public class SaplQueryUpdateEmitter implements QueryUpdateEmitter {

    private static final String QUERY_UPDATE_TASKS_RESOURCE_KEY = "/update-tasks";

    private final MessageMonitor<? super SubscriptionQueryUpdateMessage<?>> updateMessageMonitor;

    private final ConcurrentMap<SubscriptionQueryMessage<?, ?, ?>, SinkWrapper<?>> updateHandlers =
            new ConcurrentHashMap<>();
    private final List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>>> dispatchInterceptors =
            new CopyOnWriteArrayList<>();

    private final QueryPolicyEnforcementPoint policyEnforcementPoint;

    /**
     * Instantiate a {@link SimpleQueryUpdateEmitter} based on the fields contained in the {@link Builder}.
     *
     */
	protected SaplQueryUpdateEmitter(MessageMonitor<? super SubscriptionQueryUpdateMessage<?>> updateMessageMonitor,
			QueryPolicyEnforcementPoint policyEnforcementPoint) {
		this.updateMessageMonitor = updateMessageMonitor;
		this.policyEnforcementPoint = policyEnforcementPoint;
	}

    /**
     * Instantiate a Builder to be able to create a {@link SimpleQueryUpdateEmitter}.
     * <p>
     * The {@link MessageMonitor} is defaulted to a {@link NoOpMessageMonitor}.
     *
     * @return a Builder to be able to create a {@link SimpleQueryUpdateEmitter}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Check if a {@link SubscriptionQueryUpdateMessage} which has the same MessageIdentifier as the given {@link SubscriptionQueryUpdateMessage}
     * is already registered.
     * @param query {@link SubscriptionQueryUpdateMessage} which MessageIdentifier will be checked if it matches the MessageIdentifier of any already registered {@link SubscriptionQueryUpdateMessage}
     * @return if a {@link SubscriptionQueryUpdateMessage} with the same MessageIdentifier as the given {@link SubscriptionQueryUpdateMessage} is already registered
     */
    @Override
    public boolean queryUpdateHandlerRegistered(SubscriptionQueryMessage<?, ?, ?> query) {
        return updateHandlers.keySet()
                .stream()
                .anyMatch(m -> m.getIdentifier().equals(query.getIdentifier()));
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated in favour of using {{@link #registerUpdateHandler(SubscriptionQueryMessage, int)}}
     */


    @Override
    @Deprecated
    public <U> UpdateHandlerRegistration<U> registerUpdateHandler(SubscriptionQueryMessage<?, ?, ?> query,
                                                                  SubscriptionQueryBackpressure backpressure,
                                                                  int updateBufferSize) {
        EmitterProcessor<SubscriptionQueryUpdateMessage<U>> processor = EmitterProcessor.create(updateBufferSize);
        FluxSink<SubscriptionQueryUpdateMessage<U>> sink = processor.sink(backpressure.getOverflowStrategy());
        sink.onDispose(() -> updateHandlers.remove(query));
        FluxSinkWrapper<SubscriptionQueryUpdateMessage<U>> fluxSinkWrapper = new FluxSinkWrapper<>(sink);
        updateHandlers.put(query, fluxSinkWrapper);
        Registration registration = () -> {
            updateHandlers.remove(query);
            return true;
        };

        return new UpdateHandlerRegistration<>(registration,
                processor.replay(updateBufferSize).autoConnect(),
                fluxSinkWrapper::complete);
    }

    /**
     * Register a {@link SubscriptionQueryMessage} to receive {@link SubscriptionQueryUpdateMessage} for the given {@link ResponseType} U
     * <p></p>
     * <p>
     *     If the {@link SubscriptionQueryMessage} has a key {@link RecoverableResponse#RECOVERABLE_UPDATE_TYPE_KEY} in it's Metadata
     *     which returns an Object of Type {@link ResponseType}, then this ResponseType U is the original UpdateResponseType U and a new
     *     {@link SubscriptionQueryMessage} will be created and registered, which differs from the received {@link SubscriptionQueryMessage} only in the ResponseType U
     * </p>
     * <p></p><p>
     *     Transformation of {@link SubscriptionQueryUpdateMessage} to the correct Types will be handled internal during {@link #doEmit(SubscriptionQueryMessage, SinkWrapper, SubscriptionQueryUpdateMessage)}
     *     and at the {@link io.sapl.axon.client.gateway.SaplQueryGateway} on the Client-site.

     * </p>
     * @param query {@link SubscriptionQueryMessage} which will be registered in a {@link ConcurrentHashMap} linked with a {@link SinkWrapper}
     *                                              to receive {@link SubscriptionQueryUpdateMessage} on a {@link Flux} created from the {@link SinkWrapper}
     * @param updateBufferSize Buffersize of the {@link Flux} on which {@link SubscriptionQueryUpdateMessage} are published
     * @param <U> Type of the Updates which are published on the Flux of the {@link UpdateHandlerRegistration}
     * @return A {@link UpdateHandlerRegistration} which can be used to remove the Registration of the {@link SubscriptionQueryMessage}, call {@link SinksManyWrapper#complete()}
     * and to subscribe to the {@link Flux} created from the {@link SinkWrapper} which receives the {@link SubscriptionQueryUpdateMessage} for the given {@link SubscriptionQueryMessage}
     */
    @Override
	public <U> UpdateHandlerRegistration<U> registerUpdateHandler(SubscriptionQueryMessage<?, ?, ?> query,
			int updateBufferSize) {
		ResponseType<?> originalUpdateResponseType = (ResponseType<?>) query.getMetaData().get(RecoverableResponse.RECOVERABLE_UPDATE_TYPE_KEY);

		Sinks.Many<SubscriptionQueryUpdateMessage<U>> sink = Sinks.many().replay().limit(updateBufferSize);
		SinksManyWrapper<SubscriptionQueryUpdateMessage<U>> sinksManyWrapper = new SinksManyWrapper<>(sink);

		Runnable removeHandler;

		if (originalUpdateResponseType != null) {
			Message<?> genericMessage = new GenericMessage<>(query.getIdentifier(), query.getPayload(),
					query.getMetaData());
			SubscriptionQueryMessage<?, ?, ?> originalSubscriptionQuery = new GenericSubscriptionQueryMessage<>(
					genericMessage, query.getQueryName(), query.getResponseType(), originalUpdateResponseType);
			removeHandler = createRemoveHandler(originalSubscriptionQuery);
			updateHandlers.put(originalSubscriptionQuery, sinksManyWrapper);

			policyEnforcementPoint.addSubscriptionQueryMessage(originalSubscriptionQuery);
		} else {
			removeHandler = createRemoveHandler(query);
			updateHandlers.put(query, sinksManyWrapper);

			policyEnforcementPoint.addSubscriptionQueryMessage(query);
		}

		Registration registration = () -> {
			removeHandler.run();
			return true;
		};
		Flux<SubscriptionQueryUpdateMessage<U>> updateMessageFlux = sink.asFlux().doOnCancel(removeHandler)
				.doOnTerminate(removeHandler);

		return new UpdateHandlerRegistration<>(registration, updateMessageFlux, sinksManyWrapper::complete);
	}

    private Runnable createRemoveHandler(SubscriptionQueryMessage<?,?,?> subscriptionQueryMessage){
        return ()-> {

            SinkWrapper<?> wrappedSink = updateHandlers.remove(subscriptionQueryMessage);
            if (wrappedSink == null) return;
            policyEnforcementPoint.removeSubscription(subscriptionQueryMessage.getIdentifier());
        };
    }

    /**
     * Filters all registered {@link SubscriptionQueryMessage} and publishes the given {@link SubscriptionQueryUpdateMessage}
     * on the {@link SinkWrapper} which is assigned to each registered {@link SubscriptionQueryMessage}.
     * <p></p>
     * <p>
     * Every {@link SubscriptionQueryMessage} needs to have an entry in the {@link QueryPolicyEnforcementPoint} to determine if the {@link SubscriptionQueryUpdateMessage}
     * can be published on the assigned {@link SinkWrapper} and which
     * {@link io.sapl.axon.annotations.ConstraintHandler} needs to be called for each recipient before the {@link SubscriptionQueryUpdateMessage} is published.
     * </p>
     * <p></p>
     * <p>
     * When there is no entry in the {@link QueryPolicyEnforcementPoint} for a {@link SubscriptionQueryMessage} the {@link SubscriptionQueryUpdateMessage} is dropped for
     * the {@link SubscriptionQueryMessage} and not published on the corresponding {@link SinkWrapper}.
     * </p>
     * @param filter {@link Predicate} which will be applied as filter for all registered {@link SubscriptionQueryMessage}
     *                               to determine, which {@link SubscriptionQueryMessage} should receive the given {@link SubscriptionQueryUpdateMessage}
     * @param update {@link SubscriptionQueryUpdateMessage} which will be published on the {@link SinkWrapper} of the filtered registered {@link SubscriptionQueryMessage}
     * @param <U> Type of the {@link SubscriptionQueryUpdateMessage}
     */
    @Override
    public <U> void emit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
                         SubscriptionQueryUpdateMessage<U> update) {
        runOnAfterCommitOrNow(() -> doEmit(filter, intercept(update)));
    }

    @SuppressWarnings("unchecked")
    //Cast to SubscriptionQueryUpdateMessage<U>, because MessageDispatchInterceptor.handle() always returns an Object of the same Type as the InputType
    private <U> SubscriptionQueryUpdateMessage<U> intercept(SubscriptionQueryUpdateMessage<U> message) {
        SubscriptionQueryUpdateMessage<U> intercepted = message;
        for (MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>> interceptor : dispatchInterceptors) {
            intercepted = (SubscriptionQueryUpdateMessage<U>) interceptor.handle(intercepted);
        }
        return intercepted;
    }

    /**
     * Filters all currently registered {@link SubscriptionQueryUpdateMessage} with the given {@link Predicate} and sends a {@link SinkWrapper#complete()} signal
     * on the given {@link SinkWrapper}
     * @param filter given Predicate which is applied as filter for all registered {@link SubscriptionQueryUpdateMessage}
     */
    @Override
    public void complete(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter) {
        runOnAfterCommitOrNow(() -> doComplete(filter));
    }

    /**
     * Filters all currently registered {@link SubscriptionQueryUpdateMessage} with the given {@link Predicate} and emits an error on the {@link SinkWrapper} with the given
     * {@link Throwable} as Cause
     * @param filter given Predicate which is applied as filter for all registered {@link SubscriptionQueryUpdateMessage}
     * @param cause the given cause, which will be emitted by the {@link SinkWrapper} as error for the {@link SubscriptionQueryUpdateMessage}
     */
    @Override
    public void completeExceptionally(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter, Throwable cause) {
        runOnAfterCommitOrNow(() -> doCompleteExceptionally(filter, cause));
    }

    /**
     * Register a {@link MessageDispatchInterceptor} for {@link SubscriptionQueryUpdateMessage}
     * <p>
     * All registered {@link MessageDispatchInterceptor} will intercept an emitted {@link SubscriptionQueryUpdateMessage}
     * before the intercepted {@link SubscriptionQueryUpdateMessage} will be published on the {@link SinkWrapper} of the registed {@link SubscriptionQueryUpdateMessage}
     * </p>
     * @param interceptor A {@link MessageDispatchInterceptor}, which {@link MessageDispatchInterceptor#handle(Message)} function will be called
     *                    with an {@link SubscriptionQueryUpdateMessage} emitted as Parameter
     * @return A {@link Registration}, which can be called to remove the registered {@link MessageDispatchInterceptor} from the list of registered {@link MessageDispatchInterceptor}
     */
    @Override
    public Registration registerDispatchInterceptor(
            MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>> interceptor) {
        dispatchInterceptors.add(interceptor);
        return () -> dispatchInterceptors.remove(interceptor);
    }

    @SuppressWarnings("unchecked")
    private <U> void doEmit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
                            SubscriptionQueryUpdateMessage<U> update) {
        updateHandlers.keySet()
                .stream()
                .filter(sqm -> filter.test((SubscriptionQueryMessage<?, ?, U>) sqm))
                .forEach(query -> Optional.ofNullable(updateHandlers.get(query))
                        .ifPresent(uh -> doEmit(query, uh, update)));
    }

	@SuppressWarnings("unchecked")
	private <U> void doEmit(SubscriptionQueryMessage<?, ?, ?> query, SinkWrapper<?> updateHandler,
			SubscriptionQueryUpdateMessage<U> update) {
		MessageMonitor.MonitorCallback monitorCallback = updateMessageMonitor.onMessageIngested(update);
		try {
			var updateObj = policyEnforcementPoint.handleSubscriptionQueryUpdateMessage(query, update);

			if (query.getMetaData().containsKey(RecoverableResponse.RECOVERABLE_UPDATE_TYPE_KEY)) {
				var recoverableResponse = new RecoverableResponse(updateObj.getPayload(), null);

				SubscriptionQueryUpdateMessage<RecoverableResponse> recoverableUpdateObj = GenericSubscriptionQueryUpdateMessage
						.asUpdateMessage(recoverableResponse);

				((SinkWrapper<SubscriptionQueryUpdateMessage<RecoverableResponse>>) updateHandler)
						.next(recoverableUpdateObj);
			} else if (updateObj != null) {
				((SinkWrapper<SubscriptionQueryUpdateMessage<U>>) updateHandler).next(updateObj);
			}
			// no nothing (DropWileDenied)
			monitorCallback.reportSuccess();
		} catch (RecoverableException e) {
			if (query.getMetaData().containsKey(RecoverableResponse.RECOVERABLE_UPDATE_TYPE_KEY)) {

				var recoverableResponse = new RecoverableResponse(null, e);
				SubscriptionQueryUpdateMessage<RecoverableResponse> recoverableResponseWithException = GenericSubscriptionQueryUpdateMessage
						.asUpdateMessage(recoverableResponse);

				((SinkWrapper<SubscriptionQueryUpdateMessage<RecoverableResponse>>) updateHandler)
						.next(recoverableResponseWithException);
			} else {
				handleEmitException(query, e, monitorCallback);
				emitError(query, e, updateHandler);
			}
		} catch (Exception e) {
			handleEmitException(query, e, monitorCallback);
			emitError(query, e, updateHandler);
		}
	}

	private void handleEmitException(SubscriptionQueryMessage<?, ?, ?> query, Exception e,
			MessageMonitor.MonitorCallback monitorCallback) {
		monitorCallback.reportFailure(e);
		updateHandlers.remove(query);
		policyEnforcementPoint.removeSubscription(query.getIdentifier());
	}

    private void doComplete(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter) {
        updateHandlers.keySet()
                .stream()
                .filter(filter)
                .forEach(query -> Optional.ofNullable(updateHandlers.get(query))
                        .ifPresent(SinkWrapper::complete));
    }

    private void emitError(SubscriptionQueryMessage<?, ?, ?> query, Throwable cause,
                           SinkWrapper<?> updateHandler) {
        try {
            updateHandler.error(cause);

        } catch (Exception e) {
            log.error("An error happened while trying to inform update handler about the error. Query: {}",
                    query);
        }
    }

    private void doCompleteExceptionally(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter, Throwable cause) {
        updateHandlers.keySet()
                .stream()
                .filter(filter)
                .forEach(query -> Optional.ofNullable(updateHandlers.get(query))
                        .ifPresent(updateHandler -> emitError(query, cause, updateHandler)));
    }

    /**
     * Either runs the provided {@link Runnable} immediately or adds it to a {@link List} as a resource to the current
     * {@link UnitOfWork} if {@link SaplQueryUpdateEmitter#inStartedPhaseOfUnitOfWork} returns {@code true}. This is
     * done to ensure any emitter calls made from a message handling function are executed in the {@link
     * UnitOfWork.Phase#AFTER_COMMIT} phase.
     * <p>
     * The latter check requires the current UnitOfWork's phase to be {@link UnitOfWork.Phase#STARTED}. This is done to
     * allow users to circumvent their {@code queryUpdateTask} being handled in the AFTER_COMMIT phase. They can do this
     * by retrieving the current UnitOfWork and performing any of the {@link QueryUpdateEmitter} calls in a different
     * phase.
     *
     * @param queryUpdateTask a {@link Runnable} to be ran immediately or as a resource if {@link
     *                        SaplQueryUpdateEmitter#inStartedPhaseOfUnitOfWork} returns {@code true}
     */
    private void runOnAfterCommitOrNow(Runnable queryUpdateTask) {
        if (inStartedPhaseOfUnitOfWork()) {
            UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
            unitOfWork.getOrComputeResource(
                    this + QUERY_UPDATE_TASKS_RESOURCE_KEY,
                    resourceKey -> {
                        List<Runnable> queryUpdateTasks = new ArrayList<>();
                        unitOfWork.afterCommit(uow -> queryUpdateTasks.forEach(Runnable::run));
                        return queryUpdateTasks;
                    }
            ).add(queryUpdateTask);
        } else {
            queryUpdateTask.run();
        }
    }

    /**
     * Return {@code true} if the {@link CurrentUnitOfWork#isStarted()} returns {@code true} and in if the phase is
     * {@link UnitOfWork.Phase#STARTED}, otherwise {@code false}.
     *
     * @return {@code true} if the {@link CurrentUnitOfWork#isStarted()} returns {@code true} and in if the phase is
     * {@link UnitOfWork.Phase#STARTED}, otherwise {@code false}.
     */
    private boolean inStartedPhaseOfUnitOfWork() {
        return CurrentUnitOfWork.isStarted() && UnitOfWork.Phase.STARTED == CurrentUnitOfWork.get().phase();
    }


    /**
     * @return A Set of the currently registered updateHandlers, which can receive SubscriptionQueryUpdates
     */
    @Override
    public Set<SubscriptionQueryMessage<?, ?, ?>> activeSubscriptions() {
        return Collections.unmodifiableSet(updateHandlers.keySet());
    }



    /**
     * Builder class to instantiate a {@link SimpleQueryUpdateEmitter}.
     * <p>
     * The {@link MessageMonitor} is defaulted to a {@link NoOpMessageMonitor}.
     */
    public static class Builder {

        private MessageMonitor<? super SubscriptionQueryUpdateMessage<?>> updateMessageMonitor =
                NoOpMessageMonitor.INSTANCE;

        private QueryPolicyEnforcementPoint policyEnforcementPoint;

        /**
         * Sets the {@link MessageMonitor} used to monitor {@link SubscriptionQueryUpdateMessage}s being processed.
         * Defaults to a {@link NoOpMessageMonitor}.
         *
         * @param updateMessageMonitor the {@link MessageMonitor} used to monitor {@link SubscriptionQueryUpdateMessage}s
         *                             being processed
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder updateMessageMonitor(
                MessageMonitor<? super SubscriptionQueryUpdateMessage<?>> updateMessageMonitor) {
            assertNonNull(updateMessageMonitor, "MessageMonitor may not be null");
            this.updateMessageMonitor = updateMessageMonitor;
            return this;
        }

        public Builder policyEnforcementPoint(QueryPolicyEnforcementPoint policyEnforcementPoint) {
            this.policyEnforcementPoint = policyEnforcementPoint;
            return this;
        }


        /**
         * Initializes a {@link SimpleQueryUpdateEmitter} as specified through this Builder.
         *
         * @return a {@link SimpleQueryUpdateEmitter} as specified through this Builder
         */
        public SaplQueryUpdateEmitter build() {
            validate();
            return new SaplQueryUpdateEmitter(updateMessageMonitor,policyEnforcementPoint);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(this.policyEnforcementPoint,"PolicyEnforcementePoint is a hard requirement");
        }
    }
}

