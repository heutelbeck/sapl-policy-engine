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
package io.sapl.spring.data.mongo.integration;

import io.sapl.spring.method.metadata.QueryEnforce;
import io.sapl.spring.data.mongo.sapl.database.TestUser;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface UserIntegrationTestsRepository extends ReactiveCrudRepository<TestUser, ObjectId> {

    @QueryEnforce(action = "'findAll'", subject = "{'age': @customBean.getAge()}")
    Flux<TestUser> findAll();

    @QueryEnforce(action = "'findAllByAgeAfter'", resource = "{'age': #age}")
    Flux<TestUser> findAllByAgeAfter(Integer age, Pageable page);

    @QueryEnforce(action = "'fetchingByQueryMethod'", subject = "{'age': @customBean.getAge()}")
    @Query(value = "{'firstname': {'$regex': ?0}}", fields = "{'firstname': 0}", sort = "{'firstname': 1}")
    Flux<TestUser> fetchingByQueryMethod(String lastnameContains, Pageable pageable);

    @QueryEnforce(action = "'denyTest'")
    Flux<TestUser> findAllByAgeBefore(Integer age);
}
