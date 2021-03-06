/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.method;

import java.lang.reflect.Method;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintHandlerService;
import lombok.RequiredArgsConstructor;

/**
 * Base logic for PreEnforce and PostEnforce advice classes. This class contains
 * the logic for SpEL expression evaluation and retrieving request information
 * from the application context or method invocation.
 */
@RequiredArgsConstructor
public abstract class AbstractPolicyBasedInvocationEnforcementAdvice {

	protected static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	protected MethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
	protected final ObjectFactory<PolicyDecisionPoint> pdpFactory;
	protected final ObjectFactory<ConstraintHandlerService> constraintHandlerFactory;
	protected final ObjectFactory<ObjectMapper> objectMapperFactory;
	protected PolicyDecisionPoint pdp;
	protected ConstraintHandlerService constraintHandlers;
	protected ObjectMapper mapper;

	/**
	 * Set the expression handler.
	 * 
	 * @param expressionHandler the expression handler
	 */
	public void setExpressionHandler(MethodSecurityExpressionHandler expressionHandler) {
		this.expressionHandler = expressionHandler;
	}

	/**
	 * Lazy loading of dependencies decouples security infrastructure from domain
	 * logic in initialization. This avoids beans to become not eligible for Bean
	 * post processing.
	 */
	protected void lazyLoadDependencies() {
		if (pdp == null) {
			pdp = pdpFactory.getObject();
		}
		if (constraintHandlers == null) {
			constraintHandlers = constraintHandlerFactory.getObject();
		}
		if (mapper == null) {
			mapper = objectMapperFactory.getObject();
		}
	}

	protected Object retrieveSubject(Authentication authentication, AbstractPolicyBasedEnforcementAttribute attr,
			EvaluationContext ctx) {
		if (attr.getSubjectExpression() == null) {
			// no explicit subject declared => use the authentication object
			// to describe the subject
			return authentication;
		} else {
			// subject declared by expression
			return evaluateToJson(attr.getSubjectExpression(), ctx);
		}
	}

	protected JsonNode evaluateToJson(Expression expr, EvaluationContext ctx) {
		try {
			return mapper.valueToTree(expr.getValue(ctx));
		} catch (EvaluationException e) {
			throw new IllegalArgumentException("Failed to evaluate expression '" + expr.getExpressionString() + "'", e);
		}
	}

	protected static JsonNode serializeMethod(Method m) {
		ObjectNode result = JSON.objectNode();
		result.set("name", JSON.textNode(m.getName()));
		result.set("returnType", JSON.textNode(m.getReturnType().getName()));
		result.set("declaringClass", JSON.textNode(m.getDeclaringClass().getName()));
		return result;
	}

	protected static JsonNode serializeTargetClassDescription(Class<?> clazz) {
		ObjectNode result = JSON.objectNode();
		result.set("name", JSON.textNode(clazz.getName()));
		result.set("canonicalName", JSON.textNode(clazz.getCanonicalName()));
		result.set("typeName", JSON.textNode(clazz.getTypeName()));
		result.set("simpleName", JSON.textNode(clazz.getSimpleName()));
		return result;
	}

	protected static Optional<HttpServletRequest> retrieveRequestObject() {
		final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
		final HttpServletRequest httpRequest = requestAttributes != null
				? ((ServletRequestAttributes) requestAttributes).getRequest()
				: null;
		return Optional.ofNullable(httpRequest);
	}

	protected Object retrieveAction(MethodInvocation mi, AbstractPolicyBasedEnforcementAttribute attr,
			EvaluationContext ctx) {
		if (attr.getActionExpression() == null) {
			// no explicit action declared => derive action from MethodInvocation
			return retrieveAction(mi);
		} else {
			// action declared by expression
			return evaluateToJson(attr.getActionExpression(), ctx);
		}
	}

	protected Object retrieveAction(MethodInvocation mi) {
		ObjectNode actionNode = mapper.createObjectNode();
		Optional<HttpServletRequest> httpServletRequest = retrieveRequestObject();
		httpServletRequest.ifPresent(servletRequest -> actionNode.set("http", mapper.valueToTree(servletRequest)));
		actionNode.set("java", mapper.valueToTree(mi));

		// Collect call arguments. not serializable => null
		ArrayNode array = JSON.arrayNode();	
		for (Object o : mi.getArguments()) {
			try {
				JsonNode json = mapper.valueToTree(o);
				array.add(json);
			} catch (IllegalArgumentException e) {
				array.add(JSON.nullNode());
			}
		}
		actionNode.set("arguments", array);
		return actionNode;
	}

	protected Object retrieveResource(MethodInvocation mi, AbstractPolicyBasedEnforcementAttribute attr,
			EvaluationContext ctx) {
		if (attr.getResourceExpression() == null) {
			// no explicit action declared => derive action from MethodInvocation
			return retrieveResource(mi);
		} else {
			// declared by expression
			return evaluateToJson(attr.getResourceExpression(), ctx);
		}
	}

	protected Object retrieveResource(MethodInvocation mi) {
		// No specific resource information found. Construct basic information from the
		// runtime context
		ObjectNode resourceNode = mapper.createObjectNode();
		Optional<HttpServletRequest> httpServletRequest = retrieveRequestObject();
		// The action is in the context of a HTTP request. Adding it to the resource.
		httpServletRequest.ifPresent(servletRequest -> resourceNode.set("http", mapper.valueToTree(servletRequest)));
		JsonNode target = serializeTargetClassDescription(mi.getThis().getClass());
		resourceNode.set("targetClass", target);
		return resourceNode;
	}

	protected Object retrieveEnvironment(AbstractPolicyBasedEnforcementAttribute attr, EvaluationContext ctx) {
		if (attr.getEnvironmentExpression() == null) {
			return null;
		} else {
			JsonNode exprResult = evaluateToJson(attr.getEnvironmentExpression(), ctx);
			return exprResult;
		}
	}

}
