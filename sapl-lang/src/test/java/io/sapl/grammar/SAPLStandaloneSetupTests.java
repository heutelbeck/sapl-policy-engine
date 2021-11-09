package io.sapl.grammar;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

public class SAPLStandaloneSetupTests {

	@Test
	void standaloneNotNullTest() {
		assertDoesNotThrow(() -> SAPLStandaloneSetup.doSetup());
	}
}
