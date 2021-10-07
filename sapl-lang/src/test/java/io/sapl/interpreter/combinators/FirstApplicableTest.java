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
package io.sapl.interpreter.combinators;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;

class FirstApplicableTest {

	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final AuthorizationSubscription EMPTY_AUTH_SUBSCRIPTION = new AuthorizationSubscription(null, null,
			null, null);

	private static final AuthorizationSubscription AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE = new AuthorizationSubscription(
			null, null, JSON.booleanNode(true), null);

	private EvaluationContext evaluationCtx;

	@BeforeEach
	void setUp() {
		var attributeCtx = new AnnotationAttributeContext();
		var functionCtx = new AnnotationFunctionContext();
		evaluationCtx = new EvaluationContext(attributeCtx, functionCtx, new HashMap<>());
	}

	@Test
	void permit() {
		String policySet = "set \"tests\" first-applicable" + " policy \"testp\" permit";

		assertEquals(Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void deny() {
		String policySet = "set \"tests\" first-applicable" + " policy \"testp\" deny";

		assertEquals(Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void notApplicableTarget() {
		String policySet = "set \"tests\" first-applicable" + " policy \"testp\" deny true == false";

		assertEquals(Decision.NOT_APPLICABLE,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void notApplicableCondition() {
		String policySet = "set \"tests\" first-applicable" + " policy \"testp\" deny where true == false;";

		assertEquals(Decision.NOT_APPLICABLE,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test //**
	void indeterminateTarget() {
		String policySet = "set \"tests\" first-applicable" + " policy \"testp\" permit \"a\" < 5";

		assertEquals(Decision.INDETERMINATE,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}
	
	@Test
	void indeterminateCondition() {
		String policySet = "set \"tests\" first-applicable" + " policy \"testp\" permit where \"a\" < 5;";

		assertEquals(Decision.INDETERMINATE,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void permitDeny() {
		String policySet = "set \"tests\" first-applicable" + " policy \"testp1\" permit" + " policy \"testp2\" deny";

		assertEquals(Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void notApplicableDeny() {
		String policySet = "set \"tests\" first-applicable" + " policy \"testp1\" permit where false;"
				+ " policy \"testp2\" permit true == false" + " policy \"testp3\" deny";

		assertEquals(Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void multiplePermitTransformation() {
		String policySet = "set \"tests\" first-applicable" + " policy \"testp1\" permit transform true"
				+ " policy \"testp2\" permit transform false";

		assertEquals(Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void permitTransformationResource() {
		String policySet = "set \"tests\" first-applicable" + " policy \"testp1\" permit transform true"
				+ " policy \"testp2\" permit transform false" + " policy \"testp3\" permit";

		assertEquals(Optional.of(JSON.booleanNode(true)),
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getResource());
	}

	@Test
	void collectObligationDeny() {
		String policySet = "set \"tests\" first-applicable" + " policy \"testp\" permit false"
				+ " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\"";

		ArrayNode obligation = JSON.arrayNode();
		obligation.add(JSON.textNode("obligation1"));

		assertEquals(Optional.of(obligation),
				INTERPRETER.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst()
						.getObligations());
	}

	@Test
	void collectAdviceDeny() {
		String policySet = "set \"tests\" first-applicable" + " policy \"testp\" permit false"
				+ " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\"";

		ArrayNode advice = JSON.arrayNode();
		advice.add(JSON.textNode("advice1"));

		assertEquals(Optional.of(advice), INTERPRETER
				.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst().getAdvice());
	}

	@Test
	void collectObligationPermit() {
		String policySet = "set \"tests\" first-applicable"
				+ " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\"";

		ArrayNode obligation = JSON.arrayNode();
		obligation.add(JSON.textNode("obligation1"));

		assertEquals(Optional.of(obligation),
				INTERPRETER.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst()
						.getObligations());
	}

	@Test
	void collectAdvicePermit() {
		String policySet = "set \"tests\" first-applicable"
				+ " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\"";

		ArrayNode advice = JSON.arrayNode();
		advice.add(JSON.textNode("advice1"));

		assertEquals(Optional.of(advice), INTERPRETER
				.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst().getAdvice());
	}
	

}
