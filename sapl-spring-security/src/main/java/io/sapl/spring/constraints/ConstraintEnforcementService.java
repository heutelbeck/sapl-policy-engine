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
package io.sapl.spring.constraints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Subscription;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Functions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.FilterPredicateConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MethodInvocationConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RequestHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider.Signal;
import io.sapl.spring.constraints.api.SubscriptionHandlerProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

/**
 * 
 * The ConstraintEnforcementService is responsible for collecting executable
 * constraint handlers in bundles for the PEP whenever the PDP sends a new
 * decision. The PEP in return will execute the matching handlers in the
 * protected code path.
 * 
 * @author Dominic Heutelbeck
 *
 */
@Slf4j
@Service
public class ConstraintEnforcementService {

	private final List<ConsumerConstraintHandlerProvider<?>>          globalConsumerProviders;
	private final List<SubscriptionHandlerProvider>                   globalSubscriptionHandlerProviders;
	private final List<RequestHandlerProvider>                        globalRequestHandlerProviders;
	private final List<MappingConstraintHandlerProvider<?>>           globalMappingHandlerProviders;
	private final List<ErrorMappingConstraintHandlerProvider>         globalErrorMappingHandlerProviders;
	private final List<ErrorHandlerProvider>                          globalErrorHandlerProviders;
	private final List<FilterPredicateConstraintHandlerProvider>      filterPredicateProviders;
	private final List<MethodInvocationConstraintHandlerProvider>     methodInvocationHandlerProviders;
	private final ObjectMapper                                        mapper;
	private final Multimap<Signal, RunnableConstraintHandlerProvider> globalRunnableIndex;

	/**
	 * Constructor with dependency injection of all beans implementing handler
	 * providers.
	 * 
	 * @param globalRunnableProviders            all
	 *                                           RunnableConstraintHandlerProvider
	 * @param globalConsumerProviders            all
	 *                                           ConsumerConstraintHandlerProvider
	 * @param globalSubscriptionHandlerProviders all SubscriptionHandlerProvider
	 * @param globalRequestHandlerProviders      all RequestHandlerProvider
	 * @param globalMappingHandlerProviders      all
	 *                                           MappingConstraintHandlerProvider
	 * @param globalErrorMappingHandlerProviders all
	 *                                           ErrorMappingConstraintHandlerProvider
	 * @param globalErrorHandlerProviders        all ErrorHandlerProvider
	 * @param filterPredicateProviders           all
	 *                                           FilterPredicateConstraintHandlerProvider
	 * @param methodInvocationHandlerProviders   all
	 *                                           MethodInvocationConstraintHandlerProvider
	 * @param mapper                             the global ObjectMapper
	 */
	public ConstraintEnforcementService(List<RunnableConstraintHandlerProvider> globalRunnableProviders,
			List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders,
			List<SubscriptionHandlerProvider> globalSubscriptionHandlerProviders,
			List<RequestHandlerProvider> globalRequestHandlerProviders,
			List<MappingConstraintHandlerProvider<?>> globalMappingHandlerProviders,
			List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders,
			List<ErrorHandlerProvider> globalErrorHandlerProviders,
			List<FilterPredicateConstraintHandlerProvider> filterPredicateProviders,
			List<MethodInvocationConstraintHandlerProvider> methodInvocationHandlerProviders, ObjectMapper mapper) {

		this.globalConsumerProviders            = globalConsumerProviders;
		this.globalSubscriptionHandlerProviders = globalSubscriptionHandlerProviders;
		this.globalRequestHandlerProviders      = globalRequestHandlerProviders;
		this.globalErrorHandlerProviders        = globalErrorHandlerProviders;
		this.methodInvocationHandlerProviders   = methodInvocationHandlerProviders;
		this.filterPredicateProviders           = filterPredicateProviders;
		this.mapper                             = mapper;

		this.globalMappingHandlerProviders = globalMappingHandlerProviders;
		Collections.sort(this.globalMappingHandlerProviders);
		this.globalErrorMappingHandlerProviders = globalErrorMappingHandlerProviders;
		Collections.sort(this.globalErrorMappingHandlerProviders);

		globalRunnableIndex = ArrayListMultimap.create();
		for (var provider : globalRunnableProviders)
			globalRunnableIndex.put(provider.getSignal(), provider);
	}

