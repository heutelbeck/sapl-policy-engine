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
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.InitializationException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.SimpleFunctionLibrary;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

public class PolicyImplCustomTest {

	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	private EvaluationContext ctx;

	@Before
	public void setUp() throws JsonProcessingException, InitializationException {
		Hooks.onOperatorDebug();
		var attributeCtx = new AnnotationAttributeContext();
		var functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new SimpleFunctionLibrary());
		functionCtx.loadLibrary(new FilterFunctionLibrary());
		ctx = new EvaluationContext(attributeCtx, functionCtx, new HashMap<>());
	}

	@Test
	public void simplePermitAll() {
		var policy = INTERPRETER.parse("policy \"p\" permit");
		var expected = AuthorizationDecision.PERMIT;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void simpleDenyAll() {
		var policy = INTERPRETER.parse("policy \"p\" deny");
		var expected = AuthorizationDecision.DENY;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void simplePermitAllWithBodyTrue() {
		var policy = INTERPRETER.parse("policy \"p\" permit where true;");
		var expected = AuthorizationDecision.PERMIT;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void simplePermitAllWithBodyFalse() {
		var policy = INTERPRETER.parse("policy \"p\" permit where false;");
		var expected = AuthorizationDecision.NOT_APPLICABLE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void simplePermitAllWithBodyError() {
		var policy = INTERPRETER.parse("policy \"p\" permit where (10/0);");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void obligationEvaluatesSuccessfully() throws JsonProcessingException, PolicyEvaluationException {
		var policy = INTERPRETER.parse("policy \"p\" permit obligation true");
		var expected = new AuthorizationDecision(Decision.PERMIT, Optional.empty(),
				Optional.of(Val.ofJson("[true]").getArrayNode()), Optional.empty());
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void obligationErrors() {
		var policy = INTERPRETER.parse("policy \"p\" permit obligation (10/0)");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void obligationUndefined() {
		var policy = INTERPRETER.parse("policy \"p\" permit obligation undefined");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void adviceEvaluatesSuccessfully() throws JsonProcessingException, PolicyEvaluationException {
		var policy = INTERPRETER.parse("policy \"p\" permit advice true");
		var expected = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
				Optional.of(Val.ofJson("[true]").getArrayNode()));
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void adviceErrors() {
		var policy = INTERPRETER.parse("policy \"p\" permit advice (10/0)");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void adviceUndefined() {
		var policy = INTERPRETER.parse("policy \"p\" permit advice undefined");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void transformEvaluatesSuccessfully() {
		var policy = INTERPRETER.parse("policy \"p\" permit transform true");
		var expected = new AuthorizationDecision(Decision.PERMIT, Optional.of(Val.JSON.booleanNode(true)),
				Optional.empty(), Optional.empty());
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void transformErrors() {
		var policy = INTERPRETER.parse("policy \"p\" permit transform (10/0)");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void transformUndefined() {
		var policy = INTERPRETER.parse("policy \"p\" permit transform undefined");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void allComponentsPresentSuccessfully() throws JsonProcessingException, PolicyEvaluationException {
		var policy = INTERPRETER.parse(
				"policy \"p\" permit where true; obligation \"wash your hands\" advice \"smile\" transform [true,false,null]");
		var expected = new AuthorizationDecision(Decision.PERMIT, Optional.of(Val.ofJson("[true,false,null]").get()),
				Optional.of((ArrayNode) Val.ofJson("[\"wash your hands\"]").get()),
				Optional.of((ArrayNode) Val.ofJson("[\"smile\"]").get()));
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

}
