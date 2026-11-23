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
import lombok.val;

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
        @NonNull Map<SubscriptionKey, AttributeSnapshot> snapshot,
        @Nullable EvaluationObserver observer) {

    /** Receives the result of a reached operand within one evaluation round. */
    @FunctionalInterface
    public interface EvaluationObserver {
        /**
         * @param parent the enclosing expression, or the operand for a direct evaluation
         * @param operand the expression that was evaluated
         * @param value the result, or null while waiting for an attribute
         */
        void evaluated(CompiledExpression parent, CompiledExpression operand, @Nullable Value value);

        /**
         * @param guard guard classified by the index
         * @param result resolved guard value
         */
        default void classified(CompiledExpression guard, Value result) {
            // Observers may subscribe to individual operand evaluations only.
        }
    }

    /**
     * @param pdpId PDP identifier
     * @param configurationId configuration identifier
     * @param subscriptionId subscription identifier
     * @param authorizationSubscription inputs of the authorization request
     * @param functionBroker registered functions
     * @param relativeValue current relative value
     * @param relativeLocation current relative location
     * @param snapshot attribute values available to this round
     */
    public EvaluationContext(String pdpId,
            String configurationId,
            String subscriptionId,
            AuthorizationSubscription authorizationSubscription,
            FunctionBroker functionBroker,
            Value relativeValue,
            Value relativeLocation,
            Map<SubscriptionKey, AttributeSnapshot> snapshot) {
        this(pdpId, configurationId, subscriptionId, authorizationSubscription, functionBroker, relativeValue,
                relativeLocation, snapshot, null);
    }

    /**
     * @param listener observer for just this evaluation
     * @return the same inputs with an observer attached
     */
    public EvaluationContext withObserver(EvaluationObserver listener) {
        return new EvaluationContext(pdpId, configurationId, subscriptionId, authorizationSubscription, functionBroker,
                relativeValue, relativeLocation, snapshot, listener);
    }

    /**
     * @param parent expression evaluating the operand
     * @param operand evaluated operand
     * @param result completed value, or null while pending
     */
    public void observe(CompiledExpression parent, CompiledExpression operand, @Nullable Value result) {
        if (observer != null) {
            observer.evaluated(parent, operand, result);
        }
    }

    /**
     * @param operand the evaluated guard or index predicate
     * @return its value for this subscription
     */
    public Value evaluate(PureOperator operand) {
        val value = operand.evaluate(this);
        observe(operand, operand, value);
        return value;
    }

    /**
     * Convenience constructor matching the pre-snapshot field shape.
     * The snapshot defaults to an empty map so existing callers do not
     * need to thread snapshot state through. The trigger loop uses the
     * snapshot constructor to bind a populated snapshot.
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
                relativeValue, relativeLocation, snapshot, observer);
    }

    public EvaluationContext withRelativeValue(Value relativeValue) {
        return new EvaluationContext(pdpId, configurationId, subscriptionId, authorizationSubscription, functionBroker,
                relativeValue, Value.UNDEFINED, snapshot, observer);
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
                relativeValue, relativeLocation, snapshot, observer);
    }

    public @Nullable Value lookup(SubscriptionKey key) {
        var entry = snapshot.get(key);
        return entry == null ? null : entry.value();
    }
}
