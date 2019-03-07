package io.sapl.spring.method;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

public class ExpressionBasedPolicyEnforcementAttributeFactory implements PolicyEnforcementAttributeFactory {
	private final Object parserLock = new Object();
	private ExpressionParser parser;
	private MethodSecurityExpressionHandler handler;

	public ExpressionBasedPolicyEnforcementAttributeFactory(MethodSecurityExpressionHandler handler) {
		this.handler = handler;
	}

	@Override
	public PreInvocationEnforcementAttribute createPreInvocationAttribute(String subjectAttribute,
			String actionAttribute, String resourceAttribute, String environmentAttribute) {
		try {
			Expression subjectExpression = (subjectAttribute == null || subjectAttribute.isEmpty()) ? null
					: getParser().parseExpression(subjectAttribute);
			Expression actionExpression = (actionAttribute == null || actionAttribute.isEmpty()) ? null
					: getParser().parseExpression(actionAttribute);
			Expression resourceExpression = (resourceAttribute == null || resourceAttribute.isEmpty()) ? null
					: getParser().parseExpression(resourceAttribute);
			Expression environmentExpression = (environmentAttribute == null || environmentAttribute.isEmpty()) ? null
					: getParser().parseExpression(environmentAttribute);
			return new PreInvocationExpressionEnforcementAttribute(subjectExpression, actionExpression,
					resourceExpression, environmentExpression);
		} catch (ParseException e) {
			throw new IllegalArgumentException("Failed to parse expression '" + e.getExpressionString() + "'", e);
		}
	}

	@Override
	public PostInvocationEnforcementAttribute createPostInvocationAttribute(String subjectAttribute,
			String actionAttribute, String resourceAttribute, String environmentAttribute) {
		try {
			Expression subjectExpression = (subjectAttribute == null || subjectAttribute.isEmpty()) ? null
					: getParser().parseExpression(subjectAttribute);
			Expression actionExpression = (actionAttribute == null || actionAttribute.isEmpty()) ? null
					: getParser().parseExpression(actionAttribute);
			Expression resourceExpression = (resourceAttribute == null || resourceAttribute.isEmpty()) ? null
					: getParser().parseExpression(resourceAttribute);
			Expression environmentExpression = (environmentAttribute == null || environmentAttribute.isEmpty()) ? null
					: getParser().parseExpression(environmentAttribute);
			return new PostInvocationExpressionEnforcementAttribute(subjectExpression, actionExpression,
					resourceExpression, environmentExpression);
		} catch (ParseException e) {
			throw new IllegalArgumentException("Failed to parse expression '" + e.getExpressionString() + "'", e);
		}
	}

	/**
	 * Delay the lookup of the {@link ExpressionParser} to prevent SEC-2136
	 *
	 * @return
	 */
	private ExpressionParser getParser() {
		if (this.parser != null) {
			return this.parser;
		}
		synchronized (parserLock) {
			this.parser = handler.getExpressionParser();
			this.handler = null;
		}
		return this.parser;
	}
}
