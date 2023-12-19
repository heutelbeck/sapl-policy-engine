package io.sapl.springdatar2dbc.sapl;

import reactor.core.publisher.Flux;

public interface QueryManipulationEnforcementPoint<T> {

    Flux<T> enforce();

}
