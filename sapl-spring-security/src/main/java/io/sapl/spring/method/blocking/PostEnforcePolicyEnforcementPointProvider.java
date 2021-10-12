/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.method.blocking;

import java.util.Collection;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.AfterInvocationProvider;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;

import io.sapl.spring.method.metadata.PostEnforceAttribute;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PostEnforcePolicyEnforcementPointProvider implements AfterInvocationProvider {

	private final PostEnforcePolicyEnforcementPoint postPEP;

	@Override
	public boolean supports(ConfigAttribute attribute) {
		return attribute instanceof PostEnforceAttribute;
	}

	@Override
	public boolean supports(Class<?> clazz) {
		return clazz.isAssignableFrom(MethodInvocation.class);
	}

	@Override
	public Object decide(Authentication authentication, Object object, Collection<ConfigAttribute> attributes,
			Object returnedObject) {
		var pia = findPostInvocationEnforcementAttribute(attributes);
		if (pia == null)
			return returnedObject;

		return postPEP.after(authentication, (MethodInvocation) object, pia, returnedObject);
	}

	private PostEnforceAttribute findPostInvocationEnforcementAttribute(Collection<ConfigAttribute> config) {
		for (var attribute : config)
			if (supports(attribute))
				return (PostEnforceAttribute) attribute;
		return null;
	}

}
