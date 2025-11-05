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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BaseJsonNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.sapl.api.SaplVersion;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.io.Serializable;
import java.util.*;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

/**
 * A multi-subscription holds a list of subjects, a list of actions, a list of
 * resources, a list of environments (which are the elements of a
 * {@link AuthorizationSubscription SAPL authorization subscription}) and a map
 * holding subscription IDs and corresponding
 * {@link AuthorizationSubscriptionElements authorization subscription
 * elements}. It provides methods to
 * {@link #addAuthorizationSubscription(String, JsonNode, JsonNode, JsonNode, JsonNode)
 * add} single authorization subscriptions and to {@link #iterator() iterate}
 * over all the authorization subscriptions.
 *
 * @see AuthorizationSubscription
 */
@Data
@NoArgsConstructor
@JsonInclude(NON_EMPTY)
public class MultiAuthorizationSubscription implements Iterable<IdentifiableAuthorizationSubscription>, Serializable {

    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module());

    @NotEmpty
    ArrayList<BaseJsonNode> subjects = new ArrayList<>();

    @NotEmpty
    ArrayList<BaseJsonNode> actions = new ArrayList<>();

    @NotEmpty
    ArrayList<BaseJsonNode> resources = new ArrayList<>();

    ArrayList<BaseJsonNode> environments = new ArrayList<>();

    @NotEmpty
    HashMap<String, AuthorizationSubscriptionElements> authorizationSubscriptions = new HashMap<>();

    /**
     * Convenience method to add an authorization subscription without environment
     * data. Calls
     * {@code addAuthorizationSubscription(String, Object, Object, Object)
     * addAuthorizationSubscription(subscriptionId, subject, action, resource, null)}.
     *
     * @param subscriptionId the id identifying the authorization subscription to be
     * added.
     * @param subject the subject of the authorization subscription to be added.
     * @param action the action of the authorization subscription to be added.
     * @param resource the resource of the authorization subscription to be added.
     * @return this {@code MultiAuthorizationSubscription} instance to support
     * chaining of multiple calls to {@code addAuthorizationSubscription}.
     */
    public MultiAuthorizationSubscription addAuthorizationSubscription(String subscriptionId, JsonNode subject,
            JsonNode action, JsonNode resource) {
        return addAuthorizationSubscription(subscriptionId, subject, action, resource, null);
    }

    /**
     * Convenience method to add an authorization subscription without environment
     * data. Calls
     * {@link #addAuthorizationSubscription(String, JsonNode, JsonNode, JsonNode)
     * addAuthorizationSubscription(subscriptionId, subject, action, resource,
     * null)}.
     *
     * @param subscriptionId the id identifying the authorization subscription to be
     * added.
     * @param subscription an authorization subscription.
     * @return this {@code MultiAuthorizationSubscription} instance to support
     * chaining of multiple calls to {@code addAuthorizationSubscription}.
     */
    public MultiAuthorizationSubscription addAuthorizationSubscription(String subscriptionId,
            AuthorizationSubscription subscription) {
        return addAuthorizationSubscription(subscriptionId, subscription.getSubject(), subscription.getAction(),
                subscription.getResource(), subscription.getEnvironment());
    }

    /**
     * Adds the authorization subscription defined by the given subject, action,
     * resource and environment. The given {@code subscriptionId} is associated with
     * the according decision to allow the recipient of the PDP decision to
     * correlate subscription/decision pairs.
     *
     * @param subscriptionId the id identifying the authorization subscription to be
     * added.
     * @param subject the subject of the authorization subscription to be added.
     * @param action the action of the authorization subscription to be added.
     * @param resource the resource of the authorization subscription to be added.
     * @param environment the environment of the authorization subscription to be
     * added.
     * @return this {@code MultiAuthorizationSubscription} instance to support
     * chaining of multiple calls to {@code addAuthorizationSubscription}.
     */
    public MultiAuthorizationSubscription addAuthorizationSubscription(@NonNull String subscriptionId,
            @NonNull JsonNode subject, @NonNull JsonNode action, @NonNull JsonNode resource, JsonNode environment) {

        if (authorizationSubscriptions.containsKey(subscriptionId))
            throw new IllegalArgumentException("Cannot add two subscriptions with the same ID: " + subscriptionId);

        final var subjectId     = ensureIsElementOfListAndReturnIndex(subject, subjects);
        final var actionId      = ensureIsElementOfListAndReturnIndex(action, actions);
        final var resourceId    = ensureIsElementOfListAndReturnIndex(resource, resources);
        final var environmentId = ensureIsElementOfListAndReturnIndex(environment, environments);

        authorizationSubscriptions.put(subscriptionId,
                new AuthorizationSubscriptionElements(subjectId, actionId, resourceId, environmentId));
        return this;
    }

    private Integer ensureIsElementOfListAndReturnIndex(JsonNode element, List<BaseJsonNode> list) {
        if (element == null)
            return null;

        int index = list.indexOf(element);
        if (index == -1) {
            index = list.size();
            list.add((BaseJsonNode) element);
        }
        return index;
    }

    /**
     * @return {@code true} if this multi-subscription holds at least one
     * authorization subscription, {@code false} otherwise.
     */
    public boolean hasAuthorizationSubscriptions() {
        return !authorizationSubscriptions.isEmpty();
    }

    /**
     * Returns the authorization subscription related to the given ID or
     * {@code null} if this multi-subscription contains no such ID.
     *
     * @param subscriptionId the ID of the authorization subscription to be
     * returned.
     * @return the authorization subscription related to the given ID or
     * {@code null}.
     */
    public AuthorizationSubscription getAuthorizationSubscriptionWithId(String subscriptionId) {
        final var authorizationSubscription = authorizationSubscriptions.get(subscriptionId);
        if (authorizationSubscription == null) {
            return null;
        }
        return toAuthzSubscription(authorizationSubscription);
    }

    /**
     * @return an {@link Iterator iterator} providing access to the
     * {@link IdentifiableAuthorizationSubscription identifiable authorization
     * subscriptions} created from the data held by this multi-subscription.
     */
    @Override
    public Iterator<IdentifiableAuthorizationSubscription> iterator() {
        final Iterator<Map.Entry<String, AuthorizationSubscriptionElements>> subscriptionIterator = authorizationSubscriptions
                .entrySet().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return subscriptionIterator.hasNext();
            }

            @Override
            public IdentifiableAuthorizationSubscription next() {
                final Map.Entry<String, AuthorizationSubscriptionElements> entry                = subscriptionIterator
                        .next();
                final String                                               id                   = entry.getKey();
                final AuthorizationSubscriptionElements                    subscriptionElements = entry.getValue();
                final AuthorizationSubscription                            authzSubscription    = toAuthzSubscription(
                        subscriptionElements);
                return new IdentifiableAuthorizationSubscription(id, authzSubscription);
            }
        };
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MultiAuthorizationSubscription {");
        for (IdentifiableAuthorizationSubscription subscription : this) {
            sb.append("\n\t[").append("SUBSCRIPTION-ID: ").append(subscription.authorizationSubscriptionId())
                    .append(" | ").append("SUBJECT: ").append(subscription.authorizationSubscription().getSubject())
                    .append(" | ").append("ACTION: ").append(subscription.authorizationSubscription().getAction())
                    .append(" | ").append("RESOURCE: ").append(subscription.authorizationSubscription().getResource())
                    .append(" | ").append("ENVIRONMENT: ")
                    .append(subscription.authorizationSubscription().getEnvironment()).append(']');
        }
        sb.append("\n}");
        return sb.toString();
    }

    private AuthorizationSubscription toAuthzSubscription(
            @NonNull AuthorizationSubscriptionElements subscriptionElements) {
        final Object subject     = subjects.get(subscriptionElements.getSubjectId());
        final Object action      = actions.get(subscriptionElements.getActionId());
        final Object resource    = resources.get(subscriptionElements.getResourceId());
        final Object environment = subscriptionElements.getEnvironmentId() == null ? null
                : environments.get(subscriptionElements.getEnvironmentId());
        return new AuthorizationSubscription(MAPPER.valueToTree(subject), MAPPER.valueToTree(action),
                MAPPER.valueToTree(resource), environment == null ? null : MAPPER.valueToTree(environment));
    }

}
