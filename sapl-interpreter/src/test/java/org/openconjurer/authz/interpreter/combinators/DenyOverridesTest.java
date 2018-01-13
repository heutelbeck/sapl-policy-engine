/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.openconjurer.authz.interpreter.combinators;

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

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Request;
import io.sapl.api.pip.AttributeException;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;

public class DenyOverridesTest {

	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	private static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private AttributeContext attributeCtx;
	private FunctionContext functionCtx;

	@Before
	public void init() throws FunctionException, AttributeException {
		attributeCtx = new AnnotationAttributeContext();
		functionCtx = new AnnotationFunctionContext();
	}

	@Test
	public void permit() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp\" permit");

		assertEquals("should return permit if the only policy evaluates to permit", Decision.PERMIT, INTERPRETER
				.evaluate(new Request(null, null, null, null), policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.getDecision());
	}

	@Test
	public void deny() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp\" deny");

		assertEquals("should return deny if the only policy evaluates to deny", Decision.DENY, INTERPRETER
				.evaluate(new Request(null, null, null, null), policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.getDecision());
	}

	@Test
	public void notApplicableTarget() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp\" deny true == false");

		assertEquals("should return not applicable if the only policy target evaluates to not applicable",
				Decision.NOT_APPLICABLE, INTERPRETER.evaluate(new Request(null, null, null, null), policySet,
						attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void notApplicableCondition() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER
				.parse("set \"tests\" deny-overrides" + " policy \"testp\" deny where true == false;");

		assertEquals("should return not applicable if the only policy condition evaluates to not applicable",
				Decision.NOT_APPLICABLE, INTERPRETER.evaluate(new Request(null, null, null, null), policySet,
						attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void indeterminateTarget() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp\" permit \"a\" < 5");

		assertEquals("should return indeterminate if the only target is indeterminate", Decision.INDETERMINATE,
				INTERPRETER.evaluate(new Request(null, null, null, null), policySet, attributeCtx, functionCtx,
						SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void indeterminateCondition() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER
				.parse("set \"tests\" deny-overrides" + " policy \"testp\" permit where \"a\" < 5;");

		assertEquals("should return indeterminate if the only condition is indeterminate", Decision.INDETERMINATE,
				INTERPRETER.evaluate(new Request(null, null, null, null), policySet, attributeCtx, functionCtx,
						SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void permitDeny() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER
				.parse("set \"tests\" deny-overrides" + " policy \"testp1\" permit" + " policy \"testp2\" deny");

		assertEquals("should return deny if any policy evaluates to deny", Decision.DENY, INTERPRETER
				.evaluate(new Request(null, null, null, null), policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.getDecision());
	}

	@Test
	public void denyIndeterminate() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp1\" deny"
				+ " policy \"testp2\" deny where \"a\" > 5;");

		assertEquals("should return deny if any policy evaluates to deny", Decision.DENY, INTERPRETER
				.evaluate(new Request(null, null, null, null), policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.getDecision());
	}

	@Test
	public void permitNotApplicableDeny() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit true == false" + " policy \"testp3\" deny");

		assertEquals("should return deny if any policy evaluates to deny", Decision.DENY, INTERPRETER
				.evaluate(new Request(null, null, null, null), policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.getDecision());
	}

	@Test
	public void permitNotApplicableIndeterminateDeny() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit true == false" + " policy \"testp3\" permit \"a\" > 5"
				+ " policy \"testp4\" deny" + " policy \"testp5\" permit");

		assertEquals("should return deny if any policy evaluates to deny", Decision.DENY, INTERPRETER
				.evaluate(new Request(null, null, null, null), policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.getDecision());
	}

	@Test
	public void permitIndeterminateNotApplicable() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp1\" permit"
				+ " policy \"testp2\" deny \"a\" < 5" + " policy \"testp3\" deny true == false");

		assertEquals("should return indeterminate if only indeterminate, permit and not applicable present",
				Decision.INDETERMINATE, INTERPRETER.evaluate(new Request(null, null, null, null), policySet,
						attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void multiplePermitTransformation() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit transform true");

		assertEquals(
				"should return indeterminate if final decision would be permit and there is a transformation incertainty",
				Decision.INDETERMINATE, INTERPRETER.evaluate(new Request(null, null, null, null), policySet,
						attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void multiplePermitTransformationDeny() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit transform true" + " policy \"testp3\" deny");

		assertEquals("should return deny if final decision would be deny and there is a transformation incertainty",
				Decision.DENY, INTERPRETER.evaluate(new Request(null, null, null, null), policySet, attributeCtx,
						functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void singlePermitTransformation() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp\" permit transform true");

		assertEquals("should return permit if there is no transformation incertainty", Decision.PERMIT, INTERPRETER
				.evaluate(new Request(null, null, null, null), policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.getDecision());
	}

	@Test
	public void singlePermitTransformationResource() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp\" permit transform true");

		assertEquals("should return resource if there is no transformation incertainty",
				Optional.of(JSON.booleanNode(true)), INTERPRETER.evaluate(new Request(null, null, null, null),
						policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).getResource());
	}

	@Test
	public void multiplePermitNoTransformation() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER
				.parse("set \"tests\" deny-overrides" + " policy \"testp1\" permit" + " policy \"testp2\" permit");

		assertEquals("should return permit if there is no transformation incertainty", Decision.PERMIT,
				INTERPRETER.evaluate(new Request(null, null, JsonNodeFactory.instance.booleanNode(true), null),
						policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).getDecision());
	}

	@Test
	public void collectObligationDeny() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse(
				"set \"tests\" deny-overrides" + " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
						+ " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\""
						+ " policy \"testp3\" permit obligation \"obligation3\" advice \"advice3\""
						+ " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"");

		ArrayNode obligation = JSON.arrayNode();
		obligation.add(JSON.textNode("obligation1"));
		obligation.add(JSON.textNode("obligation2"));

		assertEquals("should collect all deny obligation", obligation,
				INTERPRETER.evaluate(new Request(null, null, JsonNodeFactory.instance.booleanNode(true), null),
						policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).getObligation());
	}

	@Test
	public void collectAdviceDeny() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse(
				"set \"tests\" deny-overrides" + " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
						+ " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\""
						+ " policy \"testp3\" permit obligation \"obligation3\" advice \"advice3\""
						+ " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"");

		ArrayNode advice = JSON.arrayNode();
		advice.add(JSON.textNode("advice1"));
		advice.add(JSON.textNode("advice2"));

		assertEquals("should collect all deny advice", advice,
				INTERPRETER.evaluate(new Request(null, null, JsonNodeFactory.instance.booleanNode(true), null),
						policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).getAdvice());
	}

	@Test
	public void collectObligationPermit() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides"
				+ " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" deny false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny where false; obligation \"obligation4\" advice \"advice4\"");

		ArrayNode obligation = JSON.arrayNode();
		obligation.add(JSON.textNode("obligation1"));
		obligation.add(JSON.textNode("obligation2"));

		assertEquals("should collect all permit obligation", obligation,
				INTERPRETER.evaluate(new Request(null, null, JsonNodeFactory.instance.booleanNode(true), null),
						policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).getObligation());
	}

	@Test
	public void collectAdvicePermit() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides"
				+ " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" deny false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny where false; obligation \"obligation4\" advice \"advice4\"");

		ArrayNode advice = JSON.arrayNode();
		advice.add(JSON.textNode("advice1"));
		advice.add(JSON.textNode("advice2"));

		assertEquals("should collect all permit advice", advice,
				INTERPRETER.evaluate(new Request(null, null, JsonNodeFactory.instance.booleanNode(true), null),
						policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).getAdvice());
	}

}
