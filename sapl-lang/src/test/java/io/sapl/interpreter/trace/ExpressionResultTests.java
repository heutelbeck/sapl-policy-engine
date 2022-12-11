package io.sapl.interpreter.trace;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.BinaryOperator;
import io.sapl.grammar.sapl.Plus;
import io.sapl.grammar.sapl.Regex;
import io.sapl.grammar.sapl.StringLiteral;
import io.sapl.grammar.sapl.TrueLiteral;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExpressionResultTests {

	private final static boolean DEBUG = false;

	@Test
	void testTrace() {
		var result1 = Val.TRUE.withTrace(new Trace(TrueLiteral.class));
		var result2 = Val.of("ABC").withTrace(new Trace(StringLiteral.class));
		var result3 = Val.of("XXXXX").withTrace(new Trace(Plus.class, result1, result2));
		var result4 = Val.of("YYY").withTrace(new Trace(BinaryOperator.class, result3, result3));
		var result5 = Val.of("SOMETHING").withTrace(new Trace(Regex.class, result4, result3));
		multiLineLog(result5.evaluationTree());
	}

	void multiLineLog(String message) {
		if (DEBUG)
			for (var line : message.split("\n")) {
				log.info(line);
			}
	}
}
