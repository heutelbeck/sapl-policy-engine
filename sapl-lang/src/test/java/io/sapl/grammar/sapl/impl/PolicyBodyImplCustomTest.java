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
package io.sapl.grammar.sapl.impl;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import io.sapl.api.interpreter.InitializationException;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.SimpleFunctionLibrary;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

public class PolicyBodyImplCustomTest {
	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	private EvaluationContext ctx;

	@Before
	public void setUp() throws InitializationException {
		Hooks.onOperatorDebug();
		var attributeCtx = new AnnotationAttributeContext();
		var functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new SimpleFunctionLibrary());
		functionCtx.loadLibrary(new FilterFunctionLibrary());
		ctx = new EvaluationContext(attributeCtx, functionCtx, new HashMap<>());
	}

	@Test
	public void trueReturnsEntitlement() {
		var policy = INTERPRETER.parse("policy \"p\" permit true where true; true; true;");
		var expected = AuthorizationDecision.PERMIT;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void oneFalseReturnsNotApplicableEntitlement() {
		var policy = INTERPRETER.parse("policy \"p\" permit true where true; false; true;");
		var expected = AuthorizationDecision.NOT_APPLICABLE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void oneErrorReturnsIndeterminate() {
		var policy = INTERPRETER.parse("policy \"p\" permit true where true; (10/0); true;");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void valueDefinitionsEvaluateAndScope() {
		var policy = INTERPRETER
				.parse("policy \"p\" permit true where variable == undefined; var variable = 1; variable == 1;");
		var expected = AuthorizationDecision.PERMIT;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void valueDefinitionsDefineUndefined() {
		var policy = INTERPRETER.parse(
				"policy \"p\" permit true where variable == undefined; var variable = undefined; variable == undefined;");
		var expected = AuthorizationDecision.PERMIT;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void valueDefinitionsDefineError() {
		var policy = INTERPRETER.parse("policy \"p\" permit where var variable = (10/0);");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void valueDefinitionsAttemptToOverwriteDefailtSubscriptionVariableSubjectError() {
		var policy = INTERPRETER.parse("policy \"p\" permit where var subject = {};");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void valueDefinitionsAttemptToOverwriteDefailtSubscriptionVariableActionError() {
		var policy = INTERPRETER.parse("policy \"p\" permit where var action = {};");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void valueDefinitionsAttemptToOverwriteDefailtSubscriptionVariableResourceError() {
		var policy = INTERPRETER.parse("policy \"p\" permit where var resource = {};");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void valueDefinitionsAttemptToOverwriteDefailtSubscriptionVariableEnvironmentError() {
		var policy = INTERPRETER.parse("policy \"p\" permit where var environment = {};");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void lazyStatementEvaluationVarDef() {
		var policy = INTERPRETER.parse("policy \"p\" permit true where false; var variable = (10/0);");
		var expected = AuthorizationDecision.NOT_APPLICABLE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void lazyStatementEvaluationVarDefOnError() {
		var policy = INTERPRETER.parse("policy \"p\" permit true where (10/0); var variable = (10/0);");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void lazyStatementEvaluation() {
		var policy = INTERPRETER.parse("policy \"p\" permit true where false; (10/0);");
		var expected = AuthorizationDecision.NOT_APPLICABLE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

}
