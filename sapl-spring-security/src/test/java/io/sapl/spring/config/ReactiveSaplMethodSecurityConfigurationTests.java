/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.util.MethodInvocationUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.method.metadata.SaplAttributeRegistry;
import io.sapl.spring.method.reactive.ReactiveSaplMethodInterceptor;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;
import io.sapl.spring.subscriptions.WebfluxAuthorizationSubscriptionBuilderService;
import jakarta.servlet.http.HttpServletRequest;

class ReactiveSaplMethodSecurityConfigurationTests {

    @Test
    void whenRan_thenBeansArePresent() {
        new ApplicationContextRunner().withUserConfiguration(SecurityConfiguration.class)
                .withBean(PolicyDecisionPoint.class, () -> mock(PolicyDecisionPoint.class))
                .withBean(ConstraintEnforcementService.class, () -> mock(ConstraintEnforcementService.class))
                .withBean(ObjectMapper.class, () -> mock(ObjectMapper.class)).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SaplAttributeRegistry.class);
                    assertThat(context).hasSingleBean(ReactiveSaplMethodInterceptor.class);
                    assertThat(context).hasSingleBean(WebfluxAuthorizationSubscriptionBuilderService.class);
                    assertThat(context).hasSingleBean(MethodSecurityExpressionHandler.class);
                });
    }

    @Test
    void whenRanWithAuthorityDefaults_thenBeansArePresent() {
        new ApplicationContextRunner().withUserConfiguration(SecurityConfiguration.class)
                .withBean(GrantedAuthorityDefaults.class, () -> new GrantedAuthorityDefaults("SOMETHING_"))
                .withBean(PolicyDecisionPoint.class, () -> mock(PolicyDecisionPoint.class))
                .withBean(ConstraintEnforcementService.class, () -> mock(ConstraintEnforcementService.class))
                .withBean(ObjectMapper.class, () -> mock(ObjectMapper.class)).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SaplAttributeRegistry.class);
                    assertThat(context).hasSingleBean(ReactiveSaplMethodInterceptor.class);
                    assertThat(context).hasSingleBean(WebfluxAuthorizationSubscriptionBuilderService.class);
                    assertThat(context).hasSingleBean(MethodSecurityExpressionHandler.class);
                });
    }

    @Test
    void whenRan_thenAuthorizationSubscriptionBuilderServiceCanLazyLoadMapper() {
        new ApplicationContextRunner().withUserConfiguration(SecurityConfiguration.class)
                .withBean(PolicyDecisionPoint.class, () -> mock(PolicyDecisionPoint.class))
                .withBean(ConstraintEnforcementService.class, () -> mock(ConstraintEnforcementService.class))
                .withBean(ObjectMapper.class, () -> {
                    final var mapper = new ObjectMapper();
                    SimpleModule module = new SimpleModule();
                    module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
                    module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
                    module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
                    mapper.registerModule(module);
                    return mapper;
                }).run(context -> {
                    final var invocation = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class, "publicVoid",
                            null, null);
                    final var attribute = attribute(null, null, null, null, Object.class);
                    final var actual = context.getBean(WebfluxAuthorizationSubscriptionBuilderService.class)
                            .reactiveConstructAuthorizationSubscription(invocation, attribute);
                    assertNotNull(actual);
                });
    }

    private SaplAttribute attribute(String subject, String action, String resource, String environment, Class<?> type) {
        return new SaplAttribute(PreEnforce.class, parameterToExpression(subject), parameterToExpression(action),
                parameterToExpression(resource), parameterToExpression(environment), type);
    }

    private Expression parameterToExpression(String parameter) {
        final var parser = new SpelExpressionParser();
        return parameter == null || parameter.isEmpty() ? null : parser.parseExpression(parameter);
    }

    @EnableReactiveSaplMethodSecurity
    public static class SecurityConfiguration {

    }

    public static class TestClass {

        public void publicVoid() {
            // NOOP test dummy
        }

    }

}
