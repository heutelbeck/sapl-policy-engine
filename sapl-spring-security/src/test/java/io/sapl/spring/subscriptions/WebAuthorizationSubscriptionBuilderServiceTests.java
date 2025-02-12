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
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonMissing;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonNull;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonObject;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.util.MethodInvocationUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.sapl.api.SaplVersion;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;
import jakarta.servlet.http.HttpServletRequest;

class WebAuthorizationSubscriptionBuilderServiceTests {

    private Authentication                             authentication;
    private WebAuthorizationSubscriptionBuilderService defaultWebBuilderUnderTest;
    private MethodInvocation                           invocation;
    private ObjectMapper                               mapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void beforeEach() {
        mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
        module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
        module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
        mapper.registerModule(module);
        final var user = new User("the username", "the password", true, true, true, true,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        authentication = new UsernamePasswordAuthenticationToken(user, "the credentials");
        final var mockExpressionHandlerProvider = mock(ObjectProvider.class);
        when(mockExpressionHandlerProvider.getIfAvailable(any()))
                .thenReturn(new DefaultMethodSecurityExpressionHandler());
        final var mockMapperProvider = mock(ObjectProvider.class);
        when(mockMapperProvider.getIfAvailable(any())).thenReturn(mapper);
        final var mockDefaultsProvider = mock(ObjectProvider.class);
        final var mockContext          = mock(ApplicationContext.class);
        defaultWebBuilderUnderTest = new WebAuthorizationSubscriptionBuilderService(mockExpressionHandlerProvider,
                mockMapperProvider, mockDefaultsProvider, mockContext);
        invocation                 = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class,
                "publicVoid", null, null);
    }

    private static class Provider<T> implements ObjectProvider<T> {

        T o = null;

        public Provider() {
        }

        public Provider(T o) {
            this.o = o;
        }

        @Override
        public T getObject() throws BeansException {
            return o;
        }

        @Override
        public T getObject(Object... args) throws BeansException {
            return o;
        }

        @Override
        public T getIfAvailable() throws BeansException {
            return o;
        }

        @Override
        public T getIfUnique() throws BeansException {
            return o;
        }

    }

    @Test
    @SuppressWarnings("unchecked")
    void when_expressionHandlerProvided_then_FactoryThrows() {
        final var mockContext        = mock(ApplicationContext.class);
        final var mockMapperProvider = mock(ObjectProvider.class);
        when(mockMapperProvider.getIfAvailable(any())).thenReturn(mapper);
        final var emptyExpressionHandlerProvider = new Provider<MethodSecurityExpressionHandler>();
        final var defaults                       = mock(GrantedAuthorityDefaults.class);
        final var defaultsProvider               = new Provider<>(defaults);
        final var webBuilderUnderTest            = new WebAuthorizationSubscriptionBuilderService(
                emptyExpressionHandlerProvider, mockMapperProvider, defaultsProvider, mockContext);
        final var attribute                      = attribute("'a subject'", "'an action'", "'a resource'",
                "'an environment'", Object.class);
        webBuilderUnderTest.constructAuthorizationSubscription(authentication, invocation, attribute);
        verify(defaults, times(1)).getRolePrefix();
    }

