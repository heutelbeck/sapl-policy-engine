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
import com.google.common.collect.Maps;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;

public class VariableContext {

    private static final String SUBJECT = "subject";

    private static final String ACTION = "action";

    private static final String RESOURCE = "resource";

    private static final String ENVIRONMENT = "environment";

    private final Map<String, Val> variables;

    public VariableContext(Map<String, Val> environmentVariables) {
        variables = Maps.newHashMapWithExpectedSize(environmentVariables.size());
        variables.putAll(environmentVariables);
    }

    public VariableContext withEnvironmentVariable(String identifier, JsonNode value) {
        return copy().putEnvironmentVariable(identifier, value, false);
    }

    public VariableContext withSecretEnvironmentVariable(String identifier, JsonNode value) {
        return copy().putEnvironmentVariable(identifier, value, true);
    }

    public VariableContext forAuthorizationSubscription(AuthorizationSubscription authzSubscription) {
        return copy().loadSubscriptionVariables(authzSubscription);
    }

    private VariableContext loadSubscriptionVariables(AuthorizationSubscription authzSubscription) {
        variables.put(SUBJECT, Val.of(authzSubscription.getSubject()));
        variables.put(ACTION, Val.of(authzSubscription.getAction()));
        variables.put(RESOURCE, Val.of(authzSubscription.getResource()));
        variables.put(ENVIRONMENT, Val.of(authzSubscription.getEnvironment()));
        return this;
    }

    private VariableContext putEnvironmentVariable(String identifier, JsonNode value, boolean isSecret) {
        if (SUBJECT.equals(identifier) || RESOURCE.equals(identifier) || ACTION.equals(identifier)
                || ENVIRONMENT.equals(identifier)) {
            throw new PolicyEvaluationException("cannot overwrite request variable: %s", identifier);
        }
        if (isSecret) {
            variables.put(identifier, Val.of(value).asSecret());
        } else {
            variables.put(identifier, Val.of(value));
        }
        return this;
    }

    public Map<String, Val> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    public boolean exists(String identifier) {
        return variables.containsKey(identifier);
    }

    public Val get(String identifier) {
        if (!variables.containsKey(identifier)) {
            return Val.UNDEFINED;
        }
        return variables.get(identifier);
    }

    /**
     * @return a deep copy of this variable's context.
     */
    private VariableContext copy() {
        final var variablesCopy = new HashMap<String, Val>();
        variablesCopy.putAll(variables);
        return new VariableContext(variablesCopy);
    }

}
