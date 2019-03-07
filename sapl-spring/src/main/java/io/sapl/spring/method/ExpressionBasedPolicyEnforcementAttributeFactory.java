package io.sapl.spring.method;

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
			return new PreInvocationExpressionEnforcementAttribute(getParser().parseExpression(subjectAttribute),
					getParser().parseExpression(actionAttribute), getParser().parseExpression(resourceAttribute),
					getParser().parseExpression(environmentAttribute));
		} catch (ParseException e) {
			throw new IllegalArgumentException("Failed to parse expression '" + e.getExpressionString() + "'", e);
		}
	}

	@Override
	public PostInvocationEnforcementAttribute createPostInvocationAttribute(String subjectAttribute,
			String actionAttribute, String resourceAttribute, String environmentAttribute) {
		try {
			return new PostInvocationExpressionEnforcementAttribute(getParser().parseExpression(subjectAttribute),
					getParser().parseExpression(actionAttribute), getParser().parseExpression(resourceAttribute),
					getParser().parseExpression(environmentAttribute));
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
