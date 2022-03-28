package io.sapl.vaadin.base;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.core.Authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * This class contains the logic for SpEL expression evaluation and retrieving request
 * information from the application context or method invocation.
 */
@RequiredArgsConstructor
public class VaadinAuthorizationSubscriptionBuilderService {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	private final MethodSecurityExpressionHandler expressionHandler;
	private final ObjectMapper mapper;

	/**
	 * Get subject by evaluating the subjectExpression against the provided authentication.
	 * @param authentication Spring authentication context
	 * @param expression	SePL Expression that will be evaluated
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
	 * @param authentication Spring authentication context
	 * @return JSON subject for Authentication Subscription
	 */
	public JsonNode retrieveSubject(Authentication authentication){
		// sanitize the authentication depending on the application context, the
		// authentication may still contain credentials information, which should not be
		// sent over the wire to the PDP
		ObjectNode subject = mapper.valueToTree(authentication);
		subject.remove("credentials");
		var principal = subject.get("principal");
		if (principal instanceof ObjectNode)
			((ObjectNode) principal).remove("password");

		return subject;
	}

	/**
	 * Evaluate expression string against root Object and return JSON
	 * @param expressionString expression string that get evaluated
	 * @param rootObject root object used as evaluation context
	 * @return JSON representation of the evaluation result
	 */
	public JsonNode evaluateExpressionStringToJson(String expressionString, Object rootObject){
		Expression expr = expressionHandler.getExpressionParser().parseExpression(expressionString);
		EvaluationContext evaluationContext = new StandardEvaluationContext(rootObject);
		return evaluateToJson(expr, evaluationContext);
	}

	private JsonNode evaluateToJson(Expression expr, EvaluationContext ctx) {
		try {
			return mapper.valueToTree(expr.getValue(ctx));
		}
		catch (EvaluationException e) {
			throw new IllegalArgumentException("Failed to evaluate expression '" + expr.getExpressionString() + "'", e);
		}
	}

	/**
	 * Returns JSON representation of the mentioned class.
	 * @param clazz class that is analyzed
	 * @return JSON representation of class metadata
	 */
	public static JsonNode serializeTargetClassDescription(Class<?> clazz) {
		var result = JSON.objectNode();
		result.set("name", JSON.textNode(clazz.getName()));
		result.set("canonicalName", JSON.textNode(clazz.getCanonicalName()));
		result.set("typeName", JSON.textNode(clazz.getTypeName()));
		result.set("simpleName", JSON.textNode(clazz.getSimpleName()));
		return result;
	}

}
