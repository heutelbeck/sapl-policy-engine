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
package io.sapl.spring.constraints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Subscription;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
     * @param globalRunnableProviders all RunnableConstraintHandlerProvider
     * @param globalConsumerProviders all ConsumerConstraintHandlerProvider
     * @param globalSubscriptionHandlerProviders all SubscriptionHandlerProvider
     * @param globalRequestHandlerProviders all RequestHandlerProvider
     * @param globalMappingHandlerProviders all MappingConstraintHandlerProvider
     * @param globalErrorMappingHandlerProviders all
     * ErrorMappingConstraintHandlerProvider
     * @param globalErrorHandlerProviders all ErrorHandlerProvider
     * @param filterPredicateProviders all FilterPredicateConstraintHandlerProvider
     * @param methodInvocationHandlerProviders all
     * MethodInvocationConstraintHandlerProvider
     * @param mapper the global ObjectMapper
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
     * @param <T> event type
     * @param decision a decision
     * @param resourceAccessPoint a Flux to be protected
     * @param clazz the class of the event type
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
     * @param <T> event type
     * @param decision a decision
     * @param clazz class of the event type
     * @param ignoredObligations if the client of this method already has taken care
     * of specific obligations that have not to be handled by the bundle, these can
     * be indicated here and will be ignored when checking for completeness of
     * obligation handing by the bundle.
     * @return a ReactiveTypeConstraintHandlerBundle with handlers for all
     * constraints in the decision, or throws AccessDeniedException, if bundle
     * cannot be constructed.
     */
    public <T> ReactiveConstraintHandlerBundle<T> reactiveTypeBundleFor(AuthorizationDecision decision, Class<T> clazz,
            JsonNode... ignoredObligations) {

        final var unhandledObligations = Sets.newHashSet(decision.getObligations().orElseGet(mapper::createArrayNode));

        // @formatter:off
		var bundle = new ReactiveConstraintHandlerBundle<>(
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

        if (!unhandledObligations.isEmpty()) {
            for (var unhandledObligation : unhandledObligations) {
                if (Arrays.stream(ignoredObligations).filter(ignored -> ignored.equals(unhandledObligation)).findFirst()
                        .isEmpty()) {
                    throw missingHandlerError(unhandledObligations);
                }
            }
        }

        return bundle;
    }

    /**
     * @param <T> event type
     * @param decision a decision
     * @param clazz class of the event type
     * @return a BlockingPostEnforceConstraintHandlerBundle with handlers for all
     * constraints in the decision, or throws AccessDeniedException, if bundle
     * cannot be constructed.
     */
    public <T> BlockingConstraintHandlerBundle<T> blockingPostEnforceBundleFor(AuthorizationDecision decision,
            Class<T> clazz) {

        final var unhandledObligations = Sets.newHashSet(decision.getObligations().orElseGet(mapper::createArrayNode));

        // @formatter:off
		var bundle = BlockingConstraintHandlerBundle.postEnforceConstraintHandlerBundle(
				runnableHandlersForSignal(Signal.ON_DECISION, decision, unhandledObligations),
				onNextHandlers(decision, unhandledObligations, clazz),
				mapNextHandlers(decision, unhandledObligations, clazz),
				onErrorHandlers(decision, unhandledObligations),
				mapErrorHandlers(decision, unhandledObligations),
				filterConstraintHandlers(decision, unhandledObligations),
				replaceHandler(decision.getResource(),clazz));
		// @formatter:on

        if (!unhandledObligations.isEmpty())
            throw missingHandlerError(unhandledObligations);

        return bundle;
    }

    private AccessDeniedException missingHandlerError(HashSet<JsonNode> unhandledObligations) {
        return new AccessDeniedException(String.format(
                "Access Denied by PEP. The PDP required at least one obligation to be enforced for which no handler is registered. Obligations that could not be handled: %s",
                unhandledObligations));
    }

    /**
     * @param <T> the return type of the protected Resource Access Point
     * @param decision a decision
     * @param clazz the return type of the protected Resource Access Point
     * @return a BlockingPreEnforceConstraintHandlerBundle with handlers for all
     * constraints in the decision, or throws AccessDeniedException, if bundle
     * cannot be constructed.
     */
    public <T> BlockingConstraintHandlerBundle<T> blockingPreEnforceBundleFor(AuthorizationDecision decision,
            Class<T> clazz) {
        final var unhandledObligations = Sets.newHashSet(decision.getObligations().orElseGet(mapper::createArrayNode));
        final var bundle               = BlockingConstraintHandlerBundle.preEnforceConstraintHandlerBundle(
                runnableHandlersForSignal(Signal.ON_DECISION, decision, unhandledObligations),
                onNextHandlers(decision, unhandledObligations, clazz),
                mapNextHandlers(decision, unhandledObligations, clazz), onErrorHandlers(decision, unhandledObligations),
                mapErrorHandlers(decision, unhandledObligations),
                filterConstraintHandlers(decision, unhandledObligations),
                methodInvocationHandlers(decision, unhandledObligations),
                replaceHandler(decision.getResource(), clazz));

        if (!unhandledObligations.isEmpty())
            throw missingHandlerError(unhandledObligations);

        return bundle;
    }

    /**
     * This Method provides a blocking constraint handler for use in AccessManagers
     * for servlet-based filtering. Only ON_DECISION handlers are feasible here.
     *
     * @param decision a decision
     * @return a BlockingPreEnforceConstraintHandlerBundle with handlers for all
     * constraints in the decision, or throws AccessDeniedException, if bundle
     * cannot be constructed.
     */
    public <T> BlockingConstraintHandlerBundle<T> accessManagerBundleFor(AuthorizationDecision decision) {
        final var unhandledObligations = Sets.newHashSet(decision.getObligations().orElseGet(mapper::createArrayNode));
        final var bundle               = BlockingConstraintHandlerBundle.<T>accessManagerConstraintHandlerBundle(
                runnableHandlersForSignal(Signal.ON_DECISION, decision, unhandledObligations));
        if (!unhandledObligations.isEmpty())
            throw missingHandlerError(unhandledObligations);
        return bundle;
    }

    private Consumer<MethodInvocation> methodInvocationHandlers(AuthorizationDecision decision,
            HashSet<JsonNode> unhandledObligations) {
        final var obligationHandlers = obligation(constructMethodInvocationHandlersForConstraints(
                decision.getObligations(), unhandledObligations::remove));
        final var adviceHandlers     = advice(
                constructMethodInvocationHandlersForConstraints(decision.getAdvice(), FunctionUtil.sink()));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    private Consumer<MethodInvocation> constructMethodInvocationHandlersForConstraints(Optional<ArrayNode> constraints,
            Consumer<JsonNode> onHandlerFound) {
        var handlers = FunctionUtil.<MethodInvocation>sink();

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
        final var obligationHandlers = constructFilterHandlersForConstraint(decision.getObligations(),
                unhandledObligations::remove, this::obligation);
        final var adviceHandlers     = constructFilterHandlersForConstraint(decision.getAdvice(), FunctionUtil.sink(),
                this::advice);
        return obligationHandlers.and(adviceHandlers);
    }

    private Predicate<Object> constructFilterHandlersForConstraint(Optional<ArrayNode> constraints,
            Consumer<JsonNode> onHandlerFound, UnaryOperator<Predicate<Object>> wrapper) {
        var handlers = FunctionUtil.all();

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
        final var obligationHandlers = obligation(
                constructOnErrorHandlersForConstraints(decision.getObligations(), unhandledObligations::remove));
        final var adviceHandlers     = advice(
                constructOnErrorHandlersForConstraints(decision.getAdvice(), FunctionUtil.sink()));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    private Consumer<Throwable> constructOnErrorHandlersForConstraints(Optional<ArrayNode> constraints,
            Consumer<JsonNode> onHandlerFound) {
        var handlers = FunctionUtil.<Throwable>sink();

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

    private UnaryOperator<Throwable> mapErrorHandlers(AuthorizationDecision decision,
            HashSet<JsonNode> unhandledObligations) {
        final var obligationHandlers = constructMapNextHandlersForConstraints(decision.getObligations(),
                unhandledObligations::remove, this::obligation);
        final var adviceHandlers     = constructMapNextHandlersForConstraints(decision.getAdvice(), FunctionUtil.sink(),
                this::advice);
        return mapBoth(obligationHandlers, adviceHandlers);
    }

    private UnaryOperator<Throwable> constructMapNextHandlersForConstraints(Optional<ArrayNode> constraints,
            Consumer<JsonNode> onHandlerFound, UnaryOperator<UnaryOperator<Throwable>> wrapper) {
        var handlers = UnaryOperator.<Throwable>identity();

        if (constraints.isEmpty())
            return handlers;

        final var prioritizedHandlers = new ArrayList<HandlerWithPriority<UnaryOperator<Throwable>>>();

        for (var constraint : constraints.get()) {
            for (var provider : globalErrorMappingHandlerProviders) {
                if (provider.isResponsible(constraint)) {
                    onHandlerFound.accept(constraint);
                    prioritizedHandlers
                            .add(new HandlerWithPriority<>(provider.getHandler(constraint), provider.getPriority()));
                }
            }
        }

        Collections.sort(prioritizedHandlers);

        for (var handler : prioritizedHandlers) {
            handlers = mapBoth(handlers, wrapper.apply(handler.getHandler()));
        }

        return handlers;
    }

    private <T> UnaryOperator<T> mapNextHandlers(AuthorizationDecision decision, HashSet<JsonNode> unhandledObligations,
            Class<T> clazz) {
        final var obligationHandlers = constructMapNextHandlersForConstraints(decision.getObligations(),
                unhandledObligations::remove, clazz, this::obligation);
        final var adviceHandlers     = constructMapNextHandlersForConstraints(decision.getAdvice(), FunctionUtil.sink(),
                clazz, this::advice);
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
    private <T> UnaryOperator<T> constructMapNextHandlersForConstraints(Optional<ArrayNode> constraints,
            Consumer<JsonNode> onHandlerFound, Class<T> clazz, UnaryOperator<UnaryOperator<T>> wrapper) {
        var handlers = UnaryOperator.<T>identity();

        if (constraints.isEmpty())
            return handlers;

        final var prioritizedHandlers = new ArrayList<HandlerWithPriority<UnaryOperator<T>>>();
        for (var constraint : constraints.get()) {
            for (var provider : globalMappingHandlerProviders) {
                if (provider.supports(clazz) && provider.isResponsible(constraint)) {
                    onHandlerFound.accept(constraint);
                    prioritizedHandlers.add(new HandlerWithPriority<>(
                            (UnaryOperator<T>) provider.getHandler(constraint), provider.getPriority()));
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
        final var obligationHandlers = obligation(
                constructOnNextHandlersForConstraints(decision.getObligations(), unhandledObligations::remove, clazz));
        final var adviceHandlers     = advice(
                constructOnNextHandlersForConstraints(decision.getAdvice(), FunctionUtil.sink(), clazz));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    @SuppressWarnings("unchecked") // false positive. "support()" does check
    private <T> Consumer<T> constructOnNextHandlersForConstraints(Optional<ArrayNode> constraints,
            Consumer<JsonNode> onHandlerFound, Class<T> clazz) {
        var handlers = FunctionUtil.<T>sink();

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
        final var obligationHandlers = obligation(
                constructRequestHandlersForConstraints(decision.getObligations(), unhandledObligations::remove));
        final var adviceHandlers     = advice(
                constructRequestHandlersForConstraints(decision.getAdvice(), FunctionUtil.sink()));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    private LongConsumer constructRequestHandlersForConstraints(Optional<ArrayNode> constraints,
            Consumer<JsonNode> onHandlerFound) {
        var handlers = FunctionUtil.longSink();

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
        final var obligationHandlers = obligation(
                constructSubscriptionHandlersForConstraints(decision.getObligations(), unhandledObligations::remove));
        final var adviceHandlers     = advice(
                constructSubscriptionHandlersForConstraints(decision.getAdvice(), FunctionUtil.sink()));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    private Consumer<Subscription> constructSubscriptionHandlersForConstraints(Optional<ArrayNode> constraints,
            Consumer<JsonNode> onHandlerFound) {
        var handlers = FunctionUtil.<Subscription>sink();

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
        final var onDecisionObligationHandlers = obligation(constructRunnableHandlersForConstraints(signal,
                decision.getObligations(), unhandledObligations::remove));
        final var onDecisionAdviceHandlers     = advice(
                constructRunnableHandlersForConstraints(signal, decision.getAdvice(), FunctionUtil.sink()));
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

    private <T> UnaryOperator<T> obligation(UnaryOperator<T> handlers) {
        return x -> {
            try {
                return handlers.apply(x);
            } catch (Throwable t) {
                Exceptions.throwIfFatal(t);
                throw new AccessDeniedException("Failed to execute mapping obligation handler", t);
            }
        };
    }

    private <T> UnaryOperator<T> advice(UnaryOperator<T> handlers) {
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

    private <T> UnaryOperator<T> mapBoth(UnaryOperator<T> first, UnaryOperator<T> second) {
        return t -> second.apply(first.apply(t));
    }

    private Runnable constructRunnableHandlersForConstraints(Signal signal, Optional<ArrayNode> constraints,
            Consumer<JsonNode> onHandlerFound) {
        var handlers = (Runnable) () -> {};

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

    private <T> UnaryOperator<T> replaceHandler(Optional<JsonNode> resource, Class<T> clazz) {
        if (resource.isEmpty())
            return UnaryOperator.identity();

        try {
            final var replacement = unmarshallResource(resource.get(), clazz);
            return originalResult -> replacement;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            final var message = String.format("Cannot map resource %s to type %s", resource.get(),
                    clazz.getSimpleName());
            log.warn(message);
            throw new AccessDeniedException(message, e);
        }
    }

    /**
     * Convenience method to replace the resource access point (RAP) with a Flux
     * only containing the resource if present.
     *
     * @param <T> event type
     * @param resourceAccessPoint the original RAP
     * @param resource an optional resource to replace the RAP
     * @param clazz event type class
     * @return the replacement, if a resource was present. Else return the original
     * RAP.
     */
    public <T> Flux<T> replaceIfResourcePresent(Flux<T> resourceAccessPoint, Optional<JsonNode> resource,
            Class<T> clazz) {
        if (resource.isEmpty())
            return resourceAccessPoint;
        try {
            return Flux.just(unmarshallResource(resource.get(), clazz));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            final var message = String.format("Cannot map resource %s to type %s", resource.get(),
                    clazz.getSimpleName());
            log.warn(message);
            return Flux.error(new AccessDeniedException(message, e));
        }
    }

    /**
     * Convenience method to convert a JSON to a JavaObject using the global
     * ObjectMapper.
     *
     * @param <T> type of the expected output
     * @param resource a JSON value
     * @param clazz class of the expected output
     * @return the JSON object converted into the provided class
     * @throws JsonProcessingException on JSON marshaling error
     * @throws IllegalArgumentException on JSON marshaling error
     */
    public <T> T unmarshallResource(JsonNode resource, Class<T> clazz) throws JsonProcessingException {
        return mapper.treeToValue(resource, clazz);
    }

}
