package io.sapl.spring.method.pre;

import java.util.Collection;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PreInvocationEnforcementAdviceVoter implements AccessDecisionVoter<MethodInvocation> {
	private final PreInvocationEnforcementAdvice preAdvice;

	public PreInvocationEnforcementAdviceVoter(PreInvocationEnforcementAdvice pre) {
		preAdvice = pre;
	}

	@Override
	public boolean supports(ConfigAttribute attribute) {
		LOGGER.info("do I support ? {}", attribute instanceof PolicyBasedPreInvocationEnforcementAttribute);
		return attribute instanceof PolicyBasedPreInvocationEnforcementAttribute;
	}

	@Override
	public boolean supports(Class<?> clazz) {
		LOGGER.info("sup? {}", clazz.getSimpleName());
		return MethodInvocation.class.isAssignableFrom(clazz);
	}

	@Override
	public int vote(Authentication authentication, MethodInvocation method, Collection<ConfigAttribute> attributes) {
		PolicyBasedPreInvocationEnforcementAttribute preAttr = findPreInvocationEnforcementAttribute(attributes);

		if (preAttr == null) {
			// No matching attribute found => abstain
			return ACCESS_ABSTAIN;
		}

		boolean permitted = preAdvice.before(authentication, method, preAttr);

		int vote = permitted ? ACCESS_GRANTED : ACCESS_DENIED;

		LOGGER.info("Voting: {}", vote);
		return vote;
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
