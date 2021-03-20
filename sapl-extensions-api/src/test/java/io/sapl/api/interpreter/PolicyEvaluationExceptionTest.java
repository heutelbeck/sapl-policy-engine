package io.sapl.api.interpreter;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PolicyEvaluationExceptionTest {

	private static final String MESSAGE_STRING_1 = "MESSAGE STRING 1";
	private static final String MESSAGE_STRING_D = "MESSAGE STRING %d";

	@Test
	void defaultConstructor() {
		var exception = new PolicyEvaluationException();
		assertThat(exception, notNullValue());
	}

	@Test
	void exceptionHoldsMessage() {
		var exception = new PolicyEvaluationException(MESSAGE_STRING_D);
		assertEquals(MESSAGE_STRING_D, exception.getMessage());
	}

	@Test
	void exceptionHoldsFormattedMessage() {
		var exception = new PolicyEvaluationException(MESSAGE_STRING_D, 1);
		assertEquals(MESSAGE_STRING_1, exception.getMessage());
	}

	@Test
	void exceptionHoldsFormattedMessageAndCause() {
		var exception = new PolicyEvaluationException(new RuntimeException(), MESSAGE_STRING_D, 1);
		assertAll(() -> assertEquals(MESSAGE_STRING_1, exception.getMessage()),
				() -> assertThat(exception.getCause(), is(instanceOf(RuntimeException.class))));
	}

	@Test
	void exceptionHoldsMessageAndCause() {
		var exception = new PolicyEvaluationException(MESSAGE_STRING_D, new RuntimeException());
		assertAll(() -> assertEquals(MESSAGE_STRING_D, exception.getMessage()),
				() -> assertThat(exception.getCause(), is(instanceOf(RuntimeException.class))));
	}

	@Test
	void exceptionHoldsCause() {
		var exception = new PolicyEvaluationException(new RuntimeException());
		assertThat(exception.getCause(), is(instanceOf(RuntimeException.class)));
	}
}
