package io.sapl.spring.method;

import org.springframework.expression.Expression;
import org.springframework.security.access.ConfigAttribute;

/**
 * Interface for attributes which are created from @PreEnforce @PostEnforce
 * annotations.
 */
public interface EnforcementAttribute extends ConfigAttribute {
	Expression getSubjectExpression();

	Expression getActionExpression();

	Expression getResourceExpression();

	Expression getEnvironmentExpression();
}