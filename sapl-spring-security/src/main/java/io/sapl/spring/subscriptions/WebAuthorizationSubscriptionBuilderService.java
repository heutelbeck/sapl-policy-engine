/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.spring.method.metadata.SaplAttribute;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * This class contains the logic for SpEL expression evaluation and retrieving
 * request information from the application context or method invocation.
 */
@RequiredArgsConstructor
public class WebAuthorizationSubscriptionBuilderService {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private final ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider;
	private final ObjectProvider<ObjectMapper>                    mapperProvider;
	private final ObjectProvider<GrantedAuthorityDefaults>        defaultsProvider;
	private final ApplicationContext                              context;

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
		DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
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

	public AuthorizationSubscription constructAuthorizationSubscriptionWithReturnObject(Authentication authentication,
			MethodInvocation methodInvocation, SaplAttribute attribute, Object returnObject) {
		var evaluationCtx = expressionHandler().createEvaluationContext(authentication, methodInvocation);
		expressionHandler().setReturnObject(returnObject, evaluationCtx);
		evaluationCtx.setVariable("authentication", authentication);
		evaluationCtx.setVariable("methodInvocation", methodInvocation);
		return constructAuthorizationSubscription(authentication, methodInvocation, attribute, evaluationCtx);
	}

	public AuthorizationSubscription constructAuthorizationSubscription(Authentication authentication,
			MethodInvocation methodInvocation, SaplAttribute attribute) {
		var evaluationCtx = expressionHandler().createEvaluationContext(authentication, methodInvocation);
		evaluationCtx.setVariable("authentication", authentication);
		evaluationCtx.setVariable("methodInvocation", methodInvocation);
		return constructAuthorizationSubscription(authentication, methodInvocation, attribute, evaluationCtx);
	}

	private AuthorizationSubscription constructAuthorizationSubscription(Authentication authentication,
			MethodInvocation methodInvocation, SaplAttribute attribute, EvaluationContext evaluationCtx) {
		var subject     = retrieveSubject(authentication, attribute, evaluationCtx);
		var action      = retrieveAction(methodInvocation, attribute, evaluationCtx, retrieveRequestObject());
		var resource    = retrieveResource(methodInvocation, attribute, evaluationCtx);
		var environment = retrieveEnvironment(attribute, evaluationCtx);
		return new AuthorizationSubscription(mapper().valueToTree(subject), mapper().valueToTree(action),
				mapper().valueToTree(resource), mapper().valueToTree(environment));
	}

	private JsonNode retrieveSubject(Authentication authentication, SaplAttribute attr, EvaluationContext ctx) {
		if (attr.subjectExpression() != null)
			return evaluateToJson(attr.subjectExpression(), ctx);

		ObjectNode subject = mapper().valueToTree(authentication);

		// sanitize the authentication depending on the application context, the
		// authentication may still contain credentials information, which should not be
		// sent over the wire to the PDP

		subject.remove("credentials");
		var principal = subject.get("principal");
		if (principal instanceof ObjectNode objectPrincipal)
			objectPrincipal.remove("password");

		return subject;
	}

	private JsonNode evaluateToJson(Expression expr, EvaluationContext ctx) {
		try {
			return mapper().valueToTree(expr.getValue(ctx));
		} catch (EvaluationException e) {
			throw new IllegalArgumentException("Failed to evaluate expression '" + expr.getExpressionString() + "'", e);
		}
	}

	private static Optional<HttpServletRequest> retrieveRequestObject() {
		var requestAttributes = RequestContextHolder.getRequestAttributes();
		var httpRequest       = requestAttributes != null ? ((ServletRequestAttributes) requestAttributes).getRequest()
				: null;
		return Optional.ofNullable(httpRequest);
	}

	private Object retrieveAction(MethodInvocation mi, SaplAttribute attr, EvaluationContext ctx,
			Optional<?> requestObject) {
		if (attr.actionExpression() == null)
			return retrieveAction(mi, requestObject);
		return evaluateToJson(attr.actionExpression(), ctx);
	}

	private Object retrieveAction(MethodInvocation mi, Optional<?> requestObject) {
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
					// drop of not mappable to JSON
				}
			}
			if (array.size() > 0)
				java.set("arguments", array);
		}
		actionNode.set("java", java);
		return actionNode;
	}

	private Object retrieveResource(MethodInvocation mi, SaplAttribute attr, EvaluationContext ctx) {
		if (attr.resourceExpression() == null)
			return retrieveResource(mi);
		return evaluateToJson(attr.resourceExpression(), ctx);
	}

	private Object retrieveResource(MethodInvocation mi) {
		var resourceNode       = mapper().createObjectNode();
		var httpServletRequest = retrieveRequestObject();
		// The action is in the context of an HTTP request. Adding it to the resource.
		httpServletRequest.ifPresent(servletRequest -> resourceNode.set("http", mapper().valueToTree(servletRequest)));
		var java = (ObjectNode) mapper().valueToTree(mi);
		resourceNode.set("java", java);
		return resourceNode;
	}

	private Object retrieveEnvironment(SaplAttribute attr, EvaluationContext ctx) {
		if (attr.environmentExpression() == null)
			return null;
		return evaluateToJson(attr.environmentExpression(), ctx);
	}

}
