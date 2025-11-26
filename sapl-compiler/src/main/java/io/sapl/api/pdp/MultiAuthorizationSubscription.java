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
package io.sapl.api.pdp;

import io.sapl.api.model.Value;
import lombok.NonNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A container for multiple authorization subscriptions, each identified by a
 * unique ID. This enables batch
 * authorization requests where multiple access control decisions can be
 * requested and correlated with their
 * subscriptions.
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

    private final Map<String, AuthorizationSubscription> subscriptions = new HashMap<>();

    /**
     * Adds an authorization subscription with the given ID.
     *
     * @param subscriptionId
     * unique identifier for this subscription
     * @param subscription
     * the authorization subscription
     *
     * @return this instance for method chaining
     *
     * @throws IllegalArgumentException
     * if a subscription with the same ID already exists
     */
    public MultiAuthorizationSubscription addSubscription(@NonNull String subscriptionId,
            @NonNull AuthorizationSubscription subscription) {
        if (subscriptions.containsKey(subscriptionId)) {
            throw new IllegalArgumentException("Cannot add two subscriptions with the same ID: " + subscriptionId);
        }
        subscriptions.put(subscriptionId, subscription);
        return this;
    }

    /**
     * Adds an authorization subscription with the given ID, constructed from
     * individual components.
     *
     * @param subscriptionId
     * unique identifier for this subscription
     * @param subject
     * the subject requesting access
     * @param action
     * the action being requested
     * @param resource
     * the resource being accessed
     * @param environment
     * contextual environment data
     *
     * @return this instance for method chaining
     *
     * @throws IllegalArgumentException
     * if a subscription with the same ID already exists
     */
    public MultiAuthorizationSubscription addSubscription(@NonNull String subscriptionId, @NonNull Value subject,
            @NonNull Value action, @NonNull Value resource, @NonNull Value environment) {
        return addSubscription(subscriptionId, new AuthorizationSubscription(subject, action, resource, environment));
    }

    /**
     * Returns the subscription with the given ID, or null if not found.
     *
     * @param subscriptionId
     * the subscription ID
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
    public Iterator<IdentifiableAuthorizationSubscription> iterator() {
        var entryIterator = subscriptions.entrySet().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return entryIterator.hasNext();
            }

            @Override
            public IdentifiableAuthorizationSubscription next() {
                var entry = entryIterator.next();
                return new IdentifiableAuthorizationSubscription(entry.getKey(), entry.getValue());
            }
        };
    }

    @Override
    public String toString() {
        var builder = new StringBuilder("MultiAuthorizationSubscription {");
        for (var subscription : this) {
            builder.append("\n\t[ID: ").append(subscription.subscriptionId()).append(" | SUBJECT: ")
                    .append(subscription.subscription().subject()).append(" | ACTION: ")
                    .append(subscription.subscription().action()).append(" | RESOURCE: ")
                    .append(subscription.subscription().resource()).append(" | ENVIRONMENT: ")
                    .append(subscription.subscription().environment()).append(']');
        }
        builder.append("\n}");
        return builder.toString();
    }
}
