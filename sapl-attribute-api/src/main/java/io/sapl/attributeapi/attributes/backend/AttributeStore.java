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
package io.sapl.attributeapi.attributes.backend;

import io.sapl.api.model.Value;
import org.jspecify.annotations.Nullable;
import java.time.Duration;
import java.util.List;

/**
 * The interface for an attribute store that implements a connection to
 * a persistent backend. Used by the API and offers methods that support
 * common endpoint mechanics.
 */
public interface AttributeStore {
    /**
     * Publish an attribute to a persistent backend without a set time-to-live (TTL).
     * A non set TTL means that the attribute never expires.
     *
     * @param key The attribute key that contains the entity, attribute name and arguments.
     * @param value The value of an attribute.
     * @param pdpId The pdp id the attribute is stored for. The default is "default".
     * @return {@code true}, the attribute could be published and {@code false} if not.
     */
    boolean publish(AttributeKey key, Value value, String pdpId);

    /**
     * Publish an attribute to a persistent backend with a set time-to-live (TTL).
     * The time-to-live set the lifetime of an attribute and is removed after it's expired.
     *
     * @param key The attribute key that contains the entity, attribute name and arguments.
     * @param value The value of an attribute.
     * @param ttl The time to live of an attribute
     * @param pdpId The pdp id the attribute is stored for. The default is "default".
     * @return {@code true}, the attribute could be published and {@code false} if not.
     */
    boolean publish(AttributeKey key, Value value, Duration ttl, String pdpId);

    /**
     * Removes an attribute from the persistent backend.
     *
     * @param key The attribute key that contains the entity, attribute name and arguments.
     * @param pdpId The pdp id to the delete the attribute for.
     * @return {@code true}, the attribute could be removed and {@code false} if not.
     */
    boolean remove(AttributeKey key, String pdpId);

    /**
     * Counts all attributes for a specific pdp id that are stored on the persistent storage.
     *
     * @param pdpId The pdp id the count operation is executed for.
     * @return A {@code Long} value that indicates the amount of entries.
     */
    Long count(String pdpId);

    /**
     * Get a specific attribute from the backend. Specified by the attribute key and a mandatory
     * pdp id.
     *
     * @param key The {@code AttributeKey} that contains an attribute name and a optional entity and arguments.
     * @param pdpId The pdp id the attribute lookup is done for.
     * @return An {@code Value} that contains the stored value for the attribute.
     */
    Value get(AttributeKey key, String pdpId);

    /**
     * Lists all attributes for a given pdp id.
     *
     * @param pdpId The pdp id the attribute lookup is done for.
     * @param limit Limit the result by [0..MAX_INT] entries.
     * @param offset Start the list at the number specified by the offset.
     * @return A list of all stored attributes containing the {@code AttributeKey} and the {@code Value}.
     */
    List<AttributeEntry> getAll(String pdpId, @Nullable Integer limit, @Nullable Integer offset);

    /**
     * Safely closes all the connections to the backend storage.
     */
    void close();
}
