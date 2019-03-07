package io.sapl.spring.method;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.security.core.Authentication;

/**
 * Performs policy enforcement and authorization logic before a method is
 * invoked.
 */
public interface PreInvocationEnforcementAdvice extends AopInfrastructureBean {
	/**
	 * The "before" advice which should be executed to perform policy enforcement to
	 * decide whether the method call is authorized. Required obligations and
	 * available advices are executed.
	 *
	 * @param authentication         the information on the principal on whose
	 *                               account the decision should be made
	 * @param mi                     the method invocation being attempted
	 * @param preInvocationAttribute the attribute built from the @PreEnforce
	 *                               annotation.
	 * @return true if authorized and obligations fulfilled, false otherwise
	 */
	boolean before(Authentication authentication, MethodInvocation mi,
			PreInvocationEnforcementAttribute preInvocationEnforcementAttribute);
}
