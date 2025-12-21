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
package io.sapl.spring.data.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.spring.method.metadata.QueryEnforce;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Builds {@link AuthorizationSubscription} for {@link QueryEnforce} annotated
 * methods.
 * <p>
 * When annotation attributes are empty, sensible defaults are provided:
 * <ul>
 * <li>subject: The sanitized Authentication object (credentials/password
 * removed)</li>
 * <li>action: Java method information</li>
 * <li>resource: Java method information enriched with entity type</li>
 * <li>environment: null</li>
 * </ul>
 */
@RequiredArgsConstructor
public class QueryEnforceAuthorizationSubscriptionService {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider;
    private final ObjectProvider<ObjectMapper>                    mapperProvider;
    private final ObjectProvider<GrantedAuthorityDefaults>        defaultsProvider;
    private final ApplicationContext                              context;
    private final SpelExpressionParser                            parser = new SpelExpressionParser();

    private MethodSecurityExpressionHandler expressionHandler;
    private ObjectMapper                    mapper;

    private MethodSecurityExpressionHandler expressionHandler() {
        if (expressionHandler == null) {
            expressionHandler = expressionHandlerProvider
                    .getIfAvailable(() -> defaultExpressionHandler(defaultsProvider, context));
        }
        return expressionHandler;
    }

    private static MethodSecurityExpressionHandler defaultExpressionHandler(
            ObjectProvider<GrantedAuthorityDefaults> defaultsProvider, ApplicationContext context) {
        var handler = new DefaultMethodSecurityExpressionHandler();
        defaultsProvider.ifAvailable(d -> handler.setDefaultRolePrefix(d.getRolePrefix()));
        handler.setApplicationContext(context);
        return handler;
    }

    private ObjectMapper mapper() {
        if (mapper == null) {
            mapper = mapperProvider.getIfAvailable(ObjectMapper::new);
        }
        return mapper;
    }

