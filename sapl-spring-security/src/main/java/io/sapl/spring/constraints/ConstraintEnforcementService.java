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
package io.sapl.spring.constraints;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.FilterPredicateConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RequestHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider.Signal;
import io.sapl.spring.constraints.api.SubscriptionHandlerProvider;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

@Service
public class ConstraintEnforcementService {

	private final List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders;

	private final List<SubscriptionHandlerProvider> globalSubscriptionHandlerProviders;

	private final List<RequestHandlerProvider> globalRequestHandlerProviders;

	private final List<MappingConstraintHandlerProvider<?>> globalMappingHandlerProviders;

	private final List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders;

	private final List<ErrorHandlerProvider> globalErrorHandlerProviders;

	private final List<FilterPredicateConstraintHandlerProvider<?>> filterPredicateProviders;

	private final ObjectMapper mapper;

	private final SortedSetMultimap<Signal, RunnableConstraintHandlerProvider> globalRunnableIndex;

	public ConstraintEnforcementService(List<RunnableConstraintHandlerProvider> globalRunnableProviders,
			List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders,
			List<SubscriptionHandlerProvider> globalSubscriptionHandlerProviders,
			List<RequestHandlerProvider> globalRequestHandlerProviders,
			List<MappingConstraintHandlerProvider<?>> globalMappingHandlerProviders,
			List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders,
			List<ErrorHandlerProvider> globalErrorHandlerProviders,
			List<FilterPredicateConstraintHandlerProvider<?>> filterPredicateProviders,
			ObjectMapper mapper) {
		this.globalConsumerProviders = globalConsumerProviders;
		Collections.sort(this.globalConsumerProviders);
		this.globalSubscriptionHandlerProviders = globalSubscriptionHandlerProviders;
		Collections.sort(this.globalSubscriptionHandlerProviders);
		this.globalRequestHandlerProviders = globalRequestHandlerProviders;
		Collections.sort(this.globalRequestHandlerProviders);
		this.globalMappingHandlerProviders = globalMappingHandlerProviders;
		Collections.sort(this.globalMappingHandlerProviders);
		this.globalErrorMappingHandlerProviders = globalErrorMappingHandlerProviders;
		Collections.sort(this.globalErrorMappingHandlerProviders);
		this.globalErrorHandlerProviders = globalErrorHandlerProviders;
		Collections.sort(this.globalErrorHandlerProviders);
		this.filterPredicateProviders = filterPredicateProviders;
		this.mapper                   = mapper;
		globalRunnableIndex           = TreeMultimap.create();
		for (var provider : globalRunnableProviders)
			globalRunnableIndex.put(provider.getSignal(), provider);
	}

	public <T> Flux<T> enforceConstraintsOfDecisionOnResourceAccessPoint(
			AuthorizationDecision decision,
			Flux<T> resourceAccessPoint,
			Class<T> clazz) {
		var wrapped = resourceAccessPoint;
		wrapped = replaceIfResourcePresent(wrapped, decision.getResource(), clazz);
		try {
			return bundleFor(decision, clazz).wrap(wrapped);
		} catch (AccessDeniedException e) {
			return Flux.error(e);
		}
	}

	public <T> ConstraintHandlerBundle<T> bundleFor(AuthorizationDecision decision, Class<T> clazz) {
		var bundle = new ConstraintHandlerBundle<T>();
		addConstraintHandlers(bundle, decision.getObligations(), true, clazz);
		addConstraintHandlers(bundle, decision.getAdvice(), false, clazz);
		return bundle;
	}

	private <T> void addConstraintHandlers(
			ConstraintHandlerBundle<T> bundle,
			Optional<ArrayNode> constraints,
			boolean isObligation,
			Class<T> clazz) {
		if (constraints.isPresent())
			for (var constraint : constraints.get())
				addConstraintHandlers(bundle, constraint, isObligation, clazz);
	}

