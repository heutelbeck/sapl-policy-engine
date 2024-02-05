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
package io.sapl.spring.subscriptions;

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonArray;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonInt;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonMissing;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonNull;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonObject;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.server.reactive.ServerHttpRequest;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

class WebfluxAuthorizationSubscriptionBuilderServiceTests {

    private Authentication                                 authentication;
    private WebfluxAuthorizationSubscriptionBuilderService defaultWebfluxBuilderUnderTest;
    private MethodInvocation                               invocation;
    private ObjectMapper                                   mapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void beforeEach() {
        mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
        module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
        module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
        mapper.registerModule(module);
        var user = new User("the username", "the password", true, true, true, true,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        authentication                 = new UsernamePasswordAuthenticationToken(user, "the credentials");
        defaultWebfluxBuilderUnderTest = new WebfluxAuthorizationSubscriptionBuilderService(
                new DefaultMethodSecurityExpressionHandler(), mapper);
        var mockExpressionHandlerProvider = mock(ObjectProvider.class);
        when(mockExpressionHandlerProvider.getIfAvailable(any()))
                .thenReturn(new DefaultMethodSecurityExpressionHandler());
        var mockMapperProvider = mock(ObjectProvider.class);
        when(mockMapperProvider.getIfAvailable(any())).thenReturn(mapper);
        invocation = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class, "publicVoid", null, null);
    }

