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
import io.sapl.spring.constraints.ConstraintHandlerBundle;
import lombok.extern.slf4j.Slf4j;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * The EnforceTillDeniedPolicyEnforcementPoint implements continuous policy
 * enforcement on a Flux resource access point.
 * 
 * If the initial decision of the PDP is not PERMIT, an AccessDeniedException is
 * signaled downstream without subscribing to resource access point.
 * 
 * After an initial PERMIT, the PEP subscribes to the resource access point and
 * forwards events downstream until a non-PERMIT decision from the PDP is
 * received. Then, an AccessDeniedException is signaled downstream and the PDP
 * and resource access point subscriptions are cancelled.
 * 
 * Whenever a decision is received, the handling of obligations and advice are
 * updated accordingly.
 * 
 * The PEP does not permit onErrorContinue() downstream.
 */
@Slf4j
public class EnforceTillDeniedPolicyEnforcementPoint<T> extends Flux<T> {

	private Flux<AuthorizationDecision> decisions;
	private Flux<T> resourceAccessPoint;
	private ConstraintEnforcementService constraintsService;
	EnforcementSink<T> sink;
	private Class<T> clazz;

	AtomicReference<Disposable> decisionsSubscription = new AtomicReference<Disposable>();
	AtomicReference<Disposable> dataSubscription = new AtomicReference<Disposable>();
	AtomicReference<AuthorizationDecision> latestDecision = new AtomicReference<AuthorizationDecision>();
	AtomicReference<ConstraintHandlerBundle<T>> constraintHandler = new AtomicReference<ConstraintHandlerBundle<T>>();
	AtomicBoolean stopped = new AtomicBoolean(false);

	private EnforceTillDeniedPolicyEnforcementPoint(Flux<AuthorizationDecision> decisions, Flux<T> resourceAccessPoint,
			ConstraintEnforcementService constraintsService, Class<T> clazz) {
		this.decisions = decisions;
		this.resourceAccessPoint = resourceAccessPoint;
		this.constraintsService = constraintsService;
		this.clazz = clazz;
	}

	public static <V> Flux<V> of(Flux<AuthorizationDecision> decisions, Flux<V> resourceAccessPoint,
			ConstraintEnforcementService constraintsService, Class<V> clazz) {
		var pep = new EnforceTillDeniedPolicyEnforcementPoint<V>(decisions, resourceAccessPoint, constraintsService,
				clazz);
		return pep.doOnTerminate(pep::handleOnTerminateConstraints)
				.doAfterTerminate(pep::handleAfterTerminateConstraints)
				.onErrorMap(AccessDeniedException.class, pep::handleAccessDenied).doOnCancel(pep::handleCancel)
				.onErrorStop();
	}

	@Override
	public void subscribe(CoreSubscriber<? super T> subscriber) {
		if (sink != null)
			throw new IllegalStateException("Operator may only be subscribed once.");
		var context = subscriber.currentContext();
		sink = new EnforcementSink<T>();
		resourceAccessPoint = resourceAccessPoint.contextWrite(context);
		Flux.create(sink).subscribe(subscriber);
		decisionsSubscription.set(decisions.doOnNext(this::handleNextDecision).contextWrite(context).subscribe());
	}

	private void handleNextDecision(AuthorizationDecision decision) {
		var previousDecision = latestDecision.getAndSet(decision);
		ConstraintHandlerBundle<T> newBundle;
		try {
			newBundle = constraintsService.bundleFor(decision, clazz);
			constraintHandler.set(newBundle);
		} catch (AccessDeniedException e) {
			constraintHandler.set(new ConstraintHandlerBundle<T>());
			sink.error(e);
			disposeDecisionsAndResourceAccessPoint();
			return;
		}
		constraintHandler.get().handleOnDecisionConstraints();

		if (decision.getDecision() != Decision.PERMIT) {
			sink.error(new AccessDeniedException("Access Denied by PDP"));
			disposeDecisionsAndResourceAccessPoint();
			return;
		}

		if (decision.getResource().isPresent()) {
			try {
				sink.next(constraintsService.unmarshallResource(decision.getResource().get(), clazz));
			} catch (JsonProcessingException | IllegalArgumentException e) {
				sink.error(new AccessDeniedException("Error replacing stream with resource. Ending Stream.", e));
			}
			sink.complete();
			disposeDecisionsAndResourceAccessPoint();
		}

		if (previousDecision == null)
			dataSubscription.set(wrapResourceAccessPointAndSubcribe());
	}

