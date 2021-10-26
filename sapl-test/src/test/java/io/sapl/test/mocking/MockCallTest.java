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
package io.sapl.test.mocking;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class MockCallTest {

	@Test
	void test() {
		MockCall call = new MockCall(Val.of("foo"));
		
		
		Assertions.assertThat(call.getNumberOfArguments()).isEqualTo(1);
		Assertions.assertThat(call.getArgument(0)).isEqualTo(Val.of("foo"));
		Assertions.assertThat(call.getListOfArguments().size()).isEqualTo(1);
	}
	
	@Test
	void test_invalidIndex() {
		MockCall call = new MockCall(Val.of("foo"));
		
		
		Assertions.assertThat(call.getNumberOfArguments()).isEqualTo(1);
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> call.getArgument(1));
	}
	
	@Test
	void test_modifyParameterList() {
		MockCall call = new MockCall(Val.of("foo"));
		
		
		Assertions.assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(() -> call.getListOfArguments().add(Val.of("barr")));
	}

}
