package io.sapl.spring.method.pre;

import java.util.Collection;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;

import io.sapl.spring.method.post.PolicyBasedPostInvocationEnforcementAttribute;

public class PreInvocationEnforcementAdviceVoter implements AccessDecisionVoter<MethodInvocation> {
	protected final Log logger = LogFactory.getLog(getClass());

	private final PreInvocationEnforcementAdvice preAdvice;

	public PreInvocationEnforcementAdviceVoter(PreInvocationEnforcementAdvice pre) {
		this.preAdvice = pre;
	}

	public boolean supports(ConfigAttribute attribute) {
		logger.info("Got asked if I support: " + attribute);
		return attribute instanceof PolicyBasedPostInvocationEnforcementAttribute;
	}

	public boolean supports(Class<?> clazz) {
		return MethodInvocation.class.isAssignableFrom(clazz);
	}

	public int vote(Authentication authentication, MethodInvocation method, Collection<ConfigAttribute> attributes) {
		logger.info("voting->auth      : " + authentication);
		logger.info("voting->method    : " + method);
		for (ConfigAttribute a : attributes) {
			logger.info("voting->attribute : " + a + " ... " + a.getClass().getName());
		}
		PolicyBasedPreInvocationEnforcementAttribute preAttr = findPreInvocationEnforcementAttribute(attributes);

		if (preAttr == null) {
			// No matching attribute found => abstain
			return ACCESS_ABSTAIN;
		}

		boolean permitted = preAdvice.before(authentication, method, preAttr);

		return permitted ? ACCESS_GRANTED : ACCESS_DENIED;
	}

	private PolicyBasedPreInvocationEnforcementAttribute findPreInvocationEnforcementAttribute(
			Collection<ConfigAttribute> config) {
		for (ConfigAttribute attribute : config) {
			if (attribute instanceof PolicyBasedPostInvocationEnforcementAttribute) {
				return (PolicyBasedPreInvocationEnforcementAttribute) attribute;
			}
		}
		return null;
	}
}
