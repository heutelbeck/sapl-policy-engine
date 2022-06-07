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

import static io.sapl.spring.constraints.BundleUtil.consumeAll;
import static io.sapl.spring.constraints.BundleUtil.mapAll;
import static io.sapl.spring.constraints.BundleUtil.runAll;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class BlockingPostEnforceConstraintHandlerBundle<T> {

	final List<Runnable>                       onDecisionHandlers = new LinkedList<>();
	final List<Consumer<T>>                    doOnNextHandlers   = new LinkedList<>();
	final List<Function<T, T>>                 onNextMapHandlers  = new LinkedList<>();
	final List<Consumer<Throwable>>            doOnErrorHandlers  = new LinkedList<>();
	final List<Function<Throwable, Throwable>> onErrorMapHandlers = new LinkedList<>();

	public T handleAllOnNextConstraints(T value) {
		handleOnNextConstraints(value);
		return handleOnNextMapConstraints(value);
	}

	private T handleOnNextMapConstraints(T value) {
		return mapAll(onNextMapHandlers).apply(value);
	}

	private void handleOnNextConstraints(T value) {
		consumeAll(doOnNextHandlers).accept(value);
	}

	public void handleOnDecisionConstraints() {
		runAll(onDecisionHandlers).run();
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

}
