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
package io.sapl.functions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.sapl.api.interpreter.PolicyEvaluationException
import io.sapl.api.pdp.AuthorizationSubscription
import io.sapl.api.pdp.AuthorizationDecision
import io.sapl.functions.SelectionFunctionLibrary
import io.sapl.interpreter.DefaultSAPLInterpreter
import io.sapl.interpreter.functions.AnnotationFunctionContext
import io.sapl.interpreter.functions.FunctionContext
import io.sapl.interpreter.pip.AnnotationAttributeContext
import io.sapl.interpreter.pip.AttributeContext
import java.util.Collections
import java.util.HashMap
import java.util.Map
import org.junit.Before
import org.junit.Test

import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.assertThat

class SelectionFunctionLibraryTest {

	static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	static final AttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();
	static final FunctionContext FUNCTION_CTX = new AnnotationFunctionContext();
	static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<String, JsonNode>());

	static final String authzSubscription = '''
		{  
		    "subject": {  
		        "id": "12345"
		    },
		    "action": {},
		    "resource": {  
				"_content": {
					"personal": {
						"firstname": "John",
						"lastname": "Doe",
						"age": 18
					},
					"records": [
						{ 
							"name": "name1",
							"value": 100
						},
						{ 
							"name": "name2",
							"value": 200
						},
						{ 
							"name": "name3",
							"value": 300
						}
					],
					"dummy": "John"
				}
			},
			"environment": {	}
		}
	''';
	static AuthorizationSubscription authzSubscriptionObj

	@Before
	def void init() {
		FUNCTION_CTX.loadLibrary(new SelectionFunctionLibrary());
		authzSubscriptionObj = MAPPER.readValue(authzSubscription, AuthorizationSubscription)
	}

	@Test
	def void apply() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit where
				var _selector = "@.records[-1].name";
				selection.apply(resource._content, _selector) == "name3";
		''';

		val expectedAuthzDecision = AuthorizationDecision.PERMIT
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("apply function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void matchSimpleFalse() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit where
				var _selector = "@.personal.age";
				selection.match(resource._content, _selector, "@.records");
		''';

		val expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("match function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void matchSimpleTrue() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit where
				var _selector = "@.records[1]";
				selection.match(resource._content, _selector, "@.records");
		''';

		val expectedAuthzDecision = AuthorizationDecision.PERMIT
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("match function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void matchDetectPosition() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit where
				var _selector = "@.dummy";
				selection.match(resource._content, _selector, "@.personal.firstname");
		''';

		val expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("match function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void matchExtended1False() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit where
				var _selector = "@.records[-1]";
				selection.match(resource._content, _selector, "@.records[:-1]");
		''';

		val expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("match function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void matchExtended1True() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit where
				var _selector = "@.records[0]";
				selection.match(resource._content, _selector, "@.records[:-1]");
		''';

		val expectedAuthzDecision = AuthorizationDecision.PERMIT
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("match function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void matchExtended2False() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit where
				var _selector = "@.records[0]";
				selection.match(resource._content, _selector, "@.records[?(@.value>250)]");
		''';

		val expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("match function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void matchExtended2True() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit where
				var _selector = "@.records[-1]";
				selection.match(resource._content, _selector, "@.records[?(@.value>250)]");
		''';

		val expectedAuthzDecision = AuthorizationDecision.PERMIT
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("match function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void matchTrueNoStepsHaystack() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit where
				var _selector = "@.records[-1]";
				selection.match(resource._content, _selector, "@");
		''';

		val expectedAuthzDecision = AuthorizationDecision.PERMIT
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("match function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void matchTrueNoStepsHaystackNeedle() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit where
				var _selector = "@";
				selection.match(resource._content, _selector, "@");
		''';

		val expectedAuthzDecision = AuthorizationDecision.PERMIT
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("match function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void matchFalseNoStepsNeedle() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit where
				var _selector = "@";
				selection.match(resource._content, _selector, "@.records");
		''';

		val expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("match function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void equalsTrue() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit selection.equal(resource._content, "@.records[-1].name","@.records[2].name")
		''';

		val expectedAuthzDecision = AuthorizationDecision.PERMIT
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("equals function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void equalsTrueNoSteps() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit selection.equal(resource._content, "@","@")
		''';

		val expectedAuthzDecision = AuthorizationDecision.PERMIT
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("equals function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void equalsFalse() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit selection.equal(resource._content, "@.dummy","@.personal.firstname")
		''';

		val expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("equals function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void equalsList() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "test" 
			permit selection.equal(resource._content, "@.records[:-1].name","@.records[0:2].name")
		''';

		val expectedAuthzDecision = AuthorizationDecision.PERMIT
		val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES).blockFirst()

		assertThat("equals function not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}
}
