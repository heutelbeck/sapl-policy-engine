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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import javax.servlet.http.HttpServletRequest;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.intercept.aopalliance.MethodSecurityMetadataSourceAdvisor;
import org.springframework.security.access.method.DelegatingMethodSecurityMetadataSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.util.MethodInvocationUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.PreEnforceAttribute;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.method.reactive.ReactiveSaplMethodInterceptor;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;

class ReactiveSaplMethodSecurityConfigurationTests {

	@Test
	void whenRan_thenBeansArePresent() {
		new ApplicationContextRunner().withUserConfiguration(SecurityCongiguration.class)
				.withBean(PolicyDecisionPoint.class, () -> mock(PolicyDecisionPoint.class))
				.withBean(ConstraintEnforcementService.class, () -> mock(ConstraintEnforcementService.class))
				.withBean(ObjectMapper.class, () -> mock(ObjectMapper.class)).run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(MethodSecurityMetadataSourceAdvisor.class);
					assertThat(context).hasSingleBean(DelegatingMethodSecurityMetadataSource.class);
					assertThat(context).hasSingleBean(ReactiveSaplMethodInterceptor.class);
					assertThat(context).hasSingleBean(AuthorizationSubscriptionBuilderService.class);
					assertThat(context).hasSingleBean(MethodSecurityExpressionHandler.class);
				});
	}

	@Test
	void whenRanWithAuthorityDefaults_thenBeansArePresent() {
		new ApplicationContextRunner().withUserConfiguration(SecurityCongiguration.class)
				.withBean(GrantedAuthorityDefaults.class, () -> new GrantedAuthorityDefaults("SOMETHING_"))
				.withBean(PolicyDecisionPoint.class, () -> mock(PolicyDecisionPoint.class))
				.withBean(ConstraintEnforcementService.class, () -> mock(ConstraintEnforcementService.class))
				.withBean(ObjectMapper.class, () -> mock(ObjectMapper.class)).run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(MethodSecurityMetadataSourceAdvisor.class);
					assertThat(context).hasSingleBean(DelegatingMethodSecurityMetadataSource.class);
					assertThat(context).hasSingleBean(ReactiveSaplMethodInterceptor.class);
					assertThat(context).hasSingleBean(AuthorizationSubscriptionBuilderService.class);
					assertThat(context).hasSingleBean(MethodSecurityExpressionHandler.class);
				});
	}

	@Test
	void whenRan_thenAuthorizationSubscriptionBuilderServiceCanLazyLoadMapper() {
		new ApplicationContextRunner().withUserConfiguration(SecurityCongiguration.class)
				.withBean(PolicyDecisionPoint.class, () -> mock(PolicyDecisionPoint.class))
				.withBean(ConstraintEnforcementService.class, () -> mock(ConstraintEnforcementService.class))
				.withBean(ObjectMapper.class, () -> {
					var mapper = new ObjectMapper();
					SimpleModule module = new SimpleModule();
					module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
					module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
					module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
					mapper.registerModule(module);
					return mapper;
				}).run(context -> {
					var invocation = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class,
							"publicVoid", null, null);
					var attribute = attribute(null, null, null, null, Object.class);
					var authentication = new AnonymousAuthenticationToken("key", "anonymous",
							AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
					var actual = context.getBean(AuthorizationSubscriptionBuilderService.class)
							.constructAuthorizationSubscription(authentication, invocation, attribute);
					assertNotNull(actual);
				});
	}

	private SaplAttribute attribute(String subject, String action, String resource, String environment, Class<?> type) {
		return new PreEnforceAttribute(parameterToExpression(subject), parameterToExpression(action),
				parameterToExpression(resource), parameterToExpression(environment), type);
	}

	private Expression parameterToExpression(String parameter) {
		var parser = new SpelExpressionParser();
		return parameter == null || parameter.isEmpty() ? null : parser.parseExpression(parameter);
	}

	@EnableReactiveSaplMethodSecurity
	public static class SecurityCongiguration {
	}

	public static class TestClass {
		public void publicVoid() {
		}
	}
}
