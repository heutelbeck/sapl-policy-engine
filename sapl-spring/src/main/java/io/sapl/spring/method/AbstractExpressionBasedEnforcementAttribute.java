package io.sapl.spring.method;

import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.ConfigAttribute;

import lombok.Getter;

@Getter
public abstract class AbstractExpressionBasedEnforcementAttribute implements ConfigAttribute {

	private final static SpelExpressionParser PARSER = new SpelExpressionParser();

	private final Expression subjectExpression;
	private final Expression actionExpression;
	private final Expression resourceExpression;
	private final Expression environmentExpression;

	AbstractExpressionBasedEnforcementAttribute(String subjectExpression, String actionExpression,
			String resourceExpression, String environmentExpression) throws ParseException {
		this.subjectExpression = subjectExpression == null ? null : PARSER.parseExpression(subjectExpression);
		this.actionExpression = actionExpression == null ? null : PARSER.parseExpression(actionExpression);
		this.resourceExpression = resourceExpression == null ? null : PARSER.parseExpression(resourceExpression);
		this.environmentExpression = environmentExpression == null ? null : PARSER.parseExpression(subjectExpression);
	}

	AbstractExpressionBasedEnforcementAttribute(Expression subjectExpression, Expression actionExpression,
			Expression resourceExpression, Expression environmentExpression) throws ParseException {
		this.subjectExpression = subjectExpression;
		this.actionExpression = actionExpression;
		this.resourceExpression = resourceExpression;
		this.environmentExpression = environmentExpression;
	}

	@Override
	public String getAttribute() {
		return null;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[enforcee: subject='")
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
