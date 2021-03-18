package io.sapl.api.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PolicyEvaluationExceptionTest {

	private static final String MESSAGE_STRING_D = "MESSAGE STRING %d";

	@Test
	void exceptionHoldsMessage() {
		var exception = new PolicyEvaluationException(MESSAGE_STRING_D);
		assertEquals(MESSAGE_STRING_D, exception.getMessage());
	}

}
