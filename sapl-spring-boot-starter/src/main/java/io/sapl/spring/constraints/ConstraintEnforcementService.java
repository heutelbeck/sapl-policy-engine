/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.model.NullValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
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
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Subscription;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    private static final String ERROR_CANNOT_MAP_RESOURCE               = "Cannot map resource %s to type %s";
    private static final String ERROR_CONSUMER_OBLIGATION_FAILED        = "Failed to execute obligation handler ";
    private static final String ERROR_INVOCATION_TYPE_INVALID           = "MethodInvocation not ReflectiveMethodInvocation";
    private static final String ERROR_MAPPING_OBLIGATION_FAILED         = "Failed to execute mapping obligation handler";
    private static final String ERROR_NULL_RESOURCE_IN_REACTIVE_CONTEXT = "Cannot replace resource with null in reactive context. Null cannot be emitted in Reactive Streams.";
    private static final String ERROR_REQUEST_OBLIGATION_FAILED         = "Failed to execute request obligation handler";
    private static final String ERROR_RUNNABLE_OBLIGATION_FAILED        = "Failed to execute runnable obligation handler";
    private static final String ERROR_UNHANDLED_OBLIGATIONS             = "Access Denied by PEP. The PDP required at least one obligation to be enforced for which no handler is registered. Obligations that could not be handled: %s";

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
        for (val provider : globalRunnableProviders)
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
        val wrapped = replaceIfResourcePresent(resourceAccessPoint, decision.resource(), clazz);
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

        if (decision.resource() instanceof NullValue) {
            throw new AccessDeniedException(ERROR_NULL_RESOURCE_IN_REACTIVE_CONTEXT);
        }

        val unhandledObligations = new HashSet<>(decision.obligations());
        val bundle               = constructReactiveBundle(decision, clazz, unhandledObligations);

        if (!unhandledObligations.isEmpty()) {
            for (val unhandledObligation : unhandledObligations) {
                if (Arrays.stream(ignoredObligations).filter(ignored -> ignored.equals(unhandledObligation)).findFirst()
                        .isEmpty()) {
                    throw missingHandlerError(unhandledObligations);
                }
            }
        }

        return bundle;
    }

    /**
     * Builds a best-effort reactive constraint handler bundle for non-PERMIT
     * decision paths. Unlike {@link #reactiveTypeBundleFor}, this method does not
     * throw if some obligations have no registered handler. All handlers that CAN
     * be constructed are included, ensuring registered audit and logging handlers
     * still execute.
     *
     * @param <T> event type
     * @param decision a decision
     * @param clazz class of the event type
     * @return a ReactiveConstraintHandlerBundle with all constructable handlers
     */
    public <T> ReactiveConstraintHandlerBundle<T> reactiveTypeBestEffortBundleFor(AuthorizationDecision decision,
            Class<T> clazz) {

        if (decision.resource() instanceof NullValue) {
            return new ReactiveConstraintHandlerBundle<>();
        }

        try {
            val unhandledObligations = new HashSet<>(decision.obligations());
            return constructReactiveBundle(decision, clazz, unhandledObligations);
        } catch (AccessDeniedException e) {
            return new ReactiveConstraintHandlerBundle<>();
        }
    }

    private <T> ReactiveConstraintHandlerBundle<T> constructReactiveBundle(AuthorizationDecision decision,
            Class<T> clazz, HashSet<Value> unhandledObligations) {
        // @formatter:off
		return new ReactiveConstraintHandlerBundle<>(
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
				methodInvocationHandlers(decision, unhandledObligations),
				replaceHandler(decision.resource(), clazz));
		// @formatter:on
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

        val unhandledObligations = new HashSet<>(decision.obligations());

        // @formatter:off
		val bundle = BlockingConstraintHandlerBundle.postEnforceConstraintHandlerBundle(
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
        return new AccessDeniedException(String.format(ERROR_UNHANDLED_OBLIGATIONS, unhandledObligations));
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
        val unhandledObligations = new HashSet<>(decision.obligations());
        val bundle               = BlockingConstraintHandlerBundle.preEnforceConstraintHandlerBundle(
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
        val unhandledObligations = new HashSet<>(decision.obligations());
        val bundle               = BlockingConstraintHandlerBundle.<T>accessManagerConstraintHandlerBundle(
                runnableHandlersForSignal(Signal.ON_DECISION, decision, unhandledObligations));
        if (!unhandledObligations.isEmpty())
            throw missingHandlerError(unhandledObligations);
        return bundle;
    }

    private Consumer<MethodInvocation> methodInvocationHandlers(AuthorizationDecision decision,
            HashSet<Value> unhandledObligations) {
        val obligationHandlers = obligation(
                constructMethodInvocationHandlersForConstraints(decision.obligations(), unhandledObligations::remove));
        val adviceHandlers     = advice(
                constructMethodInvocationHandlersForConstraints(decision.advice(), FunctionUtil.sink()));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    private Consumer<MethodInvocation> constructMethodInvocationHandlersForConstraints(List<Value> constraints,
            Consumer<Value> onHandlerFound) {
        var handlers = FunctionUtil.<MethodInvocation>sink();

        if (constraints.isEmpty())
            return handlers;

        for (val constraint : constraints) {
            for (val provider : methodInvocationHandlerProviders) {
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
            if (!(i instanceof ReflectiveMethodInvocation reflective))
                throw new IllegalArgumentException(ERROR_INVOCATION_TYPE_INVALID);
            handler.accept(reflective);
        };
    }

    private Predicate<Object> filterConstraintHandlers(AuthorizationDecision decision,
            HashSet<Value> unhandledObligations) {
        val obligationHandlers = constructFilterHandlersForConstraint(decision.obligations(),
                unhandledObligations::remove, this::obligation);
        val adviceHandlers     = constructFilterHandlersForConstraint(decision.advice(), FunctionUtil.sink(),
                this::advice);
        return obligationHandlers.and(adviceHandlers);
    }

    private Predicate<Object> constructFilterHandlersForConstraint(List<Value> constraints,
            Consumer<Value> onHandlerFound, UnaryOperator<Predicate<Object>> wrapper) {
        var handlers = FunctionUtil.all();

        if (constraints.isEmpty())
            return handlers;

        for (val constraint : constraints) {
            for (val provider : filterPredicateProviders) {
                if (provider.isResponsible(constraint)) {
                    onHandlerFound.accept(constraint);
                    handlers = handlers.and(wrapper.apply(provider.getHandler(constraint)));
                }
            }
        }

        return handlers;
    }

    private Consumer<Throwable> onErrorHandlers(AuthorizationDecision decision, HashSet<Value> unhandledObligations) {
        val obligationHandlers = obligation(
                constructOnErrorHandlersForConstraints(decision.obligations(), unhandledObligations::remove));
        val adviceHandlers     = advice(constructOnErrorHandlersForConstraints(decision.advice(), FunctionUtil.sink()));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    private Consumer<Throwable> constructOnErrorHandlersForConstraints(List<Value> constraints,
            Consumer<Value> onHandlerFound) {
        var handlers = FunctionUtil.<Throwable>sink();

        if (constraints.isEmpty())
            return handlers;

        for (val constraint : constraints) {
            for (val provider : globalErrorHandlerProviders) {
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
        val obligationHandlers = constructMapErrorHandlersForConstraints(decision.obligations(),
                unhandledObligations::remove, this::obligation);
        val adviceHandlers     = constructMapErrorHandlersForConstraints(decision.advice(), FunctionUtil.sink(),
                this::advice);
        return mapBoth(obligationHandlers, adviceHandlers);
    }

    private UnaryOperator<Throwable> constructMapErrorHandlersForConstraints(List<Value> constraints,
            Consumer<Value> onHandlerFound, UnaryOperator<UnaryOperator<Throwable>> wrapper) {
        var handlers = UnaryOperator.<Throwable>identity();

        if (constraints.isEmpty())
            return handlers;

        val prioritizedHandlers = new ArrayList<HandlerWithPriority<UnaryOperator<Throwable>>>();

        for (val constraint : constraints) {
            for (val provider : globalErrorMappingHandlerProviders) {
                if (provider.isResponsible(constraint)) {
                    onHandlerFound.accept(constraint);
                    prioritizedHandlers
                            .add(new HandlerWithPriority<>(provider.getHandler(constraint), provider.getPriority()));
                }
            }
        }

        Collections.sort(prioritizedHandlers);

        for (val handler : prioritizedHandlers) {
            handlers = mapBoth(handlers, wrapper.apply(handler.handler()));
        }

        return handlers;
    }

    private <T> UnaryOperator<T> mapNextHandlers(AuthorizationDecision decision, HashSet<Value> unhandledObligations,
            Class<T> clazz) {
        val obligationHandlers = constructMapNextHandlersForConstraints(decision.obligations(),
                unhandledObligations::remove, clazz, this::obligation);
        val adviceHandlers     = constructMapNextHandlersForConstraints(decision.advice(), FunctionUtil.sink(), clazz,
                this::advice);
        return mapBoth(obligationHandlers, adviceHandlers);
    }

    private record HandlerWithPriority<T>(T handler, int priority) implements Comparable<HandlerWithPriority<T>> {
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

        val prioritizedHandlers = new ArrayList<HandlerWithPriority<UnaryOperator<T>>>();
        for (val constraint : constraints) {
            for (val provider : globalMappingHandlerProviders) {
                if (provider.supports(clazz) && provider.isResponsible(constraint)) {
                    onHandlerFound.accept(constraint);
                    prioritizedHandlers.add(new HandlerWithPriority<>(
                            (UnaryOperator<T>) provider.getHandler(constraint), provider.getPriority()));
                }
            }
        }

        Collections.sort(prioritizedHandlers);

        for (val handler : prioritizedHandlers) {
            handlers = mapBoth(handlers, wrapper.apply(handler.handler()));
        }

        return handlers;
    }

    private <T> Consumer<T> onNextHandlers(AuthorizationDecision decision, HashSet<Value> unhandledObligations,
            Class<T> clazz) {
        val obligationHandlers = obligation(
                constructOnNextHandlersForConstraints(decision.obligations(), unhandledObligations::remove, clazz));
        val adviceHandlers     = advice(
                constructOnNextHandlersForConstraints(decision.advice(), FunctionUtil.sink(), clazz));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    @SuppressWarnings("unchecked") // false positive. "support()" does check
    private <T> Consumer<T> constructOnNextHandlersForConstraints(List<Value> constraints,
            Consumer<Value> onHandlerFound, Class<T> clazz) {
        var handlers = FunctionUtil.<T>sink();

        if (constraints.isEmpty())
            return handlers;

        for (val constraint : constraints) {
            for (val provider : globalConsumerProviders) {
                if (provider.supports(clazz) && provider.isResponsible(constraint)) {
                    onHandlerFound.accept(constraint);
                    handlers = consumeWithBoth(handlers, (Consumer<T>) provider.getHandler(constraint));
                }
            }
        }

        return handlers;
    }

    private LongConsumer requestHandlers(AuthorizationDecision decision, HashSet<Value> unhandledObligations) {
        val obligationHandlers = obligation(
                constructRequestHandlersForConstraints(decision.obligations(), unhandledObligations::remove));
        val adviceHandlers     = advice(constructRequestHandlersForConstraints(decision.advice(), FunctionUtil.sink()));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    private LongConsumer constructRequestHandlersForConstraints(List<Value> constraints,
            Consumer<Value> onHandlerFound) {
        var handlers = FunctionUtil.longSink();

        if (constraints.isEmpty())
            return handlers;

        for (val constraint : constraints) {
            for (val provider : globalRequestHandlerProviders) {
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
        val obligationHandlers = obligation(
                constructSubscriptionHandlersForConstraints(decision.obligations(), unhandledObligations::remove));
        val adviceHandlers     = advice(
                constructSubscriptionHandlersForConstraints(decision.advice(), FunctionUtil.sink()));
        return consumeWithBoth(obligationHandlers, adviceHandlers);
    }

    private Consumer<Subscription> constructSubscriptionHandlersForConstraints(List<Value> constraints,
            Consumer<Value> onHandlerFound) {
        var handlers = FunctionUtil.<Subscription>sink();

        if (constraints.isEmpty())
            return handlers;

        for (val constraint : constraints) {
            for (val provider : globalSubscriptionHandlerProviders) {
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
        val onDecisionObligationHandlers = obligation(
                constructRunnableHandlersForConstraints(signal, decision.obligations(), unhandledObligations::remove));
        val onDecisionAdviceHandlers     = advice(
                constructRunnableHandlersForConstraints(signal, decision.advice(), FunctionUtil.sink()));
        return runBoth(onDecisionObligationHandlers, onDecisionAdviceHandlers);
    }

    private <T> Predicate<T> obligation(Predicate<T> handlers) {
        return x -> {
            try {
                return handlers.test(x);
            } catch (Throwable t) {
                Exceptions.throwIfFatal(t);
                throw new AccessDeniedException(ERROR_RUNNABLE_OBLIGATION_FAILED, t);
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
                throw new AccessDeniedException(ERROR_MAPPING_OBLIGATION_FAILED, t);
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
                throw new AccessDeniedException(ERROR_RUNNABLE_OBLIGATION_FAILED, t);
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
                throw new AccessDeniedException(ERROR_CONSUMER_OBLIGATION_FAILED + s.getClass().getSimpleName(), t);
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
                throw Exceptions.bubble(new AccessDeniedException(ERROR_REQUEST_OBLIGATION_FAILED, t));
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

        for (val constraint : constraints) {
            for (val provider : globalRunnableIndex.getOrDefault(signal, List.of())) {
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
            val replacement = unmarshallResource(resource, clazz);
            return originalResult -> replacement;
        } catch (JacksonException | IllegalArgumentException e) {
            val message = ERROR_CANNOT_MAP_RESOURCE.formatted(resource, clazz.getSimpleName());
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
        if (resource instanceof NullValue)
            return Flux.error(new AccessDeniedException(ERROR_NULL_RESOURCE_IN_REACTIVE_CONTEXT));
        try {
            return Flux.just(unmarshallResource(resource, clazz));
        } catch (JacksonException | IllegalArgumentException e) {
            val message = ERROR_CANNOT_MAP_RESOURCE.formatted(resource, clazz.getSimpleName());
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
     * @throws JacksonException on JSON marshaling error
     * @throws IllegalArgumentException on JSON marshaling error
     */
    public <T> T unmarshallResource(Value resource, Class<T> clazz) throws JacksonException {
        val jsonString = ValueJsonMarshaller.toJsonString(resource);
        return mapper.readValue(jsonString, clazz);
    }

}
