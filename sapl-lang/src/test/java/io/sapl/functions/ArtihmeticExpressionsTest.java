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
package io.sapl.functions;

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.math.BigDecimal;

import org.hamcrest.Matchers;
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
	public void oneMinusOne_IsNull() throws IOException {
		doTest("1-1", 0D, false);
	}

	@Test
	public void unaryPlus_IsImplemented() throws IOException {
		doTest("1+ +(2)", 3D, false);
	}

	@Test
	public void noSpacesPlusAndMinusEvaluates() throws IOException {
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
