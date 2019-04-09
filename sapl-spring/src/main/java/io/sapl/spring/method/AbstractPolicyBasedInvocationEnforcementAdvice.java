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

	public void setExpressionHandler(MethodSecurityExpressionHandler expressionHandler) {
		this.expressionHandler = expressionHandler;
	}

	/**
	 * Lazy loading of decouples security infrastructure from domain logic in
	 * initialization. This avoids beans to become not eligible for Bean post
	 * processing.
	 */
	protected void lazyLoadDepdendencies() {
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

	protected Object retrieveSubjet(Authentication authentication, AbstractPolicyBasedEnforcementAttribute attr,
			EvaluationContext ctx) {
		if (attr.getSubjectExpression() == null) {
			// no explicit subject declared => use the authentication object to indicate the
			// subject
			return authentication;
		} else {
			// subject declared by expression
			JsonNode exprResult = evaluateToJson(attr.getSubjectExpression(), ctx);
			return exprResult;
		}
	}

	protected JsonNode evaluateToJson(Expression expr, EvaluationContext ctx) {
		try {
			return mapper.valueToTree(expr.getValue(ctx));
		} catch (EvaluationException e) {
			throw new IllegalArgumentException("Failed to evaluate expression '" + expr.getExpressionString() + "'", e);
		}
	}

	protected JsonNode serizalizeMethod(Method m) {
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
			JsonNode exprResult = evaluateToJson(attr.getActionExpression(), ctx);
			return exprResult;
		}
	}

	protected Object retrieveAction(MethodInvocation mi) {
		ObjectNode actionNode = mapper.createObjectNode();
		Optional<HttpServletRequest> httpServletRequest = retrieveRequestObject();
		if (httpServletRequest.isPresent()) {
			actionNode.set("http", mapper.valueToTree(httpServletRequest.get()));
		}
		actionNode.set("java", mapper.valueToTree(mi));

		// Collect call arguments. not serializable => null
		ArrayNode array = JsonNodeFactory.instance.arrayNode();
		for (Object o : mi.getArguments()) {
			try {
				JsonNode json = mapper.valueToTree(o);
				array.add(json);
			} catch (IllegalArgumentException e) {
				array.add(JsonNodeFactory.instance.nullNode());
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
			JsonNode exprResult = evaluateToJson(attr.getResourceExpression(), ctx);
			return exprResult;
		}
	}

	protected Object retrieveResource(MethodInvocation mi) {
		// No specific resource information found. Construct basic information from the
		// runtime context
		ObjectNode resourceNode = mapper.createObjectNode();
		Optional<HttpServletRequest> httpServletRequest = retrieveRequestObject();
		if (httpServletRequest.isPresent()) {
			// The action is in the context of a HTTP request. Adding it to the resource.
			resourceNode.set("http", mapper.valueToTree(httpServletRequest.get()));
		}
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
