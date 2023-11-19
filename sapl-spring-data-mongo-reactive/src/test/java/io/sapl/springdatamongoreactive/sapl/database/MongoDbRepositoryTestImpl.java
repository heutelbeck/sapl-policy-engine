package io.sapl.springdatamongoreactive.sapl.database;

import org.bson.types.ObjectId;
import reactor.core.publisher.Flux;

public class MongoDbRepositoryTestImpl implements MongoDbRepositoryTestCustom<TestUser, String> {
    @Override
    public Flux<TestUser> methodTestWithAge(int age) {
        return Flux.just(new TestUser(new ObjectId(), "Dan", 55));
    }
}
