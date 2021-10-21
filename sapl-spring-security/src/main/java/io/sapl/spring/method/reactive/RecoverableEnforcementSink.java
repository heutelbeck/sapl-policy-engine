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