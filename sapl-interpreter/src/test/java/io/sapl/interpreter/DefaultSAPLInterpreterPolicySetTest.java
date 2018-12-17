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
package io.sapl.interpreter;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import org.junit.Before;
import org.junit.Test;

public class DefaultSAPLInterpreterPolicySetTest {

	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	private static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());

	private Request request;
	private AttributeContext attributeCtx;
	private FunctionContext functionCtx;

	@Before
	public void init() throws FunctionException {
		request = new Request(null, null, null, null);
		attributeCtx = new AnnotationAttributeContext();
		functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new FilterFunctionLibrary());
	}

	@Test
	public void setPermit() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp\" permit");
		Response expectedResponse = new Response(Decision.PERMIT, Optional.empty(), Optional.empty(), Optional.empty());
		assertEquals("simple policy set should evaluate to permit", expectedResponse, INTERPRETER
				.evaluate(request, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void setDeny() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp\" deny");
		Response expectedResponse = Response.deny();
		assertEquals("simple policy set should evaluate to deny", expectedResponse, INTERPRETER
				.evaluate(request, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void setNotApplicable() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides for true == false" + " policy \"testp\" deny");
		Response expectedResponse = Response.notApplicable();
		assertEquals("simple policy set should evaluate to not applicable", expectedResponse, INTERPRETER
				.evaluate(request, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void noApplicablePolicies() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER
				.parse("set \"tests\" deny-overrides for true" + " policy \"testp\" deny true == false");
		Response expectedResponse = Response.notApplicable();
		assertEquals("set with no applicable policies should evaluate to not applicable", expectedResponse, INTERPRETER
				.evaluate(request, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void setIndeterminate() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides for \"a\" > 4" + " policy \"testp\" permit");
		Response expectedResponse = Response.indeterminate();
		assertEquals("simple policy set should evaluate to indeterminate", expectedResponse, INTERPRETER
				.evaluate(request, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void denyOverridesPermitAndDeny() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER
				.parse("set \"tests\" deny-overrides" + " policy \"testp1\" permit" + " policy \"testp2\" deny");
		Response expectedResponse = Response.deny();
		assertEquals("algorithm should return deny if any policy evaluates to deny", expectedResponse, INTERPRETER
				.evaluate(request, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void denyOverridesPermitAndNotApplicableAndDeny() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit true == false" + " policy \"testp3\" deny");
		Response expectedResponse = Response.deny();
		assertEquals("algorithm should return deny if any policy evaluates to deny", expectedResponse, INTERPRETER
				.evaluate(request, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void denyOverridesPermitAndIntederminateAndDeny() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit \"a\" < 5" + " policy \"testp3\" deny");
		Response expectedResponse = Response.deny();
		assertEquals("algorithm should return deny if any policy evaluates to deny", expectedResponse, INTERPRETER
				.evaluate(request, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void importsInSetAvailableInPolicy() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("import filter.replace set \"tests\" deny-overrides"
				+ " policy \"testp1\" permit transform true |- replace(false)");
		assertEquals("imports for policy set must be available in policies",
				Optional.of(JsonNodeFactory.instance.booleanNode(false)),
				INTERPRETER.evaluate(request, policySet, attributeCtx, functionCtx,
						SYSTEM_VARIABLES).getResource());
	}

	@Test
	public void importsDuplicatesByPolicySet() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("import filter.replace import filter.replace set \"tests\" deny-overrides"
				+ " policy \"testp1\" permit where true;");
		assertEquals("imports for policy set must not contain duplicates", Response.indeterminate(), INTERPRETER
				.evaluate(request, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES));
	}

	@Test
	public void variablesOnSetLevel() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER
				.parse("set \"tests\" deny-overrides var var1 = true;" + " policy \"testp1\" permit var1 == true");
		assertEquals("variables defined for policy set must be available in policies", Decision.PERMIT, INTERPRETER
				.evaluate(request, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.getDecision());
	}

	@Test
	public void variablesOnSetLevelError() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER
				.parse("set \"tests\" deny-overrides var var1 = true + null;" + " policy \"testp1\" permit");
		assertEquals("error in policy set variable definition should lead to indeterminate", Response.indeterminate(),
				INTERPRETER.evaluate(request, policySet, attributeCtx, functionCtx,
						SYSTEM_VARIABLES));
	}

	@Test
	public void variablesOverwriteInPolicy() throws PolicyEvaluationException {
		SAPL policySet = INTERPRETER.parse("set \"tests\" deny-overrides var var1 = true;"
				+ " policy \"testp1\" permit where var var1 = 10; var1 == 10;"
				+ " policy \"testp2\" deny where !(var1 == true);");
		assertEquals("it should be possible to overwrite a variable in a policy", Decision.PERMIT, INTERPRETER
				.evaluate(request, policySet, attributeCtx, functionCtx, SYSTEM_VARIABLES)
				.getDecision());
	}

	@Test
	public void subjectAsVariable() {
		final String policyDefinition = "set \"test\" deny-overrides var subject = null;  policy \"test\" permit";
		final Response expected = Response.indeterminate();
		final Response actual = INTERPRETER.evaluate(request, policyDefinition, attributeCtx, functionCtx, SYSTEM_VARIABLES);
		assertEquals("'subject' as variable name should evaluate to indeterminate", expected, actual);
	}

}
