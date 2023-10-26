/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static java.util.function.Predicate.not;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.ReactiveConstraintHandlerBundle;
import lombok.NonNull;
import lombok.SneakyThrows;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

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
 * The PEP supports onErrorContinue().
 *
 * @param <T> type of the Flux contents
 */
public class EnforceRecoverableIfDeniedPolicyEnforcementPoint<T>
        extends Flux<Tuple2<Optional<T>, Optional<Throwable>>> {

    private final Flux<AuthorizationDecision> decisions;

    private Flux<T> resourceAccessPoint;

    private final ConstraintEnforcementService constraintsService;

    private RecoverableEnforcementSink<T> sink;

    private final Class<T> clazz;

    final AtomicReference<Disposable> decisionsSubscription = new AtomicReference<>();

    final AtomicReference<Disposable> dataSubscription = new AtomicReference<>();

    final AtomicReference<AuthorizationDecision> latestDecision = new AtomicReference<>();

    final AtomicReference<ReactiveConstraintHandlerBundle<T>> constraintHandlerBundle = new AtomicReference<>();

    final AtomicBoolean stopped = new AtomicBoolean(false);

    private EnforceRecoverableIfDeniedPolicyEnforcementPoint(Flux<AuthorizationDecision> decisions,
            Flux<T> resourceAccessPoint, ConstraintEnforcementService constraintsService, Class<T> clazz) {
        this.decisions           = decisions;
        this.resourceAccessPoint = resourceAccessPoint;
        this.constraintsService  = constraintsService;
        this.clazz               = clazz;
    }

    public static <V> Flux<V> of(Flux<AuthorizationDecision> decisions, Flux<V> resourceAccessPoint,
            ConstraintEnforcementService constraintsService, Class<V> clazz) {
        var pep = new EnforceRecoverableIfDeniedPolicyEnforcementPoint<>(decisions, resourceAccessPoint,
                constraintsService, clazz);
        return pep.doOnTerminate(pep::handleOnTerminateConstraints)
                .doAfterTerminate(pep::handleAfterTerminateConstraints).map(pep::handleAccessDenied)
                .doOnCancel(pep::handleCancel).onErrorStop()
                .flatMap(EnforceRecoverableIfDeniedPolicyEnforcementPoint::extractPayloadOrError);
    }

    @SneakyThrows
    private static <T> Mono<T> extractPayloadOrError(Tuple2<Optional<T>, Optional<Throwable>> tuple) {
        var potentialPayload = tuple.getT1();
        var potentialError   = tuple.getT2();
        if (potentialError.isPresent())
            throw potentialError.get();
        return Mono.just(potentialPayload.orElseThrow(() -> new AccessDeniedException(
                "Error in PEP during payload extraction. Payload was not present. Should not be possible.")));
    }

    @Override
    public void subscribe(@NonNull CoreSubscriber<? super Tuple2<Optional<T>, Optional<Throwable>>> actual) {
        if (sink != null)
            throw new IllegalStateException("Operator may only be subscribed once.");
        var context = actual.currentContext();
        sink                = new RecoverableEnforcementSink<>();
        resourceAccessPoint = resourceAccessPoint.contextWrite(context);
        Flux.create(sink).subscribe(actual);
        decisionsSubscription.set(decisions.doOnNext(this::handleNextDecision).contextWrite(context).subscribe());
    }

    private void handleNextDecision(AuthorizationDecision decision) {
        if (decision.getDecision() == Decision.INDETERMINATE) {
            sink.error(new AccessDeniedException(
                    "The PDP encountered an error during decison making and returned INDETERMINATE."));
            latestDecision.set(decision);
            constraintHandlerBundle.set(new ReactiveConstraintHandlerBundle<>());
            return;
        }

        if (decision.getDecision() == Decision.NOT_APPLICABLE) {
            sink.error(new AccessDeniedException(
                    "The PDP has no applicable rules answering the authorization subscription and retuned NOT_APPLICABLE."));
            latestDecision.set(decision);
            constraintHandlerBundle.set(new ReactiveConstraintHandlerBundle<>());
            return;
        }

        ReactiveConstraintHandlerBundle<T> newConstraintHandlerBundle;
        try {
            newConstraintHandlerBundle = constraintsService.reactiveTypeBundleFor(decision, clazz);
            constraintHandlerBundle.set(newConstraintHandlerBundle);
        } catch (AccessDeniedException e) {
            sink.error(new AccessDeniedException(
                    "The PEP failed to construct constraint handlers. Will be handled like an INDETERMINATE decision",
                    e));
            latestDecision.set(AuthorizationDecision.INDETERMINATE);
            constraintHandlerBundle.set(new ReactiveConstraintHandlerBundle<>());
            return;
        }

        try {
            newConstraintHandlerBundle.handleOnDecisionConstraints();
        } catch (AccessDeniedException e) {
            sink.error(new AccessDeniedException(
                    "The PEP failed to comply with the onDecision obligations. Will be handled like an INDETERMINATE decision",
                    e));
            latestDecision.set(AuthorizationDecision.INDETERMINATE);
            constraintHandlerBundle.set(new ReactiveConstraintHandlerBundle<>());
            return;
        }

        if (decision.getDecision() == Decision.DENY) {
            sink.error(new AccessDeniedException("PDP decided to deny access."));
            latestDecision.set(decision);
            return;
        }

        // decision == Decision.PERMIT from here on

        latestDecision.set(decision);
        var potentialResource = decision.getResource();
        if (potentialResource.isPresent()) {
            try {
                sink.next(constraintsService.unmarshallResource(potentialResource.get(), clazz));
            } catch (JsonProcessingException | IllegalArgumentException e) {
                sink.error(new AccessDeniedException(
                        "The PEP failed to replace stream with decision's resource object. Will be handled like an INDETERMINATE decision",
                        e));
                latestDecision.set(AuthorizationDecision.INDETERMINATE);
                constraintHandlerBundle.set(new ReactiveConstraintHandlerBundle<>());
                return;
            }
        }

        dataSubscription.updateAndGet(sub -> {
            if (sub == null) {
                return wrapResourceAccessPointAndSubscribe();
            } else {
                return sub;
            }
        });
    }

    private Disposable wrapResourceAccessPointAndSubscribe() {
        return resourceAccessPoint.doOnError(this::handleError).doOnRequest(this::handleRequest)
                .doOnSubscribe(this::handleSubscribe).doOnNext(this::handleNext).doOnComplete(this::handleComplete)
                .subscribe();
    }

    private void handleSubscribe(Subscription s) {
        try {
            constraintHandlerBundle.get().handleOnSubscribeConstraints(s);
        } catch (Throwable t) {
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

        var decision = latestDecision.get();

        if (decision.getDecision() != Decision.PERMIT)
            return;

        // drop elements while the last decision replaced data with resource
        if (decision.getResource().isPresent())
            return;

        try {
            var transformedValue = constraintHandlerBundle.get().handleAllOnNextConstraints(value);
            if (transformedValue != null)
                sink.next(transformedValue);
        } catch (Throwable t) {
            // Signal error but drop only the element with the failed obligation
            // doing handleNextDecision(AuthorizationDecision.DENY); would drop all
            // subsequent messages, even if the constraint handler would succeed on then.
            // Do not signal original error, as this may leak information in message.
            sink.error(new AccessDeniedException("Failed to handle onNext obligation.", t));
        }
    }

    private void handleRequest(Long value) {
        try {
            constraintHandlerBundle.get().handleOnRequestConstraints(value);
        } catch (Throwable t) {
            handleNextDecision(AuthorizationDecision.INDETERMINATE);
        }
    }

    private void handleOnTerminateConstraints() {
        constraintHandlerBundle.get().handleOnTerminateConstraints();
    }

    private void handleAfterTerminateConstraints() {
        constraintHandlerBundle.get().handleAfterTerminateConstraints();
    }

    private void handleComplete() {
        if (stopped.get())
            return;
        try {
            constraintHandlerBundle.get().handleOnCompleteConstraints();
        } catch (Throwable t) {
            // NOOP stream is finished nothing more to protect.
        }
        sink.complete();
        disposeDecisionsAndResourceAccessPoint();
    }

    private void handleCancel() {
        try {
            constraintHandlerBundle.get().handleOnCancelConstraints();
        } catch (Throwable t) {
            // NOOP stream is finished nothing more to protect.
        }
        disposeDecisionsAndResourceAccessPoint();
    }

    private void handleError(Throwable error) {
        try {
            sink.error(constraintHandlerBundle.get().handleAllOnErrorConstraints(error));
        } catch (Throwable t) {
            sink.error(t);
            handleNextDecision(AuthorizationDecision.INDETERMINATE);
            disposeDecisionsAndResourceAccessPoint();
        }
    }

    private Tuple2<Optional<T>, Optional<Throwable>> handleAccessDenied(
            Tuple2<Optional<T>, Optional<Throwable>> tuple) {

        var potentialPayload = tuple.getT1();
        if (potentialPayload.isPresent())
            return tuple;

        var error = tuple.getT2().orElseGet(() -> new AccessDeniedException(
                "Error in PEP during payload extraction. Payload was not present. Should not be possible."));
        if (!(error instanceof AccessDeniedException))
            return tuple;

        try {
            var transformedError = constraintHandlerBundle.get().handleAllOnErrorConstraints(error);
            return Tuples.of(Optional.empty(), Optional.of(transformedError));
        } catch (Throwable t) {
            return Tuples.of(Optional.empty(), Optional.of(
                    new AccessDeniedException("Error in PEP during contraint-based transformation of exceptions.", t)));
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
