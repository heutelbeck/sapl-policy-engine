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
package io.sapl.interpreter.combinators;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class DenyOverridesTest {

	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final AuthorizationSubscription EMPTY_AUTH_SUBSCRIPTION = new AuthorizationSubscription(null, null,
			null, null);

	private static final AuthorizationSubscription AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE = new AuthorizationSubscription(
			null, null, JSON.booleanNode(true), null);

	private static final Map<String, JsonNode> VARIABLES = new HashMap<>();

	private AnnotationAttributeContext attributeCtx;

	private AnnotationFunctionContext functionCtx;

	@BeforeEach
	void setUp() {
		attributeCtx = new AnnotationAttributeContext();
		functionCtx  = new AnnotationFunctionContext();
	}

	@Test
	void permit() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" permit";
		var expected  = Decision.PERMIT;
		validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
	}

	@Test
	void deny() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" deny";
		var expected  = Decision.DENY;
		validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
	}

	@Test
	void notApplicableTarget() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" deny true == false";
		var expected  = Decision.NOT_APPLICABLE;
		validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
	}

	@Test
	void notApplicableCondition() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" deny where true == false;";
		var expected  = Decision.NOT_APPLICABLE;
		validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
	}

	@Test
	void indeterminateTarget() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" permit \"a\" < 5";
		var expected  = Decision.INDETERMINATE;
		validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
	}

	@Test
	void indeterminateCondition() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" permit where \"a\" < 5;";
		var expected  = Decision.INDETERMINATE;
		validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
	}

	@Test
	void permitDeny() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" permit" + " policy \"testp2\" deny";
		var expected  = Decision.DENY;
		validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
	}

	@Test
	void denyIndeterminate() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" deny"
				+ " policy \"testp2\" deny where \"a\" > 5;";
		var expected  = Decision.DENY;
		validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
	}

	@Test
	void permitNotApplicableDeny() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit true == false" + " policy \"testp3\" deny";
		var expected  = Decision.DENY;
		validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
	}

	@Test
	void permitNotApplicableIndeterminateDeny() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit true == false" + " policy \"testp3\" permit \"a\" > 5"
				+ " policy \"testp4\" deny" + " policy \"testp5\" permit";
		var expected  = Decision.DENY;
		validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
	}

	@Test
	void permitIndeterminateNotApplicable() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" permit"
				+ " policy \"testp2\" deny \"a\" < 5" + " policy \"testp3\" deny true == false";
		var expected  = Decision.INDETERMINATE;
		validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
	}

	@Test
	void multiplePermitTransformation() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" permit transform false"
				+ " policy \"testp2\" permit transform true";
		var expected  = Decision.INDETERMINATE;
		validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
	}

	@Test
	void multiplePermitTransformationDeny() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit transform true" + " policy \"testp3\" deny";
		var expected  = Decision.DENY;
		validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
	}

	@Test
	void singlePermitTransformation() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" permit transform true";
		var expected  = Decision.PERMIT;
		validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
	}

	@Test
	void singlePermitTransformationResource() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" permit transform true";
		validateResource(EMPTY_AUTH_SUBSCRIPTION, policySet, Optional.of(JSON.booleanNode(true)));
	}

	@Test
	void transformUncertaintyButItIsDenySoItIsJustDeny() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" deny transform true"
				+ " policy \"testp2\" permit transform false";
		validateResource(EMPTY_AUTH_SUBSCRIPTION, policySet, Optional.of(JSON.booleanNode(true)));
	}

	@Test
	void multiplePermitNoTransformation() {
		var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" permit" + " policy \"testp2\" permit";
		var expected  = Decision.PERMIT;
		validateDecision(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, expected);
	}

	@Test
	void collectObligationDeny() {
		var policySet = "set \"tests\" deny-overrides"
				+ " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" permit obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

		ArrayNode obligations = JSON.arrayNode();
		obligations.add(JSON.textNode("obligation1"));
		obligations.add(JSON.textNode("obligation2"));

		validateObligations(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(obligations));
	}

	@Test
	void collectAdviceDeny() {
		var policySet = "set \"tests\" deny-overrides"
				+ " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" permit obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

		ArrayNode advice = JSON.arrayNode();
		advice.add(JSON.textNode("advice1"));
		advice.add(JSON.textNode("advice2"));

		validateAdvice(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(advice));
	}

	@Test
	void collectObligationPermit() {
		var policySet = "set \"tests\" deny-overrides"
				+ " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" deny false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny where false; obligation \"obligation4\" advice \"advice4\"";

		ArrayNode obligations = JSON.arrayNode();
		obligations.add(JSON.textNode("obligation1"));
		obligations.add(JSON.textNode("obligation2"));

		validateObligations(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(obligations));
	}

	@Test
	void collectAdvicePermit() {
		var policySet = "set \"tests\" deny-overrides"
				+ " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" deny false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny where false; obligation \"obligation4\" advice \"advice4\"";

		ArrayNode advice = JSON.arrayNode();
		advice.add(JSON.textNode("advice1"));
		advice.add(JSON.textNode("advice2"));

		validateAdvice(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(advice));
	}

	private void validateDecision(AuthorizationSubscription subscription, String policySet, Decision expected) {
		var decisions = evaluate(subscription, policySet).map(AuthorizationDecision::getDecision);
		StepVerifier.create(decisions).expectNext(expected).verifyComplete();
	}

	private void validateResource(AuthorizationSubscription subscription, String policySet,
			Optional<JsonNode> expected) {
		var decisions = evaluate(subscription, policySet).map(AuthorizationDecision::getResource);
		StepVerifier.create(decisions).expectNext(expected).verifyComplete();
	}

	private void validateObligations(AuthorizationSubscription subscription, String policySet,
			Optional<ArrayNode> expected) {
		var decisions = evaluate(subscription, policySet).map(AuthorizationDecision::getObligations);
		StepVerifier.create(decisions).expectNext(expected).verifyComplete();
	}

	private void validateAdvice(AuthorizationSubscription subscription, String policySet,
			Optional<ArrayNode> expected) {
		var decisions = evaluate(subscription, policySet).map(AuthorizationDecision::getAdvice);
		StepVerifier.create(decisions).expectNext(expected).verifyComplete();
	}

	private Flux<AuthorizationDecision> evaluate(AuthorizationSubscription subscription, String policySet) {
		return INTERPRETER.evaluate(subscription, policySet, attributeCtx, functionCtx, VARIABLES);
	}

}