	private <T> void addConstraintHandlers(
			ConstraintHandlerBundle<T> bundle,
			JsonNode constraint,
			boolean isObligation,
			Class<T> clazz) {
		var onDecisionHandlers = constructRunnableHandlersForConstraint(Signal.ON_DECISION, constraint, isObligation);
		bundle.onDecisionHandlers.addAll(onDecisionHandlers);
		var onCancelHandlers = constructRunnableHandlersForConstraint(Signal.ON_CANCEL, constraint, isObligation);
		bundle.onCancelHandlers.addAll(onCancelHandlers);
		var onCompleteHandlers = constructRunnableHandlersForConstraint(Signal.ON_COMPLETE, constraint, isObligation);
		bundle.onCompleteHandlers.addAll(onCompleteHandlers);
		var onTerminateHandlers = constructRunnableHandlersForConstraint(Signal.ON_TERMINATE, constraint, isObligation);
		bundle.onTerminateHandlers.addAll(onTerminateHandlers);
		var afterTerminateHandlers = constructRunnableHandlersForConstraint(Signal.AFTER_TERMINATE, constraint,
				isObligation);
		bundle.afterTerminateHandlers.addAll(afterTerminateHandlers);
		var onSubscribeHandlers = constructOnSubscribeHandlersForConstraint(constraint, isObligation);
		bundle.onSubscribeHandlers.addAll(onSubscribeHandlers);
		var onRequestHandlers = constructOnRequestHandlersForConstraint(constraint, isObligation);
		bundle.onRequestHandlers.addAll(onRequestHandlers);
		var doOnNextHandlers = constructConsumerHandlersForConstraint(constraint, isObligation, clazz);
		bundle.doOnNextHandlers.addAll(doOnNextHandlers);
		var onNextMapHandlers = constructMappingConstraintHandlersForConstraint(constraint, isObligation, clazz);
		bundle.onNextMapHandlers.addAll(onNextMapHandlers);
		var doOnErrorHandlers = constructDoOnErrorHandlersForConstraint(constraint, isObligation);
		bundle.doOnErrorHandlers.addAll(doOnErrorHandlers);
		var onErrorMapHandlers = constructErrorMappingConstraintHandlersForConstraint(constraint, isObligation);
		bundle.onErrorMapHandlers.addAll(onErrorMapHandlers);
		var filterHandlers = constructFilterConstraintHandlersForConstraint(constraint, isObligation, clazz);
		bundle.filterPredicateHandlers.addAll(filterHandlers);

		if (isObligation)
			if (onDecisionHandlers.size() + onCancelHandlers.size() + onCompleteHandlers.size()
					+ onTerminateHandlers.size() + afterTerminateHandlers.size() + doOnNextHandlers.size()
					+ onNextMapHandlers.size() + doOnErrorHandlers.size() + onErrorMapHandlers.size()
					+ onSubscribeHandlers.size() + onRequestHandlers.size() + filterHandlers.size() == 0)
				throw new AccessDeniedException(
						String.format("No handler found for obligation: %s", constraint.asText()));
	}

	private <T> Flux<T> replaceIfResourcePresent(
			Flux<T> resourceAccessPoint,
			Optional<JsonNode> resource,
			Class<T> clazz) {
		if (resource.isEmpty())
			return resourceAccessPoint;
		try {
			return Flux.just(unmarshallResource(resource.get(), clazz));
		} catch (JsonProcessingException | IllegalArgumentException e) {
			return Flux.error(new AccessDeniedException(
					String.format("Cannot map resource %s to type %s", resource.get().asText(), clazz.getSimpleName()),
					e));
		}
	}

	public <T> T unmarshallResource(JsonNode resource, Class<T> clazz)
			throws JsonProcessingException,
				IllegalArgumentException {
		return mapper.treeToValue(resource, clazz);
	}

	@SuppressWarnings("unchecked") // False positive the filter checks type
	private <T> List<Predicate<T>>
			constructFilterConstraintHandlersForConstraint(
					JsonNode constraint,
					boolean isObligation,
					Class<T> clazz) {
		return filterPredicateProviders.stream().filter(provider -> provider.supports(clazz))
				.filter(provider -> provider.isResponsible(constraint))
				.map(provider -> (Predicate<T>) provider.getHandler(constraint))
				.collect(Collectors.toList());
	}

	private List<Function<Throwable, Throwable>> constructErrorMappingConstraintHandlersForConstraint(
			JsonNode constraint,
			boolean isObligation) {
		return globalErrorMappingHandlerProviders.stream().filter(provider -> provider.isResponsible(constraint))
				.map(provider -> provider.getHandler(constraint))
				.map(failFunctionOnlyIfObligationOrFatalElseFallBackToIdentity(isObligation))
				.collect(Collectors.toList());
	}

