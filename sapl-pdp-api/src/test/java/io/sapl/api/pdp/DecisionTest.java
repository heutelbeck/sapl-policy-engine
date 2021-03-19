package io.sapl.api.pdp;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DecisionTest {

	@Test
	void decisionTest() {
		assertThrows(IllegalArgumentException.class, () -> {
			Decision.valueOf("");
		});
	}

}
