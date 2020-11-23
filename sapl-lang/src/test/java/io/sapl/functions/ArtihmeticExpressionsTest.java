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
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.grammar.sapl.impl.util.ParserUtil;

public class ArtihmeticExpressionsTest {

	@Test
	public void parentPriority() throws IOException {
		assertEvaluatesTo("(1+2)*3.0", 9.00D);
	}

	@Test
	public void unaryMinusNoSpace() throws IOException {
		assertEvaluatesTo("1+-1", 0D);
	}

	@Test
	public void unaryMinusSpace() throws IOException {
		assertEvaluatesTo("1+ -1", 0D);
	}

	@Test
	public void twoSpacesUnaryMinus() throws IOException {
		assertEvaluatesTo("1 + -1", 0D);
	}

	@Test
	public void threeSpacesUnaryMinus() throws IOException {
		assertEvaluatesTo("1 + - 1", 0D);
	}

	@Test
	public void doubleNegation() throws IOException {
		assertEvaluatesTo("--1", 1D);
	}

	@Test
	public void oneMinusOne_IsNull() throws IOException {
		assertEvaluatesTo("1-1", 0D);
	}

	@Test
	public void unaryPlus_IsImplemented() throws IOException {
		assertEvaluatesTo("1+ +(2)", 3D);
	}

	@Test
	public void noSpacesPlusAndMinusEvaluates() throws IOException {
		assertEvaluatesTo("5+5-3", 7D);
	}

	private void assertEvaluatesTo(String given, double expected) throws IOException {
		var expression = ParserUtil.expression(given);
		var actual = expression.evaluate(MockUtil.constructTestEnvironmentPdpScopedEvaluationContext(), Val.UNDEFINED)
				.blockFirst();
		assertThat(actual.decimalValue(), Matchers.comparesEqualTo(BigDecimal.valueOf(expected)));

	}

}
