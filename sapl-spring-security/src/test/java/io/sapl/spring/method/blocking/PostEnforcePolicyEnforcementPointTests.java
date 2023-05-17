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
package io.sapl.spring.method.blocking;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.EnableSaplMethodSecurity;
import io.sapl.spring.constraints.BlockingConstraintHandlerBundle;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.FunctionUtil;
import io.sapl.spring.method.blocking.PostEnforcePolicyEnforcementPointTests.Application;
import io.sapl.spring.method.blocking.PostEnforcePolicyEnforcementPointTests.MethodSecurityConfiguration;
import io.sapl.spring.method.blocking.PostEnforcePolicyEnforcementPointTests.TestService;
import io.sapl.spring.method.metadata.PostEnforce;
import reactor.core.publisher.Flux;

@SpringBootTest(classes = { Application.class, MethodSecurityConfiguration.class, TestService.class })
class PostEnforcePolicyEnforcementPointTests {

	private static final JsonNodeFactory JSON                   = JsonNodeFactory.instance;
	private static final String          ORIGINAL_RETURN_OBJECT = "original return object";
	private static final String          CHANGED_RETURN_OBJECT  = "changed return object";
	private static final String          USER                   = "user";

	@MockBean
	private PolicyDecisionPoint pdp;

	@MockBean
	private ConstraintEnforcementService constraintEnforcementService;

	@Autowired
	TestService testService;

	@SpringBootApplication
	static class Application {
		public static void main(String... args) {
			SpringApplication.run(Application.class, args);
		}
	}

	@TestConfiguration
	@EnableSaplMethodSecurity
	static class MethodSecurityConfiguration {
	}

	@Service
	static class TestService {
		@PostEnforce
		public String doSomething() {
			return ORIGINAL_RETURN_OBJECT;
		}

		@PostEnforce
		public Optional<String> doSomethingOptional() {
			return Optional.of("I did something!");
		}

		@PostEnforce
		public Optional<String> doSomethingOptionalEmpty() {
			return Optional.empty();
		}
	}

	@Test
	@WithMockUser(USER)
	void when_errorDuringBundleConstruction_then_AccessDenied() {
		when(constraintEnforcementService.blockingPostEnforceBundleFor(any(), any()))
				.thenThrow(new IllegalStateException("TEST FAILURE"));
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThrows(AccessDeniedException.class, () -> testService.doSomething());
	}

	@Test
	@WithMockUser(USER)
	void when_bundleIsNull_then_AccessDenied() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThrows(AccessDeniedException.class, () -> testService.doSomething());
	}

	@Test
	@WithMockUser(USER)
	void when_AfterAndDecideIsPermit_then_ReturnOriginalReturnObject() throws Throwable {
		when(constraintEnforcementService.blockingPostEnforceBundleFor(any(), any())).thenReturn(BlockingConstraintHandlerBundle.BLOCKING_NOOP);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThat(testService.doSomething(), is(ORIGINAL_RETURN_OBJECT));
	}

	@Test
	@WithMockUser(USER)
	void when_AfterAndDecideIsDeny_then_ThrowAccessDeniedException() {
		when(constraintEnforcementService.blockingPostEnforceBundleFor(any(), any())).thenReturn(BlockingConstraintHandlerBundle.BLOCKING_NOOP);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
		assertThrows(AccessDeniedException.class, () -> testService.doSomething());
	}

	@Test
	@WithMockUser(USER)
	void when_AfterBeforeAndDecideNotApplicable_then_ThrowAccessDeniedException() {
		when(constraintEnforcementService.blockingPostEnforceBundleFor(any(), any())).thenReturn(BlockingConstraintHandlerBundle.BLOCKING_NOOP);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
		assertThrows(AccessDeniedException.class, () -> testService.doSomething());
	}

	@Test
	@WithMockUser(USER)
	void when_AfterAndDecideIsIndeterminate_then_ThrowAccessDeniedException() {
		when(constraintEnforcementService.blockingPostEnforceBundleFor(any(), any())).thenReturn(BlockingConstraintHandlerBundle.BLOCKING_NOOP);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
		assertThrows(AccessDeniedException.class, () -> testService.doSomething());
	}

	@Test
	@WithMockUser(USER)
	void when_AfterAndDecideIsEmpty_then_ThrowAccessDeniedException() {
		when(constraintEnforcementService.blockingPostEnforceBundleFor(any(), any())).thenReturn(BlockingConstraintHandlerBundle.BLOCKING_NOOP);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.empty());
		assertThrows(AccessDeniedException.class, () -> testService.doSomething());
	}

	@Test
	@WithMockUser(USER)
	void when_AfterAndDecideIsPermitWithResource_then_ReturnTheReplacementObject() throws Throwable {
		var replaceBundle = BlockingConstraintHandlerBundle.postEnforceConstraintHandlerBundle(
				FunctionUtil.noop(), FunctionUtil.sink(), UnaryOperator.identity(), FunctionUtil.sink(),
				UnaryOperator.identity(), FunctionUtil.all(),
				x -> CHANGED_RETURN_OBJECT);
		when(constraintEnforcementService.blockingPostEnforceBundleFor(any(), any())).thenReturn(replaceBundle);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode(CHANGED_RETURN_OBJECT))));
		assertThat(testService.doSomething(), is(CHANGED_RETURN_OBJECT));
	}

	@Test
	@WithMockUser(USER)
	void when_AfterAndDecideIsPermitWithResourceAndMethodReturnsOptional_then_ReturnTheReplacementObject()
			throws Throwable {
		var replaceBundle = BlockingConstraintHandlerBundle.postEnforceConstraintHandlerBundle(
				FunctionUtil.noop(), FunctionUtil.sink(), UnaryOperator.identity(), FunctionUtil.sink(),
				UnaryOperator.identity(), FunctionUtil.all(),
				x -> CHANGED_RETURN_OBJECT);

		when(constraintEnforcementService.blockingPostEnforceBundleFor(any(), any())).thenReturn(replaceBundle);
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode(CHANGED_RETURN_OBJECT))));
		assertThat(testService.doSomethingOptional(), is(Optional.of(CHANGED_RETURN_OBJECT)));
	}

	@Test
	@WithMockUser(USER)
	void when_AfterAndDecideIsPermitWithResourceAndMethodReturnsEmptyOptional_then_ReturnEmpty() throws Throwable {
		when(constraintEnforcementService.blockingPostEnforceBundleFor(any(), any())).thenReturn(BlockingConstraintHandlerBundle.BLOCKING_NOOP);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThat(testService.doSomethingOptionalEmpty(), is(Optional.empty()));
	}

	@Test
	@WithMockUser(USER)
	void when_AfterAndDecideIsPermitWithResourceAndMethodReturnsEmptyOptionalAndResourcePresent_then_ReturnResourceOptional()
			throws Throwable {
		var replaceBundle = BlockingConstraintHandlerBundle.postEnforceConstraintHandlerBundle(
				FunctionUtil.noop(), FunctionUtil.sink(), UnaryOperator.identity(), FunctionUtil.sink(),
				UnaryOperator.identity(), FunctionUtil.all(),
				x -> CHANGED_RETURN_OBJECT);

		when(constraintEnforcementService.blockingPostEnforceBundleFor(any(), any())).thenReturn(replaceBundle);
		var expectedReturnObject = Optional.of(CHANGED_RETURN_OBJECT);

		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode(CHANGED_RETURN_OBJECT))));
		assertThat(testService.doSomethingOptionalEmpty(), is(expectedReturnObject));
	}

}
