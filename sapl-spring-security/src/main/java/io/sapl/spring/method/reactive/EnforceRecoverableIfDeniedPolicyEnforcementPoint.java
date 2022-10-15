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
import io.sapl.spring.constraints.ReactiveTypeConstraintHandlerBundle;
import lombok.SneakyThrows;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * The EnforceDropWhileDeniedPolicyEnforcementPoint implements continuous policy
 * enforcement on a Flux resource access point.
 *
 * After an initial PERMIT, the PEP subscribes to the resource access point and forwards
 * events downstream until a non-PERMIT decision from the PDP is received. Then, all
 * events are dropped until a new PERMIT signal arrives.
 *
 * Whenever a decision is received, the handling of obligations and advice are updated
 * accordingly.
 *
 * The PEP does not permit onErrorContinue() downstream.
 *
 * @param <T> type of the FLux contents
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

	final AtomicReference<ReactiveTypeConstraintHandlerBundle<T>> constraintHandler = new AtomicReference<>();

	final AtomicBoolean stopped = new AtomicBoolean(false);

	private EnforceRecoverableIfDeniedPolicyEnforcementPoint(Flux<AuthorizationDecision> decisions,
			Flux<T> resourceAccessPoint, ConstraintEnforcementService constraintsService, Class<T> clazz) {
		this.decisions = decisions;
		this.resourceAccessPoint = resourceAccessPoint;
		this.constraintsService = constraintsService;
		this.clazz = clazz;
	}

	public static <V> Flux<V> of(Flux<AuthorizationDecision> decisions, Flux<V> resourceAccessPoint,
			ConstraintEnforcementService constraintsService, Class<V> clazz) {
		var pep = new EnforceRecoverableIfDeniedPolicyEnforcementPoint<>(decisions, resourceAccessPoint,
				constraintsService, clazz);
		return pep.doOnTerminate(pep::handleOnTerminateConstraints)
				.doAfterTerminate(pep::handleAfterTerminateConstraints).map(pep::handleAccessDenied)
				.doOnCancel(pep::handleCancel).onErrorStop()
				.map(EnforceRecoverableIfDeniedPolicyEnforcementPoint::extractPayloadOrError);
	}

	@SneakyThrows
	private static <T> T extractPayloadOrError(Tuple2<Optional<T>, Optional<Throwable>> tuple) {
		if (tuple.getT2().isEmpty())
			return tuple.getT1().get();
		throw tuple.getT2().get();
	}

	@Override
	public void subscribe(CoreSubscriber<? super Tuple2<Optional<T>, Optional<Throwable>>> actual) {
		if (sink != null)
			throw new IllegalStateException("Operator may only be subscribed once.");
		var context = actual.currentContext();
		sink = new RecoverableEnforcementSink<>();
		resourceAccessPoint = resourceAccessPoint.contextWrite(context);
		Flux.create(sink).subscribe(actual);
		decisionsSubscription.set(decisions.doOnNext(this::handleNextDecision).contextWrite(context).subscribe());
	}

	private void handleNextDecision(AuthorizationDecision decision) {

		var implicitDecision = decision;

		ReactiveTypeConstraintHandlerBundle<T> newBundle;
		try {
			newBundle = constraintsService.reactiveTypeBundleFor(implicitDecision, clazz);
			constraintHandler.set(newBundle);
		}
		catch (AccessDeniedException e) {
			sink.error(e);
			// INDETERMINATE -> as long as we cannot handle the obligations of the current
			// decision, drop data
			constraintHandler.set(new ReactiveTypeConstraintHandlerBundle<>());
			implicitDecision = AuthorizationDecision.INDETERMINATE;
		}

		if (implicitDecision.getDecision() != Decision.PERMIT)
			sink.error(new AccessDeniedException("Access Denied by PDP"));

		try {
			constraintHandler.get().handleOnDecisionConstraints();
		}
		catch (AccessDeniedException e) {
			sink.error(e);
			implicitDecision = AuthorizationDecision.INDETERMINATE;
		}

		latestDecision.set(implicitDecision);

		if (implicitDecision.getResource().isPresent()) {
			try {
				sink.next(constraintsService.unmarshallResource(implicitDecision.getResource().get(), clazz));
			}
			catch (JsonProcessingException | IllegalArgumentException e) {
				sink.error(new AccessDeniedException("Error replacing stream with resource. Ending Stream.", e));
				implicitDecision = AuthorizationDecision.INDETERMINATE;
			}
		}

		if (implicitDecision.getDecision() == Decision.PERMIT && dataSubscription.get() == null)
			dataSubscription.set(wrapResourceAccessPointAndSubscribe());
	}

	private Disposable wrapResourceAccessPointAndSubscribe() {
		return resourceAccessPoint.doOnError(this::handleError).doOnRequest(this::handleRequest)
				.doOnSubscribe(this::handleSubscribe).doOnNext(this::handleNext).doOnComplete(this::handleComplete)
				.subscribe();
	}

	private void handleSubscribe(Subscription s) {
		try {
			constraintHandler.get().handleOnSubscribeConstraints(s);
		}
		catch (Throwable t) {
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
			var transformedValue = constraintHandler.get().handleAllOnNextConstraints(value);
			if (transformedValue != null)
				sink.next(transformedValue);
		}
		catch (Throwable t) {
			// Signal error but drop only the element with the failed obligation
			// doing handleNextDecision(AuthorizationDecision.DENY); would drop all
			// subsequent messages, even if the constraint handler would succeed on then.
			// Do not signal original error, as this may leak information in message.
			sink.error(new AccessDeniedException("Failed to handle onNext obligation."));
		}
	}

	private void handleRequest(Long value) {
		try {
			constraintHandler.get().handleOnRequestConstraints(value);
		}
		catch (Throwable t) {
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
		}
		catch (Throwable t) {
			// NOOP stream is finished nothing more to protect.
		}
		sink.complete();
		disposeDecisionsAndResourceAccessPoint();
	}

	private void handleCancel() {
		try {
			constraintHandler.get().handleOnCancelConstraints();
		}
		catch (Throwable t) {
			// NOOP stream is finished nothing more to protect.
		}
		disposeDecisionsAndResourceAccessPoint();
	}

	private void handleError(Throwable error) {
		try {
			sink.error(constraintHandler.get().handleAllOnErrorConstraints(error));
		}
		catch (Throwable t) {
			sink.error(t);
			handleNextDecision(AuthorizationDecision.INDETERMINATE);
			disposeDecisionsAndResourceAccessPoint();
		}
	}

	private Tuple2<Optional<T>, Optional<Throwable>> handleAccessDenied(
			Tuple2<Optional<T>, Optional<Throwable>> tuple) {
		if (tuple.getT1().isPresent())
			return tuple;

		var error = tuple.getT2().get();
		if (!(error instanceof AccessDeniedException))
			return tuple;

		try {
			var transformedError = constraintHandler.get().handleAllOnErrorConstraints(error);
			return Tuples.of(Optional.empty(), Optional.of(transformedError));
		}
		catch (Throwable t) {
			return tuple;
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
