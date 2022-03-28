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

package io.sapl.axon.constraints;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.DefaultParameterResolverFactory;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.spring.config.annotation.SpringBeanParameterResolverFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The AxonConstraintHandlerBundle is used by the
 * {@link ConstraintHandlerService}. It holds constraint handlers identified as
 * responsible for given constraints. These handlers are added by the
 * ConstraintHandlerService. It provides methods to execute them at different
 * stages of the message handling process.
 * 
 * @param <T> PayloadType of Messages
 * @param <R> ResultType of QueryMessages
 * @param <U> extends Message (CommandMessage<T>, QueryMessage<T, R>)
 */

public class AxonConstraintHandlerBundle<T, R, U extends Message<T>> {

	final List<Runnable> simpleRunnableHandlers = new LinkedList<>();

	final List<Supplier<Map<String, ?>>> addMetaDataHandlers           = new LinkedList<>();
	final List<Consumer<U>>              messageConsumerHandlers       = new LinkedList<>();
	final List<Function<T, T>>           messagePayloadMappingHandlers = new LinkedList<>();

	final List<Consumer<R>>    resultConsumerHandlers = new LinkedList<>();
	final List<Function<R, R>> resultMappingHandlers  = new LinkedList<>();

	final Map<JsonNode, List<Method>> aggregateRootHandlers     = new HashMap<>();
	final Map<JsonNode, List<Method>> aggregateMemberHandlers   = new HashMap<>();
	final Map<JsonNode, Boolean>      isObligationPerConstraint = new HashMap<>();

	/**
	 * Executes the given MessageHandlers provided by the HandlerProviders:
	 * {@link AxonRunnableConstraintHandlerProvider},
	 * {@link MessageConsumerConstraintHandlerProvider},
	 * {@link MetaDataSupplierConstraintHandlerProvider}.
	 * 
	 * @param message CommandMessage or QueryMessage
	 * @return mapped CommandMessage or mapped QueryMessage with MetaData appended
	 *         provided by handler
	 */
	public U executeMessageHandlers(U message) {
		runRunnables(simpleRunnableHandlers);
		acceptMessageConsumers(messageConsumerHandlers, message);
		U messageWithSuppliedMetaData = applyAddMetaDataHandlers(addMetaDataHandlers, message);
		return applyMessagePayloadMappers(messagePayloadMappingHandlers, messageWithSuppliedMetaData);
	}

	/**
	 * Invokes constraint handler methods provided by aggregate root and aggregate
	 * members identified by the {@link io.sapl.axon.annotations.ConstraintHandler}
	 * annotation.
	 * 
	 * @param aggregateRoot  aggregate root object
	 * @param entity         aggregate member entity object
	 * @param commandMessage commandMessage
	 */
	public void invokeAggregateConstraintHandlerMethods(
			Object aggregateRoot,
			Optional<Object> entity,
			Message<?> commandMessage,
			ApplicationContext applicationContext) {
		invokeMethods(aggregateRootHandlers, aggregateRoot, commandMessage, applicationContext);
		if (entity.isEmpty()) {
			return;
		}
		invokeMethods(aggregateMemberHandlers, entity.get(), commandMessage, applicationContext);
	}

	/**
	 * Executes the given ResultHandlers provided by the ConstraintHandlerProviders:
	 * {@link io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider},
	 * {@link io.sapl.spring.constraints.api.MappingConstraintHandlerProvider}.
	 *
	 * @param handlerResult the result to be handled
	 * @return the mapped handlerResult
	 */
	public R executeResultHandlerProvider(R handlerResult) {
		acceptResultConsumers(handlerResult);
		return applyResultMappers(handlerResult);
	}

	private void invokeMethods(
			Map<JsonNode, List<Method>> handlers,
			Object contextObject,
			Message<?> message,
			ApplicationContext applicationContext) {
		for (Map.Entry<JsonNode, List<Method>> entry : handlers.entrySet()) {
			var methods    = entry.getValue();
			var constraint = entry.getKey();

			methods.forEach((method) -> {
				var arguments = resolveArgumentsForMethodParameters(method, constraint, message, applicationContext);

				try {
					method.invoke(contextObject, arguments.toArray());
				} catch (Throwable t) {
					var isObligation = isObligationPerConstraint.get(constraint);
					if (isObligation)
						throw new AccessDeniedException("Failed to invoke aggregate constraint handler", t);
				}
			});
		}
	}

	private void runRunnables(List<Runnable> handlers) {
		handlers.forEach(Runnable::run);
	}

	private void acceptMessageConsumers(List<Consumer<U>> handlers, U message) {
		handlers.forEach((consumer) -> consumer.accept(message));
	}

	@SuppressWarnings("unchecked")
	private U applyAddMetaDataHandlers(Iterable<Supplier<Map<String, ?>>> handlers, U originalMessage) {
		U message = originalMessage;
		for (var supplier : handlers) {
			Map<String, ?> metaDataToBeAdded = supplier.get();
			if (metaDataToBeAdded != null) {
				message = (U) message.andMetaData(metaDataToBeAdded);
			}
		}
		return message;
	}

	@SuppressWarnings("unchecked")
	private U applyMessagePayloadMappers(Iterable<Function<T, T>> handlers, U message) {

		U mappedMessage = message;
		for (var mapper : handlers) {
			T          mappedPayload  = mapper.apply(mappedMessage.getPayload());
			Message<T> genericMessage = new GenericMessage<>(mappedMessage.getIdentifier(), mappedPayload,
					mappedMessage.getMetaData());
			if (mappedMessage instanceof CommandMessage) {
				mappedMessage = (U) new GenericCommandMessage<>(genericMessage,
						((GenericCommandMessage<T>) mappedMessage).getCommandName());
			} else {
				mappedMessage = (U) new GenericQueryMessage<>(genericMessage,
						((GenericQueryMessage<T, R>) mappedMessage).getQueryName(),
						((GenericQueryMessage<T, R>) mappedMessage).getResponseType());
			}
		}
		return mappedMessage;
	}

	private void acceptResultConsumers(R objectToBeConsumed) {
		resultConsumerHandlers.forEach((consumer) -> consumer.accept(objectToBeConsumed));
	}

	private R applyResultMappers(R objectToBeMapped) {
		R mappedObject = objectToBeMapped;
		for (var mapper : resultMappingHandlers) {
			mappedObject = mapper.apply(mappedObject);
		}
		return mappedObject;
	}

	private List<Object> resolveArgumentsForMethodParameters(
			Method method,
			JsonNode constraint,
			Message<?> message,
			ApplicationContext applicationContext) {
		var parameters = method.getParameters();

		List<ParameterResolverFactory> resolverFactories = new ArrayList<>();
		resolverFactories.add(new DefaultParameterResolverFactory());
		resolverFactories.add(new SpringBeanParameterResolverFactory(applicationContext));
		var multiParameterResolverFactory = new MultiParameterResolverFactory(resolverFactories);

		List<Object> resolvedArguments = new ArrayList<>();
		int          parameterIndex    = 0;
		for (var parameter : parameters) {
			if (JsonNode.class.isAssignableFrom(parameter.getType())) {
				resolvedArguments.add(constraint);
			} else {
				ParameterResolver<?> factory = multiParameterResolverFactory.createInstance(method, parameters,
						parameterIndex);
				if (factory != null) {
					resolvedArguments.add(factory.resolveParameterValue(message));
				} else {
					resolvedArguments.add(null);
				}
			}
			parameterIndex++;
		}
		return resolvedArguments;
	}

}
