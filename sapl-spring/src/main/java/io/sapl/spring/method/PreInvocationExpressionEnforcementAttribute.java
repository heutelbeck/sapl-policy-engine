package io.sapl.spring.method;

import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;

public class PreInvocationExpressionEnforcementAttribute extends AbstractExpressionBasedEnforcementAttribute
		implements PreInvocationEnforcementAttribute {

	public PreInvocationExpressionEnforcementAttribute(String subjectExpression, String actionExpression,
			String resourceExpression, String environmentExpression) throws ParseException {
		super(subjectExpression, actionExpression, resourceExpression, environmentExpression);
	}

	public PreInvocationExpressionEnforcementAttribute(Expression subjectExpression, Expression actionExpression,
			Expression resourceExpression, Expression environmentExpression) throws ParseException {
		super(subjectExpression, actionExpression, resourceExpression, environmentExpression);
	}

}