    /**
     * Builds an {@link AuthorizationSubscription} from the {@link QueryEnforce}
     * annotation. Uses {@link SecurityContextHolder} to get the authentication.
     * For reactive contexts, use the overload that accepts Authentication.
     *
     * @param methodInvocation the method invocation
     * @param enforceAnnotation the QueryEnforce annotation
     * @param domainType the entity type being queried
     * @return the authorization subscription, or null if annotation is null
     */
    public AuthorizationSubscription getAuthorizationSubscription(MethodInvocation methodInvocation,
            QueryEnforce enforceAnnotation, Class<?> domainType) {
        return getAuthorizationSubscription(methodInvocation, enforceAnnotation, domainType,
                SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Builds an {@link AuthorizationSubscription} from the {@link QueryEnforce}
     * annotation with explicit authentication. Use this in reactive contexts where
     * authentication is retrieved from {@code ReactiveSecurityContextHolder}.
     *
     * @param methodInvocation the method invocation
     * @param enforceAnnotation the QueryEnforce annotation
     * @param domainType the entity type being queried
     * @param authentication the authentication object (may be null)
     * @return the authorization subscription, or null if annotation is null
     */
    public AuthorizationSubscription getAuthorizationSubscription(MethodInvocation methodInvocation,
            QueryEnforce enforceAnnotation, Class<?> domainType, Authentication authentication) {
        if (enforceAnnotation == null) {
            return null;
        }
        return buildAuthorizationSubscription(methodInvocation, enforceAnnotation, domainType, authentication);
    }

    /**
     * Builds an {@link AuthorizationSubscription} from the {@link QueryEnforce}
     * annotation. Uses Object.class as default domain type when not provided.
     *
     * @param methodInvocation the method invocation
     * @param enforceAnnotation the QueryEnforce annotation
     * @return the authorization subscription, or null if annotation is null
     */
    public AuthorizationSubscription getAuthorizationSubscription(MethodInvocation methodInvocation,
            QueryEnforce enforceAnnotation) {
        return getAuthorizationSubscription(methodInvocation, enforceAnnotation, Object.class);
    }

    private AuthorizationSubscription buildAuthorizationSubscription(MethodInvocation methodInvocation,
            QueryEnforce enforceAnnotation, Class<?> domainType, Authentication authentication) {

        // Use StandardEvaluationContext for SpEL evaluation with bean resolution
        var evaluationCtx = new StandardEvaluationContext();
        evaluationCtx.setBeanResolver(new BeanFactoryResolver(context));
        evaluationCtx.setVariable("authentication", authentication);
        evaluationCtx.setVariable("methodInvocation", methodInvocation);

        // Add method parameters as variables
        var params = methodInvocation.getMethod().getParameters();
        var args   = methodInvocation.getArguments();
        for (int i = 0; i < params.length; i++) {
            evaluationCtx.setVariable(params[i].getName(), args[i]);
        }

        var subject     = retrieveSubject(authentication, enforceAnnotation.subject(), evaluationCtx);
        var action      = retrieveAction(methodInvocation, enforceAnnotation.action(), evaluationCtx);
        var resource    = retrieveResource(methodInvocation, enforceAnnotation.resource(), evaluationCtx, domainType);
        var environment = retrieveEnvironment(enforceAnnotation.environment(), evaluationCtx);

        return AuthorizationSubscription.of(subject, action, resource, environment, mapper());
    }

    private JsonNode retrieveSubject(Authentication authentication, String subjectValue, EvaluationContext ctx) {
        if (subjectValue != null && !subjectValue.isEmpty()) {
            // Only evaluate as SpEL if it looks like a SpEL expression
            if (subjectValue.startsWith("#") || subjectValue.startsWith("@")) {
                return evaluateToJson(subjectValue, ctx);
            }
            // Otherwise use as literal value
            return mapper().valueToTree(subjectValue);
        }

        // Default: sanitized authentication object
        ObjectNode subject = mapper().valueToTree(authentication);
        subject.remove("credentials");
        var principal = subject.get("principal");
        if (principal instanceof ObjectNode objectPrincipal) {
            objectPrincipal.remove("password");
        }
        return subject;
    }

    private Object retrieveAction(MethodInvocation mi, String actionValue, EvaluationContext ctx) {
        if (actionValue != null && !actionValue.isEmpty()) {
            // Action is used as a literal value (simple string like "findAll")
            // Only evaluate as SpEL if it starts with '#' or '@'
            if (actionValue.startsWith("#") || actionValue.startsWith("@")) {
                return evaluateToJson(actionValue, ctx);
            }
            return actionValue;
        }

        // Default: java method information
        var actionNode = mapper().createObjectNode();
        var java       = buildJavaMethodInfo(mi);
        actionNode.set("java", java);
        return actionNode;
    }

    private Object retrieveResource(MethodInvocation mi, String resourceValue, EvaluationContext ctx,
            Class<?> domainType) {
        if (resourceValue != null && !resourceValue.isEmpty()) {
            // Only evaluate as SpEL if it looks like a SpEL expression
            if (resourceValue.startsWith("#") || resourceValue.startsWith("@")) {
                return evaluateToJson(resourceValue, ctx);
            }
            // Otherwise use as literal value
            return resourceValue;
        }

        // Default: java method information enriched with entity type
        var resourceNode = mapper().createObjectNode();
        var java         = buildJavaMethodInfo(mi);
        resourceNode.set("java", java);
        if (domainType != null && domainType != Object.class) {
            resourceNode.put("entityType", domainType.getName());
        }
        return resourceNode;
    }

    private ObjectNode buildJavaMethodInfo(MethodInvocation mi) {
        var java   = mapper().createObjectNode();
        var method = mi.getMethod();
        java.put("name", method.getName());
        java.put("declaringTypeName", method.getDeclaringClass().getName());
        java.put("instanceMethodName", method.getName());
        var arguments = mi.getArguments();
        if (arguments.length > 0) {
            var array = JSON.arrayNode();
            for (Object o : arguments) {
                try {
                    array.add(mapper().valueToTree(o));
                } catch (IllegalArgumentException e) {
                    // drop if not mappable to JSON
                }
            }
            if (!array.isEmpty()) {
                java.set("arguments", array);
            }
        }
        return java;
    }

    private Object retrieveEnvironment(String environmentValue, EvaluationContext ctx) {
        if (environmentValue == null || environmentValue.isEmpty()) {
            return null;
        }
        // Only evaluate as SpEL if it looks like a SpEL expression
        if (environmentValue.startsWith("#") || environmentValue.startsWith("@")) {
            return evaluateToJson(environmentValue, ctx);
        }
        // Otherwise use as literal value
        return environmentValue;
    }

    private JsonNode evaluateToJson(String expression, EvaluationContext ctx) {
        try {
            var parsed = parser.parseExpression(expression);
            var value  = parsed.getValue(ctx);
            return mapper().valueToTree(value);
        } catch (EvaluationException e) {
            throw new IllegalArgumentException("Failed to evaluate expression '" + expression + "'", e);
        }
    }

}
