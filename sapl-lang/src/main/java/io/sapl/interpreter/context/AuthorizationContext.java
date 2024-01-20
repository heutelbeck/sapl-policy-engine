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
package io.sapl.interpreter.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

@UtilityClass
public class AuthorizationContext {

    static final String CANNOT_OVERWRITE_REQUEST_VARIABLE_S_ERROR = "Cannot overwrite request variable: %s";

    private static final String          INDEX         = "index";
    private static final String          KEY           = "key";
    private static final String          ATTRIBUTE_CTX = "attributeCtx";
    private static final String          FUNCTION_CTX  = "functionCtx";
    private static final String          VARIABLES     = "variables";
    private static final String          IMPORTS       = "imports";
    private static final String          SUBJECT       = "subject";
    private static final String          ACTION        = "action";
    private static final String          RESOURCE      = "resource";
    private static final String          ENVIRONMENT   = "environment";
    private static final String          RELATIVE_NODE = "relativeNode";
    private static final JsonNodeFactory JSON          = JsonNodeFactory.instance;

    public static Map<String, String> getImports(ContextView ctx) {
        return ctx.getOrDefault(IMPORTS, Collections.emptyMap());
    }

    public static Val getRelativeNode(ContextView ctx) {
        return ctx.getOrDefault(RELATIVE_NODE, Val.UNDEFINED);
    }

    public static Integer getIndex(ContextView ctx) {
        return ctx.get(INDEX);
    }

    public static String getKey(ContextView ctx) {
        return ctx.get(KEY);
    }

    public static Context setRelativeNode(Context ctx, Val relativeNode) {
        return ctx.put(RELATIVE_NODE, relativeNode);
    }

    public static Context setRelativeNodeWithIndex(Context ctx, Val relativeNode, Integer index) {
        return ctx.put(RELATIVE_NODE, relativeNode).put(INDEX, index);
    }

    public static Context setRelativeNodeWithKey(Context ctx, Val relativeNode, String key) {
        return ctx.put(RELATIVE_NODE, relativeNode).put(KEY, key);
    }

    public static AttributeContext getAttributeContext(ContextView ctx) {
        return ctx.get(ATTRIBUTE_CTX);
    }

    public Context setAttributeContext(Context ctx, AttributeContext attributeContext) {
        return ctx.put(ATTRIBUTE_CTX, attributeContext);
    }

    public static Context setVariables(@NonNull Context ctx, Map<String, JsonNode> environmentVariables) {
        Map<String, JsonNode> variables = new HashMap<>(ctx.getOrDefault(VARIABLES, new HashMap<>()));
        for (var variable : environmentVariables.entrySet()) {
            var name = variable.getKey();
            assertVariableNameNotReserved(name);
            variables.put(name, variable.getValue());
        }

        return ctx.put(VARIABLES, variables);
    }

    public Context setVariable(@NonNull Context ctx, String name, Val value) {
        assertVariableNameNotReserved(name);

        if (value.isError())
            throw new PolicyEvaluationException(value.getMessage());

        Map<String, JsonNode> variables = new HashMap<>(ctx.getOrDefault(VARIABLES, new HashMap<>()));

        if (value.isUndefined())
            variables.remove(name);
        else
            variables.put(name, value.get());
        return ctx.put(VARIABLES, variables);
    }

    private void assertVariableNameNotReserved(String name) {
        if (SUBJECT.equals(name) || RESOURCE.equals(name) || ACTION.equals(name) || ENVIRONMENT.equals(name)) {
            throw new PolicyEvaluationException(CANNOT_OVERWRITE_REQUEST_VARIABLE_S_ERROR, name);
        }
    }

    public Context setSubscriptionVariables(@NonNull Context ctx, AuthorizationSubscription authorizationSubscription) {

        Map<String, JsonNode> variables = new HashMap<>(
                Objects.requireNonNull(ctx.getOrDefault(VARIABLES, new HashMap<>())));

        variables.put(SUBJECT, authorizationSubscription.getSubject());
        variables.put(ACTION, authorizationSubscription.getAction());
        variables.put(RESOURCE, authorizationSubscription.getResource());
        if (authorizationSubscription.getEnvironment() != null) {
            variables.put(ENVIRONMENT, authorizationSubscription.getEnvironment());
        } else {
            variables.put(ENVIRONMENT, JSON.nullNode());
        }
        return ctx.put(VARIABLES, variables);
    }

    @SuppressWarnings("unchecked")
    // In this case the catch clause takes care of making it fail safe and solves
    // the runtime type erasure problem for this case.
    public static Map<String, JsonNode> getVariables(ContextView ctx) {
        Map<String, JsonNode> result = null;
        try {
            result = (Map<String, JsonNode>) ctx.get(VARIABLES);
        } catch (ClassCastException | NoSuchElementException e) {
            // NOOP continue with result == null
        }
        if (result == null)
            result = new HashMap<>();
        return result;
    }

    public static Val getVariable(ContextView ctx, String name) {
        var value = getVariables(ctx).get(name);
        if (value == null)
            return Val.UNDEFINED;
        return Val.of(value);
    }

    public static FunctionContext functionContext(ContextView ctx) {
        return ctx.get(FUNCTION_CTX);
    }

    public Context setFunctionContext(Context ctx, FunctionContext functionContext) {
        return ctx.put(FUNCTION_CTX, functionContext);
    }

    public Context setImports(Context ctx, Map<String, String> imports) {
        return ctx.put(IMPORTS, imports);
    }

}
