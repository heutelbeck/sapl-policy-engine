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
package io.sapl.springdatar2dbc.integration;

import io.sapl.spring.method.metadata.QueryEnforce;
import io.sapl.springdatar2dbc.database.Person;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface PersonIntegrationTestsRepository extends R2dbcRepository<Person, Integer> {

    @QueryEnforce(action = "findAll", subject = "{\"age\": @customBean.getAge()}")
    Flux<Person> findAll();

    @QueryEnforce(action = "findAllByAgeAfter", resource = "{\"age\": #age}")
    Flux<Person> findAllByAgeAfter(Integer age, Sort page);

    @QueryEnforce(action = "fetchingByQueryMethod", subject = "{\"age\": @customBean.getAge()}")
    @Query("SELECT * FROM Person WHERE firstname LIKE CONCAT('%', (:firstname), '%')")
    Flux<Person> fetchingByQueryMethod(String firstname, Pageable sort);

    @QueryEnforce(action = "accessDenied")
    Flux<Person> findAllByAgeBefore(int age);
}
