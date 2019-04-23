package io.sapl.spring.method;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.ConfigAttribute;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Superclass for the SAPL ConfigAttributes, taking care of initializing SpEL
 * expression upon instantiation.
 */
@Getter
@EqualsAndHashCode
public abstract class AbstractPolicyBasedEnforcementAttribute implements ConfigAttribute {

	private static final SpelExpressionParser PARSER = new SpelExpressionParser();

	private final Expression subjectExpression;
	private final Expression actionExpression;
	private final Expression resourceExpression;
	private final Expression environmentExpression;

	protected AbstractPolicyBasedEnforcementAttribute(String subjectExpression, String actionExpression,
			String resourceExpression, String environmentExpression) {
		this.subjectExpression = subjectExpression == null ? null : PARSER.parseExpression(subjectExpression);
		this.actionExpression = actionExpression == null ? null : PARSER.parseExpression(actionExpression);
		this.resourceExpression = resourceExpression == null ? null : PARSER.parseExpression(resourceExpression);
		this.environmentExpression = environmentExpression == null ? null : PARSER.parseExpression(subjectExpression);
	}

	protected AbstractPolicyBasedEnforcementAttribute(Expression subjectExpression, Expression actionExpression,
			Expression resourceExpression, Expression environmentExpression) {
		this.subjectExpression = subjectExpression;
		this.actionExpression = actionExpression;
		this.resourceExpression = resourceExpression;
		this.environmentExpression = environmentExpression;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.springframework.security.access.ConfigAttribute#getAttribute()
	 */
	@Override
	public String getAttribute() {
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append('[').append(getClass().getSimpleName()).append(": subject='")
				.append(subjectExpression == null ? "null" : subjectExpression.getExpressionString());
		sb.append("', action='").append(actionExpression == null ? "null" : actionExpression.getExpressionString());
		sb.append("', resource='")
				.append(resourceExpression == null ? "null" : resourceExpression.getExpressionString());
		sb.append("', environment='")
				.append(environmentExpression == null ? "null" : environmentExpression.getExpressionString())
				.append("']");
		return sb.toString();
	}
}
