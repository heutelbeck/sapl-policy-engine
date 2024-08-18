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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import io.sapl.springdatar2dbc.database.Person;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Import(TestConfig.class)
@SpringBootTest(classes = { TestApplication.class })
class PersonRepositoryIT extends TestContainerBase {

    @Autowired
    private PersonIntegrationTestsRepository repository;

    @Test
    void when_findAll_then_manipulateQuery() {
        // GIVEN
        var person1    = new Person(2, null, 0, false);
        var person2    = new Person(5, null, 0, false);
        var person3    = new Person(7, null, 0, false);
        var person4    = new Person(10, null, 0, false);
        var personList = List.of(person1, person2, person3, person4);

        // WHEN
        var personFlux = repository.findAll().collectList();

        // THEN
        StepVerifier.create(personFlux).expectNext(personList).verifyComplete();
    }

    @Test
    void when_findAllByAgeAfter_then_manipulateQuery() {
        // GIVEN
        var person1    = new Person(9, "Ian", 45, true);
        var person2    = new Person(8, "Hannah", 38, true);
        var person3    = new Person(3, "Charlie", 35, true);
        var person4    = new Person(6, "Frank", 32, true);
        var personList = List.of(person1, person2, person3, person4);

        // WHEN
        var personFlux = repository.findAllByAgeAfter(30, Sort.by(Sort.Direction.DESC, "age")).collectList();

        // THEN
        StepVerifier.create(personFlux).expectNext(personList).verifyComplete();
    }

    @Test
    void when_findAllByAgeAfter_then_fetchingByQueryMethod() {
        // GIVEN
        var person1    = new Person(0, "Alice", 0, false);
        var person2    = new Person(0, "Charlie", 0, false);
        var personList = List.of(person1, person2);

        // WHEN
        var personFlux = repository.fetchingByQueryMethod("l", Pageable.ofSize(2)).collectList();

        // THEN
        StepVerifier.create(personFlux).expectNext(personList).verifyComplete();
    }

    @Test
    void when_findAllByAgeBefore_then_throwAccessDeniedException() {
        // GIVEN

        // WHEN
        Flux<Person> personFlux = repository.findAllByAgeBefore(80);

        // THEN
        StepVerifier.create(personFlux).expectError(AccessDeniedException.class).verify();
    }
}
