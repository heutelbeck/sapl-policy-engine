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
package io.sapl.attributes.broker.repository;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import io.sapl.api.model.Value;
import lombok.NonNull;

/** An assignment in an ordered, atomic publication to one repository tenant. */
public sealed interface AttributeAssignment {

    /** @return the key whose binding changes */
    RepositoryKey key();

    /**
     * @param key attribute to bind
     * @param value value supplied by the producer, including SAPL null
     * @param ttl optional positive lifetime from publication
     */
    record BoundValue(@NonNull RepositoryKey key, @NonNull Value value, @Nullable Duration ttl)
            implements AttributeAssignment {

        private static final String ERROR_TTL_NOT_POSITIVE = "TTL must be a strictly positive Duration.";

        /**
         * @param key attribute to bind
         * @param value value supplied by the producer
         * @param ttl optional lifetime from publication
         * @throws IllegalArgumentException if a supplied TTL is zero or negative
         */
        public BoundValue {
            if (ttl != null && (ttl.isNegative() || ttl.isZero())) {
                throw new IllegalArgumentException(ERROR_TTL_NOT_POSITIVE);
            }
        }
    }

    /** @param key attribute whose existing binding is removed */
    record UnboundValue(@NonNull RepositoryKey key) implements AttributeAssignment {}
}
