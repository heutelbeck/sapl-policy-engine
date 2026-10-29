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
package io.sapl.vaadin.base;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.core.Authentication;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * This class contains the logic for SpEL expression evaluation and retrieving
 * request information from the application context or method invocation.
 */
@RequiredArgsConstructor
public class VaadinAuthorizationSubscriptionBuilderService {

    private static final JsonNodeFactory          JSON = JsonNodeFactory.instance;
    private final MethodSecurityExpressionHandler expressionHandler;
    private final JsonMapper                      mapper;

    /**
     * Get subject by evaluating the subjectExpression against the provided
     * authentication.
     *
     * @param authentication Spring authentication context
     * @param expression SpEL Expression that will be evaluated
     * @return JSON subject for Authentication Subscription
     */
    public JsonNode retrieveSubject(Authentication authentication, String expression) {
        if (expression == null || expression.isEmpty()) {
            // just return the authentication json if no expression has been defined
            return retrieveSubject(authentication);

        } else {
            // Get JsonNode from evaluated subjectExpression against authentication context
            return evaluateExpressionStringToJson(expression, authentication);
        }
    }

    /**
     * Returns the JSON presentation of the provided Authentication context
     *
     * @param authentication Spring authentication context
     * @return JSON subject for Authentication Subscription
     */
    public JsonNode retrieveSubject(Authentication authentication) {
        // sanitize the authentication depending on the application context, the
        // authentication may still contain credentials information, which should not be
        // sent over the wire to the PDP
        ObjectNode subject = mapper.valueToTree(authentication);
        subject.remove("credentials");
        var principal = subject.get("principal");
        if (principal instanceof ObjectNode objectNode)
            objectNode.remove("password");

        return subject;
    }

    /**
     * Evaluate expression string against root Object and return JSON
     *
     * @param expressionString expression string that get evaluated
     * @param rootObject root object used as evaluation context
     * @return JSON representation of the evaluation result
     */
    public JsonNode evaluateExpressionStringToJson(String expressionString, Object rootObject) {
        Expression        expr              = expressionHandler.getExpressionParser().parseExpression(expressionString);
        EvaluationContext evaluationContext = new StandardEvaluationContext(rootObject);
        return evaluateToJson(expr, evaluationContext);
    }

    private JsonNode evaluateToJson(Expression expr, EvaluationContext ctx) {
        try {
            return mapper.valueToTree(expr.getValue(ctx));
        } catch (EvaluationException e) {
            throw new IllegalArgumentException("Failed to evaluate expression '" + expr.getExpressionString() + "'", e);
        }
    }

    /**
     * Returns JSON representation of the mentioned class.
     *
     * @param clazz class that is analyzed
     * @return JSON representation of class metadata
     */
    public static JsonNode serializeTargetClassDescription(Class<?> clazz) {
        var result = JSON.objectNode();
        result.set("name", JSON.stringNode(clazz.getName()));
        result.set("canonicalName", JSON.stringNode(clazz.getCanonicalName()));
        result.set("typeName", JSON.stringNode(clazz.getTypeName()));
        result.set("simpleName", JSON.stringNode(clazz.getSimpleName()));
        return result;
    }

}
