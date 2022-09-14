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
package io.sapl.spring.constraints;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Subscription;

import lombok.AccessLevel;
import lombok.Setter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Setter(AccessLevel.PROTECTED)
public class ReactiveTypeConstraintHandlerBundle<T> {

	// @formatter:off
	private static final Runnable NOP = () -> {};

	private Runnable                       onDecisionHandlers       = NOP;
	private Runnable                       onCancelHandlers         = NOP;
	private Runnable                       onCompleteHandlers       = NOP;
	private Runnable                       onTerminateHandlers      = NOP;
	private Runnable                       afterTerminateHandlers   = NOP;
	private Consumer<Subscription>         onSubscribeHandlers      = __->{};
	private LongConsumer                   onRequestHandlers        = __->{};
	private Consumer<T>                    doOnNextHandlers         = __->{};
	private Function<T, T>                 onNextMapHandlers        = x->x;
	private Consumer<Throwable>            doOnErrorHandlers        = __->{};
	private Function<Throwable, Throwable> onErrorMapHandlers       = x->x;
	private Predicate<T>                   filterPredicateHandlers  = __->true;
	private Consumer<MethodInvocation>     methodInvocationHandlers = __->{};
	// @formatter:on

	public void handleOnSubscribeConstraints(Subscription s) {
		onSubscribeHandlers.accept(s);
	}

	public T handleAllOnNextConstraints(T value) {
		handleOnNextConstraints(value);
		return handleOnNextMapConstraints(value);
	}

	private T handleOnNextMapConstraints(T value) {
		return onNextMapHandlers.apply(value);
	}

	private void handleOnNextConstraints(T value) {
		doOnNextHandlers.accept(value);
	}

	public void handleOnRequestConstraints(Long value) {
		onRequestHandlers.accept(value);
	}

	public void handleOnCompleteConstraints() {
		onCompleteHandlers.run();
	}

	public void handleOnTerminateConstraints() {
		onTerminateHandlers.run();
	}

	public void handleOnDecisionConstraints() {
		onDecisionHandlers.run();
	}

	public void handleAfterTerminateConstraints() {
		afterTerminateHandlers.run();
	}

	public void handleOnCancelConstraints() {
		onCancelHandlers.run();
	}

	public void handleMethodInvocationHandlers(MethodInvocation methodInvocation) {
		methodInvocationHandlers.accept(methodInvocation);
	}

	public Throwable handleAllOnErrorConstraints(Throwable error) {
		handleOnErrorConstraints(error);
		return handleOnErrorMapConstraints(error);
	}

	private Throwable handleOnErrorMapConstraints(Throwable error) {
		return onErrorMapHandlers.apply(error);
	}

	private void handleOnErrorConstraints(Throwable error) {
		doOnErrorHandlers.accept(error);
	}

	public Flux<T> wrap(Flux<T> resourceAccessPoint) {
		var wrapped = resourceAccessPoint.doOnRequest(onRequestHandlers)
				.doOnSubscribe(onSubscribeHandlers).filter(filterPredicateHandlers)
				.onErrorMap(onErrorMapHandlers).doOnError(doOnErrorHandlers)
				.map(onNextMapHandlers).doOnNext(doOnNextHandlers)
				.doOnCancel(onCancelHandlers).doOnComplete(onCompleteHandlers)
				.doOnTerminate(onTerminateHandlers)
				.doAfterTerminate(afterTerminateHandlers);
		return onDecision(onDecisionHandlers).thenMany(wrapped);
	}

	private Mono<Void> onDecision(Runnable handlers) {
		return Mono.fromRunnable(handlers);
	}

}
