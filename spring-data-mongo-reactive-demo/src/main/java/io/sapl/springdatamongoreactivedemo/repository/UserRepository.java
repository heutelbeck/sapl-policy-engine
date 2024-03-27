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
package io.sapl.springdatamongoreactivedemo.repository;

import io.sapl.spring.method.metadata.QueryEnforce;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface UserRepository extends ReactiveCrudRepository<User, ObjectId> {

	/**
	 * This method retrieves all users stored in the database. Excludes users with
	 * the 'ADMIN' role over the query and it excludes the column 'firstname' by the
	 * policy with 'excludeNotActiveUsers' action enforced by the
	 * {@link QueryEnforce} annotation. Only users with the 'ROLE_ADMIN' role in
	 * spring security authentication can access this method.
	 *
	 * @return a {@link Flux} emitting all users found in the database.
	 */
	@QueryEnforce(action = "excludeAdminsAndFirstname", subject = "hasRole('ROLE_ADMIN')")
	Flux<User> findAll();

	/**
	 * This method queries the database to find users whose age is greater than the
	 * provided value. It includes active users and column 'firstname' based on the
	 * 'includeActiveUsersAndFirstname' action enforced by the {@link QueryEnforce}
	 * annotation, and additionally, it applies pagination by ordering the results
	 * by age and returning a specific page of results.
	 *
	 * @param age  the minimum age for filtering users.
	 * @param page the pagination information.
	 * @return a {@link Flux} emitting users whose age is greater than the specified
	 *         value.
	 */
	@QueryEnforce(action = "includeActiveUsersAndFirstname")
	Flux<User> findAllByAgeAfter(Integer age, Pageable page);

	/**
	 * This method queries the database to find users whose last name contains the
	 * provided string. It includes users who are active based on the
	 * 'includeActiveUsers' action enforced by the {@link QueryEnforce} annotation,
	 * and for users with the 'ROLE_ADMIN' role in their spring security
	 * authentication, it applies additional query manipulation to exclude the
	 * 'firstname' field and sort the results by 'lastname'.
	 *
	 * @param lastnameContains the string to search for in the last name field.
	 * @return a {@link Flux} emitting all users whose last name contains the
	 *         specified string.
	 */
	@QueryEnforce(action = "includeActiveUsers", subject = "{\"admin\": hasRole('ROLE_ADMIN')}")
	@Query(value = "{'lastname': {'$regex': ?0}}", fields = "{'firstname': 0}", sort = "{'lastname': 1}")
	Flux<User> fetchingByQueryMethod(String lastnameContains);

}
