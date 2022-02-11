/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.util.MethodInvocationUtils;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.sapl.spring.method.metadata.PreEnforceAttribute;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class AuthorizationSubscriptionBuilderServiceTests {

	private Authentication authentication;

	private AuthorizationSubscriptionBuilderService defaultBuilderUnderTest;

	private MethodInvocation invocation;

	private ObjectMapper mapper;

	@BeforeEach
	void beforeEach() {
		mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
		module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
		mapper.registerModule(module);
		var user = new User("the username", "the password", true, true, true, true,
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		authentication = new UsernamePasswordAuthenticationToken(user, "the credentials");
		defaultBuilderUnderTest = new AuthorizationSubscriptionBuilderService(
				new DefaultMethodSecurityExpressionHandler(), mapper);
		invocation = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class, "publicVoid", null, null);
	}

	@Test
	void when_usedForAuthorizationContext_nullContext_Fails() {
		var sut = new AuthorizationSubscriptionBuilderService(new DefaultMethodSecurityExpressionHandler(), mapper);
		assertThrows(NullPointerException.class,
				() -> sut.reactiveConstructAuthorizationSubscription(Mono.just(authentication), null).block());
	}

	@Test
	void when_usedForAuthorizationContext_subscriptionReturns() {
		MockServerHttpRequest request = MockServerHttpRequest.get("/requestpath").build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);
		AuthorizationContext context = new AuthorizationContext(exchange);
		var sut = new AuthorizationSubscriptionBuilderService(new DefaultMethodSecurityExpressionHandler(), mapper);
		var actual = sut.reactiveConstructAuthorizationSubscription(Mono.just(authentication), context).block();
		assertThat(actual, is(not(nullValue())));
	}

	@Test
	void when_expressionsAreProvided_then_SubscriptionContainsResult() {
		var attribute = attribute("'a subject'", "'an action'", "'a resource'", "'an environment'", Object.class);
		var subscription = defaultBuilderUnderTest.constructAuthorizationSubscription(authentication, invocation,
				attribute);
		assertAll(() -> assertThat(subscription.getSubject(), is(jsonText("a subject"))),
				() -> assertThat(subscription.getAction(), is(jsonText("an action"))),
				() -> assertThat(subscription.getResource(), is(jsonText("a resource"))),
				() -> assertThat(subscription.getEnvironment(), is(jsonText("an environment"))));
	}

	@Test
	void when_expressionResultCannotBeMarshalledToJson_then_FactoryThrows() {
		var attribute = attribute("'a subject'", "'an action'", "'a resource'", "'an environment'", Object.class);
		var mockMapper = mock(ObjectMapper.class);
		when(mockMapper.valueToTree(any())).thenThrow(new EvaluationException("ERROR"));
		var sut = new AuthorizationSubscriptionBuilderService(new DefaultMethodSecurityExpressionHandler(), mockMapper);
		assertThrows(IllegalArgumentException.class,
				() -> sut.constructAuthorizationSubscription(authentication, invocation, attribute));
	}

	@Test
	void when_nullParameters_then_FactoryConstructsFromContext() {
		var attribute = attribute(null, null, null, null, Object.class);
		var subscription = defaultBuilderUnderTest.constructAuthorizationSubscription(authentication, invocation,
				attribute);
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
								.where("targetClass", is(jsonObject()
										.where("simpleName", jsonText("TestClass")))))),
				() -> assertThat(subscription.getEnvironment(), is(jsonNull())));
		// @formatter:on
	}

	@Test
	void when_returnObjectResourceAndNulls_then_FactoryConstructsFromContextWithReturnObjectInResource() {
		var attribute = attribute(null, null, "returnObject", null, Object.class);
		var subscription = defaultBuilderUnderTest.constructAuthorizationSubscriptionWithReturnObject(authentication,
				invocation, attribute, "the returnObject");
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
		var attribute = attribute(null, null, null, null, Object.class);
		var anonymous = new AnonymousAuthenticationToken("key", "anonymous",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
		var subscription = defaultBuilderUnderTest.constructAuthorizationSubscription(anonymous, invocation, attribute);
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
								  .where("targetClass", is(jsonObject()
										.where("simpleName", jsonText("TestClass")) )))),
			      () -> assertThat(subscription.getEnvironment(), is(jsonNull())));
		// @formatter:on
	}

	@Test
	void when_nullParametersAndHttpRequestInContext_then_FactoryConstructsFromContextIncludingRequest() {

		try (MockedStatic<RequestContextHolder> theMock = mockStatic(RequestContextHolder.class)) {
			var request = new MockHttpServletRequest();
			var requestAttributes = mock(ServletRequestAttributes.class);
			when(requestAttributes.getRequest()).thenReturn(request);
			theMock.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
			var attribute = attribute(null, null, null, null, Object.class);
			var subscription = defaultBuilderUnderTest.constructAuthorizationSubscription(authentication, invocation,
					attribute);
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
									.where("targetClass", is(jsonObject()
											.where("simpleName", jsonText("TestClass")))))),
					() -> assertThat(subscription.getEnvironment(), is(jsonNull())));
			// @formatter:on
		}
	}

	@Test
	void when_nullParametersInvocationHasArguments_then_FactoryConstructsFromContextIncludingArguments() {
		var attribute = attribute(null, null, null, null, Object.class);
		var invocationWithArgs = MethodInvocationUtils.create(new TestClass(), "publicVoidArgs", new Object[] { 1 });
		var subscription = defaultBuilderUnderTest.constructAuthorizationSubscription(authentication,
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
								.where("targetClass", is(jsonObject()
										.where("simpleName", jsonText("TestClass")))))),
				() -> assertThat(subscription.getEnvironment(), is(jsonNull())));
		// @formatter:on

	}

	@Test
	void when_nullParametersInvocationHasArgumentsThatCannotBeMappedToJson_then_FactoryConstructsFromContextExcludingProblematicArguments() {
		var attribute = attribute(null, null, null, null, Object.class);
		var invocationWithBadArgs = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class,
				"publicVoidProblemArg", new Class<?>[] { BadForJackson.class }, new Object[] { new BadForJackson() });
		var subscription = defaultBuilderUnderTest.constructAuthorizationSubscription(authentication,
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
								.where("targetClass", is(jsonObject()
										.where("simpleName", jsonText("TestClass")))))),
				() -> assertThat(subscription.getEnvironment(), is(jsonNull())));
		// @formatter:on
	}

	@Test
	void when_reactive_nullParameters_then_FactoryConstructsFromContext() {
		ServerWebExchange serverWebExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/foo/bar"));
		SecurityContext securityContext = new MockSecurityContext(authentication);
		var attribute = attribute(null, null, null, null, Object.class);
		var subscription = defaultBuilderUnderTest.reactiveConstructAuthorizationSubscription(invocation, attribute)
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
								.where("targetClass", is(jsonObject()
										.where("simpleName", jsonText("TestClass")))))),
				() -> assertThat(subscription.getEnvironment(), is(jsonNull())));
		// @formatter:on
	}

	@Test
	void when_reactive_nullParametersAndNoAuthn_then_FactoryConstructsFromContextAndAnonymous() {
		var attribute = attribute(null, null, null, null, Object.class);
		var subscription = defaultBuilderUnderTest.reactiveConstructAuthorizationSubscription(invocation, attribute)
				.block();
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
								  .where("targetClass", is(jsonObject()
										.where("simpleName", jsonText("TestClass")) )))),
			      () -> assertThat(subscription.getEnvironment(), is(jsonNull())));
		// @formatter:on
	}

	@Test
	void when_reactive_returnObjectInExpression_then_FactoryConstructsReturnObjectInSubscription() {
		var attribute = attribute(null, null, "returnObject", null, Object.class);
		var subscription = defaultBuilderUnderTest
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
		return new PreEnforceAttribute(parameterToExpression(subject), parameterToExpression(action),
				parameterToExpression(resource), parameterToExpression(environment), type);
	}

	private Expression parameterToExpression(String parameter) {
		var parser = new SpelExpressionParser();
		return parameter == null || parameter.isEmpty() ? null : parser.parseExpression(parameter);
	}

	public static class TestClass {

		public void publicVoid() {
		}

		public void publicVoidArgs(Integer x) {
		}

		public void publicVoidProblemArg(BadForJackson param) {
		}

	}

	public static class BadForJackson {

		@SuppressWarnings("unused")
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
