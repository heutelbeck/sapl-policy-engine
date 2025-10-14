/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.springdatar2dbc.database;

import io.sapl.spring.method.metadata.QueryEnforce;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Stream;

@Repository
public interface PersonR2dbcRepository extends R2dbcRepository<Person, Integer> {

    @QueryEnforce(subject = "subject", action = "general_protection_reactive_r2dbc_repository", resource = "resource", environment = "environment")
    Flux<Person> findAllByFirstname(String firstname);

    Flux<Person> findAllByAge(int age);

    @QueryEnforce(subject = "#firstname", action = "general_protection_reactive_r2dbc_repository", resource = "#setResource('field', #firstname)", environment = "@r2dbcTestService.setEnvironment(#age, 2)", staticClasses = {
            TestClass.class })
    Flux<Person> findAllByAgeAfterAndFirstname(int age, String firstname);

    Flux<Person> findAllByAgeAfter(int age);

    @QueryEnforce(subject = "#firstname", action = "general_protection_reactive_r2dbc_repository", resource = "T(io.sapl.springdatar2dbc.database.TestClass).setResource(#firstname, 'test value')", environment = "{\"testNode\":\"testValue\"}", staticClasses = {})
    Flux<Person> findAllByFirstnameAndAgeBefore(String firstname, int age);

    Flux<Person> findAllByFirstnameOrAgeBefore(String firstname, int age);

    @QueryEnforce(subject = "test", action = "test", resource = "test", environment = "{\"testNode\"!!\"testValue\"}")
    Mono<Person> findById(String id);

    @QueryEnforce(subject = "test", action = "test", resource = "#setResource('field', #firstname)", environment = "test")
    Flux<Person> findByIdBefore(String id);

    @QueryEnforce(subject = "test", action = "test", resource = "#setResource('field', #firstname)", environment = "test", staticClasses = {
            R2dbcTestService.class })
    Flux<Person> findByIdAfter(String id);

    @QueryEnforce(subject = "test", action = "test", resource = "#methodNotExist('field', #firstname)", environment = "test", staticClasses = {
            TestClass.class })
    Flux<Person> findByIdAndAge(String id, int age);

    @Query("SELECT * FROM testUser WHERE age = (:age) AND id = (:id)")
    Flux<Person> findAllUsersTest(int age, String id);

    @Query("SELECT * FROM testUser")
    Flux<Person> findAllUsersTest();

    @Query("SELECT * FROM testUser WHERE firstname = (:firstname)")
    Mono<Person> findUserTest(String firstname);

    Mono<Person> findByAge(int age);

    Flux<Person> findAllBy();

    Flux<Person> findAllByAgeBefore(int age);

    Flux<Person> methodTestWithAge(int age);

    List<Person> findAllByAgeGreaterThan(int age);

    Stream<Person> findAllByAgeLessThan(int age);
}
