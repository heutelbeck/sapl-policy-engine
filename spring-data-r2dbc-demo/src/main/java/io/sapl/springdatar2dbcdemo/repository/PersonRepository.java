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
package io.sapl.springdatar2dbcdemo.repository;

import io.sapl.spring.method.metadata.QueryEnforce;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface PersonRepository extends R2dbcRepository<Person, Integer> {

	/**
	 * This method retrieves all users stored in the database. Excludes persons with
	 * the 'ADMIN' role over the query and it excludes the column 'firstname' by the
	 * policy with 'excludeAdminsAndFirstname' action enforced by the
	 * {@link QueryEnforce} annotation. Only users with the 'ROLE_ADMIN' role in
	 * spring security authentication can access this method.
	 *
	 * @return a {@link Flux} emitting all users found in the database.
	 */
	@QueryEnforce(action = "excludeAdminsAndFirstname", subject = "hasRole('ROLE_ADMIN')")
	Flux<Person> findAll();

	/**
	 * This method queries the database to find persons whose age is greater than
	 * the provided value. It enforces the policy with
	 * 'includeActivePersonsAndFirstname' action using the {@link QueryEnforce}
	 * annotation to include active persons and column 'firstname'.
	 *
	 * @param age  the minimum age for filtering persons.
	 * @param page the sorting information.
	 * @return a {@link Flux} emitting persons whose age is greater than the
	 *         specified value.
	 */
	@QueryEnforce(action = "includeActivePersonsAndFirstname")
	Flux<Person> findAllByAgeAfter(Integer age, Sort page);

	/**
	 * This method constructs a custom query to find persons whose address city
	 * contains the provided string. It enforces the policy with
	 * 'excludeNotActivePersons' action using the {@link QueryEnforce} annotation
	 * for users with the 'ROLE_ADMIN' role in their spring security authentication.
	 * The query manipulates the SQL query and excludes not active persons.
	 *
	 * @param cityContains the string to search for in the address city field.
	 * @param sort         the pagination and sorting information.
	 * @return a {@link Flux} emitting persons whose address city contains the
	 *         specified string.
	 */
	@QueryEnforce(action = "includeActivePersons", subject = "{\"admin\": hasRole('ROLE_ADMIN')}")
	@Query("SELECT * FROM Person p JOIN Address a WHERE a.city LIKE CONCAT('%', (:cityContains), '%') AND p.addressId = a.addressId")
	Flux<Person> fetchingByQueryMethod(String cityContains, Pageable sort);

}
