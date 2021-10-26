/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.mocking.function;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.function.FunctionMockSequence;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class FunctionMockSequenceTest {

	private Val[] seq = new Val[] { Val.of(1), Val.of(2), Val.of(3) };

	@Test
	void test() {
		FunctionMockSequence mock = new FunctionMockSequence("foo");
		mock.loadMockReturnValue(seq);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("do"))).isEqualTo(seq[0]);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("not"))).isEqualTo(seq[1]);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("matter"))).isEqualTo(seq[2]);
	}

	@Test
	void test_tooMuchCalls() {
		FunctionMockSequence mock = new FunctionMockSequence("foo");
		mock.loadMockReturnValue(seq);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("do"))).isEqualTo(seq[0]);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("not"))).isEqualTo(seq[1]);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("matter"))).isEqualTo(seq[2]);
		Assertions.assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> mock.evaluateFunctionCall(Val.of("returnValueUndefined")));
	}

	@Test
	void test_tooLessCalls() {
		FunctionMockSequence mock = new FunctionMockSequence("foo");
		mock.loadMockReturnValue(seq);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("do"))).isEqualTo(seq[0]);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("not"))).isEqualTo(seq[1]);

		boolean isAssertionErrorThrown = false;
		try {
			mock.assertVerifications();
		}
		catch (AssertionError e) {
			isAssertionErrorThrown = true;
		}

		Assertions.assertThat(isAssertionErrorThrown).isTrue();
	}

	@Test
	void test_errorMessage() {
		FunctionMockSequence mock = new FunctionMockSequence("foo");
		Assertions.assertThat(mock.getErrorMessageForCurrentMode().isEmpty()).isFalse();
	}

}
