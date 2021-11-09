package io.sapl.grammar.generator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

public class SAPLGeneratorTests {

	@Test
	void generatorTest() {
		var generator = new SAPLGenerator();
		assertDoesNotThrow(()->generator.doGenerate(null, null, null));
	}
}
