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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.NonNull;

import static io.sapl.api.model.ReservedIdentifiers.*;

public record EvaluationContext(
        @NonNull String pdpId,
        @NonNull String configurationId,
        @NonNull String subscriptionId,
        @NonNull AuthorizationSubscription authorizationSubscription,
        @NonNull FunctionBroker functionBroker,
        @NonNull AttributeBroker attributeBroker,
        @NonNull Value relativeValue,
        @NonNull Value relativeLocation) {

    private EvaluationContext(String pdpId,
            String configurationId,
            String subscriptionId,
            AuthorizationSubscription authorizationSubscription,
            FunctionBroker functionBroker,
            AttributeBroker attributeBroker) {
        this(pdpId, configurationId, subscriptionId, authorizationSubscription, functionBroker, attributeBroker,
                Value.UNDEFINED, Value.UNDEFINED);
    }

    public static EvaluationContext of(String pdpId, String configurationId, String subscriptionId,
            AuthorizationSubscription authorizationSubscription, FunctionBroker functionBroker,
            AttributeBroker attributeBroker) {
        return new EvaluationContext(pdpId, configurationId, subscriptionId, authorizationSubscription, functionBroker,
                attributeBroker);
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
        case RESOURCE          -> resource();
        case ENVIRONMENT       -> environment();
        case RELATIVE_LOCATION -> relativeLocation;
        case RELATIVE_VALUE    -> relativeValue;
        default                -> Value.UNDEFINED;
        };
    }

    public EvaluationContext withRelativeValue(Value relativeValue, Value relativeLocation) {
        return new EvaluationContext(pdpId, configurationId, subscriptionId, authorizationSubscription, functionBroker,
                attributeBroker, relativeValue, relativeLocation);
    }

    public EvaluationContext withRelativeValue(Value relativeValue) {
        return new EvaluationContext(pdpId, configurationId, subscriptionId, authorizationSubscription, functionBroker,
                attributeBroker, relativeValue, Value.UNDEFINED);
    }

}
