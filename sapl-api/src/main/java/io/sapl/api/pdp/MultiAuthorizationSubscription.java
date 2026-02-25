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
package io.sapl.api.pdp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import lombok.NonNull;
import lombok.val;

/**
 * A container for multiple authorization subscriptions, each identified by a
 * unique ID. This enables batch authorization requests where multiple access
 * control decisions can be requested and correlated with their subscriptions.
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * var multiSubscription = new MultiAuthorizationSubscription()
 *         .addSubscription("read-file", new AuthorizationSubscription(subject, readAction, fileResource, env))
 *         .addSubscription("write-file", new AuthorizationSubscription(subject, writeAction, fileResource, env));
 *
 * pdp.decide(multiSubscription).subscribe(decision -> {
 *     // decision.subscriptionId() correlates with the subscription
 * });
 * }</pre>
 */
public class MultiAuthorizationSubscription implements Iterable<IdentifiableAuthorizationSubscription> {

    private static final ObjectMapper DEFAULT_MAPPER = JsonMapper.builder().build();

    private static final String ERROR_DUPLICATE_SUBSCRIPTION_ID   = "Cannot add two subscriptions with the same ID: %s.";
    private static final String ERROR_FIELD_CANNOT_BE_ERROR_VALUE = "%s cannot be an error value.";
    private static final String ERROR_FIELD_CANNOT_BE_UNDEFINED   = "%s cannot be undefined.";

    private final Map<String, AuthorizationSubscription> subscriptions = new HashMap<>();

    /**
     * Adds an authorization subscription with the given ID.
     *
     * @param subscriptionId unique identifier for this subscription
     * @param subscription the authorization subscription
     * @return this instance for method chaining
     * @throws IllegalArgumentException if a subscription with the same ID already
     * exists
     */
    public MultiAuthorizationSubscription addSubscription(@NonNull String subscriptionId,
            @NonNull AuthorizationSubscription subscription) {
        if (subscriptions.containsKey(subscriptionId)) {
            throw new IllegalArgumentException(ERROR_DUPLICATE_SUBSCRIPTION_ID.formatted(subscriptionId));
        }
        subscriptions.put(subscriptionId, subscription);
        return this;
    }

    /**
     * Adds an authorization subscription with the given ID, constructed from
     * individual Value components. Values must not be undefined or error values.
     *
     * @param subscriptionId unique identifier for this subscription
     * @param subject the subject requesting access
     * @param action the action being requested
     * @param resource the resource being accessed
     * @param environment contextual environment data
     * @return this instance for method chaining
     * @throws IllegalArgumentException if a subscription with the same ID already
     * exists, or if any Value is undefined or an
     * error
     */
    public MultiAuthorizationSubscription addAuthorizationSubscription(@NonNull String subscriptionId,
            @NonNull Value subject, @NonNull Value action, @NonNull Value resource, @NonNull Value environment) {
        return addAuthorizationSubscription(subscriptionId, subject, action, resource, environment, Value.EMPTY_OBJECT);
    }

    /**
     * Adds an authorization subscription with the given ID, constructed from
     * individual Value components including secrets. Values must not be undefined
     * or
     * error values.
     *
     * @param subscriptionId unique identifier for this subscription
     * @param subject the subject requesting access
     * @param action the action being requested
     * @param resource the resource being accessed
     * @param environment contextual environment data
     * @param secrets secrets needed for policy evaluation (never logged)
     * @return this instance for method chaining
     * @throws IllegalArgumentException if a subscription with the same ID already
     * exists, or if any Value is undefined or an
     * error
     */
    public MultiAuthorizationSubscription addAuthorizationSubscription(@NonNull String subscriptionId,
            @NonNull Value subject, @NonNull Value action, @NonNull Value resource, @NonNull Value environment,
            @NonNull ObjectValue secrets) {
        validateValue(subject, "Subject");
        validateValue(action, "Action");
        validateValue(resource, "Resource");
        validateNotError(environment, "Environment");
        return addSubscription(subscriptionId,
                new AuthorizationSubscription(subject, action, resource, environment, secrets));
    }

    /**
     * Adds an authorization subscription with the given ID, constructed from
     * arbitrary objects. The objects are marshaled using a default ObjectMapper
     * with Jdk8Module registered. Environment defaults to UNDEFINED.
     *
     * @param subscriptionId unique identifier for this subscription
     * @param subject an object describing the subject
     * @param action an object describing the action
     * @param resource an object describing the resource
     * @return this instance for method chaining
     * @throws IllegalArgumentException if a subscription with the same ID already
     * exists
     */
    public MultiAuthorizationSubscription addAuthorizationSubscription(@NonNull String subscriptionId, Object subject,
            Object action, Object resource) {
        return addAuthorizationSubscription(subscriptionId, subject, action, resource, null, DEFAULT_MAPPER);
    }

    /**
     * Adds an authorization subscription with the given ID, constructed from
     * arbitrary objects. The objects are marshaled using the supplied ObjectMapper.
     * Environment defaults to UNDEFINED.
     *
     * @param subscriptionId unique identifier for this subscription
     * @param subject an object describing the subject
     * @param action an object describing the action
     * @param resource an object describing the resource
     * @param mapper the ObjectMapper to be used for marshaling
     * @return this instance for method chaining
     * @throws IllegalArgumentException if a subscription with the same ID already
     * exists
     */
    public MultiAuthorizationSubscription addAuthorizationSubscription(@NonNull String subscriptionId, Object subject,
            Object action, Object resource, ObjectMapper mapper) {
        return addAuthorizationSubscription(subscriptionId, subject, action, resource, null, mapper);
    }

