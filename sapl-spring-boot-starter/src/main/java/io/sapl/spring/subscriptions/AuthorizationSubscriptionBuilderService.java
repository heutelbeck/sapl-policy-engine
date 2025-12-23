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
package io.sapl.spring.subscriptions;

import java.util.Optional;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.spring.method.metadata.QueryEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * Unified service for building {@link AuthorizationSubscription} instances from
 * method invocations and security annotations.
 * <p>
 * Supports both Servlet (blocking) and WebFlux (reactive) contexts, as well as
 * method-level security ({@link SaplAttribute}) and data repository security
 * ({@link QueryEnforce}) annotations.
 */
public class AuthorizationSubscriptionBuilderService {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken("key", "anonymous",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private static final SpelExpressionParser PARSER            = new SpelExpressionParser();
    public static final String                AUTHENTICATION    = "authentication";
    public static final String                METHOD_INVOCATION = "methodInvocation";

    private final ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider;
    private final ObjectProvider<ObjectMapper>                    mapperProvider;
    private final ObjectProvider<GrantedAuthorityDefaults>        defaultsProvider;
    private final ApplicationContext                              applicationContext;

    private MethodSecurityExpressionHandler expressionHandler;
    private ObjectMapper                    mapper;

    /**
     * Constructor for reactive method security context with resolved beans.
     */
    public AuthorizationSubscriptionBuilderService(MethodSecurityExpressionHandler expressionHandler,
            ObjectMapper mapper) {
        this.expressionHandler         = expressionHandler;
        this.mapper                    = mapper;
        this.expressionHandlerProvider = null;
        this.mapperProvider            = null;
        this.defaultsProvider          = null;
        this.applicationContext        = null;
    }

    /**
     * Constructor for lazy bean resolution via ObjectProviders.
     */
    public AuthorizationSubscriptionBuilderService(
            ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider,
            ObjectProvider<ObjectMapper> mapperProvider,
            ObjectProvider<GrantedAuthorityDefaults> defaultsProvider,
            ApplicationContext applicationContext) {
        this.expressionHandlerProvider = expressionHandlerProvider;
        this.mapperProvider            = mapperProvider;
        this.defaultsProvider          = defaultsProvider;
        this.applicationContext        = applicationContext;
    }

    private MethodSecurityExpressionHandler expressionHandler() {
        if (expressionHandler == null && expressionHandlerProvider != null) {
            expressionHandler = expressionHandlerProvider
                    .getIfAvailable(() -> defaultExpressionHandler(defaultsProvider, applicationContext));
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
        if (mapper == null && mapperProvider != null) {
            mapper = mapperProvider.getIfAvailable(ObjectMapper::new);
        }
        return mapper;
    }

    // ==================== Servlet (blocking) API for SaplAttribute
    // ====================

    /**
     * Constructs an authorization subscription for Servlet/blocking context.
     */
    public AuthorizationSubscription constructAuthorizationSubscription(Authentication authentication,
            MethodInvocation methodInvocation, SaplAttribute attribute) {
        var evaluationCtx = expressionHandler().createEvaluationContext(authentication, methodInvocation);
        evaluationCtx.setVariable(AUTHENTICATION, authentication);
        evaluationCtx.setVariable(METHOD_INVOCATION, methodInvocation);
        return constructServletAuthorizationSubscription(authentication, methodInvocation, attribute, evaluationCtx);
    }

    /**
     * Constructs an authorization subscription for Servlet/blocking context with
     * return object.
     */
    public AuthorizationSubscription constructAuthorizationSubscriptionWithReturnObject(Authentication authentication,
            MethodInvocation methodInvocation, SaplAttribute attribute, Object returnObject) {
        var evaluationCtx = expressionHandler().createEvaluationContext(authentication, methodInvocation);
        expressionHandler().setReturnObject(returnObject, evaluationCtx);
        evaluationCtx.setVariable(AUTHENTICATION, authentication);
        evaluationCtx.setVariable(METHOD_INVOCATION, methodInvocation);
        return constructServletAuthorizationSubscription(authentication, methodInvocation, attribute, evaluationCtx);
    }

    private AuthorizationSubscription constructServletAuthorizationSubscription(Authentication authentication,
            MethodInvocation methodInvocation, SaplAttribute attribute, EvaluationContext evaluationCtx) {
        Optional<HttpServletRequest> httpRequest = retrieveServletRequest();
        return constructAuthorizationSubscription(authentication, evaluationCtx, attribute.subjectExpression(),
                attribute.actionExpression(), attribute.resourceExpression(), attribute.environmentExpression(),
                methodInvocation, httpRequest, Object.class);
    }

    private static Optional<HttpServletRequest> retrieveServletRequest() {
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        var httpRequest       = requestAttributes != null ? ((ServletRequestAttributes) requestAttributes).getRequest()
                : null;
        return Optional.ofNullable(httpRequest);
    }

    // ==================== Reactive API for SaplAttribute ====================

    /**
     * Constructs a reactive authorization subscription for WebFlux context.
     */
    public Mono<AuthorizationSubscription> reactiveConstructAuthorizationSubscription(MethodInvocation methodInvocation,
            SaplAttribute attribute) {
        return Mono.deferContextual(contextView -> constructAuthorizationSubscriptionFromContextView(methodInvocation,
                attribute, contextView, Optional.empty()));
    }

    /**
     * Constructs a reactive authorization subscription for WebFlux context with
     * return object.
     */
    public Mono<AuthorizationSubscription> reactiveConstructAuthorizationSubscription(MethodInvocation methodInvocation,
            SaplAttribute attribute, Object returnedObject) {
        return Mono.deferContextual(contextView -> constructAuthorizationSubscriptionFromContextView(methodInvocation,
                attribute, contextView, Optional.ofNullable(returnedObject)));
    }

    private Mono<? extends AuthorizationSubscription> constructAuthorizationSubscriptionFromContextView(
            MethodInvocation methodInvocation, SaplAttribute attribute, ContextView contextView,
            Optional<Object> returnedObject) {
        Optional<ServerWebExchange>     serverWebExchange = contextView.getOrEmpty(ServerWebExchange.class);
        Optional<ServerHttpRequest>     serverHttpRequest = serverWebExchange.map(ServerWebExchange::getRequest);
        Optional<Mono<SecurityContext>> securityContext   = contextView.getOrEmpty(SecurityContext.class);
        Mono<Authentication>            authentication    = securityContext
                .map(ctx -> ctx.map(SecurityContext::getAuthentication).defaultIfEmpty(ANONYMOUS))
                .orElseGet(() -> Mono.just(ANONYMOUS));

        return authentication.map(authn -> {
            var evaluationCtx = expressionHandler().createEvaluationContext(authn, methodInvocation);
            evaluationCtx.setVariable(AUTHENTICATION, authn);
            evaluationCtx.setVariable(METHOD_INVOCATION, methodInvocation);
            returnedObject.ifPresent(returnObject -> expressionHandler().setReturnObject(returnObject, evaluationCtx));
            return constructAuthorizationSubscription(authn, evaluationCtx, attribute.subjectExpression(),
                    attribute.actionExpression(), attribute.resourceExpression(), attribute.environmentExpression(),
                    methodInvocation, serverHttpRequest, Object.class);
        });
    }

    // ==================== Reactive API for QueryEnforce (data repository)
    // ====================

    /**
     * Builds a reactive {@link AuthorizationSubscription} from a
     * {@link QueryEnforce} annotation.
     */
    public Mono<AuthorizationSubscription> reactiveConstructAuthorizationSubscription(MethodInvocation methodInvocation,
            QueryEnforce queryEnforce, Class<?> domainType) {
        if (queryEnforce == null) {
            return Mono.error(new IllegalArgumentException("QueryEnforce annotation must not be null"));
        }

        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication)
                .switchIfEmpty(Mono.fromCallable(() -> SecurityContextHolder.getContext().getAuthentication()))
                .defaultIfEmpty(ANONYMOUS).map(authentication -> {
                    var evaluationCtx = createQueryEnforceEvaluationContext(authentication, methodInvocation);
                    return constructAuthorizationSubscription(authentication, evaluationCtx,
                            parseExpressionIfNotEmpty(queryEnforce.subject()),
                            parseExpressionIfNotEmpty(queryEnforce.action()),
                            parseExpressionIfNotEmpty(queryEnforce.resource()),
                            parseExpressionIfNotEmpty(queryEnforce.environment()), methodInvocation, Optional.empty(),
                            domainType);
                });
    }

    private EvaluationContext createQueryEnforceEvaluationContext(Authentication authentication,
            MethodInvocation methodInvocation) {
        var evaluationCtx = new StandardEvaluationContext(methodInvocation);
        if (applicationContext != null) {
            evaluationCtx.setBeanResolver(new BeanFactoryResolver(applicationContext));
        }
        evaluationCtx.setVariable(AUTHENTICATION, authentication);
        evaluationCtx.setVariable(METHOD_INVOCATION, methodInvocation);

        var params = methodInvocation.getMethod().getParameters();
        var args   = methodInvocation.getArguments();
        for (int i = 0; i < params.length; i++) {
            evaluationCtx.setVariable(params[i].getName(), args[i]);
        }

        return evaluationCtx;
    }

    // ==================== Shared subscription construction ====================

    private AuthorizationSubscription constructAuthorizationSubscription(Authentication authentication,
            EvaluationContext evaluationCtx, Expression subjectExpr, Expression actionExpr, Expression resourceExpr,
            Expression environmentExpr, MethodInvocation methodInvocation, Optional<?> httpRequest,
            Class<?> domainType) {

        var subject     = retrieveSubject(authentication, subjectExpr, evaluationCtx);
        var action      = retrieveAction(methodInvocation, actionExpr, evaluationCtx, httpRequest);
        var resource    = retrieveResource(methodInvocation, resourceExpr, evaluationCtx, httpRequest, domainType);
        var environment = retrieveEnvironment(environmentExpr, evaluationCtx);

        return new AuthorizationSubscription(ValueJsonMarshaller.fromJsonNode(mapper().valueToTree(subject)),
                ValueJsonMarshaller.fromJsonNode(mapper().valueToTree(action)),
                ValueJsonMarshaller.fromJsonNode(mapper().valueToTree(resource)),
                ValueJsonMarshaller.fromJsonNode(mapper().valueToTree(environment)));
    }

    // ==================== Subject/Action/Resource/Environment retrieval
    // ====================

    private JsonNode retrieveSubject(Authentication authentication, Expression subjectExpr, EvaluationContext ctx) {
        if (subjectExpr != null)
            return evaluateToJson(subjectExpr, ctx);

        ObjectNode subject = mapper().valueToTree(authentication);
        subject.remove("credentials");
        var principal = subject.get("principal");
        if (principal instanceof ObjectNode objectPrincipal)
            objectPrincipal.remove("password");

        return subject;
    }

    private Object retrieveAction(MethodInvocation mi, Expression actionExpr, EvaluationContext ctx,
            Optional<?> requestObject) {
        if (actionExpr != null)
            return evaluateToJson(actionExpr, ctx);

        var actionNode = mapper().createObjectNode();
        requestObject.ifPresent(request -> actionNode.set("http", mapper().valueToTree(request)));
        var java      = (ObjectNode) mapper().valueToTree(mi);
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
            if (!array.isEmpty())
                java.set("arguments", array);
        }
        actionNode.set("java", java);
        return actionNode;
    }

