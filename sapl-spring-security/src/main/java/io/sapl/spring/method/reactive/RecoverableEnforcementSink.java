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
package io.sapl.spring.method.reactive;

import java.util.Optional;
import java.util.function.Consumer;

import reactor.core.publisher.FluxSink;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class RecoverableEnforcementSink<T> implements Consumer<FluxSink<Tuple2<Optional<T>, Optional<Throwable>>>> {

	private FluxSink<Tuple2<Optional<T>, Optional<Throwable>>> fluxSink;

	@Override
	public void accept(FluxSink<Tuple2<Optional<T>, Optional<Throwable>>> fluxSink) {
		this.fluxSink = fluxSink;
	}

	public void next(T value) {
		fluxSink.next(Tuples.of(Optional.of(value), Optional.empty()));
	}

	public void error(Throwable e) {
		fluxSink.next(Tuples.of(Optional.empty(), Optional.of(e)));
	}

	public void complete() {
		fluxSink.complete();
	}

}