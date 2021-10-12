package io.sapl.spring.method.reactive;

import java.util.function.Consumer;

import reactor.core.publisher.FluxSink;

public class EnforcementSink implements Consumer<FluxSink<Object>> {

	private FluxSink<Object> fluxSink;

	@Override
	public void accept(FluxSink<Object> fluxSink) {
		this.fluxSink = fluxSink;
	}

	public void next(Object event) {
		fluxSink.next(event);
	}

	public void error(Throwable e) {
		fluxSink.error(e);
	}

	public void complete() {
		fluxSink.complete();
	}

}