    private Object retrieveResource(MethodInvocation mi, Expression resourceExpr, EvaluationContext ctx,
            Optional<?> httpRequest, Class<?> domainType) {
        if (resourceExpr != null)
            return evaluateToJson(resourceExpr, ctx);

        var resourceNode = mapper().createObjectNode();
        httpRequest.ifPresent(request -> resourceNode.set("http", mapper().valueToTree(request)));
        var java = (ObjectNode) mapper().valueToTree(mi);
        resourceNode.set("java", java);
        if (domainType != null && domainType != Object.class) {
            resourceNode.put("entityType", domainType.getName());
        }
        return resourceNode;
    }

    private Object retrieveEnvironment(Expression environmentExpr, EvaluationContext ctx) {
        if (environmentExpr == null)
            return null;
        return evaluateToJson(environmentExpr, ctx);
    }

    private JsonNode evaluateToJson(Expression expr, EvaluationContext ctx) {
        try {
            return mapper().valueToTree(expr.getValue(ctx));
        } catch (EvaluationException e) {
            throw new IllegalArgumentException("Failed to evaluate expression '" + expr.getExpressionString() + "'", e);
        }
    }

    // ==================== Expression parsing ====================

    private Expression parseExpressionIfNotEmpty(String expressionString) {
        if (expressionString == null || expressionString.isEmpty()) {
            return null;
        }
        return PARSER.parseExpression(expressionString);
    }

}
