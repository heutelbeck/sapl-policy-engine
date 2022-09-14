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

import lombok.AccessLevel;
import lombok.Setter;

@Setter(AccessLevel.PROTECTED)
public class BlockingPostEnforceConstraintHandlerBundle<T> {

	// @formatter:off
	private static final Runnable NOP = () -> {};

	private Runnable                       onDecisionHandlers = NOP;
	private Consumer<T>                    doOnNextHandlers   = __ -> {};
	private Function<T, T>                 onNextMapHandlers  = x->x;
	private Consumer<Throwable>            doOnErrorHandlers  = __ -> {};
	private Function<Throwable, Throwable> onErrorMapHandlers = x->x;
	// @formatter:on

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

	public void handleOnDecisionConstraints() {
		onDecisionHandlers.run();
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

}
