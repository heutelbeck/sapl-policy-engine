/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pip

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.sapl.api.pdp.AuthorizationSubscription
import io.sapl.api.pdp.AuthorizationDecision
import io.sapl.functions.StandardFunctionLibrary
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
import static org.hamcrest.MatcherAssert.assertThat

class ClockPolicyInformationPointTest {

	static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
	static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter()
	static final AttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext()
	static final FunctionContext FUNCTION_CTX = new AnnotationFunctionContext()
	static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<String, JsonNode>())
	static final ClockPolicyInformationPoint PIP = new ClockPolicyInformationPoint()

    static final String authzSubscription = '''
		{
		    "subject": "somebody",
		    "action": "read",
		    "resource": {},
			"environment": {}
		}
	'''

    static AuthorizationSubscription authzSubscriptionObj

	@Before
	def void init() {
        FUNCTION_CTX.loadLibrary(new StandardFunctionLibrary())
		ATTRIBUTE_CTX.loadPolicyInformationPoint(PIP)
        authzSubscriptionObj = MAPPER.readValue(authzSubscription, AuthorizationSubscription)
	}

    @Test
    def void nowInUtcTime() {
        val policyDefinition = '''
			policy "test"
			permit
			    action == "read"
			where
			    standard.length("UTC".<clock.now>) > 1;
		'''

        val expectedAuthzDecision = AuthorizationDecision.PERMIT
        val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst()

        assertThat("now in UTC time zone should return a string of length > 1", authzDecision, equalTo(expectedAuthzDecision))
    }

    @Test
    def void nowInEctTime() {
        val policyDefinition = '''
			policy "test"
			permit
			    action == "read"
			where
			    standard.length("ECT".<clock.now>) > 1;
		'''

        val expectedAuthzDecision = AuthorizationDecision.PERMIT
        val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst()

        assertThat("now in ECT time zone should return a string of length  > 1", authzDecision, equalTo(expectedAuthzDecision))
    }

    @Test
    def void nowInEuropeBerlinTime() {
        val policyDefinition = '''
			policy "test"
			permit
			    action == "read"
			where
			    standard.length("Europe/Berlin".<clock.now>) > 1;
		'''

        val expectedAuthzDecision = AuthorizationDecision.PERMIT
        val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst()

        assertThat("now in Europe/Berlin time zone should return a string of length > 1", authzDecision, equalTo(expectedAuthzDecision))
    }

    @Test
    def void nowInSystemTime() {
        val policyDefinition = '''
			policy "test"
			permit
			    action == "read"
			where
			    var length = standard.length("system".<clock.now>);
			    length > 1;
		'''

        val expectedAuthzDecision = AuthorizationDecision.PERMIT
        val authzDecision = INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst()

        assertThat("now in the system's time zone should return a string of length > 1", authzDecision, equalTo(expectedAuthzDecision))
    }
}
