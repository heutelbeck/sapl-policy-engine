package io.sapl.test.unit;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;

public class SaplUnitTestFixtureTest {

	@Test
	void test_invalidSaplDocumentName1() {
		SaplTestFixture fixture = new SaplUnitTestFixture("");
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() ->fixture.constructTestCase());
	}
	
	@Test
	void test_invalidSaplDocumentName2() {
		SaplTestFixture fixture = new SaplUnitTestFixture(null);
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() ->fixture.constructTestCaseWithMocks());
	}
	
	@Test
	void test_invalidSaplDocumentName3() {
		SaplTestFixture fixture = new SaplUnitTestFixture("");
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() ->fixture.constructTestCase());
	}
	
	@Test
	void test_invalidSaplDocumentName4() {
		SaplTestFixture fixture = new SaplUnitTestFixture(null);
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() ->fixture.constructTestCaseWithMocks());
	}

}
