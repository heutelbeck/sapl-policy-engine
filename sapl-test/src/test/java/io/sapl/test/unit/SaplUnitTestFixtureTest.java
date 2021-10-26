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
package io.sapl.test.unit;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;

public class SaplUnitTestFixtureTest {

	@Test
	void test_invalidSaplDocumentName1() {
		SaplTestFixture fixture = new SaplUnitTestFixture("");
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> fixture.constructTestCase());
	}

	@Test
	void test_invalidSaplDocumentName2() {
		SaplTestFixture fixture = new SaplUnitTestFixture("");
		Assertions.assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> fixture.constructTestCaseWithMocks());
	}

	@Test
	void test_invalidSaplDocumentName3() {
		SaplTestFixture fixture = new SaplUnitTestFixture(null);
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> fixture.constructTestCase());
	}

	@Test
	void test_invalidSaplDocumentName4() {
		SaplTestFixture fixture = new SaplUnitTestFixture(null);
		Assertions.assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> fixture.constructTestCaseWithMocks());
	}

}
