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
package io.sapl.test;

import static io.sapl.test.Imports.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.sapl.api.interpreter.Val;
import io.sapl.test.mocking.MockCall;
import io.sapl.test.verification.MockRunInformation;

import org.junit.jupiter.api.Test;

/**
 * Times Verification Convenience Test Cases for additional test cases see
 * TimesCalledVerificationTest
 */
public class ImportsTest {

	@Test
	void test_times_specificNumber() {
		MockRunInformation mockRunInformation = new MockRunInformation("test.test");
		mockRunInformation.saveCall(new MockCall(Val.of(1)));
		mockRunInformation.saveCall(new MockCall(Val.of(1)));
		var verification = times(2);

		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(mockRunInformation);
		}
		catch (AssertionError e) {
			isAssertionErrorThrown = true;
		}

		assertThat(isAssertionErrorThrown).isFalse();
	}

	@Test
	void test_times_specificNumber_failure() {
		MockRunInformation mockRunInformation = new MockRunInformation("test.test");
		mockRunInformation.saveCall(new MockCall(Val.of(1)));
		var verification = times(2);

		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(mockRunInformation);
		}
		catch (AssertionError e) {
			isAssertionErrorThrown = true;
		}

		assertThat(isAssertionErrorThrown).isTrue();
	}

	@Test
	void test_times_never() {
		MockRunInformation mockRunInformation = new MockRunInformation("test.test");
		var verification = never();

		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(mockRunInformation);
		}
		catch (AssertionError e) {
			isAssertionErrorThrown = true;
		}

		assertThat(isAssertionErrorThrown).isFalse();
	}

	@Test
	void test_times_never_failure() {
		MockRunInformation mockRunInformation = new MockRunInformation("test.test");
		mockRunInformation.saveCall(new MockCall(Val.of(1)));
		var verification = never();

		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(mockRunInformation);
		}
		catch (AssertionError e) {
			isAssertionErrorThrown = true;
		}

		assertThat(isAssertionErrorThrown).isTrue();
	}

	@Test
	void test_times_anyTimes_0() {
		MockRunInformation mockRunInformation = new MockRunInformation("test.test");
		var verification = anyTimes();

		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(mockRunInformation);
		}
		catch (AssertionError e) {
			isAssertionErrorThrown = true;
		}

		assertThat(isAssertionErrorThrown).isFalse();
	}

	@Test
	void test_times_anyTimes_1() {
		MockRunInformation mockRunInformation = new MockRunInformation("test.test");
		mockRunInformation.saveCall(new MockCall(Val.of(1)));
		var verification = anyTimes();

		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(mockRunInformation);
		}
		catch (AssertionError e) {
			isAssertionErrorThrown = true;
		}

		assertThat(isAssertionErrorThrown).isFalse();
	}

	@Test
	void test_times_anyTimes_N() {
		MockRunInformation mockRunInformation = new MockRunInformation("test.test");
		mockRunInformation.saveCall(new MockCall(Val.of(1)));
		mockRunInformation.saveCall(new MockCall(Val.of(1)));
		mockRunInformation.saveCall(new MockCall(Val.of(1)));
		mockRunInformation.saveCall(new MockCall(Val.of(1)));
		var verification = anyTimes();

		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(mockRunInformation);
		}
		catch (AssertionError e) {
			isAssertionErrorThrown = true;
		}

		assertThat(isAssertionErrorThrown).isFalse();
	}

}
