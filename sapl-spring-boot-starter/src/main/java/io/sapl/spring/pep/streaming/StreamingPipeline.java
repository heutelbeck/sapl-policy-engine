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
import io.sapl.spring.pep.streaming.Emission.Emit;
import io.sapl.spring.pep.streaming.Emission.EmitComplete;
import io.sapl.spring.pep.streaming.Emission.EmitError;
import io.sapl.spring.pep.streaming.Emission.EmitTransition;
import io.sapl.spring.pep.streaming.Emission.StayQuiet;
import io.sapl.spring.pep.streaming.Event.Cancel;
import io.sapl.spring.pep.streaming.Event.PdpComplete;
import io.sapl.spring.pep.streaming.Event.PdpDeny;
import io.sapl.spring.pep.streaming.Event.PdpError;
import io.sapl.spring.pep.streaming.Event.PdpPermit;
import io.sapl.spring.pep.streaming.Event.PdpSuspend;
import io.sapl.spring.pep.streaming.Event.RapComplete;
import io.sapl.spring.pep.streaming.Event.RapError;
import io.sapl.spring.pep.streaming.Event.RapItem;
import io.sapl.spring.pep.streaming.State.Permitting;
import io.sapl.spring.pep.streaming.State.Suspended;
import io.sapl.spring.pep.streaming.State.Terminated;
import io.sapl.spring.pep.streaming.TransitionReason.EvaluationError;
import io.sapl.spring.pep.streaming.TransitionReason.Granted;
import io.sapl.spring.pep.streaming.TransitionReason.NoPolicyApplicable;
import io.sapl.spring.pep.streaming.TransitionReason.PermitNotEnforceable;
import io.sapl.spring.pep.streaming.TransitionReason.PolicySuspended;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.util.context.ContextView;

