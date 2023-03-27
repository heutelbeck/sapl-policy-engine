/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.steps;

import static io.sapl.hamcrest.Matchers.anyVal;
import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.test.Imports.arguments;
import static io.sapl.test.Imports.parentValue;
import static io.sapl.test.Imports.thenReturn;
import static io.sapl.test.Imports.times;
import static io.sapl.test.Imports.whenAttributeParams;
import static io.sapl.test.Imports.whenEnvironmentAttributeParams;
import static io.sapl.test.Imports.whenFunctionParams;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.attribute.MockingAttributeContext;
import io.sapl.test.mocking.function.MockingFunctionContext;

class StepsDefaultImplTests {

	private AttributeContext attrCtx;

	private FunctionContext funcCtx;

	private Map<String, JsonNode> variables;

	private final String Policy_SimpleFunction = "policy \"policyWithSimpleFunction\"\r\n" + "permit\r\n"
			+ "    action == \"read\"\r\n" + "where\r\n"
			+ "    time.dayOfWeek(\"2021-02-08T16:16:33.616Z\") =~ \"MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY\";";

	private final String Policy_Streaming_Permit = "policy \"policyStreaming\"\r\n" + "permit\r\n"
			+ "  resource == \"heartBeatData\"\r\n" + "where\r\n" + "  subject == \"ROLE_DOCTOR\";\r\n"
			+ "  var interval = 2;\r\n" + "  time.secondOf(<time.now(interval)>) > 4;";

	private final String Policy_Indeterminate = "policy \"policy division by zero\"\r\n" + "permit\r\n" + "where\r\n"
			+ "    17 / 0;";

	@BeforeEach
	void setUp() {
		this.attrCtx   = new MockingAttributeContext(Mockito.mock(AnnotationAttributeContext.class));
		this.funcCtx   = new MockingFunctionContext(Mockito.mock(AnnotationFunctionContext.class));
		this.variables = new HashMap<>();
	}

	@Test
	void test_mockFunction_withParameters_withTimesVerification() {
		var steps = new StepsDefaultImplTestsImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
		steps.givenFunction("time.dayOfWeek", whenFunctionParams(anyVal()), Val.of("SATURDAY"), times(1))
				.when(AuthorizationSubscription.of("willi", "read", "something")).expectPermit().verify();
	}

	@Test
	void test_mockFunction_Function_withTimesVerification() {
		var steps = new StepsDefaultImplTestsImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
		steps.givenFunction("time.dayOfWeek", (call) -> Val.of("SATURDAY"), times(1))
				.when(AuthorizationSubscription.of("willi", "read", "something")).expectPermit().verify();
	}

	@Test
	void test_mockAttribute_withParentValue() {
		String policy_Attribute_WithAttributeAsParentValue = "policy \"policy\"\r\n" + "permit\r\n" + "where\r\n"
				+ "  var test = true;\r\n" + "  test.<pip.attribute1>.<pip.attribute2> < 50;";
		var steps = new StepsDefaultImplTestsImpl(policy_Attribute_WithAttributeAsParentValue, attrCtx, funcCtx,
				variables);
		steps.givenAttribute("pip.attribute1", Val.of(true), Val.of(false))
				.givenAttribute("pip.attribute2", parentValue(val(true)), thenReturn(Val.of(0)))
				.givenAttribute("pip.attribute2", parentValue(val(false)), thenReturn(Val.of(99)))
				.when(AuthorizationSubscription.of("willi", "read", "something")).expectNextPermit()
				.expectNextNotApplicable().verify();
	}

