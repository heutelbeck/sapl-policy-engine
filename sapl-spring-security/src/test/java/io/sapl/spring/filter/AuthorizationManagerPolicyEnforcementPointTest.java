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
package io.sapl.spring.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.spring.pep.PolicyEnforcementPoint;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
public class AuthorizationManagerPolicyEnforcementPointTest {

	private final static Mono<Authentication> AUTHENTICATION = Mono
			.just(new TestingAuthenticationToken("user", "password", "ROLE_1", "ROLE_2"));

	private AuthorizationManagerPolicyEnforcementPoint<AuthorizationContext> sut;

	private PolicyEnforcementPoint pep;

	@BeforeEach
	void beforeEach() {
		pep = mock(PolicyEnforcementPoint.class);

		var subService = mock(AuthorizationSubscriptionBuilderService.class);
		when(subService.reactiveConstructAuthorizationSubscription(any(), any(AuthorizationContext.class)))
				.thenReturn(Mono.just(new AuthorizationSubscription()));

		sut = new AuthorizationManagerPolicyEnforcementPoint<AuthorizationContext>(subService, pep);
	}

	@Test
	public void check_pepReturnsFalse_shouldBeDenied() {
		var actual = sut.check(AUTHENTICATION, mock(AuthorizationContext.class));
		when(pep.isPermitted(any())).thenReturn(Mono.just(Boolean.FALSE));

		StepVerifier.create(actual).assertNext(authorization -> assertFalse(authorization.isGranted()))
				.verifyComplete();
	}

	@Test
	public void check_pepReturnsTrue_shouldBeGranted() {
		when(pep.isPermitted(any())).thenReturn(Mono.just(Boolean.TRUE));
		var actual = sut.check(AUTHENTICATION, mock(AuthorizationContext.class));
		StepVerifier.create(actual).assertNext(authorization -> assertTrue(authorization.isGranted())).verifyComplete();
	}

}
