package io.sapl.test.mocking.attribute;

import io.sapl.api.interpreter.Val;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

public class AttributeMockPublisherTest {

	@Test
	void test() {
		AttributeMockPublisher mock = new AttributeMockPublisher("foo.bar");

		StepVerifier.create(mock.evaluate(null, null, null))
			.then(() -> mock.mockEmit(Val.of(1)))
			.expectNext(Val.of(1))
			.thenCancel().verify();
		
		mock.assertVerifications();
	}
	
	@Test
	void test_errorMessage() {
		AttributeMockPublisher mock = new AttributeMockPublisher("test.test");
		Assertions.assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
	}

}
