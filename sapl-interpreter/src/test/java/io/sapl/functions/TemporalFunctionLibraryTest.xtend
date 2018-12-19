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
package io.sapl.functions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.sapl.api.pdp.Decision
import io.sapl.api.pdp.Request
import io.sapl.api.pdp.Response
import io.sapl.interpreter.DefaultSAPLInterpreter
import io.sapl.interpreter.functions.AnnotationFunctionContext
import io.sapl.interpreter.functions.FunctionContext
import io.sapl.interpreter.pip.AnnotationAttributeContext
import io.sapl.interpreter.pip.AttributeContext
import io.sapl.pip.ClockPolicyInformationPoint
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections
import java.util.HashMap
import java.util.Map
import java.util.Optional
import org.junit.Before
import org.junit.Test

import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.assertThat

class TemporalFunctionLibraryTest {

	static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
	static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter()
	static final AttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext()
	static final FunctionContext FUNCTION_CTX = new AnnotationFunctionContext()
	static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<String, JsonNode>())
	static final TemporalFunctionLibrary LIBRARY = new TemporalFunctionLibrary()
	static final ClockPolicyInformationPoint CLOCK_PIP = new ClockPolicyInformationPoint()
	static final JsonNodeFactory JSON = JsonNodeFactory.instance

    static final String request = '''
		{
		    "subject": "somebody",
		    "action": "read",
		    "resource": {},
			"environment": {}
		}
	'''

    static Request requestObject

	@Before
	def void init() {
		FUNCTION_CTX.loadLibrary(LIBRARY)
        ATTRIBUTE_CTX.loadPolicyInformationPoint(CLOCK_PIP)
        requestObject = MAPPER.readValue(request, Request)
	}
	
	@Test
	def void nowPlus10Seconds() {
        val zoneId = JSON.textNode("UTC")
		val now = new ClockPolicyInformationPoint().now(zoneId, Collections.<String, JsonNode> emptyMap)
		val plus10 = TemporalFunctionLibrary.plusSeconds(now, JSON.numberNode(10))

		val expected = Instant.parse(now.asText).plusSeconds(10)

		assertThat("plusSeconds function not working as expected", Instant.parse(plus10.asText), equalTo(expected))
	}

	@Test
	def void dayOfWeekFrom() {
        val zoneId = JSON.textNode("UTC")
        val now = new ClockPolicyInformationPoint().now(zoneId, Collections.<String, JsonNode> emptyMap)
		val dayOfWeek = TemporalFunctionLibrary.dayOfWeekFrom(now).asText
		val expected = DayOfWeek.from(OffsetDateTime.now(ZoneId.of("UTC"))).toString

		assertThat("dayOfWeekFrom function not working as expected", dayOfWeek, equalTo(expected))
	}

    @Test
    def void policyWithMatchingTemporalBody() {
        val policyDefinition = '''
			policy "test"
			permit
			    action == "read"
			where
			    time.before("UTC".<clock.now>, time.plusSeconds("UTC".<clock.now>, 10));
		'''

        val expectedResponse = Response.permit
        val response = INTERPRETER.evaluate(requestObject, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst()

        assertThat("temporal functions not working as expected", response, equalTo(expectedResponse))
    }

    @Test
    def void policyWithNonMatchingTemporalBody() {
        val policyDefinition = '''
			policy "test"
			permit
			    action == "read"
			where
			    time.after("UTC".<clock.now>, time.plusSeconds("UTC".<clock.now>, 10));
		'''

        val expectedResponse = Response.notApplicable
        val response = INTERPRETER.evaluate(requestObject, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst()

        assertThat("temporal functions not working as expected", response, equalTo(expectedResponse))
    }

    @Test
    def void policyWithDayOfWeekBody() {
        val policyDefinition = '''
			policy "test"
			permit
			    action == "read"
			where
			    time.dayOfWeekFrom("UTC".<clock.now>) == "SUNDAY";
		'''

        var Response expectedResponse
        if (DayOfWeek.from(OffsetDateTime.now(ZoneId.systemDefault)) == DayOfWeek.SUNDAY) {
            expectedResponse = Response.permit
        } else {
            expectedResponse = Response.notApplicable
        }
        val response = INTERPRETER.evaluate(requestObject, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst()

        assertThat("temporal functions not working as expected", response, equalTo(expectedResponse))
    }
}
