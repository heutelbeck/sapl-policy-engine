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
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import io.sapl.spring.pep.data.integration.MongoBlockingDbShimChainIT.Chronicle;

public interface BlockingChronicleRepository extends MongoRepository<Chronicle, String> {

    List<Chronicle> findByForbiddenTierLessThanEqual(Integer maxTier);

    long countByMoon(String moon);

    List<Chronicle> findByMoonAndForbiddenTierLessThanEqual(String moon, Integer maxTier);

    List<Chronicle> findByMoonInOrderByForbiddenTierDescIdAsc(Collection<String> moons);

    @Query("{ 'title': { '$regex': ?0 } }")
    List<Chronicle> findRareChronicles(String pattern);
}