    @Test
    void when_multiArguments_then_methodIsInAction() {
        ServerWebExchange serverWebExchange   = MockServerWebExchange.from(MockServerHttpRequest.get("/foo/bar"));
        SecurityContext   securityContext     = new MockSecurityContext(authentication);
        var               attribute           = attribute(null, null, null, null, Object.class);
        var               multiArgsInvocation = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class,
                "publicSeveralArgs", new Class<?>[] { Integer.class, String.class }, new Object[] { 1, "X" });
        var               subscription        = defaultWebfluxBuilderUnderTest
                .reactiveConstructAuthorizationSubscription(multiArgsInvocation, attribute)
                .contextWrite(Context.of(ServerWebExchange.class, serverWebExchange))
                .contextWrite(Context.of(SecurityContext.class, Mono.just(securityContext))).block();
        // @formatter:off
        assertAll(() -> assertThat(subscription.getSubject(),
                is(jsonObject()
                        .where("name", is(jsonText("the username")))
                        .where("credentials", is(jsonMissing()))
                        .where("principal", is(jsonObject()
                                .where("password", is(jsonMissing())))))),
                () -> assertThat(subscription.getAction(),
                        is(jsonObject()
                                .where("java", is(jsonObject()
                                        .where("name", jsonText("publicSeveralArgs")))))),
                () -> assertThat(subscription.getAction(),
                        is(jsonObject()
                                  .where("java", is(jsonObject()
                                        .where("arguments",
                                            is(jsonArray(containsInAnyOrder(
                                                jsonInt(1),
                                                jsonText("X"))
                                            ))))))),
                () -> assertThat(subscription.getResource(),
                        is(jsonObject()
                                  .where("java", is(jsonObject()
                                        .where("instanceof",
                                            is(jsonArray(containsInAnyOrder(
                                                jsonObject().where("simpleName",is(jsonText("TestClass"))),
                                                jsonObject().where("simpleName",is(jsonText("Object"))))))))))),
                () -> assertThat(subscription.getEnvironment(), is(jsonNull())));
        // @formatter:on
    }

    @Test
    void when_multiArgumentsWithJsonProblem_then_DropsArguments() {
        var failMapper = spy(ObjectMapper.class);
        when(failMapper.valueToTree(any())).thenAnswer((Answer<JsonNode>) invocation -> {
            Object x = invocation.getArguments()[0];
            if ("X".equals(x)) {
                throw new IllegalArgumentException("testfail");
            }
            return mapper.valueToTree(x);
        });
        var sut = new WebfluxAuthorizationSubscriptionBuilderService(new DefaultMethodSecurityExpressionHandler(),
                failMapper);

        ServerWebExchange serverWebExchange   = MockServerWebExchange.from(MockServerHttpRequest.get("/foo/bar"));
        SecurityContext   securityContext     = new MockSecurityContext(authentication);
        var               attribute           = attribute(null, null, null, null, Object.class);
        var               multiArgsInvocation = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class,
                "publicSeveralArgs", new Class<?>[] { Integer.class, String.class }, new Object[] { "X", "X" });
        var               subscription        = sut
                .reactiveConstructAuthorizationSubscription(multiArgsInvocation, attribute)
                .contextWrite(Context.of(ServerWebExchange.class, serverWebExchange))
                .contextWrite(Context.of(SecurityContext.class, Mono.just(securityContext))).block();
        // @formatter:off
        assertAll(() -> assertThat(subscription.getSubject(),
                is(jsonObject()
                        .where("name", is(jsonText("the username")))
                        .where("credentials", is(jsonMissing()))
                        .where("principal", is(jsonObject()
                                .where("password", is(jsonMissing())))))),
                () -> assertThat(subscription.getAction(),
                        is(jsonObject()
                                .where("java", is(jsonObject()
                                        .where("name", jsonText("publicSeveralArgs")))))),
                () -> assertThat(subscription.getAction(),
                        is(jsonObject()
                                  .where("java", is(jsonObject()
                                        .where("arguments",
                                                jsonMissing()))
                                            ))),
                () -> assertThat(subscription.getResource(),
                        is(jsonObject()
                                  .where("java", is(jsonObject()
                                        .where("instanceof",
                                            is(jsonArray(containsInAnyOrder(
                                                jsonObject().where("simpleName",is(jsonText("TestClass"))),
                                                jsonObject().where("simpleName",is(jsonText("Object"))))))))))),
                () -> assertThat(subscription.getEnvironment(), is(jsonNull())));
        // @formatter:on
    }

    @Test
    void when_expressionEvaluationFails_then_throws() {
        ServerWebExchange serverWebExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/foo/bar"));
        SecurityContext   securityContext   = new MockSecurityContext(authentication);
        var               attribute         = attribute("(#gewrq/0)", null, null, null, Object.class);
        var               subscription      = defaultWebfluxBuilderUnderTest
                .reactiveConstructAuthorizationSubscription(invocation, attribute)
                .contextWrite(Context.of(ServerWebExchange.class, serverWebExchange))
                .contextWrite(Context.of(SecurityContext.class, Mono.just(securityContext)));
        StepVerifier.create(subscription).expectErrorMatches(t -> t instanceof IllegalArgumentException).verify();
    }

    @Test
    void when_reactive_nullParameters_then_FactoryConstructsFromContext() {
        ServerWebExchange serverWebExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/foo/bar"));
        SecurityContext   securityContext   = new MockSecurityContext(authentication);
        var               attribute         = attribute(null, null, null, null, Object.class);
        var               subscription      = defaultWebfluxBuilderUnderTest
                .reactiveConstructAuthorizationSubscription(invocation, attribute)
                .contextWrite(Context.of(ServerWebExchange.class, serverWebExchange))
                .contextWrite(Context.of(SecurityContext.class, Mono.just(securityContext))).block();
        // @formatter:off
        assertAll(() -> assertThat(subscription.getSubject(),
                is(jsonObject()
                        .where("name", is(jsonText("the username")))
                        .where("credentials", is(jsonMissing()))
                        .where("principal", is(jsonObject()
                                .where("password", is(jsonMissing())))))),
                () -> assertThat(subscription.getAction(),
                        is(jsonObject()
                                .where("java", is(jsonObject()
                                        .where("name", jsonText("publicVoid")))))),
                () -> assertThat(subscription.getResource(),
                        is(jsonObject()
                                  .where("java", is(jsonObject()
                                        .where("instanceof",
                                            is(jsonArray(containsInAnyOrder(
                                                jsonObject().where("simpleName",is(jsonText("TestClass"))),
                                                jsonObject().where("simpleName",is(jsonText("Object"))))))))))),
                () -> assertThat(subscription.getEnvironment(), is(jsonNull())));
        // @formatter:on
    }

    @Test
    void when_reactive_nullParametersAndNoAuthn_then_FactoryConstructsFromContextAndAnonymous() {
        var attribute    = attribute(null, null, null, null, Object.class);
        var subscription = defaultWebfluxBuilderUnderTest
                .reactiveConstructAuthorizationSubscription(invocation, attribute).block();
        // @formatter:off
		assertAll(() -> assertThat(subscription.getSubject(),
						  is(jsonObject()
								  .where("name", is(jsonText("anonymous")))
								  .where("credentials", is(jsonMissing()))
								  .where("principal", is(jsonText("anonymous"))))),
				  () -> assertThat(subscription.getAction(),
						  is(jsonObject()
								  .where("java", is(jsonObject()
										.where("name", jsonText("publicVoid"))))
				  				  .where("http", is(jsonMissing())))),
				  () -> assertThat(subscription.getResource(),
						  is(jsonObject()
								  .where("http", is(jsonMissing()))
								  .where("java", is(jsonObject()
										.where("instanceof",
											is(jsonArray(containsInAnyOrder(
												jsonObject().where("simpleName",is(jsonText("TestClass"))),
												jsonObject().where("simpleName",is(jsonText("Object"))))))))))),
			      () -> assertThat(subscription.getEnvironment(), is(jsonNull())));
		// @formatter:on
    }

    @Test
    void when_reactive_returnObjectInExpression_then_FactoryConstructsReturnObjectInSubscription() {
        var attribute    = attribute(null, null, "returnObject", null, Object.class);
        var subscription = defaultWebfluxBuilderUnderTest
                .reactiveConstructAuthorizationSubscription(invocation, attribute, "the returnObject").block();
        // @formatter:off
		assertAll(() -> assertThat(subscription.getSubject(),
						  is(jsonObject()
								  .where("name", is(jsonText("anonymous")))
								  .where("credentials", is(jsonMissing()))
								  .where("principal", is(jsonText("anonymous"))))),
				  () -> assertThat(subscription.getAction(),
						  is(jsonObject()
								  .where("java", is(jsonObject()
										.where("name", jsonText("publicVoid"))))
				  				  .where("http", is(jsonMissing())))),
				  () -> assertThat(subscription.getResource(),
						  is(jsonText("the returnObject"))),
			      () -> assertThat(subscription.getEnvironment(), is(jsonNull())));
		// @formatter:on
    }

    private SaplAttribute attribute(String subject, String action, String resource, String environment, Class<?> type) {
        return new SaplAttribute(PreEnforce.class, parameterToExpression(subject), parameterToExpression(action),
                parameterToExpression(resource), parameterToExpression(environment), type);
    }

    private Expression parameterToExpression(String parameter) {
        var parser = new SpelExpressionParser();
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
