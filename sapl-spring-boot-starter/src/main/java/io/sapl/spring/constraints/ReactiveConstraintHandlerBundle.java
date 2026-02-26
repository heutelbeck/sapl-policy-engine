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
package io.sapl.spring.constraints;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.val;
import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 *
 * This bundle aggregates all constraint handlers for a specific decision which
 * are useful in a reactive scenario.
 * <p>
 *
 * @param <T> return type
 */
@NoArgsConstructor
@AllArgsConstructor
public class ReactiveConstraintHandlerBundle<T> {

    private Runnable                   onDecisionHandlers       = FunctionUtil.noop();
    private Runnable                   onCancelHandlers         = FunctionUtil.noop();
    private Runnable                   onCompleteHandlers       = FunctionUtil.noop();
    private Runnable                   onTerminateHandlers      = FunctionUtil.noop();
    private Runnable                   afterTerminateHandlers   = FunctionUtil.noop();
    private Consumer<Subscription>     onSubscribeHandlers      = FunctionUtil.sink();
    private LongConsumer               onRequestHandlers        = FunctionUtil.longSink();
    private Consumer<T>                doOnNextHandlers         = FunctionUtil.sink();
    private UnaryOperator<T>           onNextMapHandlers        = UnaryOperator.identity();
    private Consumer<Throwable>        doOnErrorHandlers        = FunctionUtil.sink();
    private UnaryOperator<Throwable>   onErrorMapHandlers       = UnaryOperator.identity();
    private Predicate<Object>          filterPredicateHandlers  = FunctionUtil.all();
    private Consumer<MethodInvocation> methodInvocationHandlers = FunctionUtil.sink();
    private UnaryOperator<T>           replaceResourceHandler   = UnaryOperator.identity();
    // @formatter:on

    /**
     * Runs all onSubscription handlers.
     *
     * @param s the Subscription.
     */
    public void handleOnSubscribeConstraints(Subscription s) {
        onSubscribeHandlers.accept(s);
    }

    /**
     * Executes all onNext constraint handlers, potentially transforming the value.
     * Execution order: replace resource, filter predicate, consumer, mapping.
     *
     * @param value a return value
     * @return the return value after constraint handling, or null if filtered out
     */
    public T handleAllOnNextConstraints(T value) {
        val replaced = replaceResourceHandler.apply(value);
        val filtered = filterPredicateHandlers.test(replaced) ? replaced : null;
        if (filtered != null) {
            doOnNextHandlers.accept(filtered);
        }
        return filtered != null ? onNextMapHandlers.apply(filtered) : null;
    }

    /**
     * Runs all onRequest handlers.
     *
     * @param value number of events requested
     */
    public void handleOnRequestConstraints(Long value) {
        onRequestHandlers.accept(value);
    }

    /**
     * Runs all onComplete handlers.
     */
    public void handleOnCompleteConstraints() {
        onCompleteHandlers.run();
    }

    /**
     * Runs all onTerminate handlers.
     */
    public void handleOnTerminateConstraints() {
        onTerminateHandlers.run();
    }

    /**
     * Runs all onDecision handlers.
     */
    public void handleOnDecisionConstraints() {
        onDecisionHandlers.run();
    }

    /**
     * Runs all afterTerminate handlers.
     */
    public void handleAfterTerminateConstraints() {
        afterTerminateHandlers.run();
    }

    /**
     * Runs all onCancel handlers.
     */
    public void handleOnCancelConstraints() {
        onCancelHandlers.run();
    }

    /**
     * Runs all method invocation handlers. These handlers may modify the
     * methodInvocation.
     *
     * @param methodInvocation the method invocation to examine and potentially
     * modify
     */
    public void handleMethodInvocationHandlers(MethodInvocation methodInvocation) {
        methodInvocationHandlers.accept(methodInvocation);
    }

    /**
     * Executes all onError constraint handlers, potentially transforming the error.
     *
     * @param error the error
     * @return the error after all handlers have run
     */
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

    /**
     * Wires the handlers into the matching reactive signals.
     *
     * @param resourceAccessPoint a reactive resource access point
     * @return the resource access point with the different handlers wired to their
     * respective hooks.
     */
    public Flux<T> wrap(Flux<T> resourceAccessPoint) {
        final var wrapped = resourceAccessPoint.doOnRequest(onRequestHandlers).doOnSubscribe(onSubscribeHandlers)
                .filter(filterPredicateHandlers).onErrorMap(onErrorMapHandlers).doOnError(doOnErrorHandlers)
                .map(onNextMapHandlers).doOnNext(doOnNextHandlers).doOnCancel(onCancelHandlers)
                .doOnComplete(onCompleteHandlers).doOnTerminate(onTerminateHandlers)
                .doAfterTerminate(afterTerminateHandlers);
        return onDecision(onDecisionHandlers).thenMany(wrapped);
    }

    private Mono<Void> onDecision(Runnable handlers) {
        return Mono.fromRunnable(handlers);
    }

}
