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
		when(pep.isPermitted(any())).thenReturn(Mono.just(false));

		StepVerifier.create(actual).assertNext(authorization -> assertFalse(authorization.isGranted()))
				.verifyComplete();
	}

	@Test
	public void check_pepReturnsTrue_shouldBeGranted() {
		when(pep.isPermitted(any())).thenReturn(Mono.just(true));
		var actual = sut.check(AUTHENTICATION, mock(AuthorizationContext.class));
		StepVerifier.create(actual).assertNext(authorization -> assertTrue(authorization.isGranted())).verifyComplete();
	}

}
