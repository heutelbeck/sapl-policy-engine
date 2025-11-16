/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.attributes;

import io.sapl.api.model.Value;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Repository for publishing and retrieving dynamic attributes that can be used
 * in SAPL policy evaluation. Attributes can be published with time-to-live
 * settings and timeout strategies.
 * <p>
 * Attributes are identified by a composite key consisting of entity, name, and
 * arguments. The entity parameter distinguishes between global attributes
 * (entity
 * is null) and entity-specific attributes (entity has a value). Global
 * attributes
 * are shared across all policy evaluations, while entity-specific attributes
 * are
 * scoped to particular entities like users, devices, or sessions.
 * <p>
 * Basic usage for global attributes:
 *
 * <pre>{@code
 * val repository = new InMemoryAttributeRepository(Clock.systemUTC());
 *
 * // Publish a global attribute with infinite TTL
 * repository.publishAttribute("system.maintenanceMode", Val.of(false)).subscribe();
 *
 * // Subscribe to attribute updates
 * repository.invoke(invocation).subscribe(value -> processValue(value));
 * }</pre>
 * <p>
 * Entity-specific attribute usage:
 *
 * <pre>{@code
 * // Publish an attribute for a specific user
 * val userId = Val.of("user-123");
 * repository.publishAttribute(userId, "user.role", Val.of("admin")).subscribe();
 *
 * // Publish with TTL - automatically removed after expiration
 * repository.publishAttribute(userId, "session.active", Val.of(true), Duration.ofMinutes(30)).subscribe();
 * }</pre>
 */
public interface AttributeRepository extends AttributeFinder {
    Duration INFINITE = null;

    /**
     * Defines how an attribute behaves when its time-to-live expires.
     */
    enum TimeOutStrategy {
        /**
         * Removes the attribute entirely from the repository when TTL expires.
         * Subscribers will receive ATTRIBUTE_UNAVAILABLE after removal.
         */
        REMOVE,

        /**
         * Keeps the attribute but sets its value to undefined when TTL expires.
         * Subscribers will receive Val.UNDEFINED after expiration.
         */
        BECOME_UNDEFINED
    }

    /**
     * Publishes an attribute with full control over entity, arguments, TTL, and
     * timeout behavior. This is the primary method that all convenience methods
     * delegate to.
     * <p>
     * The entity parameter determines attribute scope. When entity is null, the
     * attribute is global and shared across all policy evaluations. When entity
     * has a value, the attribute is scoped to that specific entity. Two attributes
     * with the same name but different entities are completely independent.
     * <p>
     * Thread Safety: This method is fully reactive and non-blocking. All operations
     * are safe for concurrent access from multiple threads.
     *
     * @param entity the entity context for this attribute, or null for global
     * attributes
     * @param attributeName the fully qualified attribute name
     * @param arguments the attribute arguments, or empty list if none
     * @param value the attribute value to publish
     * @param ttl time-to-live duration after which the timeout strategy is applied
     * @param timeOutStrategy behavior when TTL expires
     * @return a Mono that completes when the attribute is published
     */
    Mono<Void> publishAttribute(Value entity, String attributeName, List<Value> arguments, Value value, Duration ttl,
            TimeOutStrategy timeOutStrategy);

    /**
     * Publishes a global attribute with infinite TTL and removal strategy.
     * Suitable for static configuration values or long-lived attributes.
     * <p>
     * Example:
     *
     * <pre>{@code
     * repository.publishAttribute("system.maxUsers", Val.of(1000)).subscribe();
     * }</pre>
     *
     * @param attributeName the fully qualified attribute name
     * @param value the attribute value to publish
     * @return a Mono that completes when the attribute is published
     */
    default Mono<Void> publishAttribute(String attributeName, Value value) {
        return publishAttribute(null, attributeName, List.of(), value, INFINITE, TimeOutStrategy.REMOVE);
    }

