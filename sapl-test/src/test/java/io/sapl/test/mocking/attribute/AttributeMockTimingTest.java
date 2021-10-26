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
package io.sapl.test.mocking.attribute;

import java.time.Duration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import reactor.test.StepVerifier;

public class AttributeMockTimingTest {

	@Test
	void test() {
		AttributeMockTiming mock = new AttributeMockTiming("test.test");
		mock.loadAttributeMockWithTiming(Duration.ofSeconds(10), Val.of(1), Val.of(2), Val.of(3), Val.of(4));

		StepVerifier.withVirtualTime(() -> mock.evaluate(null, null, null)).expectSubscription()
				.expectNoEvent(Duration.ofSeconds(10)).expectNext(Val.of(1)).expectNoEvent(Duration.ofSeconds(10))
				.expectNext(Val.of(2)).expectNoEvent(Duration.ofSeconds(10)).expectNext(Val.of(3))
				.expectNoEvent(Duration.ofSeconds(10)).expectNext(Val.of(4)).verifyComplete();

		mock.assertVerifications();
	}

	@Test
	void test_errorMessage() {
		AttributeMockTiming mock = new AttributeMockTiming("test.test");
		Assertions.assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
	}

	@Test
	void test_nullReturnValue() {
		AttributeMockTiming mock = new AttributeMockTiming("test.test");
		mock.loadAttributeMockWithTiming(Duration.ofSeconds(1), (Val[]) null);
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> mock.evaluate(null, null, null));
	}

	@Test
	void test_nullTiming() {
		AttributeMockTiming mock = new AttributeMockTiming("test.test");
		mock.loadAttributeMockWithTiming(null, new Val[] { Val.of(1) });
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> mock.evaluate(null, null, null));
	}

}
