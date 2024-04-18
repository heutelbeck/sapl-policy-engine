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
package io.sapl.springdatamongoreactivedemo.controller;

import lombok.AllArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.sapl.springdatamongoreactivedemo.repository.User;
import io.sapl.springdatamongoreactivedemo.repository.UserRepository;
import reactor.core.publisher.Flux;

@RestController
@AllArgsConstructor
public class DemoRestController {

	private final UserRepository repository;

	@GetMapping("/findAll")
	public Flux<User> findAll() {
		return repository.findAll();
	}

	@GetMapping("/findAllByAgeAfter/{age}")
	public Flux<User> findAllByAgeAfter(@PathVariable int age) {
		return repository.findAllByAgeAfter(age, PageRequest.of(2, 2));
	}

	@GetMapping("/fetchingByQueryMethod/{lastnameContains}")
	public Flux<User> fetchingByQueryMethod(@PathVariable String lastnameContains) {
		return repository.fetchingByQueryMethod(lastnameContains, PageRequest.of(0, 2));
	}

}