	@Test
	void test_mockAttribute_withParentValueAndArguments() {
		String policy_Attribute_WithAttributeAsParentValueAndArguments = "policy \"policy\"\r\n" + "permit\r\n"
				+ "where\r\n" + "  var parentValue = true;\r\n"
				+ "  parentValue.<pip.attributeWithParams(<pip.attribute1>, <pip.attribute2>)> == true;";
		var steps = new StepsDefaultImplTestsImpl(policy_Attribute_WithAttributeAsParentValueAndArguments, attrCtx,
				funcCtx, variables);
		steps.givenAttribute("pip.attribute1").givenAttribute("pip.attribute2")
				.givenAttribute("pip.attributeWithParams",
						whenAttributeParams(parentValue(val(true)), arguments(val(2), val(2))),
						thenReturn(Val.of(true)))
				.givenAttribute("pip.attributeWithParams",
						whenAttributeParams(parentValue(val(true)), arguments(val(2), val(1))),
						thenReturn(Val.of(false)))
				.givenAttribute("pip.attributeWithParams",
						whenAttributeParams(parentValue(val(true)), arguments(val(1), val(2))),
						thenReturn(Val.of(false)))
				.when(AuthorizationSubscription.of("willi", "read", "something"))
				.thenAttribute("pip.attribute1", Val.of(1)).thenAttribute("pip.attribute2", Val.of(2))
				.expectNextNotApplicable().thenAttribute("pip.attribute1", Val.of(2)).expectNextPermit()
				.thenAttribute("pip.attribute2", Val.of(1)).expectNextNotApplicable().verify();
	}

	@Test
	void test_mockAttribute_withParentValueAndArguments_ForEnvironmentAttribute() {
		String policy_EnvironmentAttribute_WithAttributeAsParentValueAndArguments = "policy \"policy\"\r\n"
				+ "permit\r\n" + "where\r\n" + "  var parentValue = true;\r\n"
				+ "  <pip.attributeWithParams(<pip.attribute1>, <pip.attribute2>)> == true;";
		var steps = new StepsDefaultImplTestsImpl(policy_EnvironmentAttribute_WithAttributeAsParentValueAndArguments,
				attrCtx, funcCtx, variables);
		steps.givenAttribute("pip.attribute1").givenAttribute("pip.attribute2")
				.givenAttribute("pip.attributeWithParams", whenEnvironmentAttributeParams(arguments(val(2), val(2))),
						thenReturn(Val.of(true)))
				.givenAttribute("pip.attributeWithParams", whenEnvironmentAttributeParams(arguments(val(2), val(1))),
						thenReturn(Val.of(false)))
				.givenAttribute("pip.attributeWithParams", whenEnvironmentAttributeParams(arguments(val(1), val(2))),
						thenReturn(Val.of(false)))
				.when(AuthorizationSubscription.of("willi", "read", "something"))
				.thenAttribute("pip.attribute1", Val.of(1)).thenAttribute("pip.attribute2", Val.of(2))
				.expectNextNotApplicable().thenAttribute("pip.attribute1", Val.of(2)).expectNextPermit()
				.thenAttribute("pip.attribute2", Val.of(1)).expectNextNotApplicable().verify();
	}

	@Test
	void test_when_fromJsonString() throws JsonProcessingException {
		var steps = new StepsDefaultImplTestsImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
		steps.givenFunction("time.dayOfWeek", Val.of("SATURDAY"))
				.when("{\"subject\":\"willi\", \"action\":\"read\", \"resource\":\"something\", \"environment\":{}}")
				.expectPermit().verify();
	}

	@Test
	void test_when_fromJsonNode() {
		var mapper   = new ObjectMapper();
		var authzSub = mapper.createObjectNode().put("subject", "willi").put("action", "read")
				.put("resource", "something").put("environment", "test");
		var steps    = new StepsDefaultImplTestsImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
		steps.givenFunction("time.dayOfWeek", Val.of("SATURDAY")).when(authzSub).expectPermit().verify();
	}