	/**
	 * Takes the decision and derives a wrapped resource access point Flux where the
	 * decision is enforced. I.e., access is granted or denied, and all constraints
	 * are handled.
	 * 
	 * @param <T>                 event type
	 * @param decision            a decision
	 * @param resourceAccessPoint a Flux to be protected
	 * @param clazz               the class of the event type
	 * @return a Flux where the decision is enforced.
	 */
	public <T> Flux<T> enforceConstraintsOfDecisionOnResourceAccessPoint(AuthorizationDecision decision,
			Flux<T> resourceAccessPoint, Class<T> clazz) {
		var wrapped = resourceAccessPoint;
		wrapped = replaceIfResourcePresent(wrapped, decision.getResource(), clazz);
		try {
			return reactiveTypeBundleFor(decision, clazz).wrap(wrapped);
		} catch (AccessDeniedException e) {
			return Flux.error(e);
		}
	}

	/**
	 * @param <T>      event type
	 * @param decision a decision
	 * @param clazz    class of the event type
	 * @return a ReactiveTypeConstraintHandlerBundle with handlers for all
	 *         constraints in the decision, or throws AccessDeniedException, if
	 *         bundle cannot be constructed.
	 */
	public <T> ReactiveTypeConstraintHandlerBundle<T> reactiveTypeBundleFor(AuthorizationDecision decision,
			Class<T> clazz) {

		var unhandledObligations = Sets.newHashSet(decision.getObligations().orElseGet(() -> mapper.createArrayNode()));

		// @formatter:off
		var bundle = new ReactiveTypeConstraintHandlerBundle<T>(
				runnableHandlersForSignal(Signal.ON_DECISION, decision, unhandledObligations),
				runnableHandlersForSignal(Signal.ON_CANCEL, decision, unhandledObligations),
				runnableHandlersForSignal(Signal.ON_COMPLETE, decision, unhandledObligations),
				runnableHandlersForSignal(Signal.ON_TERMINATE, decision, unhandledObligations),
				runnableHandlersForSignal(Signal.AFTER_TERMINATE, decision, unhandledObligations),
				subscriptionHandlers(decision, unhandledObligations), 
				requestHandlers(decision, unhandledObligations),
				onNextHandlers(decision, unhandledObligations, clazz),
				mapNextHandlers(decision, unhandledObligations, clazz),
				onErrorHandlers(decision, unhandledObligations),
				mapErrorHandlers(decision, unhandledObligations),
				filterConstraintHandlers(decision, unhandledObligations),
				methodInvocationHandlers(decision, unhandledObligations));
		// @formatter:on

		if (!unhandledObligations.isEmpty())
			throw new AccessDeniedException("No handler for obligation: " + unhandledObligations);

		return bundle;
	}

	/**
	 * @param <T>      event type
	 * @param decision a decision
	 * @param clazz    class of the event type
	 * @return a BlockingPostEnforceConstraintHandlerBundle with handlers for all
	 *         constraints in the decision, or throws AccessDeniedException, if
	 *         bundle cannot be constructed.
	 */
	public <T> BlockingPostEnforceConstraintHandlerBundle<T> blockingPostEnforceBundleFor(
			AuthorizationDecision decision, Class<T> clazz) {

		var unhandledObligations = Sets.newHashSet(decision.getObligations().orElseGet(() -> mapper.createArrayNode()));

		// @formatter:off
		var bundle = new BlockingPostEnforceConstraintHandlerBundle<T>(
				runnableHandlersForSignal(Signal.ON_DECISION, decision, unhandledObligations),
				onNextHandlers(decision, unhandledObligations, clazz),
				mapNextHandlers(decision, unhandledObligations, clazz), 
				onErrorHandlers(decision, unhandledObligations),
				mapErrorHandlers(decision, unhandledObligations),
				filterConstraintHandlers(decision, unhandledObligations));
		// @formatter:on

		if (!unhandledObligations.isEmpty())
			throw new AccessDeniedException("No handler for obligation: " + unhandledObligations);

		return bundle;
	}