	private Disposable wrapResourceAccessPointAndSubcribe() {
		return resourceAccessPoint.doOnError(this::handleError).doOnRequest(this::handleRequest)
				.doOnSubscribe(this::handleSubscribe).doOnNext(this::handleNext).doOnComplete(this::handleComplete)
				.subscribe();
	}

	private void handleSubscribe(Subscription s) {
		try {
			constraintHandler.get().handleOnSubscribeConstraints(s);
		} catch (Throwable t) {
			sink.error(t);
			disposeDecisionsAndResourceAccessPoint();
		}
	}

	private void handleOnTerminateConstraints() {
		constraintHandler.get().handleOnTerminateConstraints();
	}

	private void handleAfterTerminateConstraints() {
		constraintHandler.get().handleAfterTerminateConstraints();
	}

	private void handleNext(T value) {
		// the following guard clause makes sure that the constraint handlers do not get
		// called after downstream consumers cancelled. If the RAP is not consisting of
		// delayed elements, but something like Flux.just(1,2,3) the handler would be
		// called for 2 and 3, even if there was a take(1) applied downstream.
		if (stopped.get())
			return;
		try {
			var transformedValue = constraintHandler.get().handleAllOnNextConstraints(value);
			if (transformedValue != null)
				sink.next(transformedValue);
		} catch (Throwable t) {
			sink.error(t);
			disposeDecisionsAndResourceAccessPoint();
		}
	}

	private void handleRequest(Long value) {
		try {
			constraintHandler.get().handleOnRequestConstraints(value);
		} catch (Throwable t) {
			sink.error(t);
			disposeDecisionsAndResourceAccessPoint();
		}
	}

	private void handleComplete() {
		if (stopped.get())
			return;
		try {
			constraintHandler.get().handleOnCompleteConstraints();
			sink.complete();
		} catch (Throwable t) {
			sink.error(t);
			sink.complete();
		}
		disposeDecisionsAndResourceAccessPoint();
	}

	private void handleCancel() {
		try {
			constraintHandler.get().handleOnCancelConstraints();
		} catch (Throwable t) {
			log.warn("Failed to handle obligation during onCancel. Error is dropped and Flux is canceled. "
					+ "No information is leaked, however take actions to mitigate error.", t);
		}
		disposeDecisionsAndResourceAccessPoint();
	}

	private void handleError(Throwable error) {
		try {
			sink.error(constraintHandler.get().handleAllOnErrorConstraints(error));
		} catch (Throwable t) {
			sink.error(t);
			disposeDecisionsAndResourceAccessPoint();
		}
	}

	private Throwable handleAccessDenied(Throwable error) {
		try {
			return constraintHandler.get().handleAllOnErrorConstraints(error);
		} catch (Throwable t) {
			disposeDecisionsAndResourceAccessPoint();
			return t;
		}
	}

	private void disposeDecisionsAndResourceAccessPoint() {
		stopped.set(true);
		disposeUndisposedIfPresent(decisionsSubscription);
		disposeUndisposedIfPresent(dataSubscription);
	}

	private void disposeUndisposedIfPresent(AtomicReference<Disposable> atomicDisposable) {
		Optional.ofNullable(atomicDisposable.get()).filter(not(Disposable::isDisposed)).ifPresent(Disposable::dispose);
	}

}
