package io.sapl.spring.method.pre;

import java.util.Collection;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;

public class PreInvocationEnforcementAdviceVoter
		implements AccessDecisionVoter<MethodInvocation> {

	private final PreInvocationEnforcementAdvice preAdvice;

	public PreInvocationEnforcementAdviceVoter(PreInvocationEnforcementAdvice pre) {
		preAdvice = pre;
	}

	@Override
	public boolean supports(ConfigAttribute attribute) {
		return attribute instanceof PolicyBasedPreInvocationEnforcementAttribute;
	}

	@Override
	public boolean supports(Class<?> clazz) {
		return MethodInvocation.class.isAssignableFrom(clazz);
	}

	@Override
	public int vote(Authentication authentication, MethodInvocation method,
			Collection<ConfigAttribute> attributes) {
		PolicyBasedPreInvocationEnforcementAttribute preAttr = findPreInvocationEnforcementAttribute(
				attributes);

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
			if (attribute instanceof PolicyBasedPreInvocationEnforcementAttribute) {
				return (PolicyBasedPreInvocationEnforcementAttribute) attribute;
			}
		}
		return null;
	}

}
