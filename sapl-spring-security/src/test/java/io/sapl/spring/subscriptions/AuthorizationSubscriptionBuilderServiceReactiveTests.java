/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Collection;

import org.aopalliance.intercept.MethodInvocation;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import io.sapl.api.SaplVersion;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;
import jakarta.servlet.http.HttpServletRequest;
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
        val module = new SimpleModule();
        module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
        module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
        module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
        mapper.registerModule(module);
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

    /**
     * Adapts an Iterable matcher to a Collection matcher for Eclipse compiler
     * compatibility.
     *
     * @param matcher the iterable matcher to adapt
     * @return a collection matcher wrapping the iterable matcher
     */
    @SuppressWarnings("unchecked")
    private static Matcher<Collection<? extends JsonNode>> asCollectionMatcher(
            Matcher<Iterable<? extends JsonNode>> matcher) {
        return (Matcher<Collection<? extends JsonNode>>) (Matcher<?>) matcher;
    }

    private static JsonNode toJson(Value value) {
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
        // @formatter:off
        assertAll(() -> assertThat(toJson(subscription.subject()),
                is(jsonObject()
                        .where("name", is(jsonText("the username")))
                        .where("credentials", is(jsonMissing()))
                        .where("principal", is(jsonObject()
                                .where("password", is(jsonMissing())))))),
                () -> assertThat(toJson(subscription.action()),
                        is(jsonObject()
                                .where("java", is(jsonObject()
                                        .where("name", jsonText("publicSeveralArgs")))))),
                () -> assertThat(toJson(subscription.action()),
                        is(jsonObject()
                                  .where("java", is(jsonObject()
                                        .where("arguments",
                                            is(jsonArray(asCollectionMatcher(containsInAnyOrder(
                                                jsonInt(1),
                                                jsonText("X"))
                                            )))))))),
                () -> assertThat(toJson(subscription.resource()),
                        is(jsonObject()
                                  .where("java", is(jsonObject()
                                        .where("instanceof",
                                            is(jsonArray(asCollectionMatcher(containsInAnyOrder(
                                                jsonObject().where("simpleName",is(jsonText("TestClass"))),
                                                jsonObject().where("simpleName",is(jsonText("Object")))))))))))),
                () -> assertThat(toJson(subscription.environment()), is(jsonNull())));
        // @formatter:on
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
        // @formatter:off
        assertAll(() -> assertThat(toJson(subscription.subject()),
                is(jsonObject()
                        .where("name", is(jsonText("the username")))
                        .where("credentials", is(jsonMissing()))
                        .where("principal", is(jsonObject()
                                .where("password", is(jsonMissing())))))),
                () -> assertThat(toJson(subscription.action()),
                        is(jsonObject()
                                .where("java", is(jsonObject()
                                        .where("name", jsonText("publicSeveralArgs")))))),
                () -> assertThat(toJson(subscription.action()),
                        is(jsonObject()
                                  .where("java", is(jsonObject()
                                        .where("arguments",
                                            is(jsonMissing())))))),
                () -> assertThat(toJson(subscription.environment()), is(jsonNull())));
        // @formatter:on
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
        // @formatter:off
        assertAll(() -> assertThat(toJson(subscription.subject()),
                is(jsonObject()
                        .where("name", is(jsonText("the username")))
                        .where("credentials", is(jsonMissing()))
                        .where("principal", is(jsonObject()
                                .where("password", is(jsonMissing())))))),
                () -> assertThat(toJson(subscription.action()),
                        is(jsonObject()
                                .where("java", is(jsonObject()
                                        .where("name", jsonText("publicVoid")))))),
                () -> assertThat(toJson(subscription.resource()),
                        is(jsonObject()
                                  .where("java", is(jsonObject()
                                        .where("instanceof",
                                            is(jsonArray(asCollectionMatcher(containsInAnyOrder(
                                                jsonObject().where("simpleName",is(jsonText("TestClass"))),
                                                jsonObject().where("simpleName",is(jsonText("Object")))))))))))),
                () -> assertThat(toJson(subscription.environment()), is(jsonNull())));
        // @formatter:on
    }

    @Test
    void when_reactive_nullParametersAndNoAuthn_then_FactoryConstructsFromContextAndAnonymous() {
        val attribute    = attribute(null, null, null, null, Object.class);
        val subscription = defaultWebfluxBuilderUnderTest
                .reactiveConstructAuthorizationSubscription(invocation, attribute).block();
        // @formatter:off
        assertAll(() -> assertThat(toJson(subscription.subject()),
                          is(jsonObject()
                                  .where("name", is(jsonText("anonymous")))
                                  .where("credentials", is(jsonMissing()))
                                  .where("principal", is(jsonText("anonymous"))))),
                  () -> assertThat(toJson(subscription.action()),
                          is(jsonObject()
                                  .where("java", is(jsonObject()
                                        .where("name", jsonText("publicVoid"))))
                                  .where("http", is(jsonMissing())))),
                  () -> assertThat(toJson(subscription.resource()),
                          is(jsonObject()
                                  .where("http", is(jsonMissing()))
                                  .where("java", is(jsonObject()
                                        .where("instanceof",
                                            is(jsonArray(asCollectionMatcher(containsInAnyOrder(
                                                jsonObject().where("simpleName",is(jsonText("TestClass"))),
                                                jsonObject().where("simpleName",is(jsonText("Object")))))))))))),
                  () -> assertThat(toJson(subscription.environment()), is(jsonNull())));
        // @formatter:on
    }

    @Test
    void when_reactive_returnObjectInExpression_then_FactoryConstructsReturnObjectInSubscription() {
        val attribute    = attribute(null, null, "returnObject", null, Object.class);
        val subscription = defaultWebfluxBuilderUnderTest
                .reactiveConstructAuthorizationSubscription(invocation, attribute, "the returnObject").block();
        // @formatter:off
        assertAll(() -> assertThat(toJson(subscription.subject()),
                          is(jsonObject()
                                  .where("name", is(jsonText("anonymous")))
                                  .where("credentials", is(jsonMissing()))
                                  .where("principal", is(jsonText("anonymous"))))),
                  () -> assertThat(toJson(subscription.action()),
                          is(jsonObject()
                                  .where("java", is(jsonObject()
                                        .where("name", jsonText("publicVoid"))))
                                  .where("http", is(jsonMissing())))),
                  () -> assertThat(toJson(subscription.resource()),
                          is(jsonText("the returnObject"))),
                  () -> assertThat(toJson(subscription.environment()), is(jsonNull())));
        // @formatter:on
    }

    private SaplAttribute attribute(String subject, String action, String resource, String environment, Class<?> type) {
        return new SaplAttribute(PreEnforce.class, parameterToExpression(subject), parameterToExpression(action),
                parameterToExpression(resource), parameterToExpression(environment), type);
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
