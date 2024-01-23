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
package io.sapl.springdatacommon.database;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import io.sapl.springdatacommon.sapl.Enforce;
import io.sapl.springdatacommon.sapl.SaplProtected;
import reactor.core.publisher.Flux;

@Repository
public interface MongoReactiveUserRepository extends ReactiveMongoRepository<User, ObjectId> {

    @Enforce(subject = "subject", action = "general_protection_reactive_mongo_repository", resource = "resource", environment = "environment")
    Flux<User> findAllByFirstname(String firstname);

    @SaplProtected
    @Query("{'firstname':  {'$in': [ ?0 ]}}")
    Flux<User> findAllUsersTest(String user);

    @SaplProtected
    @Query("{'age':  {'$in': [ ?0 ]}}")
    Flux<User> findAllByAge(int age);

}
