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
package io.sapl.api.model;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.NonNull;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static io.sapl.api.model.ReservedIdentifiers.*;

/**
 * Immutable context for policy evaluation containing all runtime dependencies.
 * <p>
 * Provides access to:
 * <ul>
 * <li>PDP and subscription identification</li>
 * <li>Authorization subscription data (subject, action, resource,
 * environment)</li>
 * <li>Policy variables</li>
 * <li>Function and attribute brokers for expression evaluation</li>
 * <li>Timestamp supplier for traced decisions</li>
 * </ul>
 */
public record EvaluationContext(
        String pdpId,
        String configurationId,
        String subscriptionId,
        AuthorizationSubscription authorizationSubscription,
        @NonNull Map<String, Value> variables,
        FunctionBroker functionBroker,
        AttributeBroker attributeBroker,
        @NonNull Supplier<String> timestampSupplier) {

    private static final Supplier<String> DEFAULT_TIMESTAMP_SUPPLIER = () -> Instant.now().toString();

    /**
     * Creates an evaluation context with default timestamp supplier
     * (Instant.now()).
     */
    public EvaluationContext(String pdpId,
            String configurationId,
            String subscriptionId,
            AuthorizationSubscription authorizationSubscription,
            FunctionBroker functionBroker,
            AttributeBroker attributeBroker) {
        this(pdpId, configurationId, subscriptionId, authorizationSubscription,
                subscriptionVariables(authorizationSubscription), functionBroker, attributeBroker,
                DEFAULT_TIMESTAMP_SUPPLIER);
    }

    /**
     * Creates an evaluation context with default timestamp supplier
     * (Instant.now()).
     */
    public static EvaluationContext of(String pdpId, String configurationId, String subscriptionId,
            AuthorizationSubscription authorizationSubscription, Map<String, Value> pdpVariables,
            FunctionBroker functionBroker, AttributeBroker attributeBroker) {
        return new EvaluationContext(pdpId, configurationId, subscriptionId, authorizationSubscription,
                subscriptionVariablesWithAdditions(authorizationSubscription, pdpVariables), functionBroker,
                attributeBroker, DEFAULT_TIMESTAMP_SUPPLIER);
    }

    /**
     * Creates an evaluation context with custom timestamp supplier. Use this with
     * LazyFastClock for high-throughput
     * scenarios.
     *
     * @param timestampSupplier
     * supplies ISO-8601 formatted timestamps for traced decisions
     */
    public static EvaluationContext of(String pdpId, String configurationId, String subscriptionId,
            AuthorizationSubscription authorizationSubscription, Map<String, Value> pdpVariables,
            FunctionBroker functionBroker, AttributeBroker attributeBroker, Supplier<String> timestampSupplier) {
        return new EvaluationContext(pdpId, configurationId, subscriptionId, authorizationSubscription,
                subscriptionVariablesWithAdditions(authorizationSubscription, pdpVariables), functionBroker,
                attributeBroker, timestampSupplier);
    }

    /**
     * Creates an evaluation context without pdpId (legacy compatibility).
     */
    public EvaluationContext(String configurationId,
            String subscriptionId,
            AuthorizationSubscription authorizationSubscription,
            FunctionBroker functionBroker,
            AttributeBroker attributeBroker) {
        this(null, configurationId, subscriptionId, authorizationSubscription, functionBroker, attributeBroker);
    }

    private EvaluationContext(EvaluationContext originalContext, Map<String, Value> additionalVariables) {
        this(originalContext.pdpId, originalContext.configurationId, originalContext.subscriptionId,
                originalContext.authorizationSubscription,
                copyWithAdditions(originalContext.variables, additionalVariables), originalContext.functionBroker,
                originalContext.attributeBroker, originalContext.timestampSupplier);
    }

    /**
     * Returns the current timestamp from the configured supplier. For traced
     * decisions, this captures when the decision
     * was finalized.
     *
     * @return ISO-8601 formatted timestamp string
     */
    public String timestamp() {
        return timestampSupplier.get();
    }

    private static Map<String, Value> subscriptionVariables(AuthorizationSubscription subscription) {
        var variables = new HashMap<String, Value>();
        if (subscription != null) {
            variables.put(SUBJECT, subscription.subject());
            variables.put(ACTION, subscription.action());
            variables.put(RESOURCE, subscription.resource());
            variables.put(ENVIRONMENT, subscription.environment());
        }
        return variables;
    }

    private static Map<String, Value> subscriptionVariablesWithAdditions(AuthorizationSubscription subscription,
            Map<String, Value> additions) {
        var variables = subscriptionVariables(subscription);
        if (additions != null) {
            variables.putAll(additions);
        }
        return variables;
    }

    private static Map<String, Value> copyWithAdditions(Map<String, Value> original, Map<String, Value> additions) {
        var copy = new HashMap<>(original);
        copy.putAll(additions);
        return copy;
    }

    public Value subject() {
        return get(SUBJECT);
    }

    public Value action() {
        return get(ACTION);
    }

    public Value resource() {
        return get(RESOURCE);
    }

    public Value relativeValue() {
        return get(RELATIVE_VALUE);
    }

    public Value relativeLocation() {
        return get(RELATIVE_LOCATION);
    }

    public Value get(String identifier) {
        return variables.getOrDefault(identifier, Value.UNDEFINED);
    }

    public EvaluationContext withRelativeValue(Value relativeValue, Value relativeLocation) {
        return new EvaluationContext(this, Map.of(RELATIVE_VALUE, relativeValue, RELATIVE_LOCATION, relativeLocation));
    }

    public EvaluationContext withRelativeValue(Value relativeValue) {
        return new EvaluationContext(this, Map.of(RELATIVE_VALUE, relativeValue, RELATIVE_LOCATION, Value.UNDEFINED));
    }

    public EvaluationContext with(String identifier, Value value) {
        if (RESERVED_IDENTIFIERS.contains(identifier)) {
            throw new IllegalArgumentException("Identifier '%s' is reserved.".formatted(identifier));
        }
        return new EvaluationContext(this, Map.of(identifier, value));
    }
}
