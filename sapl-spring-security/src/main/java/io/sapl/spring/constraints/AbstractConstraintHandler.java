package io.sapl.spring.constraints;

import static io.sapl.spring.constraints.AdviceUtil.advice;
import static io.sapl.spring.constraints.AdviceUtil.flatMapAdvice;
import static io.sapl.spring.constraints.ObligationUtil.flatMapObligation;
import static io.sapl.spring.constraints.ObligationUtil.obligation;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Flux;

public abstract class AbstractConstraintHandler implements Comparable<AbstractConstraintHandler> {

	final Integer priority;

	public AbstractConstraintHandler(Integer priority) {
		this.priority = priority;
	}

	public Integer getPriority() {
		return priority;
	}

	@Override
	public int compareTo(AbstractConstraintHandler o) {
		return priority.compareTo(o.getPriority());
	}

	public Flux<Object> applyObligation(Flux<Object> source, JsonNode constraint) {
		var wrapped = source;
		wrapped = obligation(onSubscribe(constraint)).map(wrapped::doOnSubscribe).orElse(wrapped);
		wrapped = obligation(onNext(constraint)).map(wrapped::doOnNext).orElse(wrapped);
		wrapped = obligation(onError(constraint)).map(wrapped::doOnError).orElse(wrapped);
		wrapped = obligation(onComplete(constraint)).map(wrapped::doOnComplete).orElse(wrapped);
		wrapped = obligation(onAfterTerminate(constraint)).map(wrapped::doAfterTerminate).orElse(wrapped);
		wrapped = obligation(onCancel(constraint)).map(wrapped::doOnCancel).orElse(wrapped);
		wrapped = obligation(onRequest(constraint)).map(wrapped::doOnRequest).orElse(wrapped);
		wrapped = obligation(onNextMap(constraint)).map(wrapped::map).orElse(wrapped);
		wrapped = obligation(onErrorMap(constraint)).map(wrapped::onErrorMap).orElse(wrapped);
		wrapped = flatMapObligation(onNextFlatMap(constraint)).map(wrapped::flatMap).orElse(wrapped);
		return wrapped;
	}

	public Flux<Object> applyAdvice(Flux<Object> source, JsonNode constraint) {
		var wrapped = source;
		wrapped = advice(onSubscribe(constraint)).map(wrapped::doOnSubscribe).orElse(wrapped);
		wrapped = advice(onNext(constraint)).map(wrapped::doOnNext).orElse(wrapped);
		wrapped = advice(onError(constraint)).map(wrapped::doOnError).orElse(wrapped);
		wrapped = advice(onComplete(constraint)).map(wrapped::doOnComplete).orElse(wrapped);
		wrapped = advice(onAfterTerminate(constraint)).map(wrapped::doAfterTerminate).orElse(wrapped);
		wrapped = advice(onCancel(constraint)).map(wrapped::doOnCancel).orElse(wrapped);
		wrapped = advice(onRequest(constraint)).map(wrapped::doOnRequest).orElse(wrapped);
		wrapped = advice(onNextMap(constraint)).map(wrapped::map).orElse(wrapped);
		wrapped = advice(onErrorMap(constraint)).map(wrapped::onErrorMap).orElse(wrapped);
		wrapped = flatMapAdvice(onNextFlatMap(constraint)).map(wrapped::flatMap).orElse(wrapped);
		return wrapped;
	}

	public boolean preBlockingMethodInvocationOrOnAccessDenied(JsonNode constraint) {
		return false;
	}

	public Function<Object, Object> postBlockingMethodInvocation(JsonNode constraint) {
		return null;
	}

	public Consumer<? super Subscription> onSubscribe(JsonNode constraint) {
		return null;
	}

	public <T> Consumer<T> onNext(JsonNode constraint) {
		return null;
	}

	public Consumer<? super Throwable> onError(JsonNode constraint) {
		return null;
	}

	public Runnable onComplete(JsonNode constraint) {
		return null;
	}

	public Runnable onAfterTerminate(JsonNode constraint) {
		return null;
	}

	public Runnable onCancel(JsonNode constraint) {
		return null;
	}

	public LongConsumer onRequest(JsonNode constraint) {
		return null;
	}

	public <T> Function<T, T> onNextMap(JsonNode constraint) {
		return null;
	}

	public <T> Function<T, Publisher<T>> onNextFlatMap(JsonNode constraint) {
		return null;
	}

	public Function<Throwable, Throwable> onErrorMap(JsonNode constraint) {
		return null;
	}

	public abstract boolean isResponsible(JsonNode constraint);

}
