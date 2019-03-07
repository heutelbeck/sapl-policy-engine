package io.sapl.spring.method;

import java.util.Collection;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;

public class PreInvocationEnforcementAdviceVoter implements AccessDecisionVoter<MethodInvocation> {
	protected final Log logger = LogFactory.getLog(getClass());

	private final PreInvocationEnforcementAdvice preAdvice;

	public PreInvocationEnforcementAdviceVoter(PreInvocationEnforcementAdvice pre) {
		this.preAdvice = pre;
	}

	public boolean supports(ConfigAttribute attribute) {
		logger.info("Got asked if I support: " + attribute);
		return attribute instanceof PreInvocationEnforcementAttribute;
	}

	public boolean supports(Class<?> clazz) {
		return MethodInvocation.class.isAssignableFrom(clazz);
	}

	public int vote(Authentication authentication, MethodInvocation method, Collection<ConfigAttribute> attributes) {
		logger.info("voting->auth      : " + authentication);
		logger.info("voting->method    : " + method);
		for (ConfigAttribute a : attributes) {
			logger.info("voting->attribute : " + a + " ... "+a.getClass().getName());
		}
		PreInvocationEnforcementAttribute preAttr = findPreInvocationEnforcementAttribute(attributes);

		if (preAttr == null) {
			// No matching attribute found => abstain
			return ACCESS_ABSTAIN;
		}

		boolean permitted = preAdvice.before(authentication, method, preAttr);

		return permitted ? ACCESS_GRANTED : ACCESS_DENIED;
	}

	private PreInvocationEnforcementAttribute findPreInvocationEnforcementAttribute(
			Collection<ConfigAttribute> config) {
		for (ConfigAttribute attribute : config) {
			if (attribute instanceof PreInvocationEnforcementAttribute) {
				return (PreInvocationEnforcementAttribute) attribute;
			}
		}
		return null;
	}
}
