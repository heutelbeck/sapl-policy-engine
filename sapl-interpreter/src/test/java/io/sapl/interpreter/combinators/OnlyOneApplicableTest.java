/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"; you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
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

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Request;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;

public class OnlyOneApplicableTest {

	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final Request EMPTY_REQUEST = new Request(null, null, null, null);

	private static final Request REQUEST_WITH_TRUE_RESOURCE = new Request(null, null, JSON.booleanNode(true), null);

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
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" permit";

		assertEquals("should return permit if the only policy evaluates to permit", Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_REQUEST, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst()
						.getDecision());
	}

	@Test
	public void deny() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" deny";

		assertEquals("should return deny if the only policy evaluates to deny", Decision.DENY,
				INTERPRETER.evaluate(EMPTY_REQUEST, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst()
						.getDecision());
	}

	@Test
	public void notApplicableTarget() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" deny true == false";

		assertEquals("should return not applicable if the only policy target evaluates to not applicable",
				Decision.NOT_APPLICABLE,
				INTERPRETER.evaluate(EMPTY_REQUEST, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst()
						.getDecision());
	}

	@Test
	public void notApplicableCondition() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" deny where true == false;";

		assertEquals("should return not applicable if the only policy condition evaluates to not applicable",
				Decision.NOT_APPLICABLE,
				INTERPRETER.evaluate(EMPTY_REQUEST, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst()
						.getDecision());
	}

	@Test
	public void indeterminateTarget() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" permit \"a\" < 5";

		assertEquals("should return indeterminate if the only target is indeterminate", Decision.INDETERMINATE,
				INTERPRETER.evaluate(EMPTY_REQUEST, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst()
						.getDecision());
	}

	@Test
	public void indeterminateCondition() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" permit where \"a\" < 5;";

		assertEquals("should return indeterminate if the only condition is indeterminate", Decision.INDETERMINATE,
				INTERPRETER.evaluate(EMPTY_REQUEST, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst()
						.getDecision());
	}

	@Test
	public void onePolicyMatching() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp1\" deny false"
				+ " policy \"testp2\" permit true" + " policy \"testp3\" permit false";

		assertEquals("should return permit if only matching policy evaluates to permit", Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_REQUEST, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst()
						.getDecision());
	}

	@Test
	public void twoPoliciesMatching1() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp1\" permit"
				+ " policy \"testp2\" deny";

		assertEquals("should return indeterminate if more than one policy matching", Decision.INDETERMINATE,
				INTERPRETER.evaluate(EMPTY_REQUEST, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst()
						.getDecision());
	}

	@Test
	public void twoPoliciesMatching2() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit";

		assertEquals("should return indeterminate if more than one policy matching", Decision.INDETERMINATE,
				INTERPRETER.evaluate(EMPTY_REQUEST, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst()
						.getDecision());
	}

	@Test
	public void twoPoliciesMatchingButOneNotApplicable() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp1\" permit"
				+ " policy \"testp2\" deny where false;";

		assertEquals(
				"should return indeterminate if more than one policy matching, "
						+ "even if only one evaluates to permit or deny",
				Decision.INDETERMINATE,
				INTERPRETER.evaluate(EMPTY_REQUEST, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst()
						.getDecision());
	}

	@Test
	public void singlePermitTransformation() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" permit transform true";

		assertEquals("should return permit if there is no transformation incertainty", Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_REQUEST, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst()
						.getDecision());
	}

	@Test
	public void singlePermitTransformationResource() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" permit transform true";

		assertEquals("should return resource if there is no transformation incertainty",
				Optional.of(JSON.booleanNode(true)),
				INTERPRETER.evaluate(EMPTY_REQUEST, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES).blockFirst()
						.getResource());
	}

	@Test
	public void collectObligationDeny() {
		String policySet = "set \"tests\" only-one-applicable"
				+ " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" deny false obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" permit false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

		ArrayNode obligation = JSON.arrayNode();
		obligation.add(JSON.textNode("obligation1"));

		assertEquals("should collect deny obligation of only matching policy", Optional.of(obligation),
				INTERPRETER.evaluate(REQUEST_WITH_TRUE_RESOURCE, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getObligations());
	}

	@Test
	public void collectAdviceDeny() {
		String policySet = "set \"tests\" only-one-applicable"
				+ " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" deny false obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" permit false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

		ArrayNode advice = JSON.arrayNode();
		advice.add(JSON.textNode("advice1"));

		assertEquals("should collect deny advice of only matching policy", Optional.of(advice),
				INTERPRETER.evaluate(REQUEST_WITH_TRUE_RESOURCE, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getAdvices());
	}

	@Test
	public void collectObligationPermit() {
		String policySet = "set \"tests\" only-one-applicable"
				+ " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" permit false obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" deny false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny false where false; obligation \"obligation4\" advice \"advice4\"";

		ArrayNode obligation = JSON.arrayNode();
		obligation.add(JSON.textNode("obligation1"));

		assertEquals("should collect permit obligation of only matching policy", Optional.of(obligation),
				INTERPRETER.evaluate(REQUEST_WITH_TRUE_RESOURCE, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getObligations());
	}

	@Test
	public void collectAdvicePermit() {
		String policySet = "set \"tests\" only-one-applicable"
				+ " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" permit false obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" deny false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny false where false; obligation \"obligation4\" advice \"advice4\"";

		ArrayNode advice = JSON.arrayNode();
		advice.add(JSON.textNode("advice1"));

		assertEquals("should collect permit advice of only matching policy", Optional.of(advice),
				INTERPRETER.evaluate(REQUEST_WITH_TRUE_RESOURCE, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
						.blockFirst().getAdvices());
	}

}
