package io.sapl.spring.method.post;

import org.springframework.expression.Expression;

import io.sapl.spring.method.AbstractPolicyBasedEnforcementAttribute;

public class PolicyBasedPostInvocationEnforcementAttribute extends AbstractPolicyBasedEnforcementAttribute
		implements PostInvocationEnforcementAttribute {

	public PolicyBasedPostInvocationEnforcementAttribute(String subjectExpression, String actionExpression,
			String resourceExpression, String environmentExpression) {
		super(subjectExpression, actionExpression, resourceExpression, environmentExpression);
	}

	public PolicyBasedPostInvocationEnforcementAttribute(Expression subjectExpression, Expression actionExpression,
			Expression resourceExpression, Expression environmentExpression) {
		super(subjectExpression, actionExpression, resourceExpression, environmentExpression);
	}

}
