package io.sapl.spring.method.pre;

import org.springframework.expression.Expression;

import io.sapl.spring.method.AbstractPolicyBasedEnforcementAttribute;

public class PolicyBasedPreInvocationEnforcementAttribute extends AbstractPolicyBasedEnforcementAttribute
		implements PreInvocationEnforcementAttribute {

	private static final long serialVersionUID = 2046032680569217119L;

	public PolicyBasedPreInvocationEnforcementAttribute(String subjectExpression, String actionExpression,
			String resourceExpression, String environmentExpression) {
		super(subjectExpression, actionExpression, resourceExpression, environmentExpression);
	}

	public PolicyBasedPreInvocationEnforcementAttribute(Expression subjectExpression, Expression actionExpression,
			Expression resourceExpression, Expression environmentExpression) {
		super(subjectExpression, actionExpression, resourceExpression, environmentExpression);
	}

}
