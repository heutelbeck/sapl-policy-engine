package org.openconjurer.authz.prp.inmemory.indexed;

import java.util.ArrayList;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import io.sapl.grammar.sapl.BasicIdentifier;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.prp.inmemory.indexed.Bool;
import io.sapl.prp.inmemory.indexed.ConjunctiveClause;
import io.sapl.prp.inmemory.indexed.Literal;

public class ConjunctiveClauseTest {

	private SaplFactory factory;

	@Before
	public void setUp() {
		factory = new SaplFactoryImpl();
	}

	@Test
	public void testConstructor() {
		// given
		ArrayList<Literal> emptyList = new ArrayList<>();

		// then
		Assertions.assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
			new ConjunctiveClause(emptyList);
		}).withNoCause();
	}

	@Test
	public void testReductionOfEqualLiterals() {
		// given
		BasicIdentifier id0 = createIdentifier("A");
		BasicIdentifier id1 = createIdentifier("B");
		ConjunctiveClause expanded = new ConjunctiveClause(new Literal(new Bool(id0, null)),
				new Literal(new Bool(id0, null)), new Literal(new Bool(id1, null), false));
		ConjunctiveClause reference = new ConjunctiveClause(new Literal(new Bool(id0, null)),
				new Literal(new Bool(id1, null), false));

		// when
		ConjunctiveClause reduced = expanded.reduce();

		// then
		Assertions.assertThat(reduced).isEqualTo(reference);
	}

	@Test
	public void testSubsetOf() {
		// given
		BasicIdentifier id0 = createIdentifier("A");
		BasicIdentifier id1 = createIdentifier("B");
		BasicIdentifier id2 = createIdentifier("C");
		ConjunctiveClause base = new ConjunctiveClause(new Literal(new Bool(id0, null)),
				new Literal(new Bool(id1, null)), new Literal(new Bool(id2, null)));
		ConjunctiveClause subset = new ConjunctiveClause(new Literal(new Bool(id1, null)),
				new Literal(new Bool(id2, null)));

		// then
		Assertions.assertThat(subset.isSubsetOf(base)).isTrue();
		Assertions.assertThat(base.isSubsetOf(base)).isTrue();
		Assertions.assertThat(base.isSubsetOf(subset)).isFalse();
	}

	private BasicIdentifier createIdentifier(String identifier) {
		BasicIdentifier result = factory.createBasicIdentifier();
		result.setIdentifier(identifier);
		return result;
	}
}
