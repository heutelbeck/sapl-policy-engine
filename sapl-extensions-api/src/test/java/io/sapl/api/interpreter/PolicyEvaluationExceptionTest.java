/*
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
