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
package io.sapl.spring.pep.streaming;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import io.sapl.spring.pep.streaming.MealyMachine.Emission;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.Emit;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.EmitComplete;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.EmitError;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.EmitTransition;
import io.sapl.spring.pep.streaming.MealyMachine.Event;
import io.sapl.spring.pep.streaming.MealyMachine.Event.Cancel;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpDeny;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpError;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpPermit;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpSuspend;
import io.sapl.spring.pep.streaming.MealyMachine.Event.RapComplete;
import io.sapl.spring.pep.streaming.MealyMachine.Event.RapError;
import io.sapl.spring.pep.streaming.MealyMachine.Event.RapItem;
import io.sapl.spring.pep.streaming.MealyMachine.State;
import io.sapl.spring.pep.streaming.MealyMachine.State.Permitting;
import io.sapl.spring.pep.streaming.MealyMachine.State.Suspended;
import io.sapl.spring.pep.streaming.MealyMachine.State.Terminated;
import io.sapl.spring.pep.streaming.MealyMachine.DenyKind;
import io.sapl.spring.pep.streaming.MealyMachine.TransitionReason;
import io.sapl.spring.pep.streaming.MealyMachine.TransitionReason.Granted;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Operators;
import reactor.core.publisher.SynchronousSink;
import reactor.util.context.ContextView;