    /**
     * Publishes a global attribute with specified TTL and removal strategy.
     * When TTL expires, the attribute is removed from the repository.
     * <p>
     * Example:
     *
     * <pre>{@code
     * repository.publishAttribute("cache.token", Val.of(token), Duration.ofMinutes(15)).subscribe();
     * }</pre>
     *
     * @param attributeName the fully qualified attribute name
     * @param value the attribute value to publish
     * @param ttl time-to-live duration
     * @return a Mono that completes when the attribute is published
     */
    default Mono<Void> publishAttribute(String attributeName, Value value, Duration ttl) {
        return publishAttribute(null, attributeName, List.of(), value, ttl, TimeOutStrategy.REMOVE);
    }

    /**
     * Publishes a global attribute with specified TTL and timeout strategy.
     * Allows control over whether the attribute is removed or becomes undefined
     * when TTL expires.
     * <p>
     * Example:
     *
     * <pre>{@code
     * repository.publishAttribute("session.status", Val.of("active"), Duration.ofMinutes(30),
     *         TimeOutStrategy.BECOME_UNDEFINED).subscribe();
     * }</pre>
     *
     * @param attributeName the fully qualified attribute name
     * @param value the attribute value to publish
     * @param ttl time-to-live duration
     * @param timeOutStrategy behavior when TTL expires
     * @return a Mono that completes when the attribute is published
     */
    default Mono<Void> publishAttribute(String attributeName, Value value, Duration ttl,
            TimeOutStrategy timeOutStrategy) {
        return publishAttribute(null, attributeName, List.of(), value, ttl, timeOutStrategy);
    }

    /**
     * Publishes an entity-specific attribute with infinite TTL.
     * Useful for associating attributes with specific entities like users or
     * resources.
     * <p>
     * Example:
     *
     * <pre>{@code
     * repository.publishAttribute(Val.of("user-123"), "user.role", Val.of("admin")).subscribe();
     * }</pre>
     *
     * @param entity the entity context for this attribute
     * @param attributeName the fully qualified attribute name
     * @param value the attribute value to publish
     * @return a Mono that completes when the attribute is published
     */
    default Mono<Void> publishAttribute(Value entity, String attributeName, Value value) {
        return publishAttribute(entity, attributeName, List.of(), value, INFINITE, TimeOutStrategy.REMOVE);
    }

    /**
     * Publishes an entity-specific attribute with specified TTL.
     * The attribute is removed when TTL expires.
     * <p>
     * Example:
     *
     * <pre>{@code
     * repository.publishAttribute(Val.of("device-abc"), "device.temperature", Val.of(22.5), Duration.ofSeconds(60))
     *         .subscribe();
     * }</pre>
     *
     * @param entity the entity context for this attribute
     * @param attributeName the fully qualified attribute name
     * @param value the attribute value to publish
     * @param ttl time-to-live duration
     * @return a Mono that completes when the attribute is published
     */
    default Mono<Void> publishAttribute(Value entity, String attributeName, Value value, Duration ttl) {
        return publishAttribute(entity, attributeName, List.of(), value, ttl, TimeOutStrategy.REMOVE);
    }

    /**
     * Publishes an entity-specific attribute with TTL and timeout strategy.
     * Provides full control over entity-bound attributes without arguments.
     * <p>
     * Example:
     *
     * <pre>{@code
     * repository.publishAttribute(Val.of("session-xyz"), "session.valid", Val.of(true), Duration.ofHours(1),
     *         TimeOutStrategy.BECOME_UNDEFINED).subscribe();
     * }</pre>
     *
     * @param entity the entity context for this attribute
     * @param attributeName the fully qualified attribute name
     * @param value the attribute value to publish
     * @param ttl time-to-live duration
     * @param timeOutStrategy behavior when TTL expires
     * @return a Mono that completes when the attribute is published
     */
    default Mono<Void> publishAttribute(Value entity, String attributeName, Value value, Duration ttl,
            TimeOutStrategy timeOutStrategy) {
        return publishAttribute(entity, attributeName, List.of(), value, ttl, timeOutStrategy);
    }

    /**
     * Publishes a global attribute with arguments and infinite TTL.
     * Useful for parameterized attributes like computed values or cached results.
     * <p>
     * Example:
     *
     * <pre>{@code
     * repository.publishAttribute("calculation.result", List.of(Val.of(10), Val.of(20)), Val.of(30)).subscribe();
     * }</pre>
     *
     * @param attributeName the fully qualified attribute name
     * @param arguments the attribute arguments
     * @param value the attribute value to publish
     * @return a Mono that completes when the attribute is published
     */
    default Mono<Void> publishAttribute(String attributeName, List<Value> arguments, Value value) {
        return publishAttribute(null, attributeName, arguments, value, INFINITE, TimeOutStrategy.REMOVE);
    }