	/**
	 * @param decision a decision
	 * @return a BlockingPreEnforceConstraintHandlerBundle with handlers for all
	 *         constraints in the decision, or throws AccessDeniedException, if
	 *         bundle cannot be constructed.
	 */
	public BlockingPreEnforceConstraintHandlerBundle blockingPreEnforceBundleFor(AuthorizationDecision decision) {
		var unhandledObligations = Sets.newHashSet(decision.getObligations().orElseGet(() -> mapper.createArrayNode()));

		var bundle = new BlockingPreEnforceConstraintHandlerBundle(
				runnableHandlersForSignal(Signal.ON_DECISION, decision, unhandledObligations),
				methodInvocationHandlers(decision, unhandledObligations));

		if (!unhandledObligations.isEmpty())
			throw new AccessDeniedException("No handler for obligation: " + unhandledObligations);

		return bundle;
	}

	private Consumer<MethodInvocation> methodInvocationHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> unhandledObligations) {
		var obligationHandlers = obligation(constructMethodInvocationHandlersForConstraints(decision.getObligations(),
				c -> unhandledObligations.remove(c)));
		var adviceHandlers     = advice(constructMethodInvocationHandlersForConstraints(decision.getAdvice(), __ -> {
								}));
		return consumeWithBoth(obligationHandlers, adviceHandlers);
	}

	private Consumer<MethodInvocation> constructMethodInvocationHandlersForConstraints(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound) {
		var handlers = (Consumer<MethodInvocation>) __ -> {
		};

		if (constraints.isEmpty())
			return handlers;

		for (var constraint : constraints.get()) {
			for (var provider : methodInvocationHandlerProviders) {
				if (provider.isResponsible(constraint)) {
					onHandlerFound.accept(constraint);
					handlers = consumeWithBoth(handlers, checkInvocationType(provider.getHandler(constraint)));
				}
			}
		}

		return handlers;
	}

	private Consumer<MethodInvocation> checkInvocationType(Consumer<ReflectiveMethodInvocation> handler) {
		return i -> {
			if (!(i instanceof ReflectiveMethodInvocation))
				throw new IllegalArgumentException("MethodInvocation not ReflectiveMethodInvocation");
			handler.accept((ReflectiveMethodInvocation) i);
		};
	}

	private Predicate<Object> filterConstraintHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> unhandledObligations) {
		var obligationHandlers = constructFilterHandlersForConstraint(decision.getObligations(),
				c -> unhandledObligations.remove(c), this::obligation);
		var adviceHandlers     = constructFilterHandlersForConstraint(decision.getAdvice(), __ -> {
								}, this::advice);
		return obligationHandlers.and(adviceHandlers);
	}

	private Predicate<Object> constructFilterHandlersForConstraint(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound, Function<Predicate<Object>, Predicate<Object>> wrapper) {
		var handlers = (Predicate<Object>) __ -> true;

		if (constraints.isEmpty())
			return handlers;

		for (var constraint : constraints.get()) {
			for (var provider : filterPredicateProviders) {
				if (provider.isResponsible(constraint)) {
					onHandlerFound.accept(constraint);
					handlers = handlers.and(wrapper.apply(provider.getHandler(constraint)));
				}
			}
		}

		return handlers;
	}

	private Consumer<Throwable> onErrorHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> unhandledObligations) {
		var obligationHandlers = obligation(
				constructOnErrorHandlersForConstraints(decision.getObligations(), c -> unhandledObligations.remove(c)));
		var adviceHandlers     = advice(constructOnErrorHandlersForConstraints(decision.getAdvice(), __ -> {
								}));
		return consumeWithBoth(obligationHandlers, adviceHandlers);
	}

	private Consumer<Throwable> constructOnErrorHandlersForConstraints(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound) {
		var handlers = (Consumer<Throwable>) __ -> {
		};

		if (constraints.isEmpty())
			return handlers;

		for (var constraint : constraints.get()) {
			for (var provider : globalErrorHandlerProviders) {
				if (provider.isResponsible(constraint)) {
					onHandlerFound.accept(constraint);
					handlers = consumeWithBoth(handlers, provider.getHandler(constraint));
				}
			}
		}

		return handlers;
	}

	private Function<Throwable, Throwable> mapErrorHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> unhandledObligations) {
		var obligationHandlers = constructMapNextHandlersForConstraints(decision.getObligations(),
				c -> unhandledObligations.remove(c), this::obligation);
		var adviceHandlers     = constructMapNextHandlersForConstraints(decision.getAdvice(), __ -> {
								}, this::advice);
		return mapBoth(obligationHandlers, adviceHandlers);
	}

	private Function<Throwable, Throwable> constructMapNextHandlersForConstraints(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound,
			Function<Function<Throwable, Throwable>, Function<Throwable, Throwable>> wrapper) {
		var handlers = (Function<Throwable, Throwable>) t -> t;

		if (constraints.isEmpty())
			return handlers;

		var prioritizedHandlers = new ArrayList<HandlerWithPriority<Function<Throwable, Throwable>>>();

		for (var constraint : constraints.get()) {
			for (var provider : globalErrorMappingHandlerProviders) {
				if (provider.isResponsible(constraint)) {
					onHandlerFound.accept(constraint);
					prioritizedHandlers.add(new HandlerWithPriority<Function<Throwable, Throwable>>(
							provider.getHandler(constraint), provider.getPriority()));
				}
			}
		}

		Collections.sort(prioritizedHandlers);

		for (var handler : prioritizedHandlers) {
			handlers = mapBoth(handlers, wrapper.apply(handler.getHandler()));
		}

		return handlers;
	}

	private <T> Function<T, T> mapNextHandlers(AuthorizationDecision decision, HashSet<JsonNode> unhandledObligations,
			Class<T> clazz) {
		var obligationHandlers = constructMapNextHandlersForConstraints(decision.getObligations(),
				c -> unhandledObligations.remove(c), clazz, this::obligation);
		var adviceHandlers     = constructMapNextHandlersForConstraints(decision.getAdvice(), __ -> {
								}, clazz, this::advice);
		return mapBoth(obligationHandlers, adviceHandlers);
	}

	@Data
	@AllArgsConstructor
	private static class HandlerWithPriority<T> implements Comparable<HandlerWithPriority<T>> {
		T   handler;
		int priority;

		@Override
		public int compareTo(HandlerWithPriority<T> o) {
			return Integer.compare(o.priority, priority);
		}
	}

	@SuppressWarnings("unchecked") // false positive. "support()" does check
	private <T> Function<T, T> constructMapNextHandlersForConstraints(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound, Class<T> clazz, Function<Function<T, T>, Function<T, T>> wrapper) {
		var handlers = (Function<T, T>) Functions.identity();

		if (constraints.isEmpty())
			return handlers;

		var prioritizedHandlers = new ArrayList<HandlerWithPriority<Function<T, T>>>();
		for (var constraint : constraints.get()) {
			for (var provider : globalMappingHandlerProviders) {
				if (provider.supports(clazz) && provider.isResponsible(constraint)) {
					onHandlerFound.accept(constraint);
					prioritizedHandlers.add(new HandlerWithPriority<Function<T, T>>(
							(Function<T, T>) provider.getHandler(constraint), provider.getPriority()));
				}
			}
		}

		Collections.sort(prioritizedHandlers);

		for (var handler : prioritizedHandlers) {
			handlers = mapBoth(handlers, wrapper.apply(handler.getHandler()));
		}

		return handlers;
	}

	private <T> Consumer<T> onNextHandlers(AuthorizationDecision decision, HashSet<JsonNode> unhandledObligations,
			Class<T> clazz) {
		var obligationHandlers = obligation(constructOnNextHandlersForConstraints(decision.getObligations(),
				c -> unhandledObligations.remove(c), clazz));
		var adviceHandlers     = advice(constructOnNextHandlersForConstraints(decision.getAdvice(), __ -> {
								}, clazz));
		return consumeWithBoth(obligationHandlers, adviceHandlers);
	}

	@SuppressWarnings("unchecked") // false positive. "support()" does check
	private <T> Consumer<T> constructOnNextHandlersForConstraints(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound, Class<T> clazz) {
		var handlers = (Consumer<T>) __ -> {
		};

		if (constraints.isEmpty())
			return handlers;

		for (var constraint : constraints.get()) {
			for (var provider : globalConsumerProviders) {
				if (provider.supports(clazz) && provider.isResponsible(constraint)) {
					onHandlerFound.accept(constraint);
					handlers = consumeWithBoth(handlers, (Consumer<T>) provider.getHandler(constraint));
				}
			}
		}

		return handlers;
	}

	private LongConsumer requestHandlers(AuthorizationDecision decision, HashSet<JsonNode> unhandledObligations) {
		var obligationHandlers = obligation(
				constructRequestHandlersForConstraints(decision.getObligations(), c -> unhandledObligations.remove(c)));
		var adviceHandlers     = advice(constructRequestHandlersForConstraints(decision.getAdvice(), __ -> {
								}));
		return consumeWithBoth(obligationHandlers, adviceHandlers);
	}

	private LongConsumer constructRequestHandlersForConstraints(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound) {
		var handlers = (LongConsumer) __ -> {
		};

		if (constraints.isEmpty())
			return handlers;

		for (var constraint : constraints.get()) {
			for (var provider : globalRequestHandlerProviders) {
				if (provider.isResponsible(constraint)) {
					onHandlerFound.accept(constraint);
					handlers = consumeWithBoth(handlers, provider.getHandler(constraint));
				}
			}
		}

		return handlers;
	}

	private Consumer<Subscription> subscriptionHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> unhandledObligations) {
		var obligationHandlers = obligation(constructSubscriptionHandlersForConstraints(decision.getObligations(),
				c -> unhandledObligations.remove(c)));
		var adviceHandlers     = advice(constructSubscriptionHandlersForConstraints(decision.getAdvice(), __ -> {
								}));
		return consumeWithBoth(obligationHandlers, adviceHandlers);
	}

	private Consumer<Subscription> constructSubscriptionHandlersForConstraints(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound) {
		var handlers = (Consumer<Subscription>) __ -> {
		};

		if (constraints.isEmpty())
			return handlers;

		for (var constraint : constraints.get()) {
			for (var provider : globalSubscriptionHandlerProviders) {
				if (provider.isResponsible(constraint)) {
					onHandlerFound.accept(constraint);
					handlers = consumeWithBoth(handlers, provider.getHandler(constraint));
				}
			}
		}

		return handlers;
	}

	private Runnable runnableHandlersForSignal(Signal signal, AuthorizationDecision decision,
			HashSet<JsonNode> unhandledObligations) {
		var onDecisionObligationHandlers = obligation(constructRunnableHandlersForConstraints(signal,
				decision.getObligations(), c -> unhandledObligations.remove(c)));
		var onDecisionAdviceHandlers     = advice(
				constructRunnableHandlersForConstraints(signal, decision.getAdvice(), __ -> {
													}));
		return runBoth(onDecisionObligationHandlers, onDecisionAdviceHandlers);
	}

	private <T> Predicate<T> obligation(Predicate<T> handlers) {
		return x -> {
			try {
				return handlers.test(x);
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				throw new AccessDeniedException("Failed to execute runnable obligation handler", t);
			}
		};
	}

	private <T> Predicate<T> advice(Predicate<T> handlers) {
		return x -> {
			try {
				return handlers.test(x);
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				return true;
			}
		};
	}

	private <T> Function<T, T> obligation(Function<T, T> handlers) {
		return x -> {
			try {
				return handlers.apply(x);
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				throw new AccessDeniedException("Failed to execute runnable obligation handler", t);
			}
		};
	}

	private <T> Function<T, T> advice(Function<T, T> handlers) {
		return x -> {
			try {
				return handlers.apply(x);
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				return x; // fallback to identity
			}
		};
	}

	private Runnable obligation(Runnable handlers) {
		return () -> {
			try {
				handlers.run();
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				throw new AccessDeniedException("Failed to execute runnable obligation handler", t);
			}
		};
	}

	private Runnable advice(Runnable handlers) {
		return () -> {
			try {
				handlers.run();
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
			}
		};
	}

	private <T> Consumer<T> obligation(Consumer<T> handlers) {
		return s -> {
			try {
				handlers.accept(s);
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				throw new AccessDeniedException("Failed to execute obligation handler " + s.getClass().getSimpleName(),
						t);
			}
		};
	}

	private <T> Consumer<T> advice(Consumer<T> handlers) {
		return s -> {
			try {
				handlers.accept(s);
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
			}
		};
	}

	private LongConsumer obligation(LongConsumer handlers) {
		return s -> {
			try {
				handlers.accept(s);
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				throw Exceptions.bubble(new AccessDeniedException("Failed to execute request obligation handler", t));
			}
		};
	}

	private LongConsumer advice(LongConsumer handlers) {
		return s -> {
			try {
				handlers.accept(s);
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
			}
		};
	}

	private Runnable runBoth(Runnable a, Runnable b) {
		return () -> {
			a.run();
			b.run();
		};
	}

	private <T> Consumer<T> consumeWithBoth(Consumer<T> a, Consumer<T> b) {
		return t -> {
			a.accept(t);
			b.accept(t);
		};
	}

	private LongConsumer consumeWithBoth(LongConsumer a, LongConsumer b) {
		return t -> {
			a.accept(t);
			b.accept(t);
		};
	}

	private <T> Function<T, T> mapBoth(Function<T, T> first, Function<T, T> second) {
		return t -> {
			return second.apply(first.apply(t));
		};
	}

	private Runnable constructRunnableHandlersForConstraints(Signal signal, Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound) {
		var handlers = (Runnable) () -> {
		};

		if (constraints.isEmpty())
			return handlers;

		for (var constraint : constraints.get()) {
			for (var provider : globalRunnableIndex.get(signal)) {
				if (provider.isResponsible(constraint)) {
					onHandlerFound.accept(constraint);
					handlers = runBoth(handlers, provider.getHandler(constraint));
				}
			}
		}

		return handlers;
	}

	/**
	 * Convenience method to replace the result of a resource access point (RAP)
	 * with an alternative object containing the resource if present.
	 * 
	 * @param <T>            result type
	 * @param authzDecision  a decision
	 * @param originalResult the original result
	 * @param clazz          type of the expected result
	 * @return the replacement, if a resource was present. Else return the original
	 *         result.
	 */
	public <T> T replaceResultIfResourceDefinitionIsPresentInDecision(AuthorizationDecision authzDecision,
			T originalResult, Class<T> clazz) {
		var mustReplaceResource = authzDecision.getResource().isPresent();

		if (!mustReplaceResource)
			return originalResult;

		try {
			return unmarshallResource(authzDecision.getResource().get(), clazz);
		} catch (JsonProcessingException | IllegalArgumentException e) {
			var message = String.format("Cannot map resource %s to type %s", authzDecision.getResource().get(),
					clazz.getSimpleName());
			log.warn(message);
			throw new AccessDeniedException(message, e);
		}
	}

	/**
	 * Convenience method to replace the resource access point (RAP) with a Flux
	 * only containing the resource if present.
	 * 
	 * @param <T>                 event type
	 * @param resourceAccessPoint the original RAP
	 * @param resource            an optional resource to replace the RAP
	 * @param clazz               event type class
	 * @return the replacement, if a resource was present. Else return the original
	 *         RAP.
	 */
	public <T> Flux<T> replaceIfResourcePresent(Flux<T> resourceAccessPoint, Optional<JsonNode> resource,
			Class<T> clazz) {
		if (resource.isEmpty())
			return resourceAccessPoint;
		try {
			return Flux.just(unmarshallResource(resource.get(), clazz));
		} catch (JsonProcessingException | IllegalArgumentException e) {
			var message = String.format("Cannot map resource %s to type %s", resource.get(), clazz.getSimpleName());
			log.warn(message);
			return Flux.error(new AccessDeniedException(message, e));
		}
	}

	/**
	 * Convenience method to convert a JSON to a JavaObject using the global
	 * ObjectMapper.
	 * 
	 * @param <T>      type of the expected output
	 * @param resource a JSON value
	 * @param clazz    class of the expected output
	 * @return the JSON object converted into the provided class
	 * @throws JsonProcessingException  on JSON marshaling error
	 * @throws IllegalArgumentException on JSON marshaling error
	 */
	public <T> T unmarshallResource(JsonNode resource, Class<T> clazz)
			throws JsonProcessingException, IllegalArgumentException {
		return mapper.treeToValue(resource, clazz);
	}

}