/**
 * Reactor adapter for the streaming PEP. Drives the pure
 * {@link MealyMachine} from a PDP decision flux and a lazily-subscribed
 * RAP publisher; renders the resulting {@link Emission}s onto a
 * downstream {@link Flux} for the subscriber.
 * <p>
 * One pipeline per method invocation. Owns the per-subscription mutable
 * state (current FSM state, RAP subscription, sink) and serializes all
 * event delivery through a single lock so the FSM never observes
 * concurrency.
 * <p>
 * Output shape: {@code Flux.create(...)} emits {@link ProtectedPayload}
 * wrappers (private nested record) carrying either a data value or an
 * error; the chain ends with {@code .flatMap(ProtectedPayload::unwrap)}
 * which re-emits the value or raises the error from inside the per-item
 * processing of {@code flatMap}.
 * That positioning is what lets a downstream {@code onErrorContinue} catch
 * boundary signals ({@link AccessDeniedException} on suspend,
 * {@link AccessGrantedException} on resume, when {@code signalTransitions}
 * is enabled) without terminating the subscription. Errors raised
 * directly from the upstream sink (the FSM's
 * {@link Emission.EmitError}) bypass the wrapper and terminate the
 * stream as a real Reactor error.
 *
 * @since 4.1.0
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class StreamingPipeline {

    private static final String ERROR_STREAM_SUSPENDED     = "Stream suspended: %s";
    private static final String WARN_RAP_AFTER_TERMINATION = "RAP item arrived after termination; dropping.";

    private final boolean                                          pauseRapDuringSuspend;
    private final Flux<AuthorizationDecision>                      decisions;
    private final Function<AuthorizationDecision, EnforcementPlan> planner;
    private final Supplier<? extends Flux<?>>                      rapSupplier;
    private final boolean                                          signalTransitions;

    private final Object                       lock          = new Object();
    private final Disposable.Composite         subscriptions = Disposables.composite();
    private FluxSink<ProtectedPayload<Object>> sink;
    private ContextView                        subscriberContext;
    private State                              state;
    private @Nullable BaseSubscriber<Object>   rapSubscription;
    private boolean                            rapReady;
    private long                               subscriberDemand;

    /**
     * Creates a cold {@link Flux} that, on subscription, drives the
     * streaming PEP's FSM from the supplied PDP decision flux and
     * lazily-subscribed RAP. Each subscription gets a fresh pipeline
     * instance with its own state and lifecycle.
     *
     * @param pauseRapDuringSuspend whether the RAP subscription is
     * disposed on entering suspended state and re-subscribed on resume.
     * When {@code false} (default), the RAP stays connected and items
     * are dropped silently by the FSM; when {@code true}, RAP-side
     * side effects pause for the duration of the suspension.
     * @param decisions the PDP decision flux for this subscription; an
     * empty flux is treated as a single DENY decision.
     * @param planner a closure that maps each {@link AuthorizationDecision}
     * to its {@link EnforcementPlan}; typically captures the per-method
     * supported-signal set and output type.
     * @param rapSupplier the protected method's publisher, supplied lazily
     * (invoked on each fresh PERMIT when {@code pauseRapDuringSuspend}
     * is true; once on first PERMIT otherwise).
     * @param signalTransitions whether to surface suspend/resume
     * boundaries to the subscriber as non-terminal exceptions on the
     * error channel.
     * @return a flux that emits items as the FSM permits them, surfaces
     * boundary crossings as {@link AccessDeniedException} or
     * {@link AccessGrantedException} (when {@code signalTransitions} is
     * enabled) on the error channel, and completes / errors when the
     * FSM reaches {@link State.Terminated}.
     */
    public static Flux<Object> create(boolean pauseRapDuringSuspend, Flux<AuthorizationDecision> decisions,
            Function<AuthorizationDecision, EnforcementPlan> planner, Supplier<? extends Flux<?>> rapSupplier,
            boolean signalTransitions) {
        val pipeline = new StreamingPipeline(pauseRapDuringSuspend, decisions, planner, rapSupplier, signalTransitions);
        // handle is used instead of flatMap so downstream request signals
        // pass through to the create-sink without an intervening prefetch
        // buffer. Errors raised via sink.error inside the handler remain
        // eligible for onErrorContinue, which is what surfaces boundary
        // signals as non-terminal events.
        return Flux.<ProtectedPayload<Object>>create(pipeline::initialize).handle(StreamingPipeline::unpackPayload);
    }

    private static void unpackPayload(ProtectedPayload<Object> payload, SynchronousSink<Object> sink) {
        val error = payload.error();
        if (error != null) {
            sink.error(error);
            return;
        }
        val value = payload.value();
        if (value != null) {
            sink.next(value);
        }
    }

    private void initialize(FluxSink<ProtectedPayload<Object>> fluxSink) {
        synchronized (lock) {
            this.sink              = fluxSink;
            this.subscriberContext = fluxSink.contextView();
            this.state             = State.Pending.INSTANCE;
        }
        fluxSink.onCancel(this::onCancel);
        fluxSink.onDispose(subscriptions::dispose);
        fluxSink.onRequest(this::onDownstreamRequest);
        startPdpSubscription();
    }

    /**
     * Records {@code n} additional units of subscriber demand and, if the
     * upstream subscription has finished setup, forwards exactly those
     * {@code n} units to it. When the upstream is not yet ready (no
     * permit decision yet, or paused during suspend), the demand is held
     * in {@link #subscriberDemand} and replayed on the next
     * {@code hookOnSubscribe}.
     */
    private void onDownstreamRequest(long n) {
        BaseSubscriber<Object> sub = null;
        synchronized (lock) {
            subscriberDemand = Operators.addCap(subscriberDemand, n);
            if (rapReady && rapSubscription != null) {
                sub = rapSubscription;
            }
        }
        if (sub != null) {
            sub.request(n);
        }
    }

    /**
     * Requests one additional item from the upstream to compensate for
     * an item that was dropped by the gate. Does not change the
     * outstanding subscriber demand: the subscriber did not receive an
     * item, so the demand is satisfied by the next permitted item.
     */
    private void requestOneMoreFromUpstream() {
        BaseSubscriber<Object> sub = null;
        synchronized (lock) {
            if (rapReady && rapSubscription != null) {
                sub = rapSubscription;
            }
        }
        if (sub != null) {
            sub.request(1L);
        }
    }

    private void startPdpSubscription() {
        // The PDP decision flux is contractually infinite for streaming
        // subscriptions. The 2-arg subscribe variant is correct: any
        // (out-of-contract) onComplete from the PDP is silently
        // ignored; the subscription continues to gate items against the
        // last-known plan until cancelled or the RAP terminates.
        val pdpSub = decisions.switchIfEmpty(Flux.just(io.sapl.api.pdp.AuthorizationDecision.DENY))
                .contextWrite(subscriberContext).subscribe(this::onPdpDecision, this::onPdpError);
        subscriptions.add(pdpSub);
    }

    private void onPdpDecision(AuthorizationDecision decision) {
        val   plan   = planner.apply(decision);
        val   failed = plan.enforceDecisionConstraints(decision);
        Event event  = classify(decision, plan, failed);
        process(event);
    }

    /**
     * Routes each PDP decision into a single FSM event under the strict
     * fail-closed discipline. PERMIT becomes either {@link PdpPermit}
     * (decision-scoped enforcement OK) or {@link PdpDeny} with kind
     * {@link DenyKind#PERMIT_NOT_ENFORCEABLE}. Only {@code Decision.SUSPEND}
     * becomes {@link PdpSuspend}. INDETERMINATE, NOT_APPLICABLE, and DENY
     * all become {@link PdpDeny} with the corresponding {@link DenyKind}.
     */
    Event classify(AuthorizationDecision decision, EnforcementPlan plan, boolean decisionScopedFailed) {
        return switch (decision.decision()) {
        case PERMIT         -> decisionScopedFailed ? new PdpDeny(decision, plan, DenyKind.PERMIT_NOT_ENFORCEABLE)
                : new PdpPermit(decision, plan);
        case SUSPEND        -> new PdpSuspend(decision, plan, new TransitionReason.Suspended(decision));
        case INDETERMINATE  -> new PdpDeny(decision, plan, DenyKind.INDETERMINATE);
        case NOT_APPLICABLE -> new PdpDeny(decision, plan, DenyKind.NO_POLICY_APPLICABLE);
        case DENY           -> new PdpDeny(decision, plan, DenyKind.POLICY_DENIED);
        };
    }

    private void onPdpError(Throwable throwable) {
        process(new PdpError(throwable));
    }

    private void ensureRapSubscribed() {
        BaseSubscriber<Object> subscriber;
        synchronized (lock) {
            if (rapSubscription != null || state instanceof Terminated) {
                return;
            }
            subscriber      = createRapSubscriber();
            rapSubscription = subscriber;
            rapReady        = false;
            subscriptions.add(subscriber);
        }
        rapSupplier.get().contextWrite(subscriberContext).subscribe(subscriber);
    }

    private BaseSubscriber<Object> createRapSubscriber() {
        return new BaseSubscriber<Object>() {
            @Override
            protected void hookOnSubscribe(Subscription s) {
                long toForward;
                synchronized (lock) {
                    rapReady  = true;
                    toForward = subscriberDemand;
                }
                if (toForward > 0) {
                    s.request(toForward);
                }
            }

            @Override
            protected void hookOnNext(Object value) {
                onRapItem(value);
            }

            @Override
            protected void hookOnError(Throwable throwable) {
                onRapError(throwable);
            }

            @Override
            protected void hookOnComplete() {
                onRapComplete();
            }
        };
    }

    private void disposeRap() {
        synchronized (lock) {
            if (rapSubscription == null) {
                return;
            }
            val sub = rapSubscription;
            rapSubscription = null;
            rapReady        = false;
            sub.dispose();
            subscriptions.remove(sub);
        }
    }

    private void onRapItem(Object payload) {
        EnforcementPlan plan;
        synchronized (lock) {
            if (!(state instanceof Permitting permitting)) {
                if (state instanceof Terminated) {
                    log.warn(WARN_RAP_AFTER_TERMINATION);
                    return;
                }
                process(new RapItem(payload, null));
                return;
            }
            plan = permitting.plan();
        }
        val signal = OutputSignal.ofUnchecked(plan.outputType(), payload);
        val result = plan.execute(signal, false);
        process(new RapItem(payload, result));
    }

    private void onRapError(Throwable throwable) {
        process(new RapError(throwable));
    }

    private void onRapComplete() {
        process(RapComplete.INSTANCE);
    }

    private void onCancel() {
        process(Cancel.INSTANCE);
    }

    private void process(Event event) {
        State   priorState;
        State   nextState;
        boolean rapItemGated = false;
        synchronized (lock) {
            if (state instanceof Terminated) {
                return;
            }
            priorState = state;
            val transition = MealyMachine.step(state, event);
            state     = transition.newState();
            nextState = state;
            boolean anyDataEmitted = false;
            for (val emission : transition.emissions()) {
                renderEmission(emission);
                if (emission instanceof Emit) {
                    anyDataEmitted = true;
                    if (subscriberDemand > 0) {
                        subscriberDemand--;
                    }
                }
            }
            if (event instanceof RapItem && !anyDataEmitted) {
                rapItemGated = true;
            }
            if (transition.isTerminal()) {
                subscriptions.dispose();
                rapSubscription = null;
                rapReady        = false;
                return;
            }
        }
        manageRapSubscription(priorState, nextState);
        if (nextState instanceof Permitting) {
            ensureRapSubscribed();
        }
        if (rapItemGated) {
            requestOneMoreFromUpstream();
        }
    }

    /**
     * RAP connection management on state transitions when
     * {@code pauseRapDuringSuspend} is true. Disposes the RAP
     * subscription when the FSM enters Suspended from another state,
     * and ensures it is re-subscribed when the FSM enters Permitting
     * from Suspended.
     */
    private void manageRapSubscription(State priorState, State nextState) {
        if (!pauseRapDuringSuspend) {
            return;
        }
        if (nextState instanceof Suspended && !(priorState instanceof Suspended)) {
            disposeRap();
        }
    }

    private void renderEmission(Emission emission) {
        switch (emission) {
        case Emit(var value)            -> withSink(s -> s.next(ProtectedPayload.ofValue(value)));
        case EmitError(var throwable)   -> withSink(s -> s.error(throwable));
        case EmitComplete ignored       -> withSink(FluxSink::complete);
        case EmitTransition(var reason) -> renderTransition(reason);
        }
    }

    private void renderTransition(TransitionReason reason) {
        // Both directions are gated by the same flag: if the subscriber
        // hasn't opted into transition signaling, neither suspend nor
        // resume boundaries are surfaced. Terminal denies bypass this
        // path entirely via EmitError.
        if (!signalTransitions) {
            return;
        }
        if (reason instanceof Granted(var decision)) {
            withSink(s -> s.next(ProtectedPayload.ofError(new AccessGrantedException(decision))));
            return;
        }
        val message = ERROR_STREAM_SUSPENDED.formatted(reason);
        withSink(s -> s.next(ProtectedPayload.ofError(new AccessDeniedException(message))));
    }

    private void withSink(Consumer<FluxSink<ProtectedPayload<Object>>> action) {
        if (sink != null) {
            action.accept(sink);
        }
    }

    /**
     * Carries either a data value or a non-terminal error through the
     * single {@code Flux.create} sink. The chain terminates with
     * {@code .handle(unpackPayload)} which re-emits the value via
     * {@link SynchronousSink#next} or raises the error via
     * {@link SynchronousSink#error}. Raising the error from inside a
     * handle invocation keeps the error eligible for downstream
     * {@code onErrorContinue}. Errors raised directly from the upstream
     * sink (e.g., {@code FluxSink.error}) are terminal and not
     * recoverable; only errors raised inside an operator's per-item
     * processing are eligible for {@code onErrorContinue}.
     */
    private record ProtectedPayload<T>(@Nullable T value, @Nullable Throwable error) {

        static <T> ProtectedPayload<T> ofValue(T value) {
            return new ProtectedPayload<>(value, null);
        }

        static <T> ProtectedPayload<T> ofError(Throwable error) {
            return new ProtectedPayload<>(null, error);
        }
    }
}