    @Test
    void when_expressionsAreProvided_then_SubscriptionContainsResult() {
        final var attribute    = attribute("'a subject'", "'an action'", "'a resource'", "'an environment'",
                Object.class);
        final var subscription = defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication,
                invocation, attribute);
        assertAll(() -> assertThat(subscription.getSubject(), is(jsonText("a subject"))),
                () -> assertThat(subscription.getAction(), is(jsonText("an action"))),
                () -> assertThat(subscription.getResource(), is(jsonText("a resource"))),
                () -> assertThat(subscription.getEnvironment(), is(jsonText("an environment"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_expressionResultCannotBeMarshalledToJson_then_FactoryThrows() {
        final var attribute  = attribute("'a subject'", "'an action'", "'a resource'", "'an environment'",
                Object.class);
        final var mockMapper = mock(ObjectMapper.class);
        when(mockMapper.valueToTree(any())).thenThrow(new EvaluationException("ERROR"));

        final var mockExpressionHandlerProvider = mock(ObjectProvider.class);
        when(mockExpressionHandlerProvider.getIfAvailable(any()))
                .thenReturn(new DefaultMethodSecurityExpressionHandler());
        final var mockMapperProvider = mock(ObjectProvider.class);
        when(mockMapperProvider.getIfAvailable(any())).thenReturn(mockMapper);
        final var mockDefaultsProvider = mock(ObjectProvider.class);
        final var mockContext          = mock(ApplicationContext.class);
        final var sut                  = new WebAuthorizationSubscriptionBuilderService(mockExpressionHandlerProvider,
                mockMapperProvider, mockDefaultsProvider, mockContext);
        assertThrows(IllegalArgumentException.class,
                () -> sut.constructAuthorizationSubscription(authentication, invocation, attribute));
    }

    @Test
    void when_nullParameters_then_FactoryConstructsFromContext() {
        final var attribute    = attribute(null, null, null, null, Object.class);
        final var subscription = defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication,
                invocation, attribute);
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
    void when_returnObjectResourceAndNulls_then_FactoryConstructsFromContextWithReturnObjectInResource() {
        final var attribute    = attribute(null, null, "returnObject", null, Object.class);
        final var subscription = defaultWebBuilderUnderTest.constructAuthorizationSubscriptionWithReturnObject(
                authentication, invocation, attribute, "the returnObject");
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
						is(jsonText("the returnObject"))),
				() -> assertThat(subscription.getEnvironment(), is(jsonNull())));
		// @formatter:on
    }

    @Test
    void when_nullParametersAndAnonymousAuthentication_then_FactoryConstructsFromContextAndNoAuthn() {
        final var attribute    = attribute(null, null, null, null, Object.class);
        final var anonymous    = new AnonymousAuthenticationToken("key", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        final var subscription = defaultWebBuilderUnderTest.constructAuthorizationSubscription(anonymous, invocation,
                attribute);

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
    void when_nullParametersAndHttpRequestInContext_then_FactoryConstructsFromContextIncludingRequest() {

        try (MockedStatic<RequestContextHolder> theMock = mockStatic(RequestContextHolder.class)) {
            final var request           = new MockHttpServletRequest();
            final var requestAttributes = mock(ServletRequestAttributes.class);
            when(requestAttributes.getRequest()).thenReturn(request);
            theMock.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            final var attribute    = attribute(null, null, null, null, Object.class);
            final var subscription = defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication,
                    invocation, attribute);
            // @formatter:off
			assertAll(() -> assertThat(subscription.getSubject(),
					is(jsonObject()
							.where("name", is(jsonText("the username")))
							.where("credentials", is(jsonMissing()))
							.where("principal", is(jsonObject()
									.where("password", is(jsonMissing())))))),
					() -> assertThat(subscription.getAction(),
							is(jsonObject()
									.where("http", is(jsonObject())
											))),
					() -> assertThat(subscription.getResource(),
							is(jsonObject()
									.where("http", is(jsonObject()))
									  .where("java", is(jsonObject()
												.where("instanceof",
													is(jsonArray(containsInAnyOrder(
														jsonObject().where("simpleName",is(jsonText("TestClass"))),
														jsonObject().where("simpleName",is(jsonText("Object"))))))))))),
					() -> assertThat(subscription.getEnvironment(), is(jsonNull())));
			// @formatter:on
        }
    }

    @Test
    void when_nullParametersInvocationHasArguments_then_FactoryConstructsFromContextIncludingArguments() {
        final var attribute          = attribute(null, null, null, null, Object.class);
        final var invocationWithArgs = MethodInvocationUtils.create(new TestClass(), "publicVoidArgs", 1);
        final var subscription       = defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication,
                invocationWithArgs, attribute);
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
										.where("arguments", is(jsonArray()))
										.where("name", jsonText("publicVoidArgs")))))),
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
    void when_nullParametersInvocationHasArgumentsThatCannotBeMappedToJson_then_FactoryConstructsFromContextExcludingProblematicArguments() {
        final var attribute             = attribute(null, null, null, null, Object.class);
        final var invocationWithBadArgs = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class,
                "publicVoidProblemArg", new Class<?>[] { BadForJackson.class }, new Object[] { new BadForJackson() });
        final var subscription          = defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication,
                invocationWithBadArgs, attribute);
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
										.where("arguments", is(jsonMissing()))
										.where("name", jsonText("publicVoidProblemArg")))))),
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

    private SaplAttribute attribute(String subject, String action, String resource, String environment, Class<?> type) {
        return new SaplAttribute(PreEnforce.class, parameterToExpression(subject), parameterToExpression(action),
                parameterToExpression(resource), parameterToExpression(environment), type);
    }

    private Expression parameterToExpression(String parameter) {
        final var parser = new SpelExpressionParser();
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

    }

    public static class BadForJackson {

        @SuppressWarnings("unused") // for test
        private String bad;

    }

    static class MockSecurityContext implements SecurityContext {

        private static final long serialVersionUID = SaplVersion.VERISION_UID;

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
