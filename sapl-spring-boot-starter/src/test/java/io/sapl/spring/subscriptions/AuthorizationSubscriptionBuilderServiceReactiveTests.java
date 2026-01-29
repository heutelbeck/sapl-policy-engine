/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.subscriptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.util.MethodInvocationUtils;
import org.springframework.web.server.ServerWebExchange;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.SaplVersion;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import lombok.val;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

class AuthorizationSubscriptionBuilderServiceReactiveTests {

    private Authentication                          authentication;
    private AuthorizationSubscriptionBuilderService defaultWebfluxBuilderUnderTest;
    private MethodInvocation                        invocation;
    private ObjectMapper                            mapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void beforeEach() {
        mapper = new ObjectMapper();
        val user = new User("the username", "the password", true, true, true, true,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        authentication                 = new UsernamePasswordAuthenticationToken(user, "the credentials");
        defaultWebfluxBuilderUnderTest = new AuthorizationSubscriptionBuilderService(
                new DefaultMethodSecurityExpressionHandler(), mapper);
        val mockExpressionHandlerProvider = mock(ObjectProvider.class);
        when(mockExpressionHandlerProvider.getIfAvailable(any()))
                .thenReturn(new DefaultMethodSecurityExpressionHandler());
        val mockMapperProvider = mock(ObjectProvider.class);
        when(mockMapperProvider.getIfAvailable(any())).thenReturn(mapper);
        invocation = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class, "publicVoid", null, null);
    }

    private static JsonNode toJson(Value value) {
        if (value instanceof UndefinedValue) {
            return JsonNodeFactory.instance.nullNode();
        }
        return ValueJsonMarshaller.toJsonNode(value);
    }

    @Test
    void when_multiArguments_then_methodIsInAction() {
        val serverWebExchange   = MockServerWebExchange.from(MockServerHttpRequest.get("/foo/bar"));
        val securityContext     = new MockSecurityContext(authentication);
        val attribute           = attribute(null, null, null, null, Object.class);
        val multiArgsInvocation = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class,
                "publicSeveralArgs", new Class<?>[] { Integer.class, String.class }, new Object[] { 1, "X" });
        val subscription        = defaultWebfluxBuilderUnderTest
                .reactiveConstructAuthorizationSubscription(multiArgsInvocation, attribute)
                .contextWrite(Context.of(ServerWebExchange.class, serverWebExchange))
                .contextWrite(Context.of(SecurityContext.class, Mono.just(securityContext))).block();

        var subject = toJson(subscription.subject());
        assertThat(subject.get("name").asString()).isEqualTo("the username");
        assertThat(subject.has("credentials")).isFalse();
        assertThat(subject.get("principal").has("password")).isFalse();

        var action = toJson(subscription.action());
        assertThat(action.get("java").get("name").asString()).isEqualTo("publicSeveralArgs");
        assertThat(action.get("java").get("arguments")).isNotNull();

        var resource = toJson(subscription.resource());
        assertThat(resource.get("java").get("instanceof")).isNotNull();

