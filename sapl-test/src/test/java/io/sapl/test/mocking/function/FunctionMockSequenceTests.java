/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;

class FunctionMockSequenceTests {

	private final Val[] seq = new Val[] { Val.of(1), Val.of(2), Val.of(3) };

	@Test
	void test() {
		var mock = new FunctionMockSequence("foo");
		mock.loadMockReturnValue(seq);
		assertThat(mock.evaluateFunctionCall(Val.of("do"))).isEqualTo(seq[0]);
		assertThat(mock.evaluateFunctionCall(Val.of("not"))).isEqualTo(seq[1]);
		assertThat(mock.evaluateFunctionCall(Val.of("matter"))).isEqualTo(seq[2]);
	}

	@Test
	void test_tooManyCalls() {
		var aVal = Val.of("returnValueUndefined");
		var mock = new FunctionMockSequence("foo");
		mock.loadMockReturnValue(seq);
		assertThat(mock.evaluateFunctionCall(Val.of("do"))).isEqualTo(seq[0]);
		assertThat(mock.evaluateFunctionCall(Val.of("not"))).isEqualTo(seq[1]);
		assertThat(mock.evaluateFunctionCall(Val.of("matter"))).isEqualTo(seq[2]);
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> mock.evaluateFunctionCall(aVal));
	}

	@Test
	void test_tooFewCalls() {
		var mock = new FunctionMockSequence("foo");
		mock.loadMockReturnValue(seq);
		assertThat(mock.evaluateFunctionCall(Val.of("do"))).isEqualTo(seq[0]);
		assertThat(mock.evaluateFunctionCall(Val.of("not"))).isEqualTo(seq[1]);

		assertThatExceptionOfType(AssertionError.class).isThrownBy(mock::assertVerifications);
	}

	@Test
	void test_errorMessage() {
		var mock = new FunctionMockSequence("foo");
		assertThat(mock.getErrorMessageForCurrentMode().isEmpty()).isFalse();
	}

}
