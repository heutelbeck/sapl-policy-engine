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
package io.sapl.spring.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.AfterInvocationProvider;
import org.springframework.security.access.intercept.AfterInvocationManager;
import org.springframework.security.access.intercept.AfterInvocationProviderManager;
import org.springframework.security.access.method.MethodSecurityMetadataSource;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.access.vote.AuthenticatedVoter;
import org.springframework.security.access.vote.RoleVoter;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintHandlerService;
import io.sapl.spring.method.PolicyBasedEnforcementAttributeFactory;
import io.sapl.spring.method.PolicyEnforcementMethodSecurityMetadataSource;
import io.sapl.spring.method.post.PolicyBasedPostInvocationEnforcementAdvice;
import io.sapl.spring.method.post.PostInvocationEnforcementProvider;
import io.sapl.spring.method.pre.PolicyBasedPreInvocationEnforcementAdvice;
import io.sapl.spring.method.pre.PreInvocationEnforcementAdviceVoter;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@EnableGlobalMethodSecurity
public class MethodSecurityAutoConfiguration extends GlobalMethodSecurityConfiguration {

	protected final ObjectFactory<PolicyDecisionPoint> pdpFactory;

	protected final ObjectFactory<ConstraintHandlerService> constraintHandlerFactory;

	protected final ObjectFactory<ObjectMapper> objectMapperFactory;

	@Override
	protected AccessDecisionManager accessDecisionManager() {
		List<AccessDecisionVoter<?>> decisionVoters = new ArrayList<>();
		PolicyBasedPreInvocationEnforcementAdvice policyAdvice = new PolicyBasedPreInvocationEnforcementAdvice(
				pdpFactory, constraintHandlerFactory, objectMapperFactory);
		policyAdvice.setExpressionHandler(getExpressionHandler());
		decisionVoters.add(new PreInvocationEnforcementAdviceVoter(policyAdvice));
		decisionVoters.add(new RoleVoter());
		decisionVoters.add(new AuthenticatedVoter());
		AffirmativeBased manager = new AffirmativeBased(decisionVoters);
		manager.setAllowIfAllAbstainDecisions(true);
		return manager;
	}

	@Override
	protected AfterInvocationManager afterInvocationManager() {
		PolicyBasedPostInvocationEnforcementAdvice advice = new PolicyBasedPostInvocationEnforcementAdvice(pdpFactory,
				constraintHandlerFactory, objectMapperFactory);
		advice.setExpressionHandler(getExpressionHandler());
		PostInvocationEnforcementProvider provider = new PostInvocationEnforcementProvider(advice);

		AfterInvocationProviderManager invocationProviderManager = (AfterInvocationProviderManager) super.afterInvocationManager();
		if (invocationProviderManager == null) {
			invocationProviderManager = new AfterInvocationProviderManager();
			List<AfterInvocationProvider> afterInvocationProviders = new ArrayList<>();
			afterInvocationProviders.add(provider);
			invocationProviderManager.setProviders(afterInvocationProviders);
		} else {
			List<AfterInvocationProvider> originalProviders = invocationProviderManager.getProviders();
			List<AfterInvocationProvider> afterInvocationProviders = new ArrayList<>();
			afterInvocationProviders.add(provider);
			afterInvocationProviders.addAll(originalProviders);
			invocationProviderManager.setProviders(afterInvocationProviders);
		}
		return invocationProviderManager;
	}

	@Override
	protected MethodSecurityMetadataSource customMethodSecurityMetadataSource() {
		return new PolicyEnforcementMethodSecurityMetadataSource(
				new PolicyBasedEnforcementAttributeFactory(getExpressionHandler()));
	}

}
