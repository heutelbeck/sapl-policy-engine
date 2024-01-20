/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.variables;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.Maps;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;

public class VariableContext {

    private static final String SUBJECT = "subject";

    private static final String ACTION = "action";

    private static final String RESOURCE = "resource";

    private static final String ENVIRONMENT = "environment";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final Map<String, JsonNode> variables;

    public VariableContext(Map<String, JsonNode> environmentVariables) {
        variables = Maps.newHashMapWithExpectedSize(environmentVariables.size());
        environmentVariables.forEach((key, value) -> variables.put(key, value.deepCopy()));
    }

    public VariableContext withEnvironmentVariable(String identifier, JsonNode value) {
        return copy().putEnvironmentVariable(identifier, value);
    }

    public VariableContext forAuthorizationSubscription(AuthorizationSubscription authzSubscription) {
        return copy().loadSubscriptionVariables(authzSubscription);
    }

    private VariableContext loadSubscriptionVariables(AuthorizationSubscription authzSubscription) {
        if (authzSubscription.getSubject() != null) {
            variables.put(SUBJECT, authzSubscription.getSubject());
        } else {
            variables.put(SUBJECT, JSON.nullNode());
        }
        if (authzSubscription.getAction() != null) {
            variables.put(ACTION, authzSubscription.getAction());
        } else {
            variables.put(ACTION, JSON.nullNode());
        }
        if (authzSubscription.getResource() != null) {
            variables.put(RESOURCE, authzSubscription.getResource());
        } else {
            variables.put(RESOURCE, JSON.nullNode());
        }
        if (authzSubscription.getEnvironment() != null) {
            variables.put(ENVIRONMENT, authzSubscription.getEnvironment());
        } else {
            variables.put(ENVIRONMENT, JSON.nullNode());
        }
        return this;
    }

    private VariableContext putEnvironmentVariable(String identifier, JsonNode value) {
        if (SUBJECT.equals(identifier) || RESOURCE.equals(identifier) || ACTION.equals(identifier)
                || ENVIRONMENT.equals(identifier)) {
            throw new PolicyEvaluationException("cannot overwrite request variable: %s", identifier);
        }
        variables.put(identifier, value);
        return this;
    }

    public Map<String, JsonNode> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    public boolean exists(String identifier) {
        return variables.containsKey(identifier);
    }

    public Val get(String identifier) {
        if (!variables.containsKey(identifier)) {
            return Val.UNDEFINED;
        }
        return Val.of(variables.get(identifier));
    }

    /**
     * @return a deep copy of this variable's context.
     */
    private VariableContext copy() {
        var variablesCopy = new HashMap<String, JsonNode>();
        variables.forEach((key, value) -> variablesCopy.put(key, value.deepCopy()));
        return new VariableContext(variablesCopy);
    }

}
