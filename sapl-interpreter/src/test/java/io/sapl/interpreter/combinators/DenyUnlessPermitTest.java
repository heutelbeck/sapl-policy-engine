/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;

public class DenyUnlessPermitTest {

	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final AuthorizationSubscription EMPTY_AUTH_SUBSCRIPTION = new AuthorizationSubscription(null, null,
			null, null);

	private static final AuthorizationSubscription AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE = new AuthorizationSubscription(
			null, null, JSON.booleanNode(true), null);

	private static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());

	private AttributeContext attributeCtx;

	private FunctionContext functionCtx;

	@Before
	public void init() {
		attributeCtx = new AnnotationAttributeContext();
		functionCtx = new AnnotationFunctionContext();
	}

	@Test
	public void permit() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" permit";

		assertEquals("should return permit if the only policy evaluates to permit", Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getDecision());
	}

	@Test
	public void deny() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" deny";

		assertEquals("should return deny if the only policy evaluates to deny", Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getDecision());
	}

	@Test
	public void notApplicableTarget() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" permit true == false";

		assertEquals("should return deny if the only policy target evaluates to not applicable", Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getDecision());
	}

	@Test
	public void notApplicableCondition() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" permit where true == false;";

		assertEquals("should return deny if the only policy condition evaluates to not applicable", Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getDecision());
	}

	@Test
	public void indeterminateTarget() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" permit \"a\" < 5";

		assertEquals("should return deny if the only target is indeterminate", Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getDecision());
	}

	@Test
	public void indeterminateCondition() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" permit where \"a\" < 5;";

		assertEquals("should return deny if the only condition is indeterminate", Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getDecision());
	}

	@Test
	public void permitDeny() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" permit" + " policy \"testp2\" deny";

		assertEquals("should return permit if any policy evaluates to permit", Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getDecision());
	}

	@Test
	public void denyIndeterminate() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" deny"
				+ " policy \"testp2\" deny where \"a\" > 5;";

		assertEquals("should return deny if policies evaluate to deny and indeterminate", Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getDecision());
	}

	@Test
	public void permitNotApplicableDeny() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit true == false" + " policy \"testp3\" deny";

		assertEquals("should return permit if any policy evaluates to permit", Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getDecision());
	}

	@Test
	public void permitNotApplicableIndeterminateDeny() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" deny \"a\" > 5"
				+ " policy \"testp2\" permit true == false" + " policy \"testp3\" permit \"a\" > 5"
				+ " policy \"testp4\" deny" + " policy \"testp5\" permit";

		assertEquals("should return permit if any policy evaluates to permit", Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getDecision());
	}

	@Test
	public void permitIndeterminateNotApplicable() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" deny"
				+ " policy \"testp2\" deny \"a\" < 5" + " policy \"testp3\" deny true == false";

		assertEquals("should return deny if only indeterminate, deny and not applicable present", Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getDecision());
	}

	@Test
	public void multiplePermitTransformationDeny() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit transform true" + " policy \"testp3\" deny";

		assertEquals("should return deny if final decision would be deny and there is a transformation incertainty",
				Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getDecision());
	}

	@Test
	public void singlePermitTransformation() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" permit transform true"
				+ " policy \"testp2\" deny";

		assertEquals("should return permit if there is no transformation incertainty", Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getDecision());
	}

	@Test
	public void singlePermitTransformationResource() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" permit transform true";

		assertEquals("should return resource if there is no transformation incertainty",
				Optional.of(JSON.booleanNode(true)),
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getResource());
	}

	@Test
	public void multiplePermitNoTransformation() {
		String policySet = "set \"tests\" deny-unless-permit" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit";

		assertEquals("should return permit if there is no transformation incertainty", Decision.PERMIT, INTERPRETER
				.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst().getDecision());
	}

	@Test
	public void collectObligationDeny() {
		String policySet = "set \"tests\" deny-unless-permit"
				+ " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" permit false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

		ArrayNode obligation = JSON.arrayNode();
		obligation.add(JSON.textNode("obligation1"));
		obligation.add(JSON.textNode("obligation2"));

		assertEquals("should collect all deny obligation", Optional.of(obligation), INTERPRETER
				.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst().getObligations());
	}

	@Test
	public void collectAdviceDeny() {
		String policySet = "set \"tests\" deny-unless-permit"
				+ " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" permit false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

		ArrayNode advice = JSON.arrayNode();
		advice.add(JSON.textNode("advice1"));
		advice.add(JSON.textNode("advice2"));

		assertEquals("should collect all deny advice", Optional.of(advice), INTERPRETER
				.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst().getAdvices());
	}

	@Test
	public void collectObligationPermit() {
		String policySet = "set \"tests\" deny-unless-permit"
				+ " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" deny obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny where false; obligation \"obligation4\" advice \"advice4\"";

		ArrayNode obligation = JSON.arrayNode();
		obligation.add(JSON.textNode("obligation1"));
		obligation.add(JSON.textNode("obligation2"));

		assertEquals("should collect all permit obligation", Optional.of(obligation), INTERPRETER
				.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst().getObligations());
	}

	@Test
	public void collectAdvicePermit() {
		String policySet = "set \"tests\" deny-unless-permit"
				+ " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" deny obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny where false; obligation \"obligation4\" advice \"advice4\"";

		ArrayNode advice = JSON.arrayNode();
		advice.add(JSON.textNode("advice1"));
		advice.add(JSON.textNode("advice2"));

		assertEquals("should collect all permit obligation", Optional.of(advice), INTERPRETER
				.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.blockFirst().getAdvices());
	}

}
