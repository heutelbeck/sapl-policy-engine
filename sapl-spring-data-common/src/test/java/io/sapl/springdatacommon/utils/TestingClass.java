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
package io.sapl.springdatacommon.utils;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TestingClass implements TestingInterface {

	@Override
	public <S extends Integer> Mono<S> save(S entity) {
		return null;
	}

	@Override
	public <S extends Integer> Flux<S> saveAll(Iterable<S> entities) {
		return null;
	}

	@Override
	public <S extends Integer> Flux<S> saveAll(Publisher<S> entityStream) {
		return null;
	}

	@Override
	public Mono<Integer> findById(Object id) {
		return null;
	}

	@Override
	public Mono<Integer> findById(Publisher<Object> id) {
		return null;
	}

	@Override
	public Mono<Boolean> existsById(Object id) {
		return null;
	}

	@Override
	public Mono<Boolean> existsById(Publisher<Object> id) {
		return null;
	}

	@Override
	public Flux<Integer> findAll() {
		return null;
	}

	@Override
	public Flux<Integer> findAllById(Iterable<Object> ids) {
		return null;
	}

	@Override
	public Flux<Integer> findAllById(Publisher<Object> idStream) {
		return null;
	}

	@Override
	public Mono<Long> count() {
		return null;
	}

	@Override
	public Mono<Void> deleteById(Object id) {
		return null;
	}

	@Override
	public Mono<Void> deleteById(Publisher<Object> id) {
		return null;
	}

	@Override
	public Mono<Void> delete(Integer entity) {
		return null;
	}

	@Override
	public Mono<Void> deleteAllById(Iterable<? extends Object> ids) {
		return null;
	}

	@Override
	public Mono<Void> deleteAll(Iterable<? extends Integer> entities) {
		return null;
	}

	@Override
	public Mono<Void> deleteAll(Publisher<? extends Integer> entityStream) {
		return null;
	}

	@Override
	public Mono<Void> deleteAll() {
		return null;
	}

}
