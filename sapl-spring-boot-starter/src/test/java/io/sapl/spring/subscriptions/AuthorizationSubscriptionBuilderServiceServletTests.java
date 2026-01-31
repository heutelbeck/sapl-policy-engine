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

import io.sapl.api.SaplVersion;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.spring.config.ObjectMapperAutoConfiguration;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import lombok.val;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationSubscriptionBuilderServiceServletTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
            AutoConfigurations.of(JacksonAutoConfiguration.class, ObjectMapperAutoConfiguration.class));

    private Authentication                          authentication;
    private AuthorizationSubscriptionBuilderService defaultWebBuilderUnderTest;
    private MethodInvocation                        invocation;
    private ObjectMapper                            mapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void beforeEach() {
        mapper = new ObjectMapper();
        val user = new User("the username", "the password", true, true, true, true,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        authentication = new UsernamePasswordAuthenticationToken(user, "the credentials");
        val mockExpressionHandlerProvider = mock(ObjectProvider.class);
        when(mockExpressionHandlerProvider.getIfAvailable(any()))
                .thenReturn(new DefaultMethodSecurityExpressionHandler());
        val mockMapperProvider = mock(ObjectProvider.class);
        when(mockMapperProvider.getIfAvailable(any())).thenReturn(mapper);
        val mockDefaultsProvider = mock(ObjectProvider.class);
        val mockContext          = mock(ApplicationContext.class);
        defaultWebBuilderUnderTest = new AuthorizationSubscriptionBuilderService(mockExpressionHandlerProvider,
                mockMapperProvider, mockDefaultsProvider, mockContext);
        invocation                 = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class,
                "publicVoid", null, null);
    }

    private static JsonNode toJson(Value value) {
        if (value instanceof UndefinedValue) {
            return JsonNodeFactory.instance.nullNode();
        }
        return ValueJsonMarshaller.toJsonNode(value);
    }

    private static class Provider<T> implements ObjectProvider<T> {

        T o;

        Provider() {
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
        val mockContext        = mock(ApplicationContext.class);
        val mockMapperProvider = mock(ObjectProvider.class);
        when(mockMapperProvider.getIfAvailable(any())).thenReturn(mapper);
        val emptyExpressionHandlerProvider = new Provider<MethodSecurityExpressionHandler>();
        val defaults                       = mock(GrantedAuthorityDefaults.class);
        val defaultsProvider               = new Provider<>(defaults);
        val webBuilderUnderTest            = new AuthorizationSubscriptionBuilderService(emptyExpressionHandlerProvider,
                mockMapperProvider, defaultsProvider, mockContext);
        val attribute                      = attribute("'a subject'", "'an action'", "'a resource'", "'an environment'",
                Object.class);
        webBuilderUnderTest.constructAuthorizationSubscription(authentication, invocation, attribute);
        verify(defaults, times(1)).getRolePrefix();
    }

    @Test
    void when_expressionsAreProvided_then_SubscriptionContainsResult() {
        val attribute    = attribute("'a subject'", "'an action'", "'a resource'", "'an environment'", Object.class);
        val subscription = defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication, invocation,
                attribute);
        assertThat(toJson(subscription.subject()).asString()).isEqualTo("a subject");
        assertThat(toJson(subscription.action()).asString()).isEqualTo("an action");
        assertThat(toJson(subscription.resource()).asString()).isEqualTo("a resource");
        assertThat(toJson(subscription.environment()).asString()).isEqualTo("an environment");
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_expressionResultCannotBeMarshalledToJson_then_FactoryThrows() {
        val attribute  = attribute("'a subject'", "'an action'", "'a resource'", "'an environment'", Object.class);
        val mockMapper = mock(ObjectMapper.class);
        when(mockMapper.valueToTree(any())).thenThrow(new EvaluationException("ERROR"));

        val mockExpressionHandlerProvider = mock(ObjectProvider.class);
        when(mockExpressionHandlerProvider.getIfAvailable(any()))
                .thenReturn(new DefaultMethodSecurityExpressionHandler());
        val mockMapperProvider = mock(ObjectProvider.class);
        when(mockMapperProvider.getIfAvailable(any())).thenReturn(mockMapper);
        val mockDefaultsProvider = mock(ObjectProvider.class);
        val mockContext          = mock(ApplicationContext.class);
        val webBuilderUnderTest  = new AuthorizationSubscriptionBuilderService(mockExpressionHandlerProvider,
                mockMapperProvider, mockDefaultsProvider, mockContext);

        assertThrows(IllegalArgumentException.class,
                () -> webBuilderUnderTest.constructAuthorizationSubscription(authentication, invocation, attribute));
    }

    @Test
    void when_nullParameters_then_FactoryConstructsFromContext() {
        val user         = new AnonymousAuthenticationToken("anon", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        val attribute    = attribute(null, null, null, null, Object.class);
        val subscription = defaultWebBuilderUnderTest.constructAuthorizationSubscription(user, invocation, attribute);

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
    void when_nullParametersAndHttpRequestInContext_then_FactoryConstructsFromContextIncludingRequest() {
        contextRunner.run(context -> {
            val configuredMapper = context.getBean(JsonMapper.class);
            val user             = new User("the username", "the password", true, true, true, true,
                    AuthorityUtils.createAuthorityList("ROLE_USER"));
            val auth             = new UsernamePasswordAuthenticationToken(user, "the credentials");
            val sut              = new AuthorizationSubscriptionBuilderService(
                    new DefaultMethodSecurityExpressionHandler(), configuredMapper);
            val inv              = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class, "publicVoid",
                    null, null);

            try (MockedStatic<RequestContextHolder> theMock = mockStatic(RequestContextHolder.class)) {
                val request           = new MockHttpServletRequest();
                val requestAttributes = mock(ServletRequestAttributes.class);
                when(requestAttributes.getRequest()).thenReturn(request);
                theMock.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
                val attribute    = attribute(null, null, null, null, Object.class);
                val subscription = sut.constructAuthorizationSubscription(auth, inv, attribute);

                var subject = toJson(subscription.subject());
                assertThat(subject.get("name").asString()).isEqualTo("the username");
                assertThat(subject.has("credentials")).isFalse();
                assertThat(subject.get("principal").has("password")).isFalse();

                var action = toJson(subscription.action());
                assertThat(action.get("http")).isNotNull();

                var resource = toJson(subscription.resource());
                assertThat(resource.get("http")).isNotNull();
                assertThat(resource.get("java").get("instanceof")).isNotNull();

                assertThat(toJson(subscription.environment()).isNull()).isTrue();
            }
        });
    }

    @Test
    void when_nullParametersInvocationHasArguments_then_FactoryConstructsFromContextIncludingArguments() {
        val attribute          = attribute(null, null, null, null, Object.class);
        val invocationWithArgs = MethodInvocationUtils.create(new TestClass(), "publicVoidArgs", 1);
        val subscription       = defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication,
                invocationWithArgs, attribute);

        var subject = toJson(subscription.subject());
        assertThat(subject.get("name").asString()).isEqualTo("the username");
        assertThat(subject.has("credentials")).isFalse();
        assertThat(subject.get("principal").has("password")).isFalse();

        var action = toJson(subscription.action());
        assertThat(action.get("java").get("arguments")).isNotNull();
        assertThat(action.get("java").get("name").asString()).isEqualTo("publicVoidArgs");

        var resource = toJson(subscription.resource());
        assertThat(resource.get("java").get("instanceof")).isNotNull();

        assertThat(toJson(subscription.environment()).isNull()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_nullParametersInvocationHasArgumentsThatCannotBeMappedToJson_then_FactoryConstructsFromContextExcludingProblematicArguments() {
        val failMapper = spy(ObjectMapper.class);
        when(failMapper.valueToTree(any())).thenAnswer((Answer<JsonNode>) anInvocation -> {
            Object arg = anInvocation.getArguments()[0];
            if (arg instanceof BadForJackson) {
                throw new IllegalArgumentException("testfail");
            }
            return mapper.valueToTree(arg);
        });

        val mockExpressionHandlerProvider = mock(ObjectProvider.class);
        when(mockExpressionHandlerProvider.getIfAvailable(any()))
                .thenReturn(new DefaultMethodSecurityExpressionHandler());
        val mockMapperProvider = mock(ObjectProvider.class);
        when(mockMapperProvider.getIfAvailable(any())).thenReturn(failMapper);
        val mockDefaultsProvider = mock(ObjectProvider.class);
        val mockContext          = mock(ApplicationContext.class);
        val sut                  = new AuthorizationSubscriptionBuilderService(mockExpressionHandlerProvider,
                mockMapperProvider, mockDefaultsProvider, mockContext);

        val attribute             = attribute(null, null, null, null, Object.class);
        val invocationWithBadArgs = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class,
                "publicVoidProblemArg", new Class<?>[] { BadForJackson.class }, new Object[] { new BadForJackson() });
        val subscription          = sut.constructAuthorizationSubscription(authentication, invocationWithBadArgs,
                attribute);

        var subject = toJson(subscription.subject());
        assertThat(subject.get("name").asString()).isEqualTo("the username");
        assertThat(subject.has("credentials")).isFalse();
        assertThat(subject.get("principal").has("password")).isFalse();

        var action = toJson(subscription.action());
        assertThat(action.get("java").has("arguments")).isFalse();
        assertThat(action.get("java").get("name").asString()).isEqualTo("publicVoidProblemArg");

        var resource = toJson(subscription.resource());
        assertThat(resource.get("java").get("instanceof")).isNotNull();

        assertThat(toJson(subscription.environment()).isNull()).isTrue();
    }

    private SaplAttribute attribute(String subject, String action, String resource, String environment, Class<?> type) {
        return attributeWithSecrets(subject, action, resource, environment, null, type);
    }

    private SaplAttribute attributeWithSecrets(String subject, String action, String resource, String environment,
            String secrets, Class<?> type) {
        return new SaplAttribute(PreEnforce.class, parameterToExpression(subject), parameterToExpression(action),
                parameterToExpression(resource), parameterToExpression(environment), parameterToExpression(secrets),
                type);
    }

    private Expression parameterToExpression(String parameter) {
        val parser = new SpelExpressionParser();
        return parameter == null || parameter.isEmpty() ? null : parser.parseExpression(parameter);
    }

    @Nested
    @DisplayName("Secrets handling")
    class SecretsTests {

        @ParameterizedTest(name = "when secrets expression is ''{0}'', then secrets default to empty object")
        @NullAndEmptySource
        void whenSecretsNullOrEmpty_thenSecretsAreEmptyObject(String secretsExpr) {
            val attribute    = attributeWithSecrets(null, null, null, null, secretsExpr, Object.class);
            val subscription = defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication, invocation,
                    attribute);
            assertThat(subscription.secrets()).isEqualTo(Value.EMPTY_OBJECT);
        }

        @Test
        @DisplayName("when secrets expression evaluates to object, then secrets are passed correctly")
        void whenSecretsProvidedAsObject_thenSecretsArePassedToSubscription() {
            val attribute    = attributeWithSecrets(null, null, null, null, "{apiKey: 'abc123', token: 'xyz'}",
                    Object.class);
            val subscription = defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication, invocation,
                    attribute);
            assertThat(subscription.secrets()).containsEntry("apiKey", new TextValue("abc123")).containsEntry("token",
                    new TextValue("xyz"));
        }

        @Test
        @DisplayName("when secrets expression evaluates to non-object, then exception is thrown")
        void whenSecretsEvaluateToNonObject_thenThrowsException() {
            val attribute = attributeWithSecrets(null, null, null, null, "'not an object'", Object.class);
            assertThatThrownBy(() -> defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication,
                    invocation, attribute)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Secrets expression must evaluate to an object");
        }

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
