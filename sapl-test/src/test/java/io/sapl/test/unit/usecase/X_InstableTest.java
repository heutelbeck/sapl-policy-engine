package io.sapl.test.unit.usecase;

import static io.sapl.hamcrest.Matchers.anyAuthDecision;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import io.sapl.pip.ClockPolicyInformationPoint;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

class X_InstableTest {
	
	private SaplTestFixture fixture;
	
	@BeforeEach
	void setUp() throws InitializationException {
		fixture = new SaplUnitTestFixture("policyStreaming")
				//.registerPIP(null)
				.registerFunctionLibrary(new TemporalFunctionLibrary());
	}

	@Test @Disabled("Exception on build: VerifySubscriber timed out on reactor.core.publisher.FluxPeek$PeekSubscriber")
	void test_streamingPolicyWithVirtualTime() throws InitializationException {
		
		fixture.registerPIP(new ClockPolicyInformationPoint())
			.constructTestCaseWithMocks()
			.withVirtualTime()
			.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
			.expectNext(anyAuthDecision())
			.thenAwait(Duration.ofSeconds(2))
			.expectNext(anyAuthDecision())
			.thenAwait(Duration.ofSeconds(2))
			.expectNext(anyAuthDecision())
			.thenAwait(Duration.ofSeconds(2))
			.expectNext(anyAuthDecision())
			.thenAwait(Duration.ofSeconds(2))
			.expectNext(anyAuthDecision())
			.thenAwait(Duration.ofSeconds(2))
			.expectNext(anyAuthDecision())
			.verify();
	}
}
