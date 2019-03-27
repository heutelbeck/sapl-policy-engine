package io.sapl.spring.method.pre;

import java.util.Collection;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;

import io.sapl.spring.method.post.PolicyBasedPostInvocationEnforcementAttribute;

public class PreInvocationEnforcementAdviceVoter implements AccessDecisionVoter<MethodInvocation> {
	private final PreInvocationEnforcementAdvice preAdvice;

	public PreInvocationEnforcementAdviceVoter(PreInvocationEnforcementAdvice pre) {
		this.preAdvice = pre;
	}

	public boolean supports(ConfigAttribute attribute) {
		return attribute instanceof PolicyBasedPostInvocationEnforcementAttribute;
	}

	public boolean supports(Class<?> clazz) {
		return MethodInvocation.class.isAssignableFrom(clazz);
	}

	public int vote(Authentication authentication, MethodInvocation method, Collection<ConfigAttribute> attributes) {
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
			if (attribute instanceof PolicyBasedPreInvocationEnforcementAttribute) {
				return (PolicyBasedPreInvocationEnforcementAttribute) attribute;
			}
		}
		return null;
	}
}
