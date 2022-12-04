package io.sapl.interpreter.trace;

import org.junit.jupiter.api.Test;

public class DecisionTraceTests {

	@Test
	void testTrace() {
		var trace = new DecisionTrace();
		trace.log("sout");
	}
}
