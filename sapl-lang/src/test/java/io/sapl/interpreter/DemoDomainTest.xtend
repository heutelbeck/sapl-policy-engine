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
package io.sapl.interpreter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.sapl.api.functions.FunctionException
import io.sapl.api.interpreter.PolicyEvaluationException
import io.sapl.api.pdp.AuthorizationDecision
import io.sapl.api.pdp.AuthorizationSubscription
import io.sapl.functions.FilterFunctionLibrary
import io.sapl.interpreter.functions.AnnotationFunctionContext
import io.sapl.interpreter.functions.FunctionContext
import io.sapl.interpreter.pip.AnnotationAttributeContext
import io.sapl.interpreter.pip.AttributeContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Collections
import java.util.HashMap
import java.util.Map
import org.junit.Before
import org.junit.Test

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat

class DemoDomainTest {

	static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	static final AttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();
	static final FunctionContext FUNCTION_CTX = new AnnotationFunctionContext();
	static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<String, JsonNode>());
	static final EvaluationContext PDP_EVALUATION_CONTEXT = new EvaluationContext(ATTRIBUTE_CTX, FUNCTION_CTX,
		SYSTEM_VARIABLES);

	@Before
	def void setUp() throws FunctionException {
		FUNCTION_CTX.loadLibrary(new SimpleFunctionLibrary());
		FUNCTION_CTX.loadLibrary(new FilterFunctionLibrary());
		FUNCTION_CTX.loadLibrary(new SimpleFilterFunctionLibrary(
			Clock.fixed(Instant.parse("2017-05-03T18:25:43.511Z"), ZoneId.of("Europe/Berlin"))
		));
	}

	@Test
	def void recursiveDescendTest() throws PolicyEvaluationException {
		val authzSubscription = '''
		{   
			"subject"		:	{
				     				"authorities"	: [{"authority":"DOCTOR"}],
				     				"details"		: null,
				     				"authenticated"	: true,
				     				"principal"		: "Julia",
				     				"credentials"	: null,
				     				"name"			: "Julia"
								}, 
			   "action"		:	"use",
			   "resource"		:	"ui:view:patients:createPatientButton",
			   "environment"	: null
		}''';
		val authzSubscription_object = MAPPER.readValue(authzSubscription, AuthorizationSubscription)

		val policyDefinition = '''
			policy "all authenticated users may see patient list"
			permit "getPatients" in action..java.name
		''';

		val expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE

		assertThat("demo policy fail",
			INTERPRETER.evaluate(authzSubscription_object, policyDefinition, PDP_EVALUATION_CONTEXT).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

}
