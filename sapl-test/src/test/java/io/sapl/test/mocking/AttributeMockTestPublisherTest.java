package io.sapl.test.mocking;

import io.sapl.api.interpreter.Val;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

public class AttributeMockTestPublisherTest {

	@Test
	void test() {
		AttributeMockTestPublisher mock = new AttributeMockTestPublisher("foo.bar");

		StepVerifier.create(mock.evaluate())
			.then(() -> mock.mockEmit(Val.of(1)))
			.expectNext(Val.of(1))
			.thenCancel().verify();
		
		mock.assertVerifications();
	}
	
	@Test
	void test_errorMessage() {
		AttributeMockTestPublisher mock = new AttributeMockTestPublisher("test.test");
		Assertions.assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
	}

}
