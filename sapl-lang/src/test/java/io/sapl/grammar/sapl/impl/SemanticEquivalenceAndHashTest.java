package io.sapl.grammar.sapl.impl;

import java.util.HashMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.prp.inmemory.indexed.EquivalenceAndHashUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class SemanticEquivalenceAndHashTest {

	private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	private static final String POLICY_A = "import filter.*\n" + "import clock.*\n" + "policy \"a\"\n"
			+ "permit subject.name.blacken() == now()\n";
	private static final String POLICY_B = "import filter.*\n" + "policy \"a\"\n"
			+ "permit subject.name.blacken() == clock.now()\n";
	private static final String POLICY_A1 = "policy \"a\"\n" + "permit 1 == 2\n";
	private static final String POLICY_B1 = "policy \"a\"\n" + "permit 1 == 3\n";

	@Test
	public void doTest() throws PolicyEvaluationException {
		var a = INTERPRETER.parse(POLICY_A1);
		var aTargetExp = a.getPolicyElement().getTargetExpression();
		log.info("aTargetExp: {}", aTargetExp);
		var b = INTERPRETER.parse(POLICY_B1);
		var bTargetExp = b.getPolicyElement().getTargetExpression();
		log.info("bTargetExp: {}", bTargetExp);
		var equal = EquivalenceAndHashUtil.areEquivalent(aTargetExp, new HashMap<>(), bTargetExp, new HashMap<>());
		log.info("are equivalent: {}", equal);
		var hashA = EquivalenceAndHashUtil.semanticHash(aTargetExp, new HashMap<>());
		var hashB = EquivalenceAndHashUtil.semanticHash(bTargetExp, new HashMap<>());
		log.info("hashes (A,B): ({},{})", hashA, hashB);
	}
}