/**
 * Reactor adapter for the streaming PEP. Drives the pure
 * {@link StateMachine} from a PDP decision flux and a lazily-subscribed
 * RAP publisher; renders the resulting {@link Emission}s onto a
 * downstream {@link Flux} for the subscriber.
 * <p>
 * One pipeline per method invocation. Owns the per-subscription mutable
 * state (current FSM state, RAP subscription, sink) and serializes all
 * event delivery through a single lock so the FSM never observes
 * concurrency.
 * <p>
 * Output shape: {@code Flux.create(...)} emits {@link ProtectedPayload}
 * wrappers carrying either a data value or an error; the chain ends with
 * {@code .flatMap(ProtectedPayload::unwrap)} which re-emits the value or
 * raises the error from inside the per-item processing of {@code flatMap}.
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
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class StreamingPipeline {

    private static final String ERROR_ACCESS_DENIED        = "Access denied: %s";
    private static final String WARN_RAP_AFTER_TERMINATION = "RAP item arrived after termination; dropping.";

    private final boolean                                          terminateOnItemEnforcementFailure;
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
    private @Nullable Disposable               rapSubscription;

    /**
     * Creates a cold {@link Flux} that, on subscription, drives the
     * streaming PEP's FSM from the supplied PDP decision flux and
     * lazily-subscribed RAP. Each subscription gets a fresh pipeline
     * instance with its own state and lifecycle.
     *
     * @param terminateOnItemEnforcementFailure whether per-item
     * obligation enforcement failure terminates the subscription. When
     * {@code false} (default), failure transitions to suspended and a
     * later PERMIT may resume; when {@code true} the subscription
     * terminates with {@link AccessDeniedException}.
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
    public static Flux<Object> create(boolean terminateOnItemEnforcementFailure, boolean pauseRapDuringSuspend,
            Flux<AuthorizationDecision> decisions, Function<AuthorizationDecision, EnforcementPlan> planner,
            Supplier<? extends Flux<?>> rapSupplier, boolean signalTransitions) {
        val pipeline = new StreamingPipeline(terminateOnItemEnforcementFailure, pauseRapDuringSuspend, decisions,
                planner, rapSupplier, signalTransitions);
        return Flux.<ProtectedPayload<Object>>create(pipeline::initialize).flatMap(ProtectedPayload::unwrap);
    }

    private void initialize(FluxSink<ProtectedPayload<Object>> fluxSink) {
        synchronized (lock) {
            this.sink              = fluxSink;
            this.subscriberContext = fluxSink.contextView();
            this.state             = State.Pending.INSTANCE;
        }
        fluxSink.onCancel(this::onCancel);
        fluxSink.onDispose(subscriptions::dispose);
        startPdpSubscription();
    }

    private void startPdpSubscription() {
        val pdpSub = decisions.switchIfEmpty(Flux.just(io.sapl.api.pdp.AuthorizationDecision.DENY))
                .contextWrite(subscriberContext).subscribe(this::onPdpDecision, this::onPdpError, this::onPdpComplete);
        subscriptions.add(pdpSub);
    }

    private void onPdpDecision(AuthorizationDecision decision) {
        val   plan   = planner.apply(decision);
        val   failed = plan.enforceDecisionConstraints(decision);
        Event event  = classify(decision, plan, failed);
        process(event);
    }

    /**
     * Routes each PDP decision into a single FSM event. PERMIT becomes
     * either {@link PdpPermit} (decision-scoped enforcement OK) or
     * {@link PdpSuspend} with reason {@link PermitNotEnforceable}.
     * SUSPEND, INDETERMINATE, NOT_APPLICABLE all become
     * {@link PdpSuspend} with discriminating reasons. DENY becomes
     * {@link PdpDeny}.
     */
    private Event classify(AuthorizationDecision decision, EnforcementPlan plan, boolean decisionScopedFailed) {
        return switch (decision.decision()) {
        case PERMIT         -> decisionScopedFailed ? new PdpSuspend(decision, plan, new PermitNotEnforceable(decision))
                : new PdpPermit(decision, plan, terminateOnItemEnforcementFailure);
        case SUSPEND        -> new PdpSuspend(decision, plan, new PolicySuspended(decision));
        case INDETERMINATE  -> new PdpSuspend(decision, plan, new EvaluationError(decision));
        case NOT_APPLICABLE -> new PdpSuspend(decision, plan, new NoPolicyApplicable(decision));
        case DENY           -> new PdpDeny(decision, plan);
        };
    }

    private void onPdpError(Throwable throwable) {
        process(new PdpError(throwable));
    }

    private void onPdpComplete() {
        process(PdpComplete.INSTANCE);
    }

    private void ensureRapSubscribed() {
        synchronized (lock) {
            if (rapSubscription != null || state instanceof Terminated) {
                return;
            }
            val rapSub = rapSupplier.get().contextWrite(subscriberContext).subscribe(this::onRapItem, this::onRapError,
                    this::onRapComplete);
            rapSubscription = rapSub;
            subscriptions.add(rapSub);
        }
    }

    private void disposeRap() {
        synchronized (lock) {
            if (rapSubscription == null) {
                return;
            }
            val sub = rapSubscription;
            rapSubscription = null;
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
        State priorState;
        State nextState;
        synchronized (lock) {
            if (state instanceof Terminated) {
                return;
            }
            priorState = state;
            val transition = StateMachine.step(state, event);
            state     = transition.newState();
            nextState = state;
            for (val emission : transition.emissions()) {
                renderEmission(emission);
            }
            if (transition.isTerminal()) {
                subscriptions.dispose();
                rapSubscription = null;
                return;
            }
        }
        manageRapSubscription(priorState, nextState);
        if (nextState instanceof Permitting) {
            ensureRapSubscribed();
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
        case StayQuiet ignored          -> { /* observable no-op */ }
        }
    }

    private void renderTransition(TransitionReason reason) {
        // Both directions are gated by the same flag: if the subscriber
        // hasn't opted into transition signalling, neither suspend nor
        // resume boundaries are surfaced. Terminal denies bypass this
        // path entirely via EmitError.
        if (!signalTransitions) {
            return;
        }
        if (reason instanceof Granted(var decision)) {
            withSink(s -> s.next(ProtectedPayload.ofError(new AccessGrantedException(decision))));
            return;
        }
        val message = ERROR_ACCESS_DENIED.formatted(reason);
        withSink(s -> s.next(ProtectedPayload.ofError(new AccessDeniedException(message))));
    }

    private void withSink(Consumer<FluxSink<ProtectedPayload<Object>>> action) {
        if (sink != null) {
            action.accept(sink);
        }
    }

}
