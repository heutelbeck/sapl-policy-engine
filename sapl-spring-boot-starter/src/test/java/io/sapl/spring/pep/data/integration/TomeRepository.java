/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.pep.data.integration;

import java.util.Collection;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import io.sapl.spring.pep.data.integration.RelationalShimChainIT.Tome;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TomeRepository extends ReactiveCrudRepository<Tome, Integer> {

    Flux<Tome> findByForbiddenTierLessThanEqual(Integer maxTier);

    Mono<Long> countByMoon(String moon);

    Flux<Tome> findByMoonAndForbiddenTierLessThanEqual(String moon, Integer maxTier);

    Flux<Tome> findByMoonInOrderByForbiddenTierDescIdAsc(Collection<String> moons);

    @Query("SELECT * FROM tome WHERE title LIKE :pattern")
    Flux<Tome> findRareTomes(String pattern);
}
