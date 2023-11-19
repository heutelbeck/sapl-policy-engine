package io.sapl.springdatamongoreactive.sapl.database;

import reactor.core.publisher.Flux;

public interface MongoDbRepositoryTestCustom<T, ID> {

    Flux<T> methodTestWithAge(int age);
}
