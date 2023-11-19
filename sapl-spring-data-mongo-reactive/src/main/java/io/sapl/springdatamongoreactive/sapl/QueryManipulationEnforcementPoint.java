package io.sapl.springdatamongoreactive.sapl;

import reactor.core.publisher.Flux;

public interface QueryManipulationEnforcementPoint<T> {

    Flux<T> enforce();
}