	@SuppressWarnings("unchecked") // False positive the filter checks type
	private <T> List<Function<T, T>> constructMappingConstraintHandlersForConstraint(
			JsonNode constraint,
			boolean isObligation,
			Class<T> clazz) {
		return globalMappingHandlerProviders.stream().filter(provider -> provider.supports(clazz))
				.filter(provider -> provider.isResponsible(constraint))
				.map(provider -> (Function<T, T>) provider.getHandler(constraint))
				.map(failFunctionOnlyIfObligationOrFatalElseFallBackToIdentity(isObligation))
				.collect(Collectors.toList());
	}

	private List<Consumer<Subscription>> constructOnSubscribeHandlersForConstraint(
			JsonNode constraint,
			boolean isObligation) {
		return globalSubscriptionHandlerProviders.stream().filter(provider -> provider.isResponsible(constraint))
				.map(provider -> provider.getHandler(constraint)).map(failConsumerOnlyIfObligationOrFatal(isObligation))
				.collect(Collectors.toList());
	}

	private List<LongConsumer> constructOnRequestHandlersForConstraint(JsonNode constraint, boolean isObligation) {

		return globalRequestHandlerProviders.stream().filter(provider -> provider.isResponsible(constraint))
				.map(provider -> provider.getHandler(constraint))
				.map(failLongConsumerOnlyIfObligationOrFatal(isObligation)).collect(Collectors.toList());
	}

	private List<Consumer<Throwable>> constructDoOnErrorHandlersForConstraint(
			JsonNode constraint,
			boolean isObligation) {
		return globalErrorHandlerProviders.stream().filter(provider -> provider.isResponsible(constraint))
				.map(provider -> provider.getHandler(constraint)).map(failConsumerOnlyIfObligationOrFatal(isObligation))
				.collect(Collectors.toList());
	}

	@SuppressWarnings("unchecked") // False positive the filter checks type
	private <T> List<Consumer<T>> constructConsumerHandlersForConstraint(
			JsonNode constraint,
			boolean isObligation,
			Class<T> clazz) {
		return globalConsumerProviders.stream().filter(provider -> provider.supports(clazz))
				.filter(provider -> provider.isResponsible(constraint))
				.map(provider -> (Consumer<T>) provider.getHandler(constraint))
				.map(failConsumerOnlyIfObligationOrFatal(isObligation)).collect(Collectors.toList());
	}

	private List<Runnable> constructRunnableHandlersForConstraint(
			Signal signal,
			JsonNode constraint,
			boolean isObligation) {
		var potentialProviders = globalRunnableIndex.get(signal);
		return potentialProviders.stream().filter(provider -> provider.isResponsible(constraint))
				.map(provider -> provider.getHandler(constraint)).map(failRunnableOnlyIfObligationOrFatal(isObligation))
				.collect(Collectors.toList());
	}

	private Function<Runnable, Runnable> failRunnableOnlyIfObligationOrFatal(boolean isObligation) {
		return runnable -> () -> {
			try {
				runnable.run();
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				if (isObligation)
					throw new AccessDeniedException("Failed to execute runnable constraint handler", t);
			}
		};
	}

	private <T> Function<Consumer<T>, Consumer<T>> failConsumerOnlyIfObligationOrFatal(boolean isObligation) {
		return consumer -> value -> {
			try {
				consumer.accept(value);
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				if (isObligation)
					throw new AccessDeniedException("Failed to execute consumer constraint handler", t);
			}
		};
	}

	private Function<LongConsumer, LongConsumer> failLongConsumerOnlyIfObligationOrFatal(boolean isObligation) {
		return consumer -> value -> {
			try {
				consumer.accept(value);
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				// non-fatal will not be reported by Flux in doOnRequest -> Bubble to
				// force failure for an obligation
				if (isObligation)
					throw Exceptions
							.bubble(new AccessDeniedException("Failed to execute consumer constraint handler", t));
			}
		};
	}

	private <T> Function<Function<T, T>, Function<T, T>> failFunctionOnlyIfObligationOrFatalElseFallBackToIdentity(
			boolean isObligation) {
		return function -> value -> {
			try {
				return function.apply(value);
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				if (isObligation)
					throw new AccessDeniedException("Failed to execute consumer constraint handler", t);
				return value;
			}
		};
	}

}
