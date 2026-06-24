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
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.util.MethodInvocationUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.Serial;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationSubscriptionBuilderServiceServletTests {

    private static final Instant REFERENCE = Instant.parse("2025-01-01T00:00:00Z");

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
    void when_noExpressionHandlerProvided_then_usesGrantedAuthorityDefaults() {
        val mockContext        = mock(ApplicationContext.class);
        val mockMapperProvider = mock(ObjectProvider.class);
        when(mockMapperProvider.getIfAvailable(any())).thenReturn(mapper);
        val emptyExpressionHandlerProvider = new Provider<MethodSecurityExpressionHandler>();
        val defaults                       = mock(GrantedAuthorityDefaults.class);
        val defaultsProvider               = new Provider<>(defaults);
        val webBuilderUnderTest            = new AuthorizationSubscriptionBuilderService(emptyExpressionHandlerProvider,
                mockMapperProvider, defaultsProvider, mockContext);
        val attribute                      = attribute("'a subject'", "'an action'", "'a resource'",
                "'an environment'");
        webBuilderUnderTest.constructAuthorizationSubscription(authentication, invocation, attribute);
        verify(defaults, times(1)).getRolePrefix();
    }

    @Test
    void when_argRaisesJacksonException_then_argDroppedAndSubscriptionBuilt() {
        val invocation   = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class,
                "publicVoidThrowingArg", new Class<?>[] { ThrowsOnSerialize.class },
                new Object[] { new ThrowsOnSerialize() });
        val subscription = defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication, invocation,
                attribute(null, null, null, null));

        val action = toJson(subscription.action());
        assertThat(action.get("java").has("arguments")).isFalse();
        assertThat(action.get("java").get("name").asString()).isEqualTo("publicVoidThrowingArg");
    }

    @Test
    void when_expressionsAreProvided_then_SubscriptionContainsResult() {
        val attribute    = attribute("'a subject'", "'an action'", "'a resource'", "'an environment'");
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
        val attribute  = attribute("'a subject'", "'an action'", "'a resource'", "'an environment'");
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

        assertThatThrownBy(
                () -> webBuilderUnderTest.constructAuthorizationSubscription(authentication, invocation, attribute))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_nullParameters_then_FactoryConstructsFromContext() {
        val user         = new AnonymousAuthenticationToken("anon", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        val attribute    = attribute(null, null, null, null);
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
                val attribute    = attribute(null, null, null, null);
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
        val attribute          = attribute(null, null, null, null);
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
    @DisplayName("when a method argument carries a nested credential field, then it is stripped from the action arguments")
    void whenArgumentCarriesNestedCredentialThenItIsStrippedFromActionArguments() {
        val attribute          = attribute(null, null, null, null);
        val secretBearingArg   = new CustomPrincipal("tenant-1", new NestedCredentials("super-secret-token"));
        val invocationWithArgs = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class,
                "publicVoidCredentialArg", new Class<?>[] { CustomPrincipal.class }, new Object[] { secretBearingArg });
        val subscription       = defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication,
                invocationWithArgs, attribute);

        val argument = toJson(subscription.action()).get("java").get("arguments").get(0);
        assertThat(argument.get("tenantId").asString()).isEqualTo("tenant-1");
        assertThat(argument.get("nested").has("accessToken")).isFalse();
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

        val attribute             = attribute(null, null, null, null);
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

    private SaplAttribute attribute(String subject, String action, String resource, String environment) {
        return attributeWithSecrets(subject, action, resource, environment, null);
    }

    private SaplAttribute attributeWithSecrets(String subject, String action, String resource, String environment,
            String secrets) {
        return new SaplAttribute(PreEnforce.class, parameterToExpression(subject), parameterToExpression(action),
                parameterToExpression(resource), parameterToExpression(environment), parameterToExpression(secrets),
                false, false);
    }

    private Expression parameterToExpression(String parameter) {
        val parser = new SpelExpressionParser();
        return parameter == null || parameter.isEmpty() ? null : parser.parseExpression(parameter);
    }

    @Nested
    @DisplayName("Subject token stripping")
    class SubjectTokenStrippingTests {

        @Test
        @DisplayName("when JwtAuthenticationToken, then tokenValue is stripped from subject.token and subject.principal")
        void whenJwtAuthentication_thenTokenValueStrippedFromSubject() {
            var jwt           = new org.springframework.security.oauth2.jwt.Jwt(
                    "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.sig", REFERENCE, REFERENCE.plusSeconds(3600),
                    java.util.Map.of("alg", "RS256"), java.util.Map.of("sub", "user"));
            var jwtAuth       = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                    jwt, AuthorityUtils.createAuthorityList("ROLE_USER"));
            val attribute     = attribute(null, null, null, null);
            val subscription  = defaultWebBuilderUnderTest.constructAuthorizationSubscription(jwtAuth, invocation,
                    attribute);
            val subject       = toJson(subscription.subject());
            val tokenNode     = subject.get("token");
            val principalNode = subject.get("principal");

            assertThat(subject.has("credentials")).isFalse();
            assertThat(tokenNode).isNotNull();
            assertThat(tokenNode.has("tokenValue")).isFalse();
            assertThat(principalNode).isNotNull();
            assertThat(principalNode.has("tokenValue")).isFalse();
            assertThat(principalNode.has("claims")).isTrue();
        }

        @Test
        @DisplayName("when JwtAuthenticationToken without injector then secrets are empty and token stripped")
        void whenJwtAuthWithoutInjectorThenSecretsEmptyAndTokenStripped() {
            var jwt          = new org.springframework.security.oauth2.jwt.Jwt(
                    "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.sig", REFERENCE, REFERENCE.plusSeconds(3600),
                    java.util.Map.of("alg", "RS256"), java.util.Map.of("sub", "user"));
            var jwtAuth      = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                    jwt, AuthorityUtils.createAuthorityList("ROLE_USER"));
            val attribute    = attribute(null, null, null, null);
            val subscription = defaultWebBuilderUnderTest.constructAuthorizationSubscription(jwtAuth, invocation,
                    attribute);
            val subject      = toJson(subscription.subject());

            assertThat(subscription.secrets()).isEqualTo(Value.EMPTY_OBJECT);
            assertThat(subject.get("token").has("tokenValue")).isFalse();
            assertThat(subject.get("principal").has("tokenValue")).isFalse();
        }

        @Test
        @DisplayName("when OIDC login principal, then the raw principal.idToken.tokenValue never reaches the subject")
        void whenOidcLoginThenNestedIdTokenValueStrippedFromSubject() {
            val idToken       = new OidcIdToken("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.sig", REFERENCE,
                    REFERENCE.plusSeconds(3600), java.util.Map.of("sub", "user"));
            val oidcUser      = new DefaultOidcUser(AuthorityUtils.createAuthorityList("ROLE_USER"), idToken);
            val oidcAuth      = new OAuth2AuthenticationToken(oidcUser, AuthorityUtils.createAuthorityList("ROLE_USER"),
                    "client-registration");
            val attribute     = attribute(null, null, null, null);
            val subscription  = defaultWebBuilderUnderTest.constructAuthorizationSubscription(oidcAuth, invocation,
                    attribute);
            val subject       = toJson(subscription.subject());
            val principalNode = subject.get("principal");

            assertThat(subject.has("credentials")).isFalse();
            assertThat(principalNode.has("idToken")).isFalse();
            assertThat(subject.toString()).doesNotContain("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.sig");
        }

        @Test
        @DisplayName("when principal carries a nested accessToken, then it is stripped while a benign domain field is retained")
        void whenPrincipalHasNestedAccessTokenThenStrippedButBenignFieldRetained() {
            val principal     = new CustomPrincipal("tenant-42", new NestedCredentials("raw-access-token"));
            val auth          = new UsernamePasswordAuthenticationToken(principal, "the credentials");
            val attribute     = attribute(null, null, null, null);
            val subscription  = defaultWebBuilderUnderTest.constructAuthorizationSubscription(auth, invocation,
                    attribute);
            val subject       = toJson(subscription.subject());
            val principalNode = subject.get("principal");

            assertThat(subject.has("credentials")).isFalse();
            assertThat(principalNode.get("tenantId").asString()).isEqualTo("tenant-42");
            assertThat(principalNode.get("nested").has("accessToken")).isFalse();
        }

    }

    @Nested
    @DisplayName("Default subject projection for non-object authentications")
    class NonObjectAuthenticationSubjectTests {

        @Test
        @DisplayName("when authentication serializes to a JSON string, then the subject is that string rather than a ClassCastException")
        void whenAuthenticationSerializesToStringThenSubjectIsThatString() {
            val auth         = new StringSerializingAuthentication("opaque-principal");
            val attribute    = attribute(null, null, null, null);
            val subscription = defaultWebBuilderUnderTest.constructAuthorizationSubscription(auth, invocation,
                    attribute);
            val subject      = toJson(subscription.subject());

            assertThat(subject.isString()).isTrue();
            assertThat(subject.asString()).isEqualTo("opaque-principal");
        }

    }

    @Nested
    @DisplayName("Secrets handling")
    class SecretsTests {

        @ParameterizedTest(name = "when secrets expression is ''{0}'', then secrets default to empty object")
        @NullAndEmptySource
        void whenSecretsNullOrEmpty_thenSecretsAreEmptyObject(String secretsExpr) {
            val attribute    = attributeWithSecrets(null, null, null, null, secretsExpr);
            val subscription = defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication, invocation,
                    attribute);
            assertThat(subscription.secrets()).isEqualTo(Value.EMPTY_OBJECT);
        }

        @Test
        @DisplayName("when secrets expression evaluates to object, then secrets are passed correctly")
        void whenSecretsProvidedAsObject_thenSecretsArePassedToSubscription() {
            val attribute    = attributeWithSecrets(null, null, null, null, "{apiKey: 'abc123', token: 'xyz'}");
            val subscription = defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication, invocation,
                    attribute);
            assertThat(subscription.secrets()).containsEntry("apiKey", new TextValue("abc123")).containsEntry("token",
                    new TextValue("xyz"));
        }

        @Test
        @DisplayName("when secrets expression evaluates to non-object, then exception is thrown")
        void whenSecretsEvaluateToNonObject_thenThrowsException() {
            val attribute = attributeWithSecrets(null, null, null, null, "'not an object'");
            assertThatThrownBy(() -> defaultWebBuilderUnderTest.constructAuthorizationSubscription(authentication,
                    invocation, attribute)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Secrets expression must evaluate to an object");
        }

        @Test
        @DisplayName("when injector wired then injector output appears in subscription secrets")
        void whenInjectorWiredThenSecretsContainInjectorOutput() {
            val injector = new SubscriptionSecretsInjector() {
                             @Override
                             public io.sapl.api.model.ObjectValue injectSecrets(Authentication auth) {
                                 return io.sapl.api.model.ObjectValue.builder().put("jwt", Value.of("test-token"))
                                         .build();
                             }
                         };
            val sut      = new AuthorizationSubscriptionBuilderService(new DefaultMethodSecurityExpressionHandler(),
                    mapper, injector);
            val attr     = attribute(null, null, null, null);
            val sub      = sut.constructAuthorizationSubscription(authentication, invocation, attr);
            assertThat(sub.secrets()).containsEntry("jwt", new TextValue("test-token"));
        }

        @Test
        @DisplayName("when SpEL secrets expression set then takes precedence over injector")
        void whenSpelSecretsThenPrecedenceOverInjector() {
            val injector = new SubscriptionSecretsInjector() {
                             @Override
                             public io.sapl.api.model.ObjectValue injectSecrets(Authentication auth) {
                                 return io.sapl.api.model.ObjectValue.builder().put("jwt", Value.of("injected"))
                                         .build();
                             }
                         };
            val sut      = new AuthorizationSubscriptionBuilderService(new DefaultMethodSecurityExpressionHandler(),
                    mapper, injector);
            val attr     = attributeWithSecrets(null, null, null, null, "{jwt: 'spel-value'}");
            val sub      = sut.constructAuthorizationSubscription(authentication, invocation, attr);
            assertThat(sub.secrets()).containsEntry("jwt", new TextValue("spel-value"));
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

        public void publicVoidCredentialArg(CustomPrincipal param) {
            /* NOOP */
        }

        public void publicVoidThrowingArg(ThrowsOnSerialize param) {
            /* NOOP */
        }

    }

    public static class ThrowsOnSerialize {

        @SuppressWarnings("unused") // invoked by Jackson during serialization
        public String getValue() {
            throw new IllegalStateException("serialization boom");
        }

    }

    public static class BadForJackson {

        @SuppressWarnings("unused") // for test
        private String bad;

    }

    public record CustomPrincipal(String tenantId, NestedCredentials nested) {}

    public record NestedCredentials(String accessToken) {}

    /**
     * An {@link Authentication} that Jackson serializes to a JSON string rather
     * than a JSON object, exercising the default subject projection against a
     * non-object serialization.
     */
    public static class StringSerializingAuthentication extends AbstractAuthenticationToken {

        @Serial
        private static final long serialVersionUID = SaplVersion.VERSION_UID;

        private final String principal;

        StringSerializingAuthentication(String principal) {
            super(AuthorityUtils.createAuthorityList("ROLE_USER"));
            this.principal = principal;
            setAuthenticated(true);
        }

        @JsonValue
        @Override
        public Object getPrincipal() {
            return principal;
        }

        @Override
        public Object getCredentials() {
            return "the credentials";
        }

    }

    static class MockSecurityContext implements SecurityContext {

        @Serial
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
