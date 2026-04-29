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

import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
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
import io.sapl.spring.pep.streaming.Event.RapComplete;
import io.sapl.spring.pep.streaming.Event.RapError;
import io.sapl.spring.pep.streaming.Event.RapItem;
import io.sapl.spring.pep.streaming.State.Permitting;
import io.sapl.spring.pep.streaming.State.Terminated;
import io.sapl.spring.pep.streaming.TransitionReason.DecisionDenied;
import io.sapl.spring.pep.streaming.TransitionReason.Granted;
import io.sapl.spring.pep.streaming.TransitionReason.PermitNotEnforceable;
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
 * Reactor adapter for the streaming PEP. Drives the pure {@link StateMachine}
 * from a PDP decision flux and a lazily-subscribed RAP publisher, renders
 * the resulting {@link Emission}s onto a downstream {@link Flux} for the
 * subscriber.
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
 * boundary signals ({@link AccessDeniedException} on entry to denial,
 * {@link AccessGrantedException} on recovery when
 * {@code signalTransitions} is enabled) without terminating the
 * subscription. Errors raised directly from the upstream sink (the FSM's
 * {@link Emission.EmitError}) bypass the wrapper and terminate the stream
 * as a real Reactor error.
 *
 * @since 4.1.0
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class StreamingPipeline {

    private static final String ERROR_ACCESS_DENIED        = "Access denied: %s";
    private static final String WARN_RAP_AFTER_TERMINATION = "RAP item arrived after termination; dropping.";

    private final boolean                                          annotationSurvivesDeny;
    private final Flux<AuthorizationDecision>                      decisions;
    private final Function<AuthorizationDecision, EnforcementPlan> planner;
    private final Supplier<? extends Flux<?>>                      rapSupplier;
    private final boolean                                          signalTransitions;

    private final Object                       lock          = new Object();
    private final Disposable.Composite         subscriptions = Disposables.composite();
    private FluxSink<ProtectedPayload<Object>> sink;
    private ContextView                        subscriberContext;
    private State                              state;
    private boolean                            rapSubscribed;

    /**
     * Creates a cold {@link Flux} that, on subscription, drives the
     * streaming PEP's FSM from the supplied PDP decision flux and
     * lazily-subscribed RAP. Each subscription gets a fresh pipeline
     * instance with its own state and lifecycle.
     *
     * @param annotationSurvivesDeny the deny strategy resolved from the
     * annotation. {@code false} (secure default): the first deny terminates
     * the subscription with {@link AccessDeniedException}. {@code true}:
     * the subscription survives a deny and may resume on a later PERMIT.
     * The pipeline computes {@code hardDeny = !annotationSurvivesDeny}
     * and stamps it onto every PDP decision event. A future release may
     * consult {@code decision.survivesDeny()} as an override.
     * @param decisions the PDP decision flux for this subscription; an
     * empty flux is treated as a single DENY decision
     * @param planner a closure that maps each {@link AuthorizationDecision}
     * to its {@link EnforcementPlan}; typically captures the per-method
     * supported-signal set and output type
     * @param rapSupplier the protected method's publisher, supplied lazily
     * (invoked at most once, on the first {@code PdpPermit})
     * @param signalTransitions whether to surface boundary transitions
     * (deny + grant) to the subscriber as non-terminal exceptions on the
     * error channel. Effective only when {@code annotationSurvivesDeny}
     * is {@code true}; ignored otherwise (no surviving subscription to
     * signal on).
     * @return a flux that emits items as the FSM permits them, surfaces
     * boundary crossings as {@link AccessDeniedException} or
     * {@link AccessGrantedException} (when {@code signalTransitions} and
     * the deny is non-terminal) on the error channel, and completes /
     * errors when the FSM reaches {@link State.Terminated}.
     */
    public static Flux<Object> create(boolean annotationSurvivesDeny, Flux<AuthorizationDecision> decisions,
            Function<AuthorizationDecision, EnforcementPlan> planner, Supplier<? extends Flux<?>> rapSupplier,
            boolean signalTransitions) {
        val pipeline = new StreamingPipeline(annotationSurvivesDeny, decisions, planner, rapSupplier,
                signalTransitions);
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
        val pdpSub = decisions.switchIfEmpty(Flux.just(AuthorizationDecision.DENY)).contextWrite(subscriberContext)
                .subscribe(this::onPdpDecision, this::onPdpError, this::onPdpComplete);
        subscriptions.add(pdpSub);
    }

    private void onPdpDecision(AuthorizationDecision decision) {
        val plan = planner.apply(decision);
        // The binding deny strategy for this transition. Today derived from
        // the annotation; a future release may consult decision.survivesDeny()
        // as an override. Either way, the value is decided at event-
        // construction time, never read back from the FSM's current state.
        val hardDeny = !annotationSurvivesDeny;
        // Decision-scoped enforcement runs for every decision so deny-side
        // obligations (audit, custom error handling) discharge alongside
        // permit-side ones. Only the PERMIT branch escalates a handler
        // failure into PermitNotEnforceable; for DENY the decision already
        // denies, so the failure result is observable via logging only.
        val   failed = plan.enforceDecisionConstraints(decision);
        Event event;
        if (decision.decision() == Decision.PERMIT) {
            event = failed ? new PdpDeny(decision, plan, new PermitNotEnforceable(decision), hardDeny)
                    : new PdpPermit(decision, plan, hardDeny);
        } else {
            event = new PdpDeny(decision, plan, new DecisionDenied(decision), hardDeny);
        }
        process(event);
        if (event instanceof PdpPermit) {
            ensureRapSubscribed();
        }
    }

    private void onPdpError(Throwable throwable) {
        process(new PdpError(throwable));
    }

    private void onPdpComplete() {
        process(PdpComplete.INSTANCE);
    }

    private void ensureRapSubscribed() {
        synchronized (lock) {
            if (rapSubscribed || state instanceof Terminated) {
                return;
            }
            rapSubscribed = true;
        }
        val rapSub = rapSupplier.get().contextWrite(subscriberContext).subscribe(this::onRapItem, this::onRapError,
                this::onRapComplete);
        subscriptions.add(rapSub);
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
        synchronized (lock) {
            if (state instanceof Terminated) {
                return;
            }
            val transition = StateMachine.step(state, event);
            state = transition.newState();
            for (val emission : transition.emissions()) {
                renderEmission(emission);
            }
            if (transition.isTerminal()) {
                subscriptions.dispose();
            }
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
        // hasn't opted into transition signalling, neither denied nor
        // granted boundaries are surfaced. Terminal denies (under
        // hardDeny) bypass this path entirely via EmitError.
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
