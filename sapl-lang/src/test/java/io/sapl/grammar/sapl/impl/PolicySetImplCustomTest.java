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

import java.util.HashMap;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.node.ArrayNode;

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

public class PolicySetImplCustomTest {

	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	private EvaluationContext ctx;

	@Before
	public void before() throws JsonMappingException, JsonProcessingException {
		Hooks.onOperatorDebug();
		var attributeCtx = new AnnotationAttributeContext();
		var functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new SimpleFunctionLibrary());
		functionCtx.loadLibrary(new FilterFunctionLibrary());
		ctx = new EvaluationContext(attributeCtx, functionCtx, new HashMap<>());
	}

	@Test
	public void simplePermitAllOnePolicy() {
		var policy = INTERPRETER.parse("set \"set\" deny-overrides policy \"set.p1\" permit");
		var expected = AuthorizationDecision.PERMIT;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void denyOverrides() {
		var policy = INTERPRETER.parse("set \"set\" deny-overrides " + "policy \"permits\" permit "
				+ "policy \"indeterminate\" permit where (10/0); " + "policy \"denies\" deny "
				+ "policy \"not-applicable\" deny where false;");
		var expected = AuthorizationDecision.DENY;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void permitOverrides() {
		var policy = INTERPRETER.parse("set \"set\" permit-overrides " + "policy \"permits\" permit "
				+ "policy \"indeterminate\" permit where (10/0); " + "policy \"denies\" deny "
				+ "policy \"not-applicable\" deny where false;");
		var expected = AuthorizationDecision.PERMIT;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void onlyOneApplicable() {
		var policy = INTERPRETER.parse("set \"set\" only-one-applicable " + "policy \"permits\" permit "
				+ "policy \"indeterminate\" permit where (10/0); " + "policy \"denies\" deny "
				+ "policy \"not-applicable\" deny where false;");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void firstApplicable() {
		var policy = INTERPRETER.parse("set \"set\" first-applicable " + "policy \"permits\" permit "
				+ "policy \"indeterminate\" permit where (10/0); " + "policy \"denies\" deny "
				+ "policy \"not-applicable\" deny where false;");
		var expected = AuthorizationDecision.PERMIT;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void denyUnlessPermit() {
		var policy = INTERPRETER.parse("set \"set\" deny-unless-permit " + "policy \"permits\" permit "
				+ "policy \"indeterminate\" permit where (10/0); " + "policy \"denies\" deny "
				+ "policy \"not-applicable\" deny where false;");
		var expected = AuthorizationDecision.PERMIT;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void duplicatePolicyNamesIndeterminate() {
		var policy = INTERPRETER
				.parse("set \"set\" deny-unless-permit policy \"permits\" permit policy \"permits\" permit");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void permitUnlessDeny() {
		var policy = INTERPRETER.parse("set \"set\" permit-unless-deny " + "policy \"permits\" permit "
				+ "policy \"indeterminate\" permit where (10/0); " + "policy \"denies\" deny "
				+ "policy \"not-applicable\" deny where false;");
		var expected = AuthorizationDecision.DENY;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void valueDefinitions() {
		var policy = INTERPRETER.parse(
				"set \"set\" deny-overrides var a = 5; var b = a+2; policy \"set.p1\" permit where a==5 && b == 7;");
		var expected = AuthorizationDecision.PERMIT;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void valueDefinitionsUndefined() {
		var policy = INTERPRETER
				.parse("set \"set\" deny-overrides var a = undefined; policy \"set.p1\" permit where a==undefined;");
		var expected = AuthorizationDecision.PERMIT;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void valueErrorLazy() {
		var policy = INTERPRETER.parse(
				"set \"set\" first-applicable var a = (10/0); var b = 12; policy \"set.p1\" permit where a==undefined;");
		var expected = AuthorizationDecision.INDETERMINATE;
		StepVerifier.create(policy.evaluate(ctx)).expectNext(expected).verifyComplete();
	}

	@Test
	public void valueDefinitionsFromOnePolicyDoNotLeakIntoOtherPolicy() {
		var policy = INTERPRETER.parse(
				"set \"set\" deny-overrides policy \"set.p1\" permit where var a=5; var b=2; policy \"set.p2\" permit where a==undefined && b == undefined;");
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
