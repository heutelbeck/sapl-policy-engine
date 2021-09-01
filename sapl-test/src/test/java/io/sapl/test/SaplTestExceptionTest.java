package io.sapl.test;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SaplTestExceptionTest {

	@Test
	void test_noMessage() {
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> {
			throw new SaplTestException();
			}).withMessage(null);
	}
	
	@Test
	void test_messageAndNestedException() {
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> {
			throw new SaplTestException("exception", new SaplTestException("nestedException"));
			})
			.withMessage("exception")
			.withCauseInstanceOf(SaplTestException.class);
	}

}
