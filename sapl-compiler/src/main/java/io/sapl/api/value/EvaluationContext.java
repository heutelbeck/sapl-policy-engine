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
package io.sapl.api.value;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.NonNull;
import lombok.val;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record EvaluationContext(@NonNull Map<String, CompiledExpression> variables) {

    private static final String ACTION = "action";
    private static final String ENVIRONMENT = "environment";
    private static final String RESOURCE = "resource";
    private static final String SUBJECT = "subject";
    private static final List<String> RESERVED_IDENTIFIERS = List.of(ACTION, ENVIRONMENT, RESOURCE, SUBJECT);

    public EvaluationContext(AuthorizationSubscription authorizationSubscription) {
        this(new HashMap<>());
        variables.put(SUBJECT, authorizationSubscription.subject());
        variables.put(ACTION, authorizationSubscription.action());
        variables.put(RESOURCE, authorizationSubscription.resource());
        variables.put(ENVIRONMENT, authorizationSubscription.environment());
    }

    private EvaluationContext(EvaluationContext originalContext, String identifier, CompiledExpression value) {
        this(new HashMap<>());
        variables.putAll(originalContext.variables);
        if (RESERVED_IDENTIFIERS.contains(identifier)) {
            throw new PolicyEvaluationException("Identifier " + identifier + " is reserved.");
        }
        variables.put(identifier, value);
    }

    public CompiledExpression subject() {
        return get(SUBJECT);
    }

    public CompiledExpression action() {
        return get(ACTION);
    }

    public CompiledExpression resource() {
        return get(RESOURCE);
    }

    public CompiledExpression environment() {
        return get(ENVIRONMENT);
    }

    public CompiledExpression get(String identifier) {
        val value = variables.get(identifier);
        if (value == null) {
            return Value.UNDEFINED;
        }
        return value;
    }

    public EvaluationContext with(String identifier, Value value) {
        return new EvaluationContext(this, identifier, value);
    }

}
