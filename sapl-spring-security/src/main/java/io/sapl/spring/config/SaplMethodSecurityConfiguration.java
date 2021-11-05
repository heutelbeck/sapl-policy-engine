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
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.AfterInvocationProvider;
import org.springframework.security.access.intercept.AfterInvocationManager;
import org.springframework.security.access.intercept.AfterInvocationProviderManager;
import org.springframework.security.access.method.MethodSecurityMetadataSource;
import org.springframework.security.access.vote.AbstractAccessDecisionManager;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.blocking.PostEnforcePolicyEnforcementPoint;
import io.sapl.spring.method.blocking.PostEnforcePolicyEnforcementPointProvider;
import io.sapl.spring.method.blocking.PreEnforcePolicyEnforcementPoint;
import io.sapl.spring.method.blocking.PreEnforcePolicyEnforcementPointVoter;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.metadata.SaplAttributeFactory;
import io.sapl.spring.method.metadata.SaplMethodSecurityMetadataSource;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * This configuration class adds blocking SAPL {@link PreEnforce} and {@link PostEnforce}
 * annotations to the global method security configuration.
 *
 * Classes may extend this class to customize the defaults, but must be sure to specify
 * the {@link EnableGlobalMethodSecurity} annotation on the subclass.
 */
@Slf4j
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class SaplMethodSecurityConfiguration extends GlobalMethodSecurityConfiguration {

	protected final ObjectFactory<PolicyDecisionPoint> pdpFactory;

	protected final ObjectFactory<ConstraintEnforcementService> constraintHandlerFactory;

	protected final ObjectFactory<ObjectMapper> objectMapperFactory;

	protected final ObjectFactory<AuthorizationSubscriptionBuilderService> subscriptionBuilderFactory;

	@Bean
	protected AuthorizationSubscriptionBuilderService authorizationSubscriptionBuilderService() {
		return new AuthorizationSubscriptionBuilderService(getExpressionHandler(), objectMapperFactory.getObject());
	}

	@Override
	protected AccessDecisionManager accessDecisionManager() {
		log.debug("Blocking SAPL method level pre-invocation security activated.");
		var baseManager = super.accessDecisionManager();

		var decisionVoters = new ArrayList<AccessDecisionVoter<?>>(((AbstractAccessDecisionManager) baseManager).getDecisionVoters());

		var policyAdvice = new PreEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		decisionVoters.add(new PreEnforcePolicyEnforcementPointVoter(policyAdvice));
		var manager = new AffirmativeBased(decisionVoters);
		manager.setAllowIfAllAbstainDecisions(true);
		return manager;
	}

	@Override
	protected AfterInvocationManager afterInvocationManager() {
		log.debug("Blocking SAPL method level after-invocation security activated.");
		var advice = new PostEnforcePolicyEnforcementPoint(pdpFactory, constraintHandlerFactory,
				subscriptionBuilderFactory);
		var provider = new PostEnforcePolicyEnforcementPointProvider(advice);

		var baseManager = super.afterInvocationManager();
		if (baseManager == null) {
			var invocationProviderManager = new AfterInvocationProviderManager();
			var afterInvocationProviders = new ArrayList<AfterInvocationProvider>();
			afterInvocationProviders.add(provider);
			invocationProviderManager.setProviders(afterInvocationProviders);
			return invocationProviderManager;
		}

		var invocationProviderManager = (AfterInvocationProviderManager) baseManager;
		List<AfterInvocationProvider> originalProviders = invocationProviderManager.getProviders();
		List<AfterInvocationProvider> afterInvocationProviders = new ArrayList<>();
		afterInvocationProviders.add(provider);
		afterInvocationProviders.addAll(originalProviders);
		invocationProviderManager.setProviders(afterInvocationProviders);
		return invocationProviderManager;

	}

	@Override
	protected MethodSecurityMetadataSource customMethodSecurityMetadataSource() {
		log.debug("SAPL MethodSecurityMetadataSource deployed.");
		return new SaplMethodSecurityMetadataSource(new SaplAttributeFactory(getExpressionHandler()));
	}

}
