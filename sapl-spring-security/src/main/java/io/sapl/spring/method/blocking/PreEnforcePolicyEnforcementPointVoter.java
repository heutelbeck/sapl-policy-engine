/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;

import io.sapl.spring.method.metadata.PreEnforceAttribute;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PreEnforcePolicyEnforcementPointVoter implements AccessDecisionVoter<MethodInvocation> {

	private final PreEnforcePolicyEnforcementPoint preEnforcePEP;

	@Override
	public boolean supports(ConfigAttribute attribute) {
		return attribute instanceof PreEnforceAttribute;
	}

	@Override
	public boolean supports(Class<?> clazz) {
		return MethodInvocation.class.isAssignableFrom(clazz);
	}

	@Override
	public int vote(Authentication authentication, MethodInvocation method, Collection<ConfigAttribute> attributes) {
		var preInvocationAttribute = findPreInvocationEnforcementAttribute(attributes);

		var noPreInvocationAttributeFound = preInvocationAttribute == null;
		if (noPreInvocationAttributeFound)
			return ACCESS_ABSTAIN;

		var accessGranted = preEnforcePEP.before(authentication, method, preInvocationAttribute);

		return accessGranted ? ACCESS_GRANTED : ACCESS_DENIED;
	}

	private PreEnforceAttribute findPreInvocationEnforcementAttribute(Collection<ConfigAttribute> config) {
		for (ConfigAttribute attribute : config)
			if (attribute instanceof PreEnforceAttribute)
				return (PreEnforceAttribute) attribute;
		return null;
	}

}
