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
package io.sapl.springdatamongoreactive.sapl.database;

import java.util.List;
import java.util.stream.Stream;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import io.sapl.springdatacommon.sapl.Enforce;
import io.sapl.springdatacommon.sapl.SaplProtected;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface MongoDbRepositoryTests
        extends ReactiveMongoRepository<TestUser, ObjectId>, MongoDbRepositoryTestCustom<TestUser, ObjectId> {

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
    Mono<TestUser> findById(ObjectId id);

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
