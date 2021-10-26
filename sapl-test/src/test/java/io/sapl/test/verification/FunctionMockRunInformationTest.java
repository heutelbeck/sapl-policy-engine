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
package io.sapl.test.verification;

import io.sapl.api.interpreter.Val;
import io.sapl.test.mocking.MockCall;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class FunctionMockRunInformationTest {

	@Test
	void test_initialization() {
		String fullname = "foo";

		MockRunInformation mock = new MockRunInformation(fullname);

		Assertions.assertThat(mock.getFullname()).isEqualTo(fullname);
		Assertions.assertThat(mock.getTimesCalled()).isEqualTo(0);
		Assertions.assertThat(mock.getCalls()).isNotNull();
	}

	@Test
	void test_increase() {
		String fullname = "foo";
		MockCall call = new MockCall(Val.of("foo"));
		MockRunInformation mock = new MockRunInformation(fullname);

		mock.saveCall(call);

		Assertions.assertThat(mock.getTimesCalled()).isEqualTo(1);
		Assertions.assertThat(mock.getCalls().get(0).isUsed()).isFalse();
		Assertions.assertThat(mock.getCalls().get(0).getCall().getNumberOfArguments()).isEqualTo(1);
		Assertions.assertThat(mock.getCalls().get(0).getCall().getArgument(0)).isEqualTo(Val.of("foo"));
	}

}
