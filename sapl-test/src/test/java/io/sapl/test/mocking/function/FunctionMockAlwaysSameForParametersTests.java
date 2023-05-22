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

import static io.sapl.test.Imports.times;
import static io.sapl.test.Imports.whenFunctionParams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;

class FunctionMockAlwaysSameForParametersTests {

	@Test
	void test() {
		var mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenFunctionParams(is(Val.of(1))), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenFunctionParams(is(Val.of(2))), times(2));

		assertThat(mock.evaluateFunctionCall(Val.of(1))).isEqualTo(Val.of("foo"));
		assertThat(mock.evaluateFunctionCall(Val.of(2))).isEqualTo(Val.of("bar"));
		assertThat(mock.evaluateFunctionCall(Val.of(2))).isEqualTo(Val.of("bar"));

		assertThatNoException().isThrownBy(mock::assertVerifications);
	}

	@Test
	void test_CallParameters_TooMuch() {
		var val1       = Val.of(1);
		var valTooMuch = Val.of("tooMuch");

		var mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenFunctionParams(is(Val.of(1))), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenFunctionParams(is(Val.of(2))), times(2));
		assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> mock.evaluateFunctionCall(val1, valTooMuch));
	}

	@Test
	void test_CallParameters_TooLess() {
		var mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenFunctionParams(is(Val.of(1))), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenFunctionParams(is(Val.of(2))), times(1));
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(mock::evaluateFunctionCall);
	}

	@Test
	void test_MatchingParameters_TooMuch() {
		var val1 = Val.of(1);
		var mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenFunctionParams(is(Val.of(1)), is(Val.of("tooMuch"))),
				times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenFunctionParams(is(Val.of(2)), is(Val.of("tooMuch"))),
				times(1));
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> mock.evaluateFunctionCall(val1));
	}

	@Test
	void test_MatchingParameters_TooLess() {
		var val1 = Val.of(1);
		var mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenFunctionParams(), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenFunctionParams(), times(2));
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> mock.evaluateFunctionCall(val1));
	}

	@Test
	void test_Parameters_NotFound() {
		var val3 = Val.of(3);
		var mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenFunctionParams(is(Val.of(1))), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenFunctionParams(is(Val.of(2))), times(2));
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> mock.evaluateFunctionCall(val3));
	}

	@Test
	void test_errorMessage() {
		var mock = new FunctionMockAlwaysSameForParameters("foo");
		assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
	}

}
