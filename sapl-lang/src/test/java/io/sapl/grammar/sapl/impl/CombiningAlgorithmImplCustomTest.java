package io.sapl.grammar.sapl.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CombiningAlgorithmImplCustomTest {

	@Test
	void cannotBeCalled() {
		var combiner = new CombiningAlgorithmImplCustom();
		assertThrows(UnsupportedOperationException.class, () -> combiner.combineDecisions(null, true));
	}

}
