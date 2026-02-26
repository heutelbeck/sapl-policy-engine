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
package io.sapl.spring.method.reactive;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.ReactiveConstraintHandlerBundle;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.function.Predicate.not;

/**
 * The EnforceDropWhileDeniedPolicyEnforcementPoint implements continuous policy
 * enforcement on a Flux resource access point.
 * <p>
 * After an initial PERMIT, the PEP subscribes to the resource access point and
 * forwards events downstream until a non-PERMIT decision from the PDP is
 * received. Then, all events are dropped until a new PERMIT signal arrives.
 * <p>
 * Whenever a decision is received, the handling of obligations and advice are
 * updated accordingly.
 * <p>
 * The PEP does not permit onErrorContinue() downstream.
 *
 * @param <T> type of the Flux contents
 *
 */
public class EnforceDropWhileDeniedPolicyEnforcementPoint<T> extends Flux<T> {

    private static final String ERROR_OPERATOR_MAY_ONLY_BE_SUBSCRIBED_ONCE = "Operator may only be subscribed once.";

    private final Flux<AuthorizationDecision> decisions;

    private Flux<T> resourceAccessPoint;

    private final ConstraintEnforcementService constraintsService;

    private EnforcementSink<T> sink;

    private final Class<T> clazz;

    private final AtomicReference<Disposable> decisionsSubscription = new AtomicReference<>();

    private final AtomicReference<Disposable> dataSubscription = new AtomicReference<>();

    private final AtomicReference<EnforcementState<T>> state = new AtomicReference<>(
            new EnforcementState<>(AuthorizationDecision.INDETERMINATE, new ReactiveConstraintHandlerBundle<>()));

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private EnforceDropWhileDeniedPolicyEnforcementPoint(Flux<AuthorizationDecision> decisions,
            Flux<T> resourceAccessPoint,
            ConstraintEnforcementService constraintsService,
            Class<T> clazz) {
        this.decisions           = decisions;
        this.resourceAccessPoint = resourceAccessPoint;
        this.constraintsService  = constraintsService;
        this.clazz               = clazz;
    }

    public static <V> Flux<V> of(Flux<AuthorizationDecision> decisions, Flux<V> resourceAccessPoint,
            ConstraintEnforcementService constraintsService, Class<V> clazz) {
        val pep = new EnforceDropWhileDeniedPolicyEnforcementPoint<>(decisions, resourceAccessPoint, constraintsService,
                clazz);
        return pep.doOnTerminate(pep::handleOnTerminateConstraints)
                .doAfterTerminate(pep::handleAfterTerminateConstraints).doOnCancel(pep::handleCancel).onErrorStop();
    }

    @Override
    public void subscribe(@NonNull CoreSubscriber<? super T> actual) {
        if (sink != null)
            throw new IllegalStateException(ERROR_OPERATOR_MAY_ONLY_BE_SUBSCRIBED_ONCE);
        val context = actual.currentContext();
        sink                = new EnforcementSink<>();
        resourceAccessPoint = resourceAccessPoint.contextWrite(context);
        Flux.create(sink).subscribe(actual);
        decisionsSubscription.set(decisions.doOnNext(this::handleNextDecision).contextWrite(context).subscribe());
    }

    private void handleNextDecision(AuthorizationDecision decision) {
        var implicitDecision = decision;

        ReactiveConstraintHandlerBundle<T> newBundle;
        try {
            newBundle = constraintsService.reactiveTypeBundleFor(decision, clazz);
        } catch (AccessDeniedException e) {
            newBundle        = constraintsService.reactiveTypeBestEffortBundleFor(decision, clazz);
            implicitDecision = AuthorizationDecision.INDETERMINATE;
        }

        try {
            newBundle.handleOnDecisionConstraints();
        } catch (AccessDeniedException e) {
            implicitDecision = AuthorizationDecision.INDETERMINATE;
        }

        state.set(new EnforcementState<>(implicitDecision, newBundle));

        if (implicitDecision.decision() == Decision.PERMIT && dataSubscription.get() == null)
            dataSubscription.set(wrapResourceAccessPointAndSubscribe());
    }

    private Disposable wrapResourceAccessPointAndSubscribe() {
        return resourceAccessPoint.doOnError(this::handleError).doOnRequest(this::handleRequest)
                .doOnSubscribe(this::handleSubscribe).doOnNext(this::handleNext).doOnComplete(this::handleComplete)
                .subscribe();
    }

    private void handleSubscribe(Subscription s) {
        try {
            state.get().bundle().handleOnSubscribeConstraints(s);
        } catch (Throwable t) {
            handleNextDecision(AuthorizationDecision.INDETERMINATE);
        }
    }

    private void handleNext(T value) {
        // the following guard clause makes sure that the constraint handlers do not get
        // called after downstream consumers cancelled. If the RAP is not consisting of
        // delayed elements, but something like Flux.just(1,2,3) the handler would be
        // called for 2 and 3, even if there was a take(1) applied downstream.
        if (stopped.get())
            return;

        val currentState = state.get();

        if (currentState.decision().decision() != Decision.PERMIT)
            return;

        try {
            val transformedValue = currentState.bundle().handleAllOnNextConstraints(value);
            sink.next(transformedValue);
        } catch (Throwable t) {
            // NOOP drop only the element with the failed obligation
            // doing handleNextDecision(AuthorizationDecision.DENY); would drop all
            // subsequent messages, even if the constraint handler would succeed on then.
        }
    }

    private void handleRequest(Long value) {
        try {
            state.get().bundle().handleOnRequestConstraints(value);
        } catch (Throwable t) {
            handleNextDecision(AuthorizationDecision.INDETERMINATE);
        }
    }

    private void handleOnTerminateConstraints() {
        state.get().bundle().handleOnTerminateConstraints();
    }

    private void handleAfterTerminateConstraints() {
        state.get().bundle().handleAfterTerminateConstraints();
    }

    private void handleComplete() {
        if (stopped.get())
            return;
        try {
            state.get().bundle().handleOnCompleteConstraints();
        } catch (Throwable t) {
            // NOOP stream is finished nothing more to protect.
        }
        sink.complete();
        disposeDecisionsAndResourceAccessPoint();
    }

    private void handleCancel() {
        try {
            state.get().bundle().handleOnCancelConstraints();
        } catch (Throwable t) {
            // NOOP
        }
        disposeDecisionsAndResourceAccessPoint();
    }

    private void handleError(Throwable error) {
        try {
            sink.error(state.get().bundle().handleAllOnErrorConstraints(error));
        } catch (Throwable t) {
            sink.error(t);
            handleNextDecision(AuthorizationDecision.INDETERMINATE);
            disposeDecisionsAndResourceAccessPoint();
        }
    }

    private void disposeDecisionsAndResourceAccessPoint() {
        stopped.set(true);
        disposeActiveIfPresent(decisionsSubscription);
        disposeActiveIfPresent(dataSubscription);
    }

    private void disposeActiveIfPresent(AtomicReference<Disposable> atomicDisposable) {
        Optional.ofNullable(atomicDisposable.get()).filter(not(Disposable::isDisposed)).ifPresent(Disposable::dispose);
    }

}
