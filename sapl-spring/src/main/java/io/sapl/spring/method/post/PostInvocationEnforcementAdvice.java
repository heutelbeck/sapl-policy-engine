package io.sapl.spring.method.post;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.security.core.Authentication;

/**
 * Performs policy enforcement and authorization logic after a method is invoked.
 */
public interface PostInvocationEnforcementAdvice extends AopInfrastructureBean {

	Object after(Authentication authentication, MethodInvocation mi, PolicyBasedPostInvocationEnforcementAttribute pia,
			Object returnedObject);

}