	@Test
	void test_when_fromJsonNode_null() {
		var steps = new StepsDefaultImplTestsImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> steps.when((JsonNode) null));
	}

	@Test
	void test_expectIndeterminate() {
		var steps = new StepsDefaultImplTestsImpl(Policy_Indeterminate, attrCtx, funcCtx, variables);
		steps.when(AuthorizationSubscription.of("willi", "read", "something")).expectIndeterminate().verify();
	}

	@Test
	void test_expectNextPermit_XTimes_0() {
		var steps = new StepsDefaultImplTestsImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables)
				.when(AuthorizationSubscription.of("willi", "read", "something"));
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> steps.expectNextPermit(0));
	}

	@Test
	void test_expectNextPermit_XTimes_Greater1() {
		var steps = new StepsDefaultImplTestsImpl(Policy_Streaming_Permit, attrCtx, funcCtx, variables);
		steps.givenAttribute("time.now", Val.of("value"), Val.of("doesn't"), Val.of("matter"))
				.givenFunctionOnce("time.secondOf", Val.of(5), Val.of(6), Val.of(7))
				.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData")).expectNextPermit(3)
				.verify();
	}

	@Test
	void test_expectNextDeny_XTimes_0() {
		var steps = new StepsDefaultImplTestsImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables)
				.when(AuthorizationSubscription.of("willi", "read", "something"));
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> steps.expectNextDeny(0));
	}

	@Test
	void test_expectNextDeny_XTimes_Greater1() {
		String policy_Streaming_Deny = "policy \"policyStreaming\"\r\n" + "deny\r\n"
				+ "  resource == \"heartBeatData\"\r\n" + "where\r\n" + "  subject == \"ROLE_DOCTOR\";\r\n"
				+ "  var interval = 2;\r\n" + "  time.secondOf(<time.now(interval)>) > 4;";
		var steps = new StepsDefaultImplTestsImpl(policy_Streaming_Deny, attrCtx, funcCtx, variables);
		steps.givenAttribute("time.now", Val.of("value"), Val.of("doesn't"), Val.of("matter"))
				.givenFunctionOnce("time.secondOf", Val.of(5), Val.of(6), Val.of(7))
				.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData")).expectNextDeny(3).verify();
	}

	@Test
	void test_expectNextIndeterminate_XTimes_0() {
		var steps = new StepsDefaultImplTestsImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables)
				.when(AuthorizationSubscription.of("willi", "read", "something"));
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> steps.expectNextIndeterminate(0));
	}

	@Test
	void test_expectNextIndeterminate_XTimes_Greater1() {
		String policy_Streaming_Indeterminate = "policy \"policyStreaming\"\r\n" + "permit\r\n"
				+ "  resource == \"heartBeatData\"\r\n" + "where\r\n" + "  subject == \"ROLE_DOCTOR\";\r\n"
				+ "  var interval = 2;\r\n" + "  time.secondOf(<time.now(interval)>) > 4;" + "  17 / 0;";
		var steps = new StepsDefaultImplTestsImpl(policy_Streaming_Indeterminate, attrCtx, funcCtx, variables);
		steps.givenAttribute("time.now", Val.of("value"), Val.of("doesn't"), Val.of("matter"))
				.givenFunctionOnce("time.secondOf", Val.of(5), Val.of(6), Val.of(7))
				.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData")).expectNextIndeterminate(3)
				.verify();
	}

	@Test
	void test_expectNextNotApplicable_XTimes_0() {
		var steps = new StepsDefaultImplTestsImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables)
				.when(AuthorizationSubscription.of("willi", "read", "something"));
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> steps.expectNextNotApplicable(0));
	}

	@Test
	void test_expectNextNotApplicable_XTimes_Greater1() {
		var steps = new StepsDefaultImplTestsImpl(Policy_Streaming_Permit, attrCtx, funcCtx, variables);
		steps.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "somethingDifferent")).expectNextNotApplicable(1)
				.verify();
	}

	@Test
	void test_expectNextIndeterminate() {
		var steps = new StepsDefaultImplTestsImpl(Policy_Indeterminate, attrCtx, funcCtx, variables);
		steps.when(AuthorizationSubscription.of("willi", "read", "something")).expectNextIndeterminate().verify();
	}

	@Test
	void test_expectNext_Equals() {
		var steps = new StepsDefaultImplTestsImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
		steps.givenFunction("time.dayOfWeek", Val.of("SATURDAY"))
				.when(AuthorizationSubscription.of("will", "read", "something"))
				.expectNext(AuthorizationDecision.PERMIT).verify();
	}

	@Test
	void test_expectNext_Predicate() {
		var steps = new StepsDefaultImplTestsImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
		steps.givenFunction("time.dayOfWeek", Val.of("SATURDAY"))
				.when(AuthorizationSubscription.of("will", "read", "something"))
				.expectNext((authzDec) -> authzDec.getDecision() == Decision.PERMIT).verify();
	}

}
