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
package io.sapl.server.ce.model.clients;

import lombok.NonNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.io.Serializable;
import java.util.Collection;
import java.util.Optional;

public interface ClientCredentialsRepository extends CrudRepository<ClientCredentials, Long>, Serializable {
    /**
     * Returns all instances of the {@link ClientCredentials}s.
     *
     * @return the instances
     */
    @NonNull
    @Override
    Collection<ClientCredentials> findAll();

    /**
     * Gets the {@link ClientCredentials} with a specific key.
     *
     * @param key of the credentials
     * @return the {@link ClientCredentials}
     */
    @Query(value = "SELECT c FROM ClientCredentials c WHERE c.key = :key")
    Optional<ClientCredentials> findByKey(@Param(value = "key") String key);
}
