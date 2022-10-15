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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

/**
 * 
 * This bundle aggregates all constraint handlers for a specific decision which
 * are useful in a blocking PostEnforce scenario.
 * 
 * 
 * @author Dominic Heutelbeck
 *
 * @param <T> return type
 */
@RequiredArgsConstructor
public class BlockingPostEnforceConstraintHandlerBundle<T> {

	private final Runnable                       onDecisionHandlers;
	private final Consumer<T>                    doOnNextHandlers;
	private final Function<T, T>                 onNextMapHandlers;
	private final Consumer<Throwable>            doOnErrorHandlers;
	private final Function<Throwable, Throwable> onErrorMapHandlers;
	private final Predicate<Object>              filterPredicateHandlers;

	/**
	 * Executes all onNext constraint handlers, potentially transforming the value.
	 * 
	 * @param value a return value
	 * @return the return value after constraint handling
	 */
	public T handleAllOnNextConstraints(T value) {
		var newValue = handleFilterPredicateHandlers(value);
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
			return (T) ((List<Object>) value).stream().filter(filterPredicateHandlers).collect(Collectors.toList());
		if (value instanceof Set)
			return (T) ((Set<Object>) value).stream().filter(filterPredicateHandlers).collect(Collectors.toSet());
		if (value.getClass().isArray()) {
			var filteredAsList = Arrays.stream((Object[]) value).filter(filterPredicateHandlers)
					.collect(Collectors.toList());
			var resultArray    = Array.newInstance(value.getClass().getComponentType(), filteredAsList.size());

			var i = 0;
			for (var x : filteredAsList)
				Array.set(resultArray, i++, x);

			return (T) resultArray;
		}
		return filterPredicateHandlers.test(value) ? value : null;
	}

	private T handleOnNextMapConstraints(T value) {
		return onNextMapHandlers.apply(value);
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
