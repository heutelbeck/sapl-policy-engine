package io.sapl.test.mocking;

import java.time.Duration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import reactor.test.StepVerifier;

public class AttributeMockTimingTest {

	@Test
	void test() {
		AttributeMockTiming mock = new AttributeMockTiming("test.test");
		mock.loadAttributeMockWithTiming(Duration.ofSeconds(10), Val.of(1), Val.of(2), Val.of(3), Val.of(4));
		 
		StepVerifier.withVirtualTime(() -> mock.evaluate())
			.expectSubscription()
			.expectNoEvent(Duration.ofSeconds(10))
			.expectNext(Val.of(1))
			.expectNoEvent(Duration.ofSeconds(10))
			.expectNext(Val.of(2))
			.expectNoEvent(Duration.ofSeconds(10))
			.expectNext(Val.of(3))
			.expectNoEvent(Duration.ofSeconds(10))
			.expectNext(Val.of(4))
			.verifyComplete();
		
		mock.assertVerifications();
	}
	
	@Test
	void test_errorMessage() {
		AttributeMockTiming mock = new AttributeMockTiming("test.test");
		Assertions.assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
	}


}
