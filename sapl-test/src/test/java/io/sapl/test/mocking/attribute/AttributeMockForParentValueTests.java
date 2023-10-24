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

import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.test.Imports.parentValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import reactor.test.StepVerifier;

class AttributeMockForParentValueTests {

	private AttributeMockForParentValue mock;

	@BeforeEach
	void setUp() {
		mock = new AttributeMockForParentValue("attr.test");
	}

	@Test
	void test() {
		mock.loadMockForParentValue(parentValue(val(1)), Val.of(true));
		mock.loadMockForParentValue(parentValue(val(2)), Val.of(false));

		StepVerifier.create(mock.evaluate("test.attribute", Val.of(1), null, null)).expectNext(Val.of(true))
				.thenCancel().verify();

		StepVerifier.create(mock.evaluate("test.attribute", Val.of(2), null, null)).expectNext(Val.of(false))
				.thenCancel().verify();

		mock.assertVerifications();
	}

	@Test
	void test_noMatchingMockDefined() {
		var val99 = Val.of(99);
		mock.loadMockForParentValue(parentValue(val(1)), Val.of(true));
		assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> mock.evaluate("test.attribute", val99, null, null));
	}

	@Test
	void test_errorMessage() {
		assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
	}

}
