/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.spring.method.post;

import java.util.Collection;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.AfterInvocationProvider;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;

public class PostInvocationEnforcementProvider implements AfterInvocationProvider {

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
		return clazz.isAssignableFrom(MethodInvocation.class);
	}

	@Override
	public Object decide(Authentication authentication, Object object, Collection<ConfigAttribute> attributes,
			Object returnedObject) {
		PolicyBasedPostInvocationEnforcementAttribute pia = findPostInvocationEnforcementAttribute(attributes);
		if (pia == null) {
			return returnedObject;
		}
		else {
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
