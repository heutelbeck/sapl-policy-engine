/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

import io.sapl.springdatar2dbc.sapl.utils.annotation.EnforceR2dbc;
import io.sapl.springdatar2dbc.sapl.utils.annotation.SaplProtectedR2dbc;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface R2dbcPersonRepository
        extends R2dbcRepository<Person, Integer>, R2dbcPersonRepositoryCustom<Person, Integer> {

    @EnforceR2dbc(subject = "subject", action = "general_protection_reactive_r2dbc_repository", resource = "resource", environment = "environment")
    Flux<Person> findAllByFirstname(String firstname);

    Flux<Person> findAllByAge(int age);

    @EnforceR2dbc(subject = "#firstname", action = "general_protection_reactive_r2dbc_repository", resource = "#setResource('field', #firstname)", environment = "@r2dbcTestService.setEnvironment(#age, 2)", staticClasses = {
            TestClass.class })
    Flux<Person> findAllByAgeAfterAndFirstname(int age, String firstname);

    Flux<Person> findAllByAgeAfter(int age);

    @EnforceR2dbc(subject = "#firstname", action = "general_protection_reactive_r2dbc_repository", resource = "T(io.sapl.springdatar2dbc.database.TestClass).setResource(#firstname, 'test value')", environment = "{\"testNode\":\"testValue\"}", staticClasses = {})
    Flux<Person> findAllByFirstnameAndAgeBefore(String firstname, int age);

    Flux<Person> findAllByFirstnameOrAgeBefore(String firstname, int age);

    @EnforceR2dbc(subject = "test", action = "test", resource = "test", environment = "{\"testNode\"!!\"testValue\"}")
    Mono<Person> findById(String id);

    @EnforceR2dbc(subject = "test", action = "test", resource = "#setResource('field', #firstname)", environment = "test")
    Flux<Person> findByIdBefore(String id);

    @EnforceR2dbc(subject = "test", action = "test", resource = "#setResource('field', #firstname)", environment = "test", staticClasses = {
            R2dbcTestService.class })
    Flux<Person> findByIdAfter(String id);

    @EnforceR2dbc(subject = "test", action = "test", resource = "#methodNotExist('field', #firstname)", environment = "test", staticClasses = {
            TestClass.class })
    Flux<Person> findByIdAndAge(String id, int age);

    @SaplProtectedR2dbc
    @Query("SELECT * FROM testUser WHERE age = (:age) AND id = (:id)")
    Flux<Person> findAllUsersTest(int age, String id);

    @Query("SELECT * FROM testUser")
    Flux<Person> findAllUsersTest();

    @Query("SELECT * FROM testUser WHERE firstname = (:firstname)")
    Mono<Person> findUserTest(String firstname);

    @SaplProtectedR2dbc
    Mono<Person> findByAge(int age);

    @SaplProtectedR2dbc
    Flux<Person> findAllBy();

    Flux<Person> findAllByAgeBefore(int age);

    @SaplProtectedR2dbc
    Flux<Person> methodTestWithAge(int age);

    @SaplProtectedR2dbc
    List<Person> findAllByAgeGreaterThan(int age);

    @SaplProtectedR2dbc
    Stream<Person> findAllByAgeLessThan(int age);
}
