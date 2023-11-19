package io.sapl.springdatar2dbc.database;

import io.sapl.springdatar2dbc.sapl.Enforce;
import io.sapl.springdatar2dbc.sapl.SaplProtected;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Repository
public interface R2dbcPersonRepository
        extends R2dbcRepository<Person, String>, R2dbcPersonRepositoryCustom<Person, String> {

    @Enforce(subject = "subject", action = "general_protection_reactive_r2dbc_repository", resource = "resource", environment = "environment")
    Flux<Person> findAllByFirstname(String firstname);

    Flux<Person> findAllByAge(int age);

    @Enforce(subject = "#firstname", action = "general_protection_reactive_r2dbc_repository", resource = "#setResource('field', #firstname)", environment = "@r2dbcTestService.setEnvironment(#age, 2)", staticClasses = {
            TestClass.class })
    Flux<Person> findAllByAgeAfterAndFirstname(int age, String firstname);

    Flux<Person> findAllByAgeAfter(int age);

    @Enforce(subject = "#firstname", action = "general_protection_reactive_r2dbc_repository", resource = "T(io.sapl.springdatar2dbc.database.TestClass).setResource(#firstname, 'test value')", environment = "{\"testNode\":\"testValue\"}", staticClasses = {})
    Flux<Person> findAllByFirstnameAndAgeBefore(String firstname, int age);

    Flux<Person> findAllByFirstnameOrAgeBefore(String firstname, int age);

    @Enforce(subject = "test", action = "test", resource = "test", environment = "{\"testNode\"!!\"testValue\"}")
    Mono<Person> findById(String id);

    @Enforce(subject = "test", action = "test", resource = "#setResource('field', #firstname)", environment = "test")
    Flux<Person> findByIdBefore(String id);

    @Enforce(subject = "test", action = "test", resource = "#setResource('field', #firstname)", environment = "test", staticClasses = {
            R2dbcTestService.class })
    Flux<Person> findByIdAfter(String id);

    @Enforce(subject = "test", action = "test", resource = "#methodNotExist('field', #firstname)", environment = "test", staticClasses = {
            TestClass.class })
    Flux<Person> findByIdAndAge(String id, int age);

    @SaplProtected
    @Query("SELECT * FROM testUser WHERE age = (:age) AND id = (:id)")
    Flux<Person> findAllUsersTest(int age, String id);

    @Query("SELECT * FROM testUser")
    Flux<Person> findAllUsersTest();

    @Query("SELECT * FROM testUser WHERE firstname = (:firstname)")
    Mono<Person> findUserTest(String firstname);

    @SaplProtected
    Mono<Person> findByAge(int age);

    @SaplProtected
    Flux<Person> findAllBy();

    Flux<Person> findAllByAgeBefore(int age);

    @SaplProtected
    Flux<Person> methodTestWithAge(int age);

    @SaplProtected
    List<Person> findAllByAgeGreaterThan(int age);

    @SaplProtected
    Stream<Person> findAllByAgeLessThan(int age);
}
