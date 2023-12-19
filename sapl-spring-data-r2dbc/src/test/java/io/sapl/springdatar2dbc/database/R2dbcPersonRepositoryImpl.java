package io.sapl.springdatar2dbc.database;

import reactor.core.publisher.Flux;

public class R2dbcPersonRepositoryImpl implements R2dbcPersonRepositoryCustom<Person, String> {
    @Override
    public Flux<Person> methodTestWithAge(int age) {
        return Flux.just(new Person(4, "Terrel", "Woodings", 96, Role.USER, true));
    }
}
