/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.mocking.attribute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import reactor.test.StepVerifier;

class AttributeMockTimingTests {

	@Test
	void test() {
		var mock = new AttributeMockTiming("test.test");
		mock.loadAttributeMockWithTiming(Duration.ofSeconds(10), Val.of(1), Val.of(2), Val.of(3), Val.of(4));

		StepVerifier.withVirtualTime(() -> mock.evaluate("test.attribute", null, null, null)).expectSubscription()
				.expectNoEvent(Duration.ofSeconds(10)).expectNext(Val.of(1)).expectNoEvent(Duration.ofSeconds(10))
				.expectNext(Val.of(2)).expectNoEvent(Duration.ofSeconds(10)).expectNext(Val.of(3))
				.expectNoEvent(Duration.ofSeconds(10)).expectNext(Val.of(4)).verifyComplete();

		mock.assertVerifications();
	}

	@Test
	void test_errorMessage() {
		var mock = new AttributeMockTiming("test.test");
		assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
	}

	@Test
	void test_nullReturnValue() {
		var mock = new AttributeMockTiming("test.test");
		mock.loadAttributeMockWithTiming(Duration.ofSeconds(1), (Val[]) null);
		assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> mock.evaluate("test.attribute", null, null, null));
	}

	@Test
	void test_nullTiming() {
		var mock = new AttributeMockTiming("test.test");
		mock.loadAttributeMockWithTiming(null, Val.of(1));
		assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> mock.evaluate("test.attribute", null, null, null));
	}

}
