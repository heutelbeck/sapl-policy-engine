package io.sapl.spring.method.reactive;

import static java.util.function.Predicate.not;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

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
		Flux.create(sink).doOnSubscribe(startEnforcing(sink)).doOnCancel(handleCancel(sink))
				.onErrorMap(AccessDeniedException.class,
						error -> constraintsService.handleOnErrorConstraints(latestDecision.get(), error))
				.onErrorStop().subscribe(actual);
	}

	private Consumer<Subscription> startEnforcing(EnforcementSink sink) {
		return s -> decisionsSubscription
				.set(decisions.log().doOnNext(handleNextDecision(sink)).contextWrite(context).subscribe());
	}

	private Consumer<AuthorizationDecision> handleNextDecision(EnforcementSink sink) {
		return decision -> {
			var previousDecision = latestDecision.getAndSet(decision);

			if (decision.getDecision() != Decision.PERMIT) {
				sink.error(new AccessDeniedException("Access Denied by PDP"));
				disposeDecisionsAndResourceAccessPoint();
				return;
			}

			if (previousDecision == null)
				dataSubscription.set(wrapResourceAccessPointAndSubcribe(decision, sink));
		};
	}

	private Disposable wrapResourceAccessPointAndSubcribe(AuthorizationDecision initialPermit, EnforcementSink sink) {
		if (constraintsService.handlePreSubscriptionConstraints(initialPermit))
			return resourceAccessPoint.doOnNext(handleNext(sink)).doOnComplete(handleComplete(sink))
					.doOnError(handleError(sink)).subscribe();

		sink.error(new AccessDeniedException("Access Denied. Obligations could not be fulfilled."));
		disposeUndisposedIfPresent(decisionsSubscription);
		return null;
	}

	private Consumer<? super Object> handleNext(EnforcementSink sink) {
		return value -> {
			var transformedValue = constraintsService.handleOnNextConstraints(latestDecision.get(), value);
			if (transformedValue != null)
				sink.next(transformedValue);
		};
	}

	private Runnable handleComplete(EnforcementSink sink) {
		return () -> {
			constraintsService.handleOnCompleteConstraints(latestDecision.get());
			sink.complete();
			disposeDecisionsAndResourceAccessPoint();
		};
	}

	private Runnable handleCancel(EnforcementSink sink) {
		return () -> {
			constraintsService.handleOnCancelConstraints(latestDecision.get());
			disposeDecisionsAndResourceAccessPoint();
		};
	}

	private Consumer<? super Throwable> handleError(EnforcementSink sink) {
		return error -> {
			var transformedError = constraintsService.handleOnErrorConstraints(latestDecision.get(), error);
			sink.error(transformedError);
		};
	}

	private void disposeDecisionsAndResourceAccessPoint() {
		disposeUndisposedIfPresent(decisionsSubscription);
		disposeUndisposedIfPresent(dataSubscription);
	}

	private void disposeUndisposedIfPresent(AtomicReference<Disposable> atomicDisposable) {
		Optional.ofNullable(atomicDisposable.get()).filter(not(Disposable::isDisposed)).ifPresent(Disposable::dispose);
	}
}
