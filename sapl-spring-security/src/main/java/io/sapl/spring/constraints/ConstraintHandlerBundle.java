/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

import org.reactivestreams.Subscription;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ConstraintHandlerBundle<T> {

	final List<Runnable> onDecisionHandlers = new LinkedList<>();

	final List<Runnable> onCancelHandlers = new LinkedList<>();

	final List<Runnable> onCompleteHandlers = new LinkedList<>();

	final List<Runnable> onTerminateHandlers = new LinkedList<>();

	final List<Runnable> afterTerminateHandlers = new LinkedList<>();

	final List<Consumer<Subscription>> onSubscribeHandlers = new LinkedList<>();

	final List<LongConsumer> onRequestHandlers = new LinkedList<>();

	final List<Consumer<T>> doOnNextHandlers = new LinkedList<>();

	final List<Function<T, T>> onNextMapHandlers = new LinkedList<>();

	final List<Consumer<Throwable>> doOnErrorHandlers = new LinkedList<>();

	final List<Function<Throwable, Throwable>> onErrorMapHandlers = new LinkedList<>();

	final List<Predicate<T>> filterPredicateHandlers = new LinkedList<>();

	public void handleOnSubscribeConstraints(Subscription s) {
		consumeAll(onSubscribeHandlers).accept(s);
	}

	public T handleAllOnNextConstraints(T value) {
		handleOnNextConstraints(value);
		return handleOnNextMapConstraints(value);
	}

	private T handleOnNextMapConstraints(T value) {
		return mapAll(onNextMapHandlers).apply(value);
	}

	public void handleOnNextConstraints(T value) {
		consumeAll(doOnNextHandlers).accept(value);
	}

	public void handleOnRequestConstraints(Long value) {
		consumeAllLong(onRequestHandlers).accept(value);
	}

	public void handleOnCompleteConstraints() {
		runAll(onCompleteHandlers).run();
	}

	public void handleOnTerminateConstraints() {
		runAll(onTerminateHandlers).run();
	}

	public void handleOnDecisionConstraints() {
		runAll(onDecisionHandlers).run();
	}

	public void handleAfterTerminateConstraints() {
		runAll(afterTerminateHandlers).run();
	}

	public void handleOnCancelConstraints() {
		runAll(onCancelHandlers).run();
	}

	public Throwable handleAllOnErrorConstraints(Throwable error) {
		handleOnErrorConstraints(error);
		return handleOnErrorMapConstraints(error);
	}

	private Throwable handleOnErrorMapConstraints(Throwable error) {
		return mapAll(onErrorMapHandlers).apply(error);
	}

	private void handleOnErrorConstraints(Throwable error) {
		consumeAll(doOnErrorHandlers).accept(error);
	}

	public Flux<T> wrap(Flux<T> resourceAccessPoint) {
		var wrapped = resourceAccessPoint;

		if (!onRequestHandlers.isEmpty())
			wrapped = wrapped.doOnRequest(this::handleOnRequestConstraints);

		if (!onSubscribeHandlers.isEmpty())
			wrapped = wrapped.doOnSubscribe(this::handleOnSubscribeConstraints);

		if(!filterPredicateHandlers.isEmpty())
			wrapped = wrapped.filter(this::applyFilterPredicates);

		if (!onErrorMapHandlers.isEmpty())
			wrapped = wrapped.onErrorMap(this::handleOnErrorMapConstraints);

		if (!doOnErrorHandlers.isEmpty())
			wrapped = wrapped.doOnError(this::handleOnErrorConstraints);

		if (!onNextMapHandlers.isEmpty())
			wrapped = wrapped.map(this::handleOnNextMapConstraints);

		if (!doOnNextHandlers.isEmpty())
			wrapped = wrapped.doOnNext(this::handleOnNextConstraints);

		if (!onCancelHandlers.isEmpty())
			wrapped = wrapped.doOnCancel(this::handleOnCancelConstraints);

		if (!onCompleteHandlers.isEmpty())
			wrapped = wrapped.doOnComplete(this::handleOnCompleteConstraints);

		if (!onTerminateHandlers.isEmpty())
			wrapped = wrapped.doOnTerminate(this::handleOnTerminateConstraints);

		if (!afterTerminateHandlers.isEmpty())
			wrapped = wrapped.doAfterTerminate(this::handleAfterTerminateConstraints);

		if (!onDecisionHandlers.isEmpty())
			wrapped = onDecision(onDecisionHandlers).thenMany(wrapped);
			
		return wrapped;
	}

	private boolean applyFilterPredicates(T value) {
		var result = true;
		for (var predicate : filterPredicateHandlers)
			result &= predicate.test(value); 
		return result;
	}
	
	private LongConsumer consumeAllLong(List<LongConsumer> handlers) {
		return value -> handlers.forEach(handler -> handler.accept(value));
	}

	private <V> Function<V, V> mapAll(List<Function<V, V>> handlers) {
		return value -> handlers.stream()
				.reduce(Function.identity(), (merged, newFunction) -> x -> newFunction.apply(merged.apply(x)))
				.apply(value);
	}

	private <V> Consumer<V> consumeAll(List<Consumer<V>> handlers) {
		return value -> handlers.forEach(handler -> handler.accept(value));
	}

	private Mono<Void> onDecision(List<Runnable> handlers) {
		return Mono.fromRunnable(runAll(handlers));
	}

	private Runnable runAll(List<Runnable> handlers) {
		return () -> handlers.forEach(Runnable::run);
	}

}
