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

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import io.sapl.springdatar2dbc.sapl.utils.annotation.SaplProtectedR2dbc;
import reactor.core.publisher.Flux;

@Repository
public interface R2dbcPersonRepositoryWithCrudRepository extends ReactiveCrudRepository<Person, Integer> {

    @SaplProtectedR2dbc
    @Query("SELECT * FROM testUser WHERE age = (:age) AND id = (:id)")
    Flux<Person> findAllUsersTest(int age, String id);
}
