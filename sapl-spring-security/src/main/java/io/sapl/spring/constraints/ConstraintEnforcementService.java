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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.api.*;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider.Signal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Subscription;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

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

    private final List<ConsumerConstraintHandlerProvider<?>>           globalConsumerProviders;
    private final List<SubscriptionHandlerProvider>                    globalSubscriptionHandlerProviders;
    private final List<RequestHandlerProvider>                         globalRequestHandlerProviders;
    private final List<MappingConstraintHandlerProvider<?>>            globalMappingHandlerProviders;
    private final List<ErrorMappingConstraintHandlerProvider>          globalErrorMappingHandlerProviders;
    private final List<ErrorHandlerProvider>                           globalErrorHandlerProviders;
    private final List<FilterPredicateConstraintHandlerProvider>       filterPredicateProviders;
    private final List<MethodInvocationConstraintHandlerProvider>      methodInvocationHandlerProviders;
    private final ObjectMapper                                         mapper;
    private final Map<Signal, List<RunnableConstraintHandlerProvider>> globalRunnableIndex;

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
            List<MethodInvocationConstraintHandlerProvider> methodInvocationHandlerProviders,
            ObjectMapper mapper) {

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

        globalRunnableIndex = new EnumMap<>(RunnableConstraintHandlerProvider.Signal.class);
        for (var provider : globalRunnableProviders)
            globalRunnableIndex.computeIfAbsent(provider.getSignal(), k -> new ArrayList<>()).add(provider);
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
        wrapped = replaceIfResourcePresent(wrapped, decision.resource(), clazz);
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
            Value... ignoredObligations) {

        final var unhandledObligations = new HashSet<>(decision.obligations());

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

        final var unhandledObligations = new HashSet<>(decision.obligations());

        // @formatter:off
		var bundle = BlockingConstraintHandlerBundle.postEnforceConstraintHandlerBundle(
				runnableHandlersForSignal(Signal.ON_DECISION, decision, unhandledObligations),
				onNextHandlers(decision, unhandledObligations, clazz),
				mapNextHandlers(decision, unhandledObligations, clazz),
				onErrorHandlers(decision, unhandledObligations),
				mapErrorHandlers(decision, unhandledObligations),
				filterConstraintHandlers(decision, unhandledObligations),
				replaceHandler(decision.resource(), clazz));
		// @formatter:on

        if (!unhandledObligations.isEmpty())
            throw missingHandlerError(unhandledObligations);

        return bundle;
    }

    private AccessDeniedException missingHandlerError(HashSet<Value> unhandledObligations) {
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
        final var unhandledObligations = new HashSet<>(decision.obligations());
        final var bundle               = BlockingConstraintHandlerBundle.preEnforceConstraintHandlerBundle(
                runnableHandlersForSignal(Signal.ON_DECISION, decision, unhandledObligations),
                onNextHandlers(decision, unhandledObligations, clazz),
                mapNextHandlers(decision, unhandledObligations, clazz), onErrorHandlers(decision, unhandledObligations),
                mapErrorHandlers(decision, unhandledObligations),
                filterConstraintHandlers(decision, unhandledObligations),
                methodInvocationHandlers(decision, unhandledObligations), replaceHandler(decision.resource(), clazz));

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
        final var unhandledObligations = new HashSet<>(decision.obligations());
        final var bundle               = BlockingConstraintHandlerBundle.<T>accessManagerConstraintHandlerBundle(
                runnableHandlersForSignal(Signal.ON_DECISION, decision, unhandledObligations));
        if (!unhandledObligations.isEmpty())
            throw missingHandlerError(unhandledObligations);
        return bundle;
    }

    private Consumer<MethodInvocation> methodInvocationHandlers(AuthorizationDecision decision,
            HashSet<Value> unhandledObligations) {
        final var obligationHandlers = obligation(
                constructMethodInvocationHandlersForConstraints(decision.obligations(), unhandledObligations::remove));
        final var adviceHandlers     = advice(
                constructMethodInvocationHandlersForConstraints(decision.advice(), FunctionUtil.sink()));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    private Consumer<MethodInvocation> constructMethodInvocationHandlersForConstraints(List<Value> constraints,
            Consumer<Value> onHandlerFound) {
        var handlers = FunctionUtil.<MethodInvocation>sink();

        if (constraints.isEmpty())
            return handlers;

        for (var constraint : constraints) {
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
            HashSet<Value> unhandledObligations) {
        final var obligationHandlers = constructFilterHandlersForConstraint(decision.obligations(),
                unhandledObligations::remove, this::obligation);
        final var adviceHandlers     = constructFilterHandlersForConstraint(decision.advice(), FunctionUtil.sink(),
                this::advice);
        return obligationHandlers.and(adviceHandlers);
    }

    private Predicate<Object> constructFilterHandlersForConstraint(List<Value> constraints,
            Consumer<Value> onHandlerFound, UnaryOperator<Predicate<Object>> wrapper) {
        var handlers = FunctionUtil.all();

        if (constraints.isEmpty())
            return handlers;

        for (var constraint : constraints) {
            for (var provider : filterPredicateProviders) {
                if (provider.isResponsible(constraint)) {
                    onHandlerFound.accept(constraint);
                    handlers = handlers.and(wrapper.apply(provider.getHandler(constraint)));
                }
            }
        }

        return handlers;
    }

    private Consumer<Throwable> onErrorHandlers(AuthorizationDecision decision, HashSet<Value> unhandledObligations) {
        final var obligationHandlers = obligation(
                constructOnErrorHandlersForConstraints(decision.obligations(), unhandledObligations::remove));
        final var adviceHandlers     = advice(
                constructOnErrorHandlersForConstraints(decision.advice(), FunctionUtil.sink()));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    private Consumer<Throwable> constructOnErrorHandlersForConstraints(List<Value> constraints,
            Consumer<Value> onHandlerFound) {
        var handlers = FunctionUtil.<Throwable>sink();

        if (constraints.isEmpty())
            return handlers;

        for (var constraint : constraints) {
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
            HashSet<Value> unhandledObligations) {
        final var obligationHandlers = constructMapErrorHandlersForConstraints(decision.obligations(),
                unhandledObligations::remove, this::obligation);
        final var adviceHandlers     = constructMapErrorHandlersForConstraints(decision.advice(), FunctionUtil.sink(),
                this::advice);
        return mapBoth(obligationHandlers, adviceHandlers);
    }

    private UnaryOperator<Throwable> constructMapErrorHandlersForConstraints(List<Value> constraints,
            Consumer<Value> onHandlerFound, UnaryOperator<UnaryOperator<Throwable>> wrapper) {
        var handlers = UnaryOperator.<Throwable>identity();

        if (constraints.isEmpty())
            return handlers;

        final var prioritizedHandlers = new ArrayList<HandlerWithPriority<UnaryOperator<Throwable>>>();

        for (var constraint : constraints) {
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

    private <T> UnaryOperator<T> mapNextHandlers(AuthorizationDecision decision, HashSet<Value> unhandledObligations,
            Class<T> clazz) {
        final var obligationHandlers = constructMapNextHandlersForConstraints(decision.obligations(),
                unhandledObligations::remove, clazz, this::obligation);
        final var adviceHandlers     = constructMapNextHandlersForConstraints(decision.advice(), FunctionUtil.sink(),
                clazz, this::advice);
        return mapBoth(obligationHandlers, adviceHandlers);
    }

    @Data
    @AllArgsConstructor
    private static class HandlerWithPriority<T> implements Comparable<HandlerWithPriority<T>> {
        T   handler;
        int priority;

        @Override
        public int compareTo(@NonNull HandlerWithPriority<T> o) {
            return Integer.compare(o.priority, priority);
        }
    }

    @SuppressWarnings("unchecked") // false positive. "support()" does check
    private <T> UnaryOperator<T> constructMapNextHandlersForConstraints(List<Value> constraints,
            Consumer<Value> onHandlerFound, Class<T> clazz, UnaryOperator<UnaryOperator<T>> wrapper) {
        var handlers = UnaryOperator.<T>identity();

        if (constraints.isEmpty())
            return handlers;

        final var prioritizedHandlers = new ArrayList<HandlerWithPriority<UnaryOperator<T>>>();
        for (var constraint : constraints) {
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

    private <T> Consumer<T> onNextHandlers(AuthorizationDecision decision, HashSet<Value> unhandledObligations,
            Class<T> clazz) {
        final var obligationHandlers = obligation(
                constructOnNextHandlersForConstraints(decision.obligations(), unhandledObligations::remove, clazz));
        final var adviceHandlers     = advice(
                constructOnNextHandlersForConstraints(decision.advice(), FunctionUtil.sink(), clazz));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    @SuppressWarnings("unchecked") // false positive. "support()" does check
    private <T> Consumer<T> constructOnNextHandlersForConstraints(List<Value> constraints,
            Consumer<Value> onHandlerFound, Class<T> clazz) {
        var handlers = FunctionUtil.<T>sink();

        if (constraints.isEmpty())
            return handlers;

        for (var constraint : constraints) {
            for (var provider : globalConsumerProviders) {
                if (provider.supports(clazz) && provider.isResponsible(constraint)) {
                    onHandlerFound.accept(constraint);
                    handlers = consumeWithBoth(handlers, (Consumer<T>) provider.getHandler(constraint));
                }
            }
        }

        return handlers;
    }

    private LongConsumer requestHandlers(AuthorizationDecision decision, HashSet<Value> unhandledObligations) {
        final var obligationHandlers = obligation(
                constructRequestHandlersForConstraints(decision.obligations(), unhandledObligations::remove));
        final var adviceHandlers     = advice(
                constructRequestHandlersForConstraints(decision.advice(), FunctionUtil.sink()));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    private LongConsumer constructRequestHandlersForConstraints(List<Value> constraints,
            Consumer<Value> onHandlerFound) {
        var handlers = FunctionUtil.longSink();

        if (constraints.isEmpty())
            return handlers;

        for (var constraint : constraints) {
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
            HashSet<Value> unhandledObligations) {
        final var obligationHandlers = obligation(
                constructSubscriptionHandlersForConstraints(decision.obligations(), unhandledObligations::remove));
        final var adviceHandlers     = advice(
                constructSubscriptionHandlersForConstraints(decision.advice(), FunctionUtil.sink()));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    private Consumer<Subscription> constructSubscriptionHandlersForConstraints(List<Value> constraints,
            Consumer<Value> onHandlerFound) {
        var handlers = FunctionUtil.<Subscription>sink();

        if (constraints.isEmpty())
            return handlers;

        for (var constraint : constraints) {
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
            HashSet<Value> unhandledObligations) {
        final var onDecisionObligationHandlers = obligation(
                constructRunnableHandlersForConstraints(signal, decision.obligations(), unhandledObligations::remove));
        final var onDecisionAdviceHandlers     = advice(
                constructRunnableHandlersForConstraints(signal, decision.advice(), FunctionUtil.sink()));
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

    private Runnable constructRunnableHandlersForConstraints(Signal signal, List<Value> constraints,
            Consumer<Value> onHandlerFound) {
        var handlers = (Runnable) () -> {};

        if (constraints.isEmpty())
            return handlers;

        for (var constraint : constraints) {
            for (var provider : globalRunnableIndex.getOrDefault(signal, List.of())) {
                if (provider.isResponsible(constraint)) {
                    onHandlerFound.accept(constraint);
                    handlers = runBoth(handlers, provider.getHandler(constraint));
                }
            }
        }

        return handlers;
    }

    private <T> UnaryOperator<T> replaceHandler(Value resource, Class<T> clazz) {
        if (resource instanceof UndefinedValue)
            return UnaryOperator.identity();

        try {
            final var replacement = unmarshallResource(resource, clazz);
            return originalResult -> replacement;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            final var message = "Cannot map resource %s to type %s".formatted(resource, clazz.getSimpleName());
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
     * @param resource a Value to replace the RAP (undefined means no replacement)
     * @param clazz event type class
     * @return the replacement, if a resource was present. Else return the original
     * RAP.
     */
    public <T> Flux<T> replaceIfResourcePresent(Flux<T> resourceAccessPoint, Value resource, Class<T> clazz) {
        if (resource instanceof UndefinedValue)
            return resourceAccessPoint;
        try {
            return Flux.just(unmarshallResource(resource, clazz));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            final var message = "Cannot map resource %s to type %s".formatted(resource, clazz.getSimpleName());
            log.warn(message);
            return Flux.error(new AccessDeniedException(message, e));
        }
    }

    /**
     * Convenience method to convert a Value to a JavaObject using the global
     * ObjectMapper.
     *
     * @param <T> type of the expected output
     * @param resource a Value
     * @param clazz class of the expected output
     * @return the Value converted into the provided class
     * @throws JsonProcessingException on JSON marshaling error
     * @throws IllegalArgumentException on JSON marshaling error
     */
    public <T> T unmarshallResource(Value resource, Class<T> clazz) throws JsonProcessingException {
        var jsonString = ValueJsonMarshaller.toJsonString(resource);
        return mapper.readValue(jsonString, clazz);
    }

}
