/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.inmemory.indexed;

import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;

public class TreeWalkerTest {

	private Map<String, String> imports;

	private SAPLInterpreter interpreter;

	@Before
	public void setUp() {
		interpreter = new DefaultSAPLInterpreter();
		imports = new HashMap<String, String>();
	}

	@Test
	public void testConstructor() {
		// given
		TreeWalker testObject = new TreeWalker();

		// then
		Assertions.assertThat(testObject).isNotNull();
	}

	@Test
	public void testNestedStatement() {
		// given
		String definition = "policy \"p_0\" permit !(resource.x0 | resource.x1) & resource.x2";
		SAPL document = interpreter.parse(definition);

		// when
		DisjunctiveFormula formula = TreeWalker.walk(document.getPolicyElement().getTargetExpression(), imports);

		// then
		Assertions.assertThat(formula.size()).isEqualTo(1);
		Assertions.assertThat(formula.getClauses()).allSatisfy((clause) -> {
			Assertions.assertThat(clause.size()).isEqualTo(3);
		});
	}

	@Test
	public void testSimpleConjunction() {
		// given
		String definition = "policy \"p_0\" permit resource.x0 & resource.x1";
		SAPL document = interpreter.parse(definition);

		// when
		DisjunctiveFormula formula = TreeWalker.walk(document.getPolicyElement().getTargetExpression(), imports);

		// then
		Assertions.assertThat(formula.size()).isEqualTo(1);
		Assertions.assertThat(formula.getClauses()).allSatisfy((clause) -> {
			Assertions.assertThat(clause.size()).isEqualTo(2);
		});
	}

	@Test
	public void testSimpleDisjunction() {
		// given
		String definition = "policy \"p_0\" permit resource.x0 | resource.x1";
		SAPL document = interpreter.parse(definition);

		// when
		DisjunctiveFormula formula = TreeWalker.walk(document.getPolicyElement().getTargetExpression(), imports);

		// then
		Assertions.assertThat(formula.size()).isEqualTo(2);
		Assertions.assertThat(formula.getClauses()).allSatisfy((clause) -> {
			Assertions.assertThat(clause.size()).isEqualTo(1);
		});
	}

	@Test
	public void testSimpleNegation() {
		// given
		String definition = "policy \"p_0\" permit !resource.x0";
		SAPL document = interpreter.parse(definition);

		// when
		DisjunctiveFormula formula = TreeWalker.walk(document.getPolicyElement().getTargetExpression(), imports);

		// then
		Assertions.assertThat(formula.size()).isEqualTo(1);
		Assertions.assertThat(formula.getClauses()).allSatisfy((clause) -> {
			Assertions.assertThat(clause.size()).isEqualTo(1);
			Assertions.assertThat(clause.getLiterals()).allSatisfy((literal) -> {
				Assertions.assertThat(literal.isNegated()).isTrue();
			});
		});
	}

}
