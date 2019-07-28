package io.sapl.interpreter;

import reactor.core.publisher.Flux;

@FunctionalInterface
public interface FluxProvider<T> {

	Flux<T> getFlux(T input);

}
