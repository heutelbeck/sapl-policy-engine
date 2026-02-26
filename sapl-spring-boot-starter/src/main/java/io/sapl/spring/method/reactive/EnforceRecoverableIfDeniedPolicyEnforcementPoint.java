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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.function.Predicate.not;

/**
 * The EnforceRecoverableIfDeniedPolicyEnforcementPoint implements continuous
 * policy
 * enforcement on a Flux resource access point.
 * <p>
 * After an initial PERMIT, the PEP subscribes to the resource access point and
 * forwards events downstream until a non-PERMIT decision from the PDP is
 * received. Then, all events are dropped until a new PERMIT signal arrives.
 * <p>
 * Whenever a decision is received, the handling of obligations and advice are
 * updated accordingly.
 * <p>
 * The PEP supports onErrorContinue().
 *
 * @param <T> type of the Flux contents
 */
public class EnforceRecoverableIfDeniedPolicyEnforcementPoint<T> extends Flux<ProtectedPayload<T>> {

    private static final String ERROR_CONSTRAINT_BASED_TRANSFORMATION         = "Error in PEP during constraint-based transformation of exceptions.";
    private static final String ERROR_FAILED_TO_COMPLY_WITH_ONDECISION        = "The PEP failed to comply with the onDecision obligations. Will be handled like an INDETERMINATE decision";
    private static final String ERROR_FAILED_TO_CONSTRUCT_CONSTRAINT_HANDLERS = "The PEP failed to construct constraint handlers. Will be handled like an INDETERMINATE decision";
    private static final String ERROR_FAILED_TO_HANDLE_ONNEXT_OBLIGATION      = "Failed to handle onNext obligation.";
    private static final String ERROR_OPERATOR_MAY_ONLY_BE_SUBSCRIBED_ONCE    = "Operator may only be subscribed once.";
    private static final String ERROR_PDP_DECIDED_TO_DENY_ACCESS              = "PDP decided to deny access.";
    private static final String ERROR_PDP_RETURNED_INDETERMINATE              = "The PDP encountered an error during decision making and returned INDETERMINATE.";
    private static final String ERROR_PDP_RETURNED_NOT_APPLICABLE             = "The PDP has no applicable rules answering the authorization subscription and returned NOT_APPLICABLE.";

    private final Flux<AuthorizationDecision> decisions;

    private Flux<T> resourceAccessPoint;

    private final ConstraintEnforcementService constraintsService;

    private RecoverableEnforcementSink<T> sink;

    private final Class<T> clazz;

    private final AtomicReference<Disposable> decisionsSubscription = new AtomicReference<>();

    private final AtomicReference<Disposable> dataSubscription = new AtomicReference<>();

    private final AtomicReference<AuthorizationDecision> latestDecision = new AtomicReference<>();

    private final AtomicReference<ReactiveConstraintHandlerBundle<T>> constraintHandler = new AtomicReference<>();

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private EnforceRecoverableIfDeniedPolicyEnforcementPoint(Flux<AuthorizationDecision> decisions,
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
        val pep = new EnforceRecoverableIfDeniedPolicyEnforcementPoint<>(decisions, resourceAccessPoint,
                constraintsService, clazz);
        return pep.doOnTerminate(pep::handleOnTerminateConstraints)
                .doAfterTerminate(pep::handleAfterTerminateConstraints).map(pep::handleAccessDenied)
                .doOnCancel(pep::handleCancel).onErrorStop().flatMap(ProtectedPayload::getPayload);
    }

    @Override
    public void subscribe(@NonNull CoreSubscriber<? super ProtectedPayload<T>> actual) {
        if (sink != null)
            throw new IllegalStateException(ERROR_OPERATOR_MAY_ONLY_BE_SUBSCRIBED_ONCE);
        val context = actual.currentContext();
        sink                = new RecoverableEnforcementSink<>();
        resourceAccessPoint = resourceAccessPoint.contextWrite(context);
        Flux.create(sink).subscribe(actual);
        decisionsSubscription.set(decisions.doOnNext(this::handleNextDecision).contextWrite(context).subscribe());
    }

