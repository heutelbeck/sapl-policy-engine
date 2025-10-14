/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 *
 * This bundle aggregates all constraint handlers for a specific decision which
 * are useful in a blocking PostEnforce scenario.
 * <p>
 *
 * @param <T> return type
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockingConstraintHandlerBundle<T> {

    /**
     * Utility bundle that does nothing.
     */
    public static final BlockingConstraintHandlerBundle<Object> BLOCKING_NOOP = new BlockingConstraintHandlerBundle<>(
            FunctionUtil.noop(), FunctionUtil.sink(), FunctionUtil.sink(), UnaryOperator.identity(),
            FunctionUtil.sink(), UnaryOperator.identity(), FunctionUtil.all(), UnaryOperator.identity());

    private final Runnable                   onDecisionHandlers;
    private final Consumer<MethodInvocation> methodInvocationHandlers;
    private final Consumer<T>                doOnNextHandlers;
    private final UnaryOperator<T>           onNextMapHandlers;
    private final Consumer<Throwable>        doOnErrorHandlers;
    private final UnaryOperator<Throwable>   onErrorMapHandlers;
    private final Predicate<Object>          filterPredicateHandlers;
    private final UnaryOperator<T>           replaceResourceHandler;

    /**
     * Factory method for creating a bundle for use in a @PostEnforce PEP.
     *
     * @param <T> payload type
     * @param onDecisionHandlers Handlers to be executed after each decision.
     * @param doOnNextHandlers Handlers to be executed when data is emitted by the
     * RAP.
     * @param onNextMapHandlers Handlers mapping the RAP output.
     * @param doOnErrorHandlers Handlers executed on an error.
     * @param onErrorMapHandlers Handlers mapping an error.
     * @param filterPredicateHandlers Handlers for filtering content from List,
     * Obligation, array types.
     * @param replaceResourceHandler Handler for replacing the RAP output by a value
     * provided by the PDP.
     * @return a constraint handler bundle.
     */
    public static <T> BlockingConstraintHandlerBundle<T> postEnforceConstraintHandlerBundle(Runnable onDecisionHandlers,
            Consumer<T> doOnNextHandlers, UnaryOperator<T> onNextMapHandlers, Consumer<Throwable> doOnErrorHandlers,
            UnaryOperator<Throwable> onErrorMapHandlers, Predicate<Object> filterPredicateHandlers,
            UnaryOperator<T> replaceResourceHandler) {
        return new BlockingConstraintHandlerBundle<>(onDecisionHandlers, FunctionUtil.sink(), doOnNextHandlers,
                onNextMapHandlers, doOnErrorHandlers, onErrorMapHandlers, filterPredicateHandlers,
                replaceResourceHandler);
    }

    /**
     * Factory method for creating a bundle for use in a @PreEnforce PEP.
     *
     * @param <T> payload type
     * @param onDecisionHandlers Handlers to be executed after each decision.
     * @param doOnNextHandlers Handlers to be executed when data is emitted by the
     * RAP.
     * @param onNextMapHandlers Handlers mapping the RAP output.
     * @param doOnErrorHandlers Handlers executed on an error.
     * @param onErrorMapHandlers Handlers mapping an error.
     * @param filterPredicateHandlers Handlers for filtering content from List,
     * Obligation, array types.
     * @param methodInvocationHandlers Handlers for manipulating the method
     * invocation.
     * @param replaceResourceHandler Handler for replacing the RAP output by a value
     * provided by the PDP.
     * @return a constraint handler bundle.
     */
    public static <T> BlockingConstraintHandlerBundle<T> preEnforceConstraintHandlerBundle(Runnable onDecisionHandlers,
            Consumer<T> doOnNextHandlers, UnaryOperator<T> onNextMapHandlers, Consumer<Throwable> doOnErrorHandlers,
            UnaryOperator<Throwable> onErrorMapHandlers, Predicate<Object> filterPredicateHandlers,
            Consumer<MethodInvocation> methodInvocationHandlers, UnaryOperator<T> replaceResourceHandler) {
        return new BlockingConstraintHandlerBundle<>(onDecisionHandlers, methodInvocationHandlers, doOnNextHandlers,
                onNextMapHandlers, doOnErrorHandlers, onErrorMapHandlers, filterPredicateHandlers,
                replaceResourceHandler);
    }

    /**
     * Factory method for creating a bundle for use in an AuthorizationManager PEP.
     *
     * @param <T> payload type
     * @param onDecisionHandlers Handlers to be executed after each decision.
     * @return a constraint handler bundle.
     */
    public static <T> BlockingConstraintHandlerBundle<T> accessManagerConstraintHandlerBundle(
            Runnable onDecisionHandlers) {
        return new BlockingConstraintHandlerBundle<>(onDecisionHandlers, FunctionUtil.sink(), FunctionUtil.sink(),
                UnaryOperator.identity(), FunctionUtil.sink(), UnaryOperator.identity(), FunctionUtil.all(),
                UnaryOperator.identity());
    }

    /**
     * o Runs all method invocation handlers. These handlers may modify the
     * methodInvocation.
     *
     * @param methodInvocation the method invocation to examine and potentially
     * modify
     */
    public void handleMethodInvocationHandlers(MethodInvocation methodInvocation) {
        methodInvocationHandlers.accept(methodInvocation);
    }

    /**
     * Executes all onNext constraint handlers, potentially transforming the value.
     *
     * @param value a return value
     * @return the return value after constraint handling
     */
    @SuppressWarnings("unchecked")
    public Object handleAllOnNextConstraints(Object value) {
        final var newValue = handleFilterPredicateHandlers((T) value);
        handleOnNextConstraints(newValue);
        return handleOnNextMapConstraints(newValue);
    }

    @SuppressWarnings("unchecked")
    private T handleFilterPredicateHandlers(T value) {
        if (value == null)
            return null;
        if (value instanceof Optional)
            return (T) ((Optional<Object>) value).filter(filterPredicateHandlers);
        if (value instanceof List)
            return (T) ((List<Object>) value).stream().filter(filterPredicateHandlers).toList();
        if (value instanceof Set)
            return (T) ((Set<Object>) value).stream().filter(filterPredicateHandlers).collect(Collectors.toSet());
        if (value.getClass().isArray()) {
            final var filteredAsList = Arrays.stream((Object[]) value).filter(filterPredicateHandlers).toList();
            final var resultArray    = Array.newInstance(value.getClass().getComponentType(), filteredAsList.size());

            var i = 0;
            for (var x : filteredAsList)
                Array.set(resultArray, i++, x);

            return (T) resultArray;
        }
        return filterPredicateHandlers.test(value) ? value : null;
    }

    private T handleOnNextMapConstraints(T value) {
        final var mapped = onNextMapHandlers.apply(value);
        return replaceResourceHandler.apply(mapped);
    }

    private void handleOnNextConstraints(T value) {
        doOnNextHandlers.accept(value);
    }

    /**
     * Runs all onDecision constraint handlers.
     */
    public void handleOnDecisionConstraints() {
        onDecisionHandlers.run();
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

}
