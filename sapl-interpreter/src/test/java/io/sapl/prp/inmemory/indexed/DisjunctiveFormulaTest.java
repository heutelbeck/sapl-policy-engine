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

import java.util.ArrayList;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import io.sapl.grammar.sapl.BasicIdentifier;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;

public class DisjunctiveFormulaTest {

	private SaplFactory factory;

	@Before
	public void setUp() {
		factory = new SaplFactoryImpl();
	}

	@Test
	public void testComplexFormula() {
		// given
		BasicIdentifier id0 = createIdentifier("A");
		BasicIdentifier id1 = createIdentifier("B");
		BasicIdentifier id2 = createIdentifier("C");
		BasicIdentifier id3 = createIdentifier("D");
		BasicIdentifier id4 = createIdentifier("E");
		BasicIdentifier id5 = createIdentifier("F");
		ConjunctiveClause clause0 = new ConjunctiveClause(new Literal(new Bool(id0, null)),
				new Literal(new Bool(id1, null)), new Literal(new Bool(id2, null)));
		ConjunctiveClause clause1 = new ConjunctiveClause(new Literal(new Bool(id3, null)),
				new Literal(new Bool(id4, null)), new Literal(new Bool(id5, null)));
		DisjunctiveFormula complexFormula = new DisjunctiveFormula(clause0, clause1);

		// when
		DisjunctiveFormula notNotComplexFormula = complexFormula.negate().negate();

		// then
		Assertions.assertThat(complexFormula).isEqualTo(notNotComplexFormula);
	}

	@Test
	public void testConstructor() {
		// given
		ArrayList<ConjunctiveClause> emptyList = new ArrayList<>();

		// then
		Assertions.assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
			new DisjunctiveFormula(emptyList);
		}).withNoCause();
	}

	@Test
	public void testCreateMinimalTautology() {
		// given
		Bool bool = new Bool(true);
		DisjunctiveFormula formula = new DisjunctiveFormula(new ConjunctiveClause(new Literal(bool)));

		// then
		Assertions.assertThat(formula.getClauses()).hasSize(1);
		Assertions.assertThat(formula.size()).isEqualTo(1);
		Assertions.assertThat(formula.getClauses()).allSatisfy((clause) -> {
			Assertions.assertThat(clause.getLiterals()).hasSize(1);
			Assertions.assertThat(clause.size()).isEqualTo(1);
			Assertions.assertThat(clause.getLiterals()).allSatisfy((literal) -> {
				Assertions.assertThat(literal.getBool()).isSameAs(bool);
				Assertions.assertThat(literal.isImmutable()).isTrue();
				Assertions.assertThat(literal.isNegated()).isFalse();
			});
			Assertions.assertThat(clause.isImmutable()).isTrue();
		});
		Assertions.assertThat(formula.isImmutable()).isTrue();
		Assertions.assertThat(formula.evaluate()).isTrue();
	}

	@Test
	public void testSimpleConjunction() {
		// given
		DisjunctiveFormula tautology = new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(true))));
		DisjunctiveFormula contradiction = new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(false))));

		// when
		DisjunctiveFormula conjunction = tautology.distribute(contradiction);

		// then
		Assertions.assertThat(conjunction).isEqualTo(contradiction);
	}

	@Test
	public void testSimpleDisjunction() {
		// given
		DisjunctiveFormula tautology = new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(true))));
		DisjunctiveFormula contradiction = new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(false))));

		// when
		DisjunctiveFormula disjunction = tautology.combine(contradiction);

		// then
		Assertions.assertThat(disjunction).isEqualTo(tautology);
	}

	@Test
	public void testSimpleEquality() {
		// given
		DisjunctiveFormula tautology = new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(true))));
		DisjunctiveFormula copyOfTautology = new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(true))));
		DisjunctiveFormula contradiction = new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(false))));

		// when
		DisjunctiveFormula notTautology = tautology.negate();

		// then
		Assertions.assertThat(tautology).isEqualTo(tautology);
		Assertions.assertThat(tautology).isEqualTo(copyOfTautology);
		Assertions.assertThat(tautology).isNotEqualTo(contradiction);
		Assertions.assertThat(notTautology).isNotEqualTo(contradiction);
	}

	@Test
	public void testSimpleNegation() {
		// given
		DisjunctiveFormula tautology = new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(true))));

		// when
		DisjunctiveFormula notTautology = tautology.negate();
		DisjunctiveFormula notNotTautology = notTautology.negate();

		// then
		Assertions.assertThat(tautology).isEqualTo(notNotTautology);
		Assertions.assertThat(tautology).isNotEqualTo(notTautology);
		Assertions.assertThat(tautology.evaluate()).isTrue();
		Assertions.assertThat(notTautology.evaluate()).isFalse();
	}

	@Test
	public void testSimpleReduction() {
		// given
		BasicIdentifier id0 = createIdentifier("A");
		BasicIdentifier id1 = createIdentifier("B");
		BasicIdentifier id2 = createIdentifier("C");
		ConjunctiveClause clause0 = new ConjunctiveClause(new Literal(new Bool(id0, null)),
				new Literal(new Bool(id1, null)));
		ConjunctiveClause clause1 = new ConjunctiveClause(new Literal(new Bool(false)));
		ConjunctiveClause clause2 = new ConjunctiveClause(new Literal(new Bool(id2, null)));
		ConjunctiveClause clause3 = new ConjunctiveClause(new Literal(new Bool(id0, null)),
				new Literal(new Bool(id1, null)));
		DisjunctiveFormula expanded = new DisjunctiveFormula(clause0, clause1, clause2, clause3);
		DisjunctiveFormula reference = new DisjunctiveFormula(clause0, clause2);

		// when
		DisjunctiveFormula reduced = expanded.reduce();

		// then
		Assertions.assertThat(expanded.isImmutable()).isFalse();
		Assertions.assertThat(reduced).isEqualTo(reference);
		Assertions.assertThat(reduced).isNotEqualTo(expanded);
	}

	private BasicIdentifier createIdentifier(String identifier) {
		BasicIdentifier result = factory.createBasicIdentifier();
		result.setIdentifier(identifier);
		return result;
	}

}
