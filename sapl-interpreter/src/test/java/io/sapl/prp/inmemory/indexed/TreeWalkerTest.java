package io.sapl.prp.inmemory.indexed;

import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import io.sapl.api.interpreter.PolicyEvaluationException;
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
	public void testNestedStatement() throws PolicyEvaluationException {
		// given
		String definition = "policy \"p_0\" permit !(resource.x0 | resource.x1) & resource.x2";
		SAPL document = interpreter.parse(definition);

		// when
		DisjunctiveFormula formula = TreeWalker
				.walk(document.getPolicyElement().getTargetExpression(), imports);

		// then
		Assertions.assertThat(formula.size()).isEqualTo(1);
		Assertions.assertThat(formula.getClauses()).allSatisfy((clause) -> {
			Assertions.assertThat(clause.size()).isEqualTo(3);
		});
	}

	@Test
	public void testSimpleConjunction() throws PolicyEvaluationException {
		// given
		String definition = "policy \"p_0\" permit resource.x0 & resource.x1";
		SAPL document = interpreter.parse(definition);

		// when
		DisjunctiveFormula formula = TreeWalker
				.walk(document.getPolicyElement().getTargetExpression(), imports);

		// then
		Assertions.assertThat(formula.size()).isEqualTo(1);
		Assertions.assertThat(formula.getClauses()).allSatisfy((clause) -> {
			Assertions.assertThat(clause.size()).isEqualTo(2);
		});
	}

	@Test
	public void testSimpleDisjunction() throws PolicyEvaluationException {
		// given
		String definition = "policy \"p_0\" permit resource.x0 | resource.x1";
		SAPL document = interpreter.parse(definition);

		// when
		DisjunctiveFormula formula = TreeWalker
				.walk(document.getPolicyElement().getTargetExpression(), imports);

		// then
		Assertions.assertThat(formula.size()).isEqualTo(2);
		Assertions.assertThat(formula.getClauses()).allSatisfy((clause) -> {
			Assertions.assertThat(clause.size()).isEqualTo(1);
		});
	}

	@Test
	public void testSimpleNegation() throws PolicyEvaluationException {
		// given
		String definition = "policy \"p_0\" permit !resource.x0";
		SAPL document = interpreter.parse(definition);

		// when
		DisjunctiveFormula formula = TreeWalker
				.walk(document.getPolicyElement().getTargetExpression(), imports);

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
