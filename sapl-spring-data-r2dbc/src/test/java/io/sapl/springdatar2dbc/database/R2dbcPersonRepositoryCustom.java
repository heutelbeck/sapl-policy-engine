package io.sapl.springdatar2dbc.database;

import reactor.core.publisher.Flux;

public interface R2dbcPersonRepositoryCustom<T, ID> {

    Flux<T> methodTestWithAge(int age);
}
