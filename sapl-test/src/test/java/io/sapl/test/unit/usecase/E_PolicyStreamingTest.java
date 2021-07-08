package io.sapl.test.unit.usecase;


import static io.sapl.hamcrest.Matchers.anyDecision;
import static io.sapl.test.Imports.times;

import java.time.Duration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import io.sapl.pip.ClockPolicyInformationPoint;
import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

public class E_PolicyStreamingTest {

	private SaplTestFixture fixture;
	
	@BeforeEach
	void setUp() throws InitializationException {
		fixture = new SaplUnitTestFixture("policyStreaming")
				//.registerPIP(null)
				.registerFunctionLibrary(new TemporalFunctionLibrary());
	}

	
	@Test
	void test_streamingPolicy() {
		var timestamp0 = Val.of("2021-02-08T16:16:01.000Z");
		var timestamp1 = Val.of("2021-02-08T16:16:02.000Z");
		var timestamp2 = Val.of("2021-02-08T16:16:03.000Z");
		var timestamp3 = Val.of("2021-02-08T16:16:04.000Z");
		var timestamp4 = Val.of("2021-02-08T16:16:05.000Z");
		var timestamp5 = Val.of("2021-02-08T16:16:06.000Z");
			
		fixture.constructTestCaseWithMocks()
			.givenAttribute("clock.ticker", timestamp0, timestamp1, timestamp2, timestamp3, timestamp4, timestamp5)
			.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
			.expectNextNotApplicable()
			.expectNextNotApplicable()
			.expectNextNotApplicable()
			.expectNextNotApplicable()
			//.expectNextNotApplicable(4)
			.expectNextPermit()
			.expectNextPermit()
			.verify();
	}
	
	@Test
	@Disabled
	void test_streamingPolicyWithVirtualTime() throws InitializationException {
		
		fixture.registerPIP(new ClockPolicyInformationPoint())
			.constructTestCaseWithMocks()
			.withVirtualTime()
			.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
			.expectNext(anyDecision())
			.thenAwait(Duration.ofSeconds(2))
			.expectNext(anyDecision())
			.thenAwait(Duration.ofSeconds(2))
			.expectNext(anyDecision())
			.thenAwait(Duration.ofSeconds(2))
			.expectNext(anyDecision())
			.thenAwait(Duration.ofSeconds(2))
			.expectNext(anyDecision())
			.thenAwait(Duration.ofSeconds(2))
			.expectNext(anyDecision())
			.verify();
	}
	
	@Test
	void test_streamingPolicy_TimingAttributeMock() {
		var timestamp0 = Val.of("2021-02-08T16:16:01.000Z");
		var timestamp1 = Val.of("2021-02-08T16:16:02.000Z");
		var timestamp2 = Val.of("2021-02-08T16:16:03.000Z");
		var timestamp3 = Val.of("2021-02-08T16:16:04.000Z");
		var timestamp4 = Val.of("2021-02-08T16:16:05.000Z");
		var timestamp5 = Val.of("2021-02-08T16:16:06.000Z");
			
		fixture.constructTestCaseWithMocks()
			.withVirtualTime()
			.givenAttribute("clock.ticker", Duration.ofSeconds(10), timestamp0, timestamp1, timestamp2, timestamp3, timestamp4, timestamp5)
			.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
			.thenAwait(Duration.ofSeconds(10))
			.expectNextNotApplicable()
			.thenAwait(Duration.ofSeconds(10))
			.expectNextNotApplicable()
			.thenAwait(Duration.ofSeconds(10))
			.expectNextNotApplicable()
			.thenAwait(Duration.ofSeconds(10))
			.expectNextNotApplicable()
			.thenAwait(Duration.ofSeconds(10))
			.expectNextPermit()
			.thenAwait(Duration.ofSeconds(10))
			.expectNextPermit()
			.thenAwait(Duration.ofSeconds(10))
			.verify();
	}
	
	@Test
	void test_streamingPolicy_TimingAttributeMock_WithoutVirtualTime() {
		var timestamp0 = Val.of("2021-02-08T16:16:01.000Z");
		var timestamp1 = Val.of("2021-02-08T16:16:02.000Z");
		var timestamp2 = Val.of("2021-02-08T16:16:03.000Z");
		var timestamp3 = Val.of("2021-02-08T16:16:04.000Z");
		var timestamp4 = Val.of("2021-02-08T16:16:05.000Z");
		var timestamp5 = Val.of("2021-02-08T16:16:06.000Z");
			
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(
				() -> fixture.constructTestCaseWithMocks()
					.givenAttribute("clock.ticker", Duration.ofSeconds(10), timestamp0, timestamp1, timestamp2, timestamp3, timestamp4, timestamp5)
					);
			
	}
	
	
	@Test
	void test_streamingPolicyWithSimpleMockedFunction_ConsecutiveCalls() {
		
		var timestamp0 = Val.of("2021-02-08T16:16:01.000Z");
		var timestamp1 = Val.of("2021-02-08T16:16:02.000Z");
		
		fixture.constructTestCaseWithMocks()
			.givenAttribute("clock.ticker", timestamp0, timestamp1)
			.givenFunctionOnce("time.localSecond", Val.of(4))
			.givenFunctionOnce("time.localSecond", Val.of(5))
			.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
			.expectNextNotApplicable()
			.expectNextPermit()
			.verify(); // two times mock of function -> verify two times called
	
	}
	
	@Test
	void test_streamingPolicyWithSimpleMockedFunction_ArrayOfReturnValues() {
		
		fixture.constructTestCaseWithMocks()
			.givenAttribute("clock.ticker", Val.of("value"), Val.of("doesn't"), Val.of("matter"))
			.givenFunctionOnce("time.localSecond", Val.of(3), Val.of(4), Val.of(5))
			.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
			.expectNextNotApplicable()
			.expectNextNotApplicable()
			.expectNextPermit()
			.verify(); // three times mock of function -> verify two times called
	
	}
	
	@Test
	void test_streamingPolicyWithSimpleMockedFunction_AlwaysReturn_VerifyTimesCalled() {
		
		fixture.constructTestCaseWithMocks()
			.givenAttribute("clock.ticker", Val.of("value"), Val.of("doesn't"), Val.of("matter"))
			.givenFunction("time.localSecond", Val.of(5), times(3))
			.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
			.expectNextPermit(3)
			.verify(); // three times mock of function -> three times called
	
	}
	
	

	/*
	@Test
	void test_policyWithSimpleMockedFunction_CheckArgument() {
		
		fixture.constructTestCaseWithMocks()
			.givenFunction("time.dayOfWeekFrom", Val.of("SATURDAY"), argThat(Val.of("")))
			.when(AuthorizationSubscription.of("willi", "read", "something"))
			.expectPermit()
			.verify();
	
	}
	*/
	
	
	
	
}
