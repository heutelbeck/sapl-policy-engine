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

import static io.sapl.test.Imports.times;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

public class FunctionMockAlwaysSameValueTest {

	private Val alwaysReturnValue = Val.of("bar");

	@Test
	void test() {
		FunctionMockAlwaysSameValue mock = new FunctionMockAlwaysSameValue("foo", alwaysReturnValue, times(1));
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(1))).isEqualTo(alwaysReturnValue);
	}

	@Test
	void test_multipleTimes() {
		FunctionMockAlwaysSameValue mock = new FunctionMockAlwaysSameValue("foo", alwaysReturnValue, times(3));
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(1))).isEqualTo(alwaysReturnValue);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(2))).isEqualTo(alwaysReturnValue);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(3))).isEqualTo(alwaysReturnValue);

		boolean isAssertionErrorThrown = false;
		try {
			mock.assertVerifications();
		}
		catch (AssertionError e) {
			isAssertionErrorThrown = true;
		}

		Assertions.assertThat(isAssertionErrorThrown).isFalse();
	}

	@Test
	void test_errorMessage() {
		FunctionMockAlwaysSameValue mock = new FunctionMockAlwaysSameValue("foo", alwaysReturnValue, times(1));
		Assertions.assertThat(mock.getErrorMessageForCurrentMode().isEmpty()).isFalse();
	}

}
