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
package io.sapl.spring.subscriptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.spring.method.metadata.SaplAttribute;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.Optional;

/**
 * This class contains the logic for SpEL expression evaluation and retrieving
 * request information from the application context or method invocation.
 */
@RequiredArgsConstructor
public class WebfluxAuthorizationSubscriptionBuilderService {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken("key", "anonymous",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private final MethodSecurityExpressionHandler expressionHandler;

    private final ObjectMapper mapper;

    public Mono<AuthorizationSubscription> reactiveConstructAuthorizationSubscription(MethodInvocation methodInvocation,
            SaplAttribute attribute) {
        return Mono.deferContextual(contextView -> constructAuthorizationSubscriptionFromContextView(methodInvocation,
                attribute, contextView, Optional.empty()));
    }

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
        return authentication.map(authn -> constructAuthorizationSubscription(authn, serverHttpRequest,
                methodInvocation, attribute, returnedObject));
    }

    private AuthorizationSubscription constructAuthorizationSubscription(Authentication authentication,
            Optional<ServerHttpRequest> serverHttpRequest, MethodInvocation methodInvocation, SaplAttribute attribute,
            Optional<Object> returnedObject) {
        final var evaluationCtx = expressionHandler.createEvaluationContext(authentication, methodInvocation);
        returnedObject.ifPresent(returnObject -> expressionHandler.setReturnObject(returnObject, evaluationCtx));

        final var subject     = retrieveSubject(authentication, attribute, evaluationCtx);
        final var action      = retrieveAction(methodInvocation, attribute, evaluationCtx, serverHttpRequest);
        final var resource    = retrieveResource(methodInvocation, attribute, evaluationCtx, serverHttpRequest);
        final var environment = retrieveEnvironment(attribute, evaluationCtx);
        return new AuthorizationSubscription(ValueJsonMarshaller.fromJsonNode(mapper.valueToTree(subject)),
                ValueJsonMarshaller.fromJsonNode(mapper.valueToTree(action)),
                ValueJsonMarshaller.fromJsonNode(mapper.valueToTree(resource)),
                ValueJsonMarshaller.fromJsonNode(mapper.valueToTree(environment)));
    }

    private JsonNode retrieveSubject(Authentication authentication, SaplAttribute attr, EvaluationContext ctx) {
        if (attr.subjectExpression() != null)
            return evaluateToJson(attr.subjectExpression(), ctx);

        ObjectNode subject = mapper.valueToTree(authentication);

        // sanitize the authentication depending on the application context, the
        // authentication may still contain credentials information, which should not be
        // sent over the wire to the PDP

        subject.remove("credentials");
        final var principal = subject.get("principal");
        if (principal instanceof ObjectNode objectPrincipal)
            objectPrincipal.remove("password");

        return subject;
    }

    private JsonNode evaluateToJson(Expression expr, EvaluationContext ctx) {
        try {
            return mapper.valueToTree(expr.getValue(ctx));
        } catch (EvaluationException e) {
            throw new IllegalArgumentException("Failed to evaluate expression '" + expr.getExpressionString() + "'", e);
        }
    }

    private Object retrieveAction(MethodInvocation mi, SaplAttribute attr, EvaluationContext ctx,
            Optional<?> requestObject) {
        if (attr.actionExpression() == null)
            return retrieveAction(mi, requestObject);
        return evaluateToJson(attr.actionExpression(), ctx);
    }

    private Object retrieveAction(MethodInvocation mi, Optional<?> requestObject) {
        final var actionNode = mapper.createObjectNode();
        System.err.println("########################");
        System.err.println("########################");
        System.err.println("########################");
        System.err.println("->" + requestObject.orElse(null));
        System.err.println("########################");
        System.err.println("########################");
        System.err.println("########################");
        requestObject.ifPresent(request -> actionNode.set("http", mapper.valueToTree(request)));
        final var java      = (ObjectNode) mapper.valueToTree(mi);
        final var arguments = mi.getArguments();
        if (arguments.length > 0) {
            final var array = JSON.arrayNode();
            for (Object o : arguments) {
                try {
                    array.add(mapper.valueToTree(o));
                } catch (IllegalArgumentException e) {
                    // drop of not mappable to JSON
                }
            }
            if (!array.isEmpty())
                java.set("arguments", array);
        }
        actionNode.set("java", java);
        return actionNode;
    }

    private Object retrieveResource(MethodInvocation mi, SaplAttribute attr, EvaluationContext ctx,
            Optional<ServerHttpRequest> serverHttpRequest) {
        if (attr.resourceExpression() == null)
            return retrieveResource(mi, serverHttpRequest);
        return evaluateToJson(attr.resourceExpression(), ctx);
    }

    private Object retrieveResource(MethodInvocation mi, Optional<ServerHttpRequest> serverHttpRequest) {
        final var resourceNode = mapper.createObjectNode();
        // The action is in the context of an HTTP request. Adding it to the resource.
        serverHttpRequest.ifPresent(request -> resourceNode.set("http", mapper.valueToTree(request)));
        final var java = (ObjectNode) mapper.valueToTree(mi);
        resourceNode.set("java", java);
        return resourceNode;
    }

    private Object retrieveEnvironment(SaplAttribute attr, EvaluationContext ctx) {
        if (attr.environmentExpression() == null)
            return null;
        return evaluateToJson(attr.environmentExpression(), ctx);
    }

}
