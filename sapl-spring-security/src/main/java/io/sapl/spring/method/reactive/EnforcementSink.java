package io.sapl.spring.method.reactive;

import java.util.function.Consumer;

import reactor.core.publisher.FluxSink;

public class EnforcementSink<T> implements Consumer<FluxSink<T>> {

	private FluxSink<T> fluxSink;

	@Override
	public void accept(FluxSink<T> fluxSink) {
		this.fluxSink = fluxSink;
	}

	public void next(T event) {
		fluxSink.next(event);
	}

	public void error(Throwable e) {
		fluxSink.error(e);
	}

	public void complete() {
		fluxSink.complete();
	}

}