    /**
     * Adds an authorization subscription with the given ID, constructed from
     * arbitrary objects including environment. The objects are marshaled using a
     * default ObjectMapper with Jdk8Module registered.
     *
     * @param subscriptionId unique identifier for this subscription
     * @param subject an object describing the subject
     * @param action an object describing the action
     * @param resource an object describing the resource
     * @param environment an object describing the environment (null becomes
     * UNDEFINED)
     * @return this instance for method chaining
     * @throws IllegalArgumentException if a subscription with the same ID already
     * exists
     */
    public MultiAuthorizationSubscription addAuthorizationSubscription(@NonNull String subscriptionId, Object subject,
            Object action, Object resource, Object environment) {
        return addAuthorizationSubscription(subscriptionId, subject, action, resource, environment, DEFAULT_MAPPER);
    }

    /**
     * Adds an authorization subscription with the given ID, constructed from
     * arbitrary objects including environment. The objects are marshaled using the
     * supplied ObjectMapper.
     *
     * @param subscriptionId unique identifier for this subscription
     * @param subject an object describing the subject
     * @param action an object describing the action
     * @param resource an object describing the resource
     * @param environment an object describing the environment (null becomes
     * UNDEFINED)
     * @param mapper the ObjectMapper to be used for marshaling
     * @return this instance for method chaining
     * @throws IllegalArgumentException if a subscription with the same ID already
     * exists
     */
    public MultiAuthorizationSubscription addAuthorizationSubscription(@NonNull String subscriptionId, Object subject,
            Object action, Object resource, Object environment, ObjectMapper mapper) {
        return addAuthorizationSubscription(subscriptionId, subject, action, resource, environment, null, mapper);
    }

    /**
     * Adds an authorization subscription with the given ID, constructed from
     * arbitrary objects including environment and secrets. The objects are
     * marshaled
     * using a default ObjectMapper with Jdk8Module registered.
     *
     * @param subscriptionId unique identifier for this subscription
     * @param subject an object describing the subject
     * @param action an object describing the action
     * @param resource an object describing the resource
     * @param environment an object describing the environment (null becomes
     * UNDEFINED)
     * @param secrets an object describing secrets needed for policy evaluation
     * (null
     * becomes empty; never logged)
     * @return this instance for method chaining
     * @throws IllegalArgumentException if a subscription with the same ID already
     * exists
     */
    public MultiAuthorizationSubscription addAuthorizationSubscription(@NonNull String subscriptionId, Object subject,
            Object action, Object resource, Object environment, Object secrets) {
        return addAuthorizationSubscription(subscriptionId, subject, action, resource, environment, secrets,
                DEFAULT_MAPPER);
    }

    /**
     * Adds an authorization subscription with the given ID, constructed from
     * arbitrary objects including environment and secrets. The objects are
     * marshaled
     * using the supplied ObjectMapper.
     *
     * @param subscriptionId unique identifier for this subscription
     * @param subject an object describing the subject
     * @param action an object describing the action
     * @param resource an object describing the resource
     * @param environment an object describing the environment (null becomes
     * UNDEFINED)
     * @param secrets an object describing secrets needed for policy evaluation
     * (null
     * becomes empty; never logged)
     * @param mapper the ObjectMapper to be used for marshaling
     * @return this instance for method chaining
     * @throws IllegalArgumentException if a subscription with the same ID already
     * exists
     */
    public MultiAuthorizationSubscription addAuthorizationSubscription(@NonNull String subscriptionId, Object subject,
            Object action, Object resource, Object environment, Object secrets, ObjectMapper mapper) {
        return addSubscription(subscriptionId,
                AuthorizationSubscription.of(subject, action, resource, environment, secrets, mapper));
    }

    private static void validateValue(Value value, String fieldName) {
        if (value instanceof UndefinedValue) {
            throw new IllegalArgumentException(ERROR_FIELD_CANNOT_BE_UNDEFINED.formatted(fieldName));
        }
        validateNotError(value, fieldName);
    }

    private static void validateNotError(Value value, String fieldName) {
        if (value instanceof ErrorValue) {
            throw new IllegalArgumentException(ERROR_FIELD_CANNOT_BE_ERROR_VALUE.formatted(fieldName));
        }
    }

    /**
     * Returns the subscription with the given ID, or null if not found.
     *
     * @param subscriptionId the subscription ID
     *
     * @return the subscription or null
     */
    public AuthorizationSubscription getSubscription(String subscriptionId) {
        return subscriptions.get(subscriptionId);
    }

    /**
     * Returns true if this container has at least one subscription.
     *
     * @return true if not empty
     */
    public boolean hasSubscriptions() {
        return !subscriptions.isEmpty();
    }

    /**
     * Returns the number of subscriptions in this container.
     *
     * @return subscription count
     */
    public int size() {
        return subscriptions.size();
    }

    @Override
    public @NonNull Iterator<IdentifiableAuthorizationSubscription> iterator() {
        val entryIterator = subscriptions.entrySet().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return entryIterator.hasNext();
            }

            @Override
            public IdentifiableAuthorizationSubscription next() {
                val entry = entryIterator.next();
                return new IdentifiableAuthorizationSubscription(entry.getKey(), entry.getValue());
            }
        };
    }

    @Override
    public String toString() {
        val builder = new StringBuilder("MultiAuthorizationSubscription {");
        for (val subscription : this) {
            builder.append("\n\t[ID: ").append(subscription.subscriptionId()).append(" | ")
                    .append(subscription.subscription()).append(']');
        }
        builder.append("\n}");
        return builder.toString();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MultiAuthorizationSubscription other)) {
            return false;
        }
        return Objects.equals(subscriptions, other.subscriptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subscriptions);
    }
}