    private void handleNextDecision(AuthorizationDecision decision) {
        if (decision.decision() == Decision.INDETERMINATE) {
            sink.error(new AccessDeniedException(ERROR_PDP_RETURNED_INDETERMINATE));
            latestDecision.set(decision);
            constraintHandler.set(new ReactiveConstraintHandlerBundle<>());
            return;
        }

        if (decision.decision() == Decision.NOT_APPLICABLE) {
            sink.error(new AccessDeniedException(ERROR_PDP_RETURNED_NOT_APPLICABLE));
            latestDecision.set(decision);
            constraintHandler.set(new ReactiveConstraintHandlerBundle<>());
            return;
        }

        ReactiveConstraintHandlerBundle<T> newConstraintHandlerBundle;
        try {
            newConstraintHandlerBundle = constraintsService.reactiveTypeBundleFor(decision, clazz);
            constraintHandler.set(newConstraintHandlerBundle);
        } catch (AccessDeniedException e) {
            sink.error(new AccessDeniedException(ERROR_FAILED_TO_CONSTRUCT_CONSTRAINT_HANDLERS, e));
            latestDecision.set(AuthorizationDecision.INDETERMINATE);
            constraintHandler.set(new ReactiveConstraintHandlerBundle<>());
            return;
        }

        try {
            newConstraintHandlerBundle.handleOnDecisionConstraints();
        } catch (AccessDeniedException e) {
            sink.error(new AccessDeniedException(ERROR_FAILED_TO_COMPLY_WITH_ONDECISION, e));
            latestDecision.set(AuthorizationDecision.INDETERMINATE);
            constraintHandler.set(new ReactiveConstraintHandlerBundle<>());
            return;
        }

        if (decision.decision() == Decision.DENY) {
            sink.error(new AccessDeniedException(ERROR_PDP_DECIDED_TO_DENY_ACCESS));
            latestDecision.set(decision);
            return;
        }

        // decision == Decision.PERMIT from here on

        latestDecision.set(decision);

        dataSubscription
                .updateAndGet(sub -> Objects.requireNonNullElseGet(sub, this::wrapResourceAccessPointAndSubscribe));
    }

    private Disposable wrapResourceAccessPointAndSubscribe() {
        return resourceAccessPoint.doOnError(this::handleError).doOnRequest(this::handleRequest)
                .doOnSubscribe(this::handleSubscribe).doOnNext(this::handleNext).doOnComplete(this::handleComplete)
                .subscribe();
    }

    private void handleSubscribe(Subscription s) {
        try {
            constraintHandler.get().handleOnSubscribeConstraints(s);
        } catch (Exception t) {
            // This means that we handle it as if there was no decision yet.
            // We dispose of the resourceAccessPoint and remove the lastDecision
            sink.error(t);
            Optional.ofNullable(dataSubscription.getAndSet(null)).filter(not(Disposable::isDisposed))
                    .ifPresent(Disposable::dispose);
            handleNextDecision(AuthorizationDecision.INDETERMINATE);
            // Signal this initial failure downstream, allowing to end or to recover.
        }
    }

    private void handleNext(T value) {
        // the following guard clause makes sure that the constraint handlers do not get
        // called after downstream consumers cancelled. If the RAP is not consisting of
        // delayed elements, but something like Flux.just(1,2,3) the handler would be
        // called for 2 and 3, even if there was a take(1) applied downstream.
        if (stopped.get())
            return;

        val decision = latestDecision.get();

        if (decision.decision() != Decision.PERMIT)
            return;

        try {
            val transformedValue = constraintHandler.get().handleAllOnNextConstraints(value);
            if (transformedValue != null)
                sink.next(transformedValue);
        } catch (Exception t) {
            // Signal error but drop only the element with the failed obligation
            // doing handleNextDecision(AuthorizationDecision.DENY); would drop all
            // subsequent messages, even if the constraint handler would succeed on then.
            // Do not signal original error, as this may leak information in message.
            sink.error(new AccessDeniedException(ERROR_FAILED_TO_HANDLE_ONNEXT_OBLIGATION, t));
        }
    }

    private void handleRequest(Long value) {
        try {
            constraintHandler.get().handleOnRequestConstraints(value);
        } catch (Exception t) {
            handleNextDecision(AuthorizationDecision.INDETERMINATE);
        }
    }

    private void handleOnTerminateConstraints() {
        constraintHandler.get().handleOnTerminateConstraints();
    }

    private void handleAfterTerminateConstraints() {
        constraintHandler.get().handleAfterTerminateConstraints();
    }

    private void handleComplete() {
        if (stopped.get())
            return;
        try {
            constraintHandler.get().handleOnCompleteConstraints();
        } catch (Exception t) {
            // NOOP stream is finished nothing more to protect.
        }
        sink.complete();
        disposeDecisionsAndResourceAccessPoint();
    }

    private void handleCancel() {
        try {
            constraintHandler.get().handleOnCancelConstraints();
        } catch (Exception t) {
            // NOOP stream is finished nothing more to protect.
        }
        disposeDecisionsAndResourceAccessPoint();
    }

    private void handleError(Throwable error) {
        try {
            sink.error(constraintHandler.get().handleAllOnErrorConstraints(error));
        } catch (Exception t) {
            sink.error(t);
            handleNextDecision(AuthorizationDecision.INDETERMINATE);
            disposeDecisionsAndResourceAccessPoint();
        }
    }

    private ProtectedPayload<T> handleAccessDenied(ProtectedPayload<T> payload) {
        if (payload.hasPayload())
            return payload;

        try {
            return ProtectedPayload.withError(constraintHandler.get().handleAllOnErrorConstraints(payload.getError()));
        } catch (Exception e) {
            return ProtectedPayload.withError(new AccessDeniedException(ERROR_CONSTRAINT_BASED_TRANSFORMATION, e));
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
