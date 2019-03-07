package io.sapl.spring.method;

import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;

public class PostInvocationExpressionEnforcementAttribute extends AbstractExpressionBasedEnforcementAttribute
		implements PostInvocationEnforcementAttribute {

	public PostInvocationExpressionEnforcementAttribute(String subjectExpression, String actionExpression,
			String resourceExpression, String environmentExpression) throws ParseException {
		super(subjectExpression, actionExpression, resourceExpression, environmentExpression);
	}

	public PostInvocationExpressionEnforcementAttribute(Expression subjectExpression, Expression actionExpression,
			Expression resourceExpression, Expression environmentExpression) throws ParseException {
		super(subjectExpression, actionExpression, resourceExpression, environmentExpression);
	}
}
