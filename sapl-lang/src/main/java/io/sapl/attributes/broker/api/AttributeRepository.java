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
package io.sapl.attributes.broker.api;

import io.sapl.api.interpreter.Val;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Repository for publishing and retrieving dynamic attributes that can be used
 * in SAPL policy evaluation. Attributes can be published with time-to-live
 * settings and removal strategies.
 */
public interface AttributeRepository extends AttributeFinder {
    Duration INFINITE = Duration.ofSeconds(Long.MAX_VALUE, 999999999L);

    /**
     * Defines how an attribute behaves when its time-to-live expires.
     */
    enum TimeOutStrategy {
        /**
         * Removes the attribute entirely from the repository when TTL expires.
         */
        REMOVE,
        /**
         * Keeps the attribute but sets its value to undefined when TTL expires.
         */
        BECOME_UNDEFINED
    }

    /**
     * Publishes an attribute with full control over entity, arguments, TTL, and
     * timeout behavior. This is the main method that all convenience methods
     * delegate to.
     *
     * @param entity the entity context for this attribute, or null for global
     * attributes
     * @param attributeName the fully qualified attribute name
     * @param arguments the attribute arguments, or empty list if none
     * @param value the attribute value to publish
     * @param ttl time-to-live duration after which the timeout strategy is applied
     * @param timeOutStrategy behavior when TTL expires
     */
    Mono<Void> publishAttribute(Val entity, String attributeName, List<Val> arguments, Val value, Duration ttl,
                                TimeOutStrategy timeOutStrategy);

    /**
     * Publishes a global attribute with infinite TTL and removal strategy.
     * Suitable for static configuration values or long-lived attributes.
     * <p/>
     * Example: repository.publishAttribute("system.maxUsers", Val.of(1000));
     *
     * @param attributeName the fully qualified attribute name
     * @param value the attribute value to publish
     */
    default Mono<Void> publishAttribute(String attributeName, Val value) {
        return publishAttribute(null, attributeName, List.of(), value, INFINITE, TimeOutStrategy.REMOVE);
    }

    /**
     * Publishes a global attribute with specified TTL and removal strategy.
     * When TTL expires, the attribute is removed from the repository.
     * <p/>
     * Example: repository.publishAttribute("cache.token", Val.of(token),
     * Duration.ofMinutes(15));
     *
     * @param attributeName the fully qualified attribute name
     * @param value the attribute value to publish
     * @param ttl time-to-live duration
     */
    default Mono<Void> publishAttribute(String attributeName, Val value, Duration ttl) {
        return  publishAttribute(null, attributeName, List.of(), value, ttl, TimeOutStrategy.REMOVE);
    }

    /**
     * Publishes a global attribute with specified TTL and timeout strategy.
     * Allows control over whether the attribute is removed or becomes undefined
     * when
     * TTL expires.
     * <p/>
     * Example: repository.publishAttribute("session.status", Val.of("active"),
     * Duration.ofMinutes(30), TimeOutStrategy.BECOME_UNDEFINED);
     *
     * @param attributeName the fully qualified attribute name
     * @param value the attribute value to publish
     * @param ttl time-to-live duration
     * @param timeOutStrategy behavior when TTL expires
     */
    default Mono<Void> publishAttribute(String attributeName, Val value, Duration ttl, TimeOutStrategy timeOutStrategy) {
        return publishAttribute(null, attributeName, List.of(), value, ttl, timeOutStrategy);
    }

    /**
     * Publishes an entity-specific attribute with infinite TTL.
     * Useful for associating attributes with specific entities like users or
     * resources.
     * <p/>
     * Example: repository.publishAttribute(Val.of("user:123"), "user.role",
     * Val.of("admin"));
     *
     * @param entity the entity context for this attribute
     * @param attributeName the fully qualified attribute name
     * @param value the attribute value to publish
     */
    default Mono<Void> publishAttribute(Val entity, String attributeName, Val value) {
        return publishAttribute(entity, attributeName, List.of(), value, INFINITE, TimeOutStrategy.REMOVE);
    }

    /**
     * Publishes an entity-specific attribute with specified TTL.
     * The attribute is removed when TTL expires.
     * <p/>
     * Example: repository.publishAttribute(Val.of("device:abc"),
     * "device.temperature", Val.of(22.5), Duration.ofSeconds(60));
     *
     * @param entity the entity context for this attribute
     * @param attributeName the fully qualified attribute name
     * @param value the attribute value to publish
     * @param ttl time-to-live duration
     */
    default Mono<Void> publishAttribute(Val entity, String attributeName, Val value, Duration ttl) {
        return publishAttribute(entity, attributeName, List.of(), value, ttl, TimeOutStrategy.REMOVE);
    }

