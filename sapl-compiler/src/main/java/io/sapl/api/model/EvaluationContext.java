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

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.NonNull;
import lombok.val;

import java.util.HashMap;
import java.util.Map;

import static io.sapl.api.model.ReservedIdentifiers.*;

public record EvaluationContext(@NonNull Map<String, Value> variables, FunctionBroker functionBroker) {

    public EvaluationContext(AuthorizationSubscription authorizationSubscription, FunctionBroker pluginsServer) {
        this(new HashMap<>(), pluginsServer);
        variables.put(SUBJECT, authorizationSubscription.subject());
        variables.put(ACTION, authorizationSubscription.action());
        variables.put(RESOURCE, authorizationSubscription.resource());
        variables.put(ENVIRONMENT, authorizationSubscription.environment());
    }

    private EvaluationContext(EvaluationContext originalContext,
            String identifier,
            Value value,
            FunctionBroker pluginsServer) {
        this(new HashMap<>(), pluginsServer);
        variables.putAll(originalContext.variables);
        variables.put(identifier, value);
    }

    private EvaluationContext(EvaluationContext originalContext,
            Value relativeValue,
            Value relativeLocation,
            FunctionBroker pluginsServer) {
        this(new HashMap<>(), pluginsServer);
        variables.putAll(originalContext.variables);
        variables.put(RELATIVE_VALUE, relativeValue);
        variables.put(RELATIVE_LOCATION, relativeLocation);
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
        val value = variables.get(identifier);
        if (value == null) {
            return Value.UNDEFINED;
        }
        return value;
    }

    public EvaluationContext withRelativeValue(Value relativeValue, Value relativeLocation) {
        return new EvaluationContext(this, relativeValue, relativeLocation, functionBroker);
    }

    public EvaluationContext withRelativeValue(Value relativeValue) {
        return new EvaluationContext(this, relativeValue, Value.UNDEFINED, functionBroker);
    }

    public EvaluationContext with(String identifier, Value value) {
        if (RESERVED_IDENTIFIERS.contains(identifier)) {
            throw new PolicyEvaluationException("Identifier " + identifier + " is reserved.");
        }
        return new EvaluationContext(this, identifier, value, functionBroker);
    }

}