        assertThat(toJson(subscription.environment()).isNull()).isTrue();
    }

    @Test
    void when_multiArgumentsWithJsonProblem_then_DropsArguments() {
        val failMapper = spy(ObjectMapper.class);
        when(failMapper.valueToTree(any())).thenAnswer((Answer<JsonNode>) anInvocation -> {
            Object x = anInvocation.getArguments()[0];
            if ("X".equals(x)) {
                throw new IllegalArgumentException("testfail");
            }
            return mapper.valueToTree(x);
        });
        val sut = new AuthorizationSubscriptionBuilderService(new DefaultMethodSecurityExpressionHandler(), failMapper);

        val serverWebExchange   = MockServerWebExchange.from(MockServerHttpRequest.get("/foo/bar"));
        val securityContext     = new MockSecurityContext(authentication);
        val attribute           = attribute(null, null, null, null, Object.class);
        val multiArgsInvocation = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class,
                "publicSeveralArgs", new Class<?>[] { Integer.class, String.class }, new Object[] { "X", "X" });
        val subscription        = sut.reactiveConstructAuthorizationSubscription(multiArgsInvocation, attribute)
                .contextWrite(Context.of(ServerWebExchange.class, serverWebExchange))
                .contextWrite(Context.of(SecurityContext.class, Mono.just(securityContext))).block();

        var subject = toJson(subscription.subject());
        assertThat(subject.get("name").asString()).isEqualTo("the username");
        assertThat(subject.has("credentials")).isFalse();
        assertThat(subject.get("principal").has("password")).isFalse();

        var action = toJson(subscription.action());
        assertThat(action.get("java").get("name").asString()).isEqualTo("publicSeveralArgs");
        assertThat(action.get("java").has("arguments")).isFalse();

        assertThat(toJson(subscription.environment()).isNull()).isTrue();
    }

    @Test
    void when_expressionEvaluationFails_then_throws() {
        val serverWebExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/foo/bar"));
        val securityContext   = new MockSecurityContext(authentication);
        val attribute         = attribute("(#gewrq/0)", null, null, null, Object.class);
        val subscription      = defaultWebfluxBuilderUnderTest
                .reactiveConstructAuthorizationSubscription(invocation, attribute)
                .contextWrite(Context.of(ServerWebExchange.class, serverWebExchange))
                .contextWrite(Context.of(SecurityContext.class, Mono.just(securityContext)));
        StepVerifier.create(subscription).expectErrorMatches(IllegalArgumentException.class::isInstance).verify();
    }

    @Test
    void when_reactive_nullParameters_then_FactoryConstructsFromContext() {
        val serverWebExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/foo/bar"));
        val securityContext   = new MockSecurityContext(authentication);
        val attribute         = attribute(null, null, null, null, Object.class);
        val subscription      = defaultWebfluxBuilderUnderTest
                .reactiveConstructAuthorizationSubscription(invocation, attribute)
                .contextWrite(Context.of(ServerWebExchange.class, serverWebExchange))
                .contextWrite(Context.of(SecurityContext.class, Mono.just(securityContext))).block();

        var subject = toJson(subscription.subject());
        assertThat(subject.get("name").asString()).isEqualTo("the username");
        assertThat(subject.has("credentials")).isFalse();
        assertThat(subject.get("principal").has("password")).isFalse();

        var action = toJson(subscription.action());
        assertThat(action.get("java").get("name").asString()).isEqualTo("publicVoid");

        var resource = toJson(subscription.resource());
        assertThat(resource.get("java").get("instanceof")).isNotNull();

        assertThat(toJson(subscription.environment()).isNull()).isTrue();
    }

    @Test
    void when_reactive_nullParametersAndNoAuthn_then_FactoryConstructsFromContextAndAnonymous() {
        val attribute    = attribute(null, null, null, null, Object.class);
        val subscription = defaultWebfluxBuilderUnderTest
                .reactiveConstructAuthorizationSubscription(invocation, attribute).block();

        var subject = toJson(subscription.subject());
        assertThat(subject.get("name").asString()).isEqualTo("anonymous");
        assertThat(subject.has("credentials")).isFalse();
        assertThat(subject.get("principal").asString()).isEqualTo("anonymous");

        var action = toJson(subscription.action());
        assertThat(action.get("java").get("name").asString()).isEqualTo("publicVoid");
        assertThat(action.has("http")).isFalse();

        var resource = toJson(subscription.resource());
        assertThat(resource.has("http")).isFalse();
        assertThat(resource.get("java").get("instanceof")).isNotNull();

        assertThat(toJson(subscription.environment()).isNull()).isTrue();
    }

    @Test
    void when_reactive_returnObjectInExpression_then_FactoryConstructsReturnObjectInSubscription() {
        val attribute    = attribute(null, null, "returnObject", null, Object.class);
        val subscription = defaultWebfluxBuilderUnderTest
                .reactiveConstructAuthorizationSubscription(invocation, attribute, "the returnObject").block();

        var subject = toJson(subscription.subject());
        assertThat(subject.get("name").asString()).isEqualTo("anonymous");
        assertThat(subject.has("credentials")).isFalse();
        assertThat(subject.get("principal").asString()).isEqualTo("anonymous");

        var action = toJson(subscription.action());
        assertThat(action.get("java").get("name").asString()).isEqualTo("publicVoid");
        assertThat(action.has("http")).isFalse();

        assertThat(toJson(subscription.resource()).asString()).isEqualTo("the returnObject");

        assertThat(toJson(subscription.environment()).isNull()).isTrue();
    }

    private SaplAttribute attribute(String subject, String action, String resource, String environment, Class<?> type) {
        return new SaplAttribute(PreEnforce.class, parameterToExpression(subject), parameterToExpression(action),
                parameterToExpression(resource), parameterToExpression(environment), null, type);
    }

    private Expression parameterToExpression(String parameter) {
        val parser = new SpelExpressionParser();
        return parameter == null || parameter.isEmpty() ? null : parser.parseExpression(parameter);
    }

    public static class TestClass {

        public void publicVoid() {
            /* NOOP */
        }

        public void publicVoidArgs(Integer x) {
            /* NOOP */
        }

        public void publicVoidProblemArg(BadForJackson param) {
            /* NOOP */
        }

        public void publicSeveralArgs(Integer x, String y) {
            /* NOOP */
        }

    }

    public static class BadForJackson {

        @SuppressWarnings("unused") // for test
        private String bad;

    }

    static class MockSecurityContext implements SecurityContext {

        private static final long serialVersionUID = SaplVersion.VERSION_UID;

        private Authentication authentication;

        MockSecurityContext(Authentication authentication) {
            this.authentication = authentication;
        }

        @Override
        public Authentication getAuthentication() {
            return this.authentication;
        }

        @Override
        public void setAuthentication(Authentication authentication) {
            this.authentication = authentication;
        }

    }

}
