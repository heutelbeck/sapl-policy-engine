package io.sapl.spring.method.post;

import java.util.Collection;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.access.AfterInvocationProvider;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;

import io.sapl.spring.method.pre.PreInvocationEnforcementAttribute;

public class PostInvocationEnforcementProvider implements AfterInvocationProvider {
	protected final Log logger = LogFactory.getLog(getClass());

	private final PostInvocationEnforcementAdvice postAdvice;

	public PostInvocationEnforcementProvider(PostInvocationEnforcementAdvice postAdvice) {
		this.postAdvice = postAdvice;
	}

	@Override
	public boolean supports(ConfigAttribute attribute) {
		logger.info("Got asked if I support: " + attribute);
		return attribute instanceof PreInvocationEnforcementAttribute;
	}

	@Override
	public boolean supports(Class<?> clazz) {
		return true;
	}

	private PolicyBasedPostInvocationEnforcementAttribute findPostInvocationEnforcementAttribute(
			Collection<ConfigAttribute> config) {
		for (ConfigAttribute attribute : config) {
			if (attribute instanceof PolicyBasedPostInvocationEnforcementAttribute) {
				return (PolicyBasedPostInvocationEnforcementAttribute) attribute;
			}
		}
		return null;
	}

	@Override
	public Object decide(Authentication authentication, Object object, Collection<ConfigAttribute> attributes,
			Object returnedObject) {
		logger.info("post->auth      : " + authentication);
		for (ConfigAttribute a : attributes) {
			logger.info("post->attribute : " + a + " ... " + a.getClass().getName());
		}
		PolicyBasedPostInvocationEnforcementAttribute pia = findPostInvocationEnforcementAttribute(attributes);
		return postAdvice.after(authentication, (MethodInvocation) object, pia, returnedObject);
	}
}
