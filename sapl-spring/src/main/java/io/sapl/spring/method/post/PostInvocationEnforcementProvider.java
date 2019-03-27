package io.sapl.spring.method.post;

import java.util.Collection;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.access.AfterInvocationProvider;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;

public class PostInvocationEnforcementProvider implements AfterInvocationProvider {
	protected final Log logger = LogFactory.getLog(getClass());

	private final PostInvocationEnforcementAdvice postAdvice;

	public PostInvocationEnforcementProvider(PostInvocationEnforcementAdvice postAdvice) {
		this.postAdvice = postAdvice;
	}

	@Override
	public boolean supports(ConfigAttribute attribute) {
		return attribute instanceof PostInvocationEnforcementAttribute;
	}

	@Override
	public boolean supports(Class<?> clazz) {
		return true;
	}

	@Override
	public Object decide(Authentication authentication, Object object, Collection<ConfigAttribute> attributes,
			Object returnedObject) {
		PolicyBasedPostInvocationEnforcementAttribute pia = findPostInvocationEnforcementAttribute(attributes);
		if (pia == null) {
			return returnedObject;
		} else {
			return postAdvice.after(authentication, (MethodInvocation) object, pia, returnedObject);
		}
	}

	private PolicyBasedPostInvocationEnforcementAttribute findPostInvocationEnforcementAttribute(
			Collection<ConfigAttribute> config) {
		for (ConfigAttribute attribute : config) {
			if (supports(attribute)) {
				return (PolicyBasedPostInvocationEnforcementAttribute) attribute;
			}
		}
		return null;
	}
}
