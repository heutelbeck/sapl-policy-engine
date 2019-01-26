package io.sapl.grammar.sapl.impl;

import reactor.core.publisher.Flux;

@FunctionalInterface
interface FluxProvider<T> {
    Flux<T> getFlux(T input);
}