    /**
     * Publishes an entity-specific attribute with TTL and timeout strategy.
     * Provides full control over entity-bound attributes without arguments.
     * <p/>
     * Example: repository.publishAttribute(Val.of("session:xyz"), "session.valid",
     * Val.of(true), Duration.ofHours(1), TimeOutStrategy.BECOME_UNDEFINED);
     *
     * @param entity the entity context for this attribute
     * @param attributeName the fully qualified attribute name
     * @param value the attribute value to publish
     * @param ttl time-to-live duration
     * @param timeOutStrategy behavior when TTL expires
     */
    default Mono<Void> publishAttribute(Val entity, String attributeName, Val value, Duration ttl,
            TimeOutStrategy timeOutStrategy) {
        return publishAttribute(entity, attributeName, List.of(), value, ttl, timeOutStrategy);
    }

    /**
     * Publishes a global attribute with arguments and infinite TTL.
     * Useful for parameterized attributes like computed values.
     * <p/>
     * Example: repository.publishAttribute("calculation.result",
     * List.of(Val.of(10),
     * Val.of(20)), Val.of(30));
     *
     * @param attributeName the fully qualified attribute name
     * @param arguments the attribute arguments
     * @param value the attribute value to publish
     */
    default Mono<Void> publishAttribute(String attributeName, List<Val> arguments, Val value) {
        return publishAttribute(null, attributeName, arguments, value, INFINITE, TimeOutStrategy.REMOVE);
    }

    /**
     * Publishes a global attribute with arguments and specified TTL.
     * The attribute is removed when TTL expires.
     * <p/>
     * Example: repository.publishAttribute("cache.query", List.of(Val.of("SELECT *
     * FROM users")), Val.of(resultSet), Duration.ofMinutes(5));
     *
     * @param attributeName the fully qualified attribute name
     * @param arguments the attribute arguments
     * @param value the attribute value to publish
     * @param ttl time-to-live duration
     */
    default Mono<Void> publishAttribute(String attributeName, List<Val> arguments, Val value, Duration ttl) {
        return publishAttribute(null, attributeName, arguments, value, ttl, TimeOutStrategy.REMOVE);
    }

    /**
     * Publishes a global attribute with arguments, TTL, and timeout strategy.
     * Provides full control for parameterized global attributes.
     * <p/>
     * Example: repository.publishAttribute("metrics.average",
     * List.of(Val.of("cpu")), Val.of(75.3), Duration.ofSeconds(30),
     * TimeOutStrategy.BECOME_UNDEFINED);
     *
     * @param attributeName the fully qualified attribute name
     * @param arguments the attribute arguments
     * @param value the attribute value to publish
     * @param ttl time-to-live duration
     * @param timeOutStrategy behavior when TTL expires
     */
    default Mono<Void> publishAttribute(String attributeName, List<Val> arguments, Val value, Duration ttl,
            TimeOutStrategy timeOutStrategy) {
        return publishAttribute(null, attributeName, arguments, value, ttl, timeOutStrategy);
    }

    /**
     * Removes an attribute from the repository. This is the main method that all
     * convenience methods delegate to.
     *
     * @param entity the entity context, or null for global attributes
     * @param attributeName the fully qualified attribute name
     * @param arguments the attribute arguments, or empty list if none
     */
    Mono<Void> removeAttribute(Val entity, String attributeName, List<Val> arguments);

    /**
     * Removes a global attribute without arguments.
     *
     * @param attributeName the fully qualified attribute name
     */
    default Mono<Void> removeAttribute(String attributeName) {
        return removeAttribute(null, attributeName, List.of());
    }

    /**
     * Removes an entity-specific attribute without arguments.
     *
     * @param entity the entity context
     * @param attributeName the fully qualified attribute name
     */
    default Mono<Void> removeAttribute(Val entity, String attributeName) {
        return removeAttribute(entity, attributeName, List.of());
    }

    /**
     * Removes a global attribute with arguments.
     *
     * @param attributeName the fully qualified attribute name
     * @param arguments the attribute arguments
     */
    default Mono<Void> removeAttribute(String attributeName, List<Val> arguments) {
        return removeAttribute(null, attributeName, arguments);
    }

}
