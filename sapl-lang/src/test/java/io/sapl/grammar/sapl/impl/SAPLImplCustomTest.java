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
package io.sapl.grammar.sapl.impl;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class SAPLImplCustomTest {

	private final static EvaluationContext CTX = MockUtil.constructTestEnvironmentEvaluationContext();
	private final static SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	@Test
	public void detectErrorInTargetMatches() {
		var policy = INTERPRETER.parse("policy \"policy\" permit (10/0)");
		StepVerifier.create(policy.matches(CTX)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void detectErrorInImportsDuringMatches() {
		var policy = INTERPRETER.parse("import filter.blacken import filter.blacken policy \"policy\" permit true");
		StepVerifier.create(policy.matches(CTX)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void detectErrorInImportsDuringEvaluate() {
		var policy = INTERPRETER.parse("import filter.blacken import filter.blacken policy \"policy\" permit true");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(CTX)).expectNext(expected).verifyComplete();
	}

	@Test
	public void importsWorkCorrectlyBasicFunction() {
		var policy = INTERPRETER.parse("import filter.blacken policy \"policy\" permit true");
		var expectedImports = Map.of("blacken", "filter.blacken");
		var actualImports = policy.documentScopedEvaluationContext(CTX).getImports();
		assertEquals(expectedImports, actualImports);
	}

	@Test
	public void importsWorkCorrectlyWildcardFunction() {
		var policy = INTERPRETER.parse("import filter.* policy \"policy\" permit true");
		var expectedImports = Map.of("blacken", "filter.blacken", "replace", "filter.replace", "remove",
				"filter.remove");
		var actualImports = policy.documentScopedEvaluationContext(CTX).getImports();
		assertEquals(expectedImports, actualImports);
	}

	@Test
	public void importsWorkCorrectlyLibraryFunction() {
		var policy = INTERPRETER.parse("import filter as fil policy \"policy\" permit true");
		var expectedImports = Map.of("fil.blacken", "filter.blacken", "fil.replace", "filter.replace", "fil.remove",
				"filter.remove");
		var actualImports = policy.documentScopedEvaluationContext(CTX).getImports();
		assertEquals(expectedImports, actualImports);
	}

	@Test
	public void importsWorkCorrectlyBasicAttribute() {
		var policy = INTERPRETER.parse("import test.numbers policy \"policy\" permit true");
		var expectedImports = Map.of("numbers", "test.numbers");
		var actualImports = policy.documentScopedEvaluationContext(CTX).getImports();
		assertEquals(expectedImports, actualImports);
	}

	@Test
	public void importsWorkCorrectlyWildcardAttribute() {
		var policy = INTERPRETER.parse("import test.* policy \"policy\" permit true");
		var expectedImports = Map.of("numbers", "test.numbers", "numbersWithError", "test.numbersWithError", "nilflux",
				"test.nilflux");
		var actualImports = policy.documentScopedEvaluationContext(CTX).getImports();

		assertEquals(expectedImports, actualImports);
	}

	@Test
	public void importsWorkCorrectlyLibraryAttribute() {
		var policy = INTERPRETER.parse("import test as t policy \"policy\" permit true");
		var expectedImports = Map.of("t.numbers", "test.numbers", "t.numbersWithError", "test.numbersWithError",
				"t.nilflux", "test.nilflux");
		var actualImports = policy.documentScopedEvaluationContext(CTX).getImports();
		assertEquals(expectedImports, actualImports);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void importNonExistingFails() {
		var policy = INTERPRETER.parse("import test.nonExisting policy \"policy\" permit true");
		policy.documentScopedEvaluationContext(CTX);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void doubleImportWildcardFails() {
		var policy = INTERPRETER.parse("import test.* import test.* policy \"policy\" permit true");
		policy.documentScopedEvaluationContext(CTX);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void doubleImportLibraryFails() {
		var policy = INTERPRETER.parse("import test as t import test as t policy \"policy\" permit true");
		policy.documentScopedEvaluationContext(CTX);
	}

	@Test
	public void policyBodyEvaluationDoesNotCheckTargetAgain() {
		var policy = INTERPRETER.parse("policy \"policy\" permit (10/0)");
		var expected = AuthorizationDecision.PERMIT;
		StepVerifier.create(policy.evaluate(CTX)).expectNext(expected).verifyComplete();
	}

}
