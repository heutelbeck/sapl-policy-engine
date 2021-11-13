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

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.annotation.Role;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.ExpressionBasedAnnotationAttributeFactory;
import org.springframework.security.access.expression.method.ExpressionBasedPostInvocationAdvice;
import org.springframework.security.access.expression.method.ExpressionBasedPreInvocationAdvice;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.intercept.aopalliance.MethodSecurityMetadataSourceAdvisor;
import org.springframework.security.access.method.AbstractMethodSecurityMetadataSource;
import org.springframework.security.access.method.DelegatingMethodSecurityMetadataSource;
import org.springframework.security.access.method.MethodSecurityMetadataSource;
import org.springframework.security.access.prepost.PrePostAdviceReactiveMethodInterceptor;
import org.springframework.security.access.prepost.PrePostAnnotationSecurityMetadataSource;
import org.springframework.security.config.core.GrantedAuthorityDefaults;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.SaplAttributeFactory;
import io.sapl.spring.method.metadata.SaplMethodSecurityMetadataSource;
import io.sapl.spring.method.reactive.PostEnforcePolicyEnforcementPoint;
import io.sapl.spring.method.reactive.PreEnforcePolicyEnforcementPoint;
import io.sapl.spring.method.reactive.ReactiveSaplMethodInterceptor;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Sets up automatic PEP generation for Methods with reactive return types. Bean can be
 * customized by sub classing.
 */
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
public class ReactiveSaplMethodSecurityConfiguration implements ImportAware {

	@NonNull
	private final PolicyDecisionPoint pdp;

	@NonNull
	private final ConstraintEnforcementService constraintHandlerService;

	@NonNull
	private final ObjectMapper mapper;

	private int advisorOrder;

	private GrantedAuthorityDefaults grantedAuthorityDefaults;

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	MethodSecurityMetadataSourceAdvisor methodSecurityInterceptor(AbstractMethodSecurityMetadataSource source) {
		MethodSecurityMetadataSourceAdvisor advisor = new MethodSecurityMetadataSourceAdvisor(
				"securityMethodInterceptor", source, "methodMetadataSource");
		advisor.setOrder(this.advisorOrder);
		return advisor;
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	DelegatingMethodSecurityMetadataSource methodMetadataSource(
			MethodSecurityExpressionHandler methodSecurityExpressionHandler) {
		ExpressionBasedAnnotationAttributeFactory attributeFactory = new ExpressionBasedAnnotationAttributeFactory(
				methodSecurityExpressionHandler);
		PrePostAnnotationSecurityMetadataSource prePostSource = new PrePostAnnotationSecurityMetadataSource(
				attributeFactory);
		SaplMethodSecurityMetadataSource sapl = new SaplMethodSecurityMetadataSource(
				new SaplAttributeFactory(methodSecurityExpressionHandler));
		return new DelegatingMethodSecurityMetadataSource(Arrays.asList(sapl, prePostSource));
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	ReactiveSaplMethodInterceptor securityMethodInterceptor(MethodSecurityMetadataSource source,
			MethodSecurityExpressionHandler handler,
			AuthorizationSubscriptionBuilderService authorizationSubscriptionBuilderService,
			PreEnforcePolicyEnforcementPoint preEnforcePolicyEnforcementPoint,
			PostEnforcePolicyEnforcementPoint postEnforcePolicyEnforcementPoint) {
		var springSecurityPostAdvice = new ExpressionBasedPostInvocationAdvice(handler);
		var springSecurityPreAdvice = new ExpressionBasedPreInvocationAdvice();
		springSecurityPreAdvice.setExpressionHandler(handler);
		var springPrePostInterceptor = new PrePostAdviceReactiveMethodInterceptor(source, springSecurityPreAdvice,
				springSecurityPostAdvice);
		return new ReactiveSaplMethodInterceptor(springPrePostInterceptor, source, handler, pdp,
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
			AuthorizationSubscriptionBuilderService subscriptionBuilder) {
		return new PostEnforcePolicyEnforcementPoint(pdp, constraintHandlerService, subscriptionBuilder);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	protected AuthorizationSubscriptionBuilderService authorizationSubscriptionBuilderService(
			MethodSecurityExpressionHandler methodSecurityHandler) {
		return new AuthorizationSubscriptionBuilderService(methodSecurityHandler, mapper);
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

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		var annotationAttributes = importMetadata
				.getAnnotationAttributes(EnableReactiveSaplMethodSecurity.class.getName());
		if (annotationAttributes != null)
			this.advisorOrder = (int) annotationAttributes.get("order");
	}

	@Autowired(required = false)
	void setGrantedAuthorityDefaults(GrantedAuthorityDefaults grantedAuthorityDefaults) {
		this.grantedAuthorityDefaults = grantedAuthorityDefaults;
	}

}