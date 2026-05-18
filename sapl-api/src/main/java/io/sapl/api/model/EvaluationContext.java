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
package io.sapl.api.model;

import java.util.Map;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.NonNull;

import org.jspecify.annotations.Nullable;

import static io.sapl.api.model.ReservedIdentifiers.*;

public record EvaluationContext(
        @NonNull String pdpId,
        @NonNull String configurationId,
        @NonNull String subscriptionId,
        @NonNull AuthorizationSubscription authorizationSubscription,
        @NonNull FunctionBroker functionBroker,
        @NonNull Value relativeValue,
        @NonNull Value relativeLocation,
        @NonNull Map<SubscriptionKey, AttributeSnapshot> snapshot) {

    /**
     * Convenience constructor matching the pre-snapshot field shape.
     * The snapshot defaults to an empty map so existing callers do not
     * need to thread snapshot state through. The trigger loop uses the
     * canonical 8-arg constructor to bind a populated snapshot.
     */
    public EvaluationContext(@NonNull String pdpId,
            @NonNull String configurationId,
            @NonNull String subscriptionId,
            @NonNull AuthorizationSubscription authorizationSubscription,
            @NonNull FunctionBroker functionBroker,
            @NonNull Value relativeValue,
            @NonNull Value relativeLocation) {
        this(pdpId, configurationId, subscriptionId, authorizationSubscription, functionBroker, relativeValue,
                relativeLocation, Map.of());
    }

    private EvaluationContext(String pdpId,
            String configurationId,
            String subscriptionId,
            AuthorizationSubscription authorizationSubscription,
            FunctionBroker functionBroker) {
        this(pdpId, configurationId, subscriptionId, authorizationSubscription, functionBroker, Value.UNDEFINED,
                Value.UNDEFINED, Map.of());
    }

    public static EvaluationContext of(String pdpId, String configurationId, String subscriptionId,
            AuthorizationSubscription authorizationSubscription, FunctionBroker functionBroker) {
        return new EvaluationContext(pdpId, configurationId, subscriptionId, authorizationSubscription, functionBroker);
    }

    public Value subject() {
        return authorizationSubscription.subject();
    }

    public Value action() {
        return authorizationSubscription.action();
    }

    public Value resource() {
        return authorizationSubscription.resource();
    }

    public Value environment() {
        return authorizationSubscription.environment();
    }

    public Value get(String identifier) {
        return switch (identifier) {
        case SUBJECT           -> subject();
        case ACTION            -> action();
        case RESOURCE          -> resource();
        case ENVIRONMENT       -> environment();
        case RELATIVE_LOCATION -> relativeLocation;
        case RELATIVE_VALUE    -> relativeValue;
        default                -> Value.UNDEFINED;
        };
    }

    public EvaluationContext withRelativeValue(Value relativeValue, Value relativeLocation) {
        return new EvaluationContext(pdpId, configurationId, subscriptionId, authorizationSubscription, functionBroker,
                relativeValue, relativeLocation, snapshot);
    }

    public EvaluationContext withRelativeValue(Value relativeValue) {
        return new EvaluationContext(pdpId, configurationId, subscriptionId, authorizationSubscription, functionBroker,
                relativeValue, Value.UNDEFINED, snapshot);
    }

    /**
     * Returns a new context with the given snapshot bound. Used by the
     * trigger loop between rounds: build a fresh map with the current
     * known attribute values, call this builder, hand the resulting
     * context to {@code evaluate(ctx)}.
     *
     * @param snapshot the new attribute snapshot to bind
     * @return a new context with the same request data and the
     * supplied snapshot
     */
    public EvaluationContext withSnapshot(Map<SubscriptionKey, AttributeSnapshot> snapshot) {
        return new EvaluationContext(pdpId, configurationId, subscriptionId, authorizationSubscription, functionBroker,
                relativeValue, relativeLocation, snapshot);
    }

    public @Nullable Value lookup(SubscriptionKey key) {
        var entry = snapshot.get(key);
        return entry == null ? null : entry.value();
    }
}
