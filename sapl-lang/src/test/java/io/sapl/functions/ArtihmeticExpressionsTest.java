package io.sapl.functions;

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.math.BigDecimal;

import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.EObjectUtil;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArtihmeticExpressionsTest {

	@Test
	public void collectionOfPassingExpressions() throws IOException {
		doTest("(1+2)*3.0", 9.00D, false);
		doTest("1+-1", 0D, false);
		doTest("1+ -1", 0D, false);
		doTest("1 + -1", 0D, false);
		doTest("1 + - 1", 0D, false);
		doTest("--1", 1D, false);
	}

	@Test
	@Ignore
	public void oneMinusOne_IsNull() throws IOException {
		// Parser   : BasicsValue -> value 1
		// Expected : ( 1 - 1 )
		doTest("1-1", 0D, true);

	}
	@Test
	@Ignore
	public void unaryPlus_IsImplemented() throws IOException {
		// Parser   : ( (1 + nullPointer) + ((2)) )
		// Expected : ( 1 + (UNARY_PLUS ((2))) )
		doTest("1+ +(2)", 3D, true);
	}

	@Test
	@Ignore
	public void noSpacesPlusAndMinusEvaluates() throws IOException {
		// Parser   :  ( 5 + 5 )
		// Expected : ( (5+5) - 3 )
		doTest("5+5-3", 7D, false);
	}

	private void doTest(String given, double expected, boolean logIt) throws IOException {
		var expression = ParserUtil.expression(given);

		if (logIt)
			EObjectUtil.dump(expression);

		var actual = expression.evaluate(MockUtil.constructTestEnvironmentPdpScopedEvaluationContext(), Val.UNDEFINED)
				.blockFirst();

		if (logIt)
			log.info("{}=={} -> {} - Actual: {}", given, expected,
					actual.get().decimalValue().compareTo(BigDecimal.valueOf(expected)) == 0, actual);

		assertThat(actual.decimalValue(), Matchers.comparesEqualTo(BigDecimal.valueOf(expected)));

	}

}
