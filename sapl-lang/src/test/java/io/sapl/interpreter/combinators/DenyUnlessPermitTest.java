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

class DenyUnlessPermitTest {

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
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" permit";

		assertEquals(Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void deny() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" deny";

		assertEquals(Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void notApplicableTarget() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" permit true == false";

		assertEquals(Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void notApplicableCondition() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" permit where true == false;";

		assertEquals(Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void indeterminateTarget() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" permit \"a\" < 5";

		assertEquals(Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void indeterminateCondition() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" permit where \"a\" < 5;";
		assertEquals(Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void permitDeny() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" permit" + " policy \"testp2\" deny";

		assertEquals(Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void denyIndeterminate() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" deny"
				+ " policy \"testp2\" deny where \"a\" > 5;";

		assertEquals(Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void permitNotApplicableDeny() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit true == false" + " policy \"testp3\" deny";

		assertEquals(Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void permitNotApplicableIndeterminateDeny() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" deny \"a\" > 5"
				+ " policy \"testp2\" permit true == false" + " policy \"testp3\" permit \"a\" > 5"
				+ " policy \"testp4\" deny" + " policy \"testp5\" permit";

		assertEquals(Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void permitIndeterminateNotApplicable() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" deny"
				+ " policy \"testp2\" deny \"a\" < 5" + " policy \"testp3\" deny true == false";

		assertEquals(Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void multiplePermitTransformationDeny() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" permit transform false"
				+ " policy \"testp2\" permit transform true" + " policy \"testp3\" deny";

		assertEquals(Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void singlePermitTransformation() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" permit transform true"
				+ " policy \"testp2\" deny";

		assertEquals(Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void singlePermitTransformationResource() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" permit transform true";

		assertEquals(Optional.of(JSON.booleanNode(true)),
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getResource());
	}

	@Test
	void multiplePermitNoTransformation() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit";

		assertEquals(Decision.PERMIT, INTERPRETER
				.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void collectObligationDeny() {
		String policySet = "set \"tests\" deny-unless-permit"
				+ " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" permit false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

		ArrayNode obligation = JSON.arrayNode();
		obligation.add(JSON.textNode("obligation1"));
		obligation.add(JSON.textNode("obligation2"));

		assertEquals(Optional.of(obligation),
				INTERPRETER.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst()
						.getObligations());
	}

	@Test
	void collectAdviceDeny() {
		String policySet = "set \"tests\" deny-unless-permit"
				+ " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" permit false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

		ArrayNode advice = JSON.arrayNode();
		advice.add(JSON.textNode("advice1"));
		advice.add(JSON.textNode("advice2"));

		assertEquals(Optional.of(advice), INTERPRETER
				.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst().getAdvices());
	}

	@Test
	void collectObligationPermit() {
		String policySet = "set \"tests\" deny-unless-permit"
				+ " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" deny obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny where false; obligation \"obligation4\" advice \"advice4\"";

		ArrayNode obligation = JSON.arrayNode();
		obligation.add(JSON.textNode("obligation1"));
		obligation.add(JSON.textNode("obligation2"));

		assertEquals(Optional.of(obligation),
				INTERPRETER.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst()
						.getObligations());
	}

	@Test
	void collectAdvicePermit() {
		String policySet = "set \"tests\" deny-unless-permit"
				+ " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" deny obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny where false; obligation \"obligation4\" advice \"advice4\"";

		ArrayNode advice = JSON.arrayNode();
		advice.add(JSON.textNode("advice1"));
		advice.add(JSON.textNode("advice2"));

		assertEquals(Optional.of(advice), INTERPRETER
				.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst().getAdvices());
	}

}
