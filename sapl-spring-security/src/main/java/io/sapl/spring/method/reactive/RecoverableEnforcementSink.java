package io.sapl.spring.method.reactive;

import java.util.Optional;
import java.util.function.Consumer;

import reactor.core.publisher.FluxSink;
import reactor.util.function.Tuple2;

public class RecoverableEnforcementSink implements Consumer<FluxSink<Tuple2<Optional<Object>, Optional<Throwable>>>> {

	private FluxSink<Tuple2<Optional<Object>, Optional<Throwable>>> fluxSink;

	@Override
	public void accept(FluxSink<Tuple2<Optional<Object>, Optional<Throwable>>> fluxSink) {
		this.fluxSink = fluxSink;
	}

	public void next(Tuple2<Optional<Object>, Optional<Throwable>> event) {
		fluxSink.next(event);
	}

	public void error(Throwable e) {
		fluxSink.error(e);
	}

	public void complete() {
		fluxSink.complete();
	}

}