    /**
     * Publishes a global attribute with arguments and specified TTL.
     * The attribute is removed when TTL expires.
     * <p>
     * Example:
     *
     * <pre>{@code
     * repository.publishAttribute("cache.query", List.of(Val.of("SELECT * FROM users")), Val.of(resultSet),
     *         Duration.ofMinutes(5)).subscribe();
     * }</pre>
     *
     * @param attributeName the fully qualified attribute name
     * @param arguments the attribute arguments
     * @param value the attribute value to publish
     * @param ttl time-to-live duration
     * @return a Mono that completes when the attribute is published
     */
    default Mono<Void> publishAttribute(String attributeName, List<Value> arguments, Value value, Duration ttl) {
        return publishAttribute(null, attributeName, arguments, value, ttl, TimeOutStrategy.REMOVE);
    }

    /**
     * Publishes a global attribute with arguments, TTL, and timeout strategy.
     * Provides full control for parameterized global attributes.
     * <p>
     * Example:
     *
     * <pre>{@code
     * repository.publishAttribute("metrics.average", List.of(Val.of("cpu")), Val.of(75.3), Duration.ofSeconds(30),
     *         TimeOutStrategy.BECOME_UNDEFINED).subscribe();
     * }</pre>
     *
     * @param attributeName the fully qualified attribute name
     * @param arguments the attribute arguments
     * @param value the attribute value to publish
     * @param ttl time-to-live duration
     * @param timeOutStrategy behavior when TTL expires
     * @return a Mono that completes when the attribute is published
     */
    default Mono<Void> publishAttribute(String attributeName, List<Value> arguments, Value value, Duration ttl,
            TimeOutStrategy timeOutStrategy) {
        return publishAttribute(null, attributeName, arguments, value, ttl, timeOutStrategy);
    }

    /**
     * Removes an attribute from the repository. This is the primary method that all
     * convenience methods delegate to.
     * <p>
     * Removing an attribute causes all active subscribers to receive
     * ATTRIBUTE_UNAVAILABLE.
     * The attribute key is determined by the combination of entity, name, and
     * arguments.
     *
     * @param entity the entity context, or null for global attributes
     * @param attributeName the fully qualified attribute name
     * @param arguments the attribute arguments, or empty list if none
     * @return a Mono that completes when the attribute is removed
     */
    Mono<Void> removeAttribute(Value entity, String attributeName, List<Value> arguments);

    /**
     * Removes a global attribute without arguments.
     * <p>
     * Example:
     *
     * <pre>{@code
     * repository.removeAttribute("system.maintenanceMode").subscribe();
     * }</pre>
     *
     * @param attributeName the fully qualified attribute name
     * @return a Mono that completes when the attribute is removed
     */
    default Mono<Void> removeAttribute(String attributeName) {
        return removeAttribute(null, attributeName, List.of());
    }

    /**
     * Removes an entity-specific attribute without arguments.
     * <p>
     * Example:
     *
     * <pre>{@code
     * repository.removeAttribute(Val.of("user-123"), "user.role").subscribe();
     * }</pre>
     *
     * @param entity the entity context
     * @param attributeName the fully qualified attribute name
     * @return a Mono that completes when the attribute is removed
     */
    default Mono<Void> removeAttribute(Value entity, String attributeName) {
        return removeAttribute(entity, attributeName, List.of());
    }

    /**
     * Removes a global attribute with arguments.
     * <p>
     * Example:
     *
     * <pre>{@code
     * repository.removeAttribute("cache.query", List.of(Val.of("SELECT * FROM users"))).subscribe();
     * }</pre>
     *
     * @param attributeName the fully qualified attribute name
     * @param arguments the attribute arguments
     * @return a Mono that completes when the attribute is removed
     */
    default Mono<Void> removeAttribute(String attributeName, List<Value> arguments) {
        return removeAttribute(null, attributeName, arguments);
    }

}
