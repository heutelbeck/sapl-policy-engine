package io.sapl.springdatamongoreactive.sapl.database;

import io.sapl.springdatamongoreactive.sapl.Enforce;
import io.sapl.springdatamongoreactive.sapl.SaplProtected;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Stream;

@Repository
public interface MongoDbRepositoryTest
        extends ReactiveMongoRepository<TestUser, String>, MongoDbRepositoryTestCustom<TestUser, String> {

    @Enforce(subject = "subject", action = "general_protection_reactive_mongo_repository", resource = "resource", environment = "environment")
    Flux<TestUser> findAllByFirstname(String firstname);

    @SaplProtected
    @Query("{'age':  {'$in': [ ?0 ]}}")
    Flux<TestUser> findAllByAge(int age);

    @Enforce(subject = "#firstname", action = "general_protection_reactive_mongo_repository", resource = "#setResource('field', #firstname)", environment = "@mongoTestService.setEnvironment(#age, 2)", staticClasses = {
            TestClass.class })
    Flux<TestUser> findAllByAgeAfterAndFirstname(int age, String firstname);

    @Enforce(subject = "#firstname", action = "general_protection_reactive_mongo_repository", resource = "T(io.sapl.springdatamongoreactive.sapl.database.TestClass).setResource(#firstname, 'test value')", environment = "{\"testNode\":\"testValue\"}", staticClasses = {})
    Flux<TestUser> findAllByFirstnameAndAgeBefore(String firstname, int age);

    Flux<TestUser> findAllByFirstnameOrAgeBefore(String firstname, int age);

    @Enforce(subject = "test", action = "test", resource = "test", environment = "{\"testNode\"!!\"testValue\"}")
    Flux<TestUser> findById(ObjectId id);

    @Enforce(subject = "test", action = "test", resource = "#setResource('field', #firstname)", environment = "test")
    Flux<TestUser> findByIdBefore(ObjectId id);

    @Enforce(subject = "test", action = "test", resource = "#setResource('field', #firstname)", environment = "test", staticClasses = {
            MongoTestService.class })
    Flux<TestUser> findByIdAfter(ObjectId id);

    @Enforce(subject = "test", action = "test", resource = "#methodNotExist('field', #firstname)", environment = "test", staticClasses = {
            TestClass.class })
    Flux<TestUser> findByIdAndAge(ObjectId id, int age);

    @SaplProtected
    @Query("{'firstname':  {'$in': [ ?0 ]}}")
    Flux<TestUser> findAllUsersTest(String user);

    @Query("{'firstname': ?0 }")
    Mono<TestUser> findUserTest(String user);

    @SaplProtected
    Mono<TestUser> findByAge(int age);

    Flux<TestUser> findAllBy();

    Flux<TestUser> findAllByAgeBefore(int age);

    @SaplProtected
    Flux<TestUser> methodTestWithAge(int age);

    @SaplProtected
    List<TestUser> findAllByAgeGreaterThan(int age);

    @SaplProtected
    Stream<TestUser> findAllByAgeLessThan(int age);
}
