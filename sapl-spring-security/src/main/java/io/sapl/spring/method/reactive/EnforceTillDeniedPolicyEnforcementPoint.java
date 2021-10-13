package io.sapl.spring.method.reactive;

import static java.util.function.Predicate.not;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

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
 */
public class EnforceTillDeniedPolicyEnforcementPoint extends Flux<Object> {

	private Flux<AuthorizationDecision> decisions;
	private Flux<?> resourceAccessPoint;
	private ReactiveConstraintEnforcementService constraintsService;
	EnforcementSink sink;
	ContextView context;

	AtomicReference<Disposable> decisionsSubscription = new AtomicReference<Disposable>();
	AtomicReference<Disposable> dataSubscription = new AtomicReference<Disposable>();
	AtomicReference<AuthorizationDecision> latestDecision = new AtomicReference<AuthorizationDecision>();

	private EnforceTillDeniedPolicyEnforcementPoint(Flux<AuthorizationDecision> decisions, Flux<?> resourceAccessPoint,
			ReactiveConstraintEnforcementService constraintsService) {
		this.decisions = decisions;
		this.resourceAccessPoint = resourceAccessPoint;
		this.constraintsService = constraintsService;
	}

	public static EnforceTillDeniedPolicyEnforcementPoint of(Flux<AuthorizationDecision> decisions,
			Flux<?> resourceAccessPoint, ReactiveConstraintEnforcementService constraintsService) {
		return new EnforceTillDeniedPolicyEnforcementPoint(decisions, resourceAccessPoint, constraintsService);
	}

	@Override
	public void subscribe(CoreSubscriber<? super Object> actual) {
		if (sink != null)
			throw new IllegalStateException("Operator may only be subscribed once.");

		context = actual.currentContext();
		sink = new EnforcementSink();
		resourceAccessPoint = resourceAccessPoint.contextWrite(context);
		Flux.create(sink).doOnCancel(this::handleCancel).doOnRequest(this::handleRequest)
				.doOnTerminate(this::handleOnTerminate).doAfterTerminate(this::handleAfterTerminate)
				.onErrorMap(AccessDeniedException.class,
						error -> constraintsService.handleOnErrorConstraints(latestDecision.get(), error))
				.onErrorStop().subscribe(actual);
		startEnforcing();
	}

	private void startEnforcing() {
		decisionsSubscription.set(decisions.doOnNext(this::handleNextDecision).contextWrite(context).subscribe());
	}

	private void handleNextDecision(AuthorizationDecision decision) {
		var previousDecision = latestDecision.getAndSet(decision);

		if (decision.getDecision() != Decision.PERMIT) {
			sink.error(new AccessDeniedException("Access Denied by PDP"));
			disposeDecisionsAndResourceAccessPoint();
			return;
		}

		if (previousDecision == null)
			dataSubscription.set(wrapResourceAccessPointAndSubcribe());
	}

	private Disposable wrapResourceAccessPointAndSubcribe() {
		return resourceAccessPoint.doOnSubscribe(this::handleSubscribe).doOnNext(this::handleNext)
				.doOnComplete(this::handleComplete).doOnError(this::handleError).subscribe();
	}

	private void handleSubscribe(Subscription s) {
		constraintsService.handleOnSubscribeConstraints(latestDecision.get(), s);
	}

	private void handleNext(Object value) {
		var transformedValue = constraintsService.handleOnNextConstraints(latestDecision.get(), value);
		if (transformedValue != null)
			sink.next(transformedValue);
	}

	private void handleRequest(Long value) {
		constraintsService.handleOnRequestConstraints(latestDecision.get(), value);
	}

	private void handleOnTerminate() {
		constraintsService.handleOnTerminateConstraints(latestDecision.get());
	}

	private void handleAfterTerminate() {
		constraintsService.handleAfterTerminateConstraints(latestDecision.get());
	}

	private void handleComplete() {
		constraintsService.handleOnCompleteConstraints(latestDecision.get());
		sink.complete();
		disposeDecisionsAndResourceAccessPoint();
	}

	private void handleCancel() {
		constraintsService.handleOnCancelConstraints(latestDecision.get());
		disposeDecisionsAndResourceAccessPoint();
	}

	private void handleError(Throwable error) {
		sink.error(constraintsService.handleOnErrorConstraints(latestDecision.get(), error));
	}

	private void disposeDecisionsAndResourceAccessPoint() {
		disposeUndisposedIfPresent(decisionsSubscription);
		disposeUndisposedIfPresent(dataSubscription);
	}

	private void disposeUndisposedIfPresent(AtomicReference<Disposable> atomicDisposable) {
		Optional.ofNullable(atomicDisposable.get()).filter(not(Disposable::isDisposed)).ifPresent(Disposable::dispose);
	}

}
