/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.springframework.aop.Advisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.core.GrantedAuthorityDefaults;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.blocking.PolicyEnforcementPointAroundMethodInterceptor;
import io.sapl.spring.method.metadata.SaplAttributeRegistry;
import io.sapl.spring.method.reactive.PostEnforcePolicyEnforcementPoint;
import io.sapl.spring.method.reactive.PreEnforcePolicyEnforcementPoint;
import io.sapl.spring.method.reactive.ReactiveSaplMethodInterceptor;
import io.sapl.spring.subscriptions.WebfluxAuthorizationSubscriptionBuilderService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sets up automatic PEP generation for Methods with reactive return types. Bean
 * can be customized by sub classing.
 */
@Slf4j
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class ReactiveSaplMethodSecurityConfiguration {

	@NonNull
	private final PolicyDecisionPoint pdp;

	@NonNull
	private final ConstraintEnforcementService constraintHandlerService;

	@NonNull
	private final ObjectMapper mapper;

	private GrantedAuthorityDefaults grantedAuthorityDefaults;

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	Advisor reactiveSaplMethodSecurityPolicyEnforcementPoint(SaplAttributeRegistry source,
			MethodSecurityExpressionHandler expressionHandler,
			WebfluxAuthorizationSubscriptionBuilderService authorizationSubscriptionBuilderService,
			PreEnforcePolicyEnforcementPoint preEnforcePolicyEnforcementPoint,
			PostEnforcePolicyEnforcementPoint postEnforcePolicyEnforcementPoint) {

		log.debug("Deploy ReactiveSaplMethodInterceptor");
		var policyEnforcementPoint = new ReactiveSaplMethodInterceptor(source, expressionHandler, pdp,
				constraintHandlerService, mapper, authorizationSubscriptionBuilderService,
				preEnforcePolicyEnforcementPoint, postEnforcePolicyEnforcementPoint);
		return PolicyEnforcementPointAroundMethodInterceptor.reactive(policyEnforcementPoint);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	ReactiveSaplMethodInterceptor securityMethodInterceptor(SaplAttributeRegistry source,
			MethodSecurityExpressionHandler handler,
			WebfluxAuthorizationSubscriptionBuilderService authorizationSubscriptionBuilderService,
			PreEnforcePolicyEnforcementPoint preEnforcePolicyEnforcementPoint,
			PostEnforcePolicyEnforcementPoint postEnforcePolicyEnforcementPoint) {
		return new ReactiveSaplMethodInterceptor(source, handler, pdp,
				constraintHandlerService, mapper, authorizationSubscriptionBuilderService,
				preEnforcePolicyEnforcementPoint, postEnforcePolicyEnforcementPoint);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	protected PreEnforcePolicyEnforcementPoint preEnforcePolicyEnforcementPoint(
			ConstraintEnforcementService constraintHandlerService) {
		return new PreEnforcePolicyEnforcementPoint(constraintHandlerService);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	protected PostEnforcePolicyEnforcementPoint postEnforcePolicyEnforcementPoint(PolicyDecisionPoint pdp,
			ConstraintEnforcementService constraintHandlerService,
			WebfluxAuthorizationSubscriptionBuilderService subscriptionBuilder) {
		return new PostEnforcePolicyEnforcementPoint(pdp, constraintHandlerService, subscriptionBuilder);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	protected WebfluxAuthorizationSubscriptionBuilderService authorizationSubscriptionBuilderService(
			MethodSecurityExpressionHandler methodSecurityHandler) {
		return new WebfluxAuthorizationSubscriptionBuilderService(methodSecurityHandler, mapper);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
		var handler = new DefaultMethodSecurityExpressionHandler();
		if (this.grantedAuthorityDefaults != null) {
			handler.setDefaultRolePrefix(this.grantedAuthorityDefaults.getRolePrefix());
		}
		return handler;
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	SaplAttributeRegistry saplAttributeRegistry(ObjectProvider<GrantedAuthorityDefaults> defaultsProvider,
			ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider, ApplicationContext context) {
		var exprProvider = expressionHandlerProvider
				.getIfAvailable(() -> defaultExpressionHandler(defaultsProvider, context));
		return new SaplAttributeRegistry(exprProvider);
	}

	private static MethodSecurityExpressionHandler defaultExpressionHandler(
			ObjectProvider<GrantedAuthorityDefaults> defaultsProvider, ApplicationContext context) {
		DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
		defaultsProvider.ifAvailable(d -> handler.setDefaultRolePrefix(d.getRolePrefix()));
		handler.setApplicationContext(context);
		return handler;
	}

	@Autowired(required = false)
	void setGrantedAuthorityDefaults(GrantedAuthorityDefaults grantedAuthorityDefaults) {
		this.grantedAuthorityDefaults = grantedAuthorityDefaults;
	}

}