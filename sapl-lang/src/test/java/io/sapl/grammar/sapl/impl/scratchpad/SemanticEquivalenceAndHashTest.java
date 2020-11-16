package io.sapl.grammar.sapl.impl.scratchpad;

import java.util.logging.Level;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.prp.inmemory.indexed.EquivalenceAndHashUtil;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class SemanticEquivalenceAndHashTest {

	private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

//	private static final String POLICY_A = "import filter.*\n" + "import clock.*\n" + "policy \"a\"\n"
//			+ "permit subject.name.blacken() == now()\n";
//	private static final String POLICY_B = "import filter.*\n" + "policy \"a\"\n"
//			+ "permit subject.name.blacken() == clock.now()\n";
//	private static final String POLICY_A1 = "policy \"a\"\n permit time.before() == \"\"\n";
//	private static final String POLICY_B1 = "import time.*\n policy \"a\"\n permit before() == \"\"\n";
	private static final String POLICY_A2 = "policy \"a\" permit false";
	private static final String POLICY_B2 = "policy \"a\" permit false";

	@Ignore
	@Test
	public void doTest() {
		var a = INTERPRETER.parse(POLICY_A2);
		var functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new TemporalFunctionLibrary());
		var aImports = a.fetchFunctionImports(functionCtx);
		var aTargetExp = a.getPolicyElement().getTargetExpression();
		var b = INTERPRETER.parse(POLICY_B2);
		var bImports = b.fetchFunctionImports(functionCtx);
		var bTargetExp = b.getPolicyElement().getTargetExpression();
		var equal = EquivalenceAndHashUtil.areEquivalent(aTargetExp, aImports, bTargetExp, bImports);
		log.info("are equivalent: {}", equal);
		var hashA = EquivalenceAndHashUtil.semanticHash(aTargetExp, aImports);
		log.info("-----------------------------------------------------------");
		var hashB = EquivalenceAndHashUtil.semanticHash(bTargetExp, bImports);
		log.info("hashes {} - (A,B): ({},{})", hashA == hashB, hashA, hashB);

		// log.info("->{}", dangerous(2).doOnError(e -> log.info("XXXX-{}",
		// e.getMessage())).onErrorReturn(999));
		log.info("GOT: {}", dangerous(2).onErrorReturn(123).block());
		Flux.fromArray(new Integer[] { 1, 2, 3, 4 }).concatMap(i -> dangerous(i).onErrorReturn(123)).onErrorReturn(999)
				.log(null, Level.INFO, SignalType.ON_NEXT).blockLast();
	}

	private Mono<Integer> dangerous(Integer i) {
		if (i == 2)
			return Mono.error(new RuntimeException());
		return Mono.just(i);
	}
}
