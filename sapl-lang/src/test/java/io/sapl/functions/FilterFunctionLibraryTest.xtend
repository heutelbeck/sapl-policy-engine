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
package io.sapl.functions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.sapl.api.functions.FunctionException
import io.sapl.api.interpreter.Val
import io.sapl.api.pdp.AuthorizationDecision
import io.sapl.api.pdp.AuthorizationSubscription
import io.sapl.api.pdp.Decision
import io.sapl.interpreter.DefaultSAPLInterpreter
import io.sapl.interpreter.EvaluationContext
import io.sapl.interpreter.functions.AnnotationFunctionContext
import io.sapl.interpreter.functions.FunctionContext
import io.sapl.interpreter.pip.AnnotationAttributeContext
import io.sapl.interpreter.pip.AttributeContext
import java.util.Collections
import java.util.HashMap
import java.util.Map
import java.util.Optional
import org.junit.Before
import org.junit.Test

import static io.sapl.interpreter.functions.IsError.isError
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat

class FilterFunctionLibraryTest {

	static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	static final AttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();
	static final FunctionContext FUNCTION_CTX = new AnnotationFunctionContext();
	static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<String, JsonNode>());
	static final FilterFunctionLibrary LIBRARY = new FilterFunctionLibrary();
	static final EvaluationContext PDP_EVALUATION_CONTEXT = new EvaluationContext(ATTRIBUTE_CTX, FUNCTION_CTX,
		SYSTEM_VARIABLES);

	@Before
	def void setUp() throws FunctionException {
		FUNCTION_CTX.loadLibrary(LIBRARY);
	}

	@Test
	def void blackenTooManyArguments() {
		val result = FilterFunctionLibrary.blacken(Val.of("abcde"), Val.of(2), Val.of(2), Val.of("x"), Val.of(2))
		assertThat(
			"blacken function not working as expected",
			result,
			isError()
		)

	}

	@Test
	def void blackenNoString() {
		val result = FilterFunctionLibrary.blacken(Val.of(2))
		assertThat(
			"blacken function not working as expected",
			result,
			isError()
		)
	}

	@Test
	def void blackenReplacementNoString() {
		val result = FilterFunctionLibrary.blacken(Val.of("abcde"), Val.of(2), Val.of(2), Val.of(2))
		assertThat(
			"blacken function not working as expected",
			result,
			isError()
		)
	}

	@Test
	def void blackenReplacementNegativeRight() {
		val result = FilterFunctionLibrary.blacken(Val.of("abcde"), Val.of(2), Val.of(-2))
		assertThat(
			"blacken function not working as expected",
			result,
			isError()
		)
	}

	@Test
	def void blackenReplacementNegativeLeft() {
		val result = FilterFunctionLibrary.blacken(Val.of("abcde"), Val.of(-2), Val.of(2))
		assertThat(
			"blacken function not working as expected",
			result,
			isError()
		)
	}

	@Test
	def void blackenReplacementRightNoNumber() {
		val result = FilterFunctionLibrary.blacken(Val.of("abcde"), Val.of(2), Val.ofNull())
		assertThat(
			"blacken function not working as expected",
			result,
			isError()
		)
	}

	@Test
	def void blackenReplacementLeftNoNumber() {
		val result = FilterFunctionLibrary.blacken(Val.of("abcde"), Val.ofNull(), Val.of(2))
		assertThat(
			"blacken function not working as expected",
			result,
			isError()
		)
	}

	@Test
	def void blackenWorking() {
		val text = Val.of("abcde")
		val discloseLeft = Val.of(1)
		val discloseRight = Val.of(1)
		val replacement = Val.of("*")

		val result = FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight, replacement)

		assertThat(
			"blacken function not working as expected",
			result,
			equalTo(Val.of("a***e"))
		)
	}

	@Test
	def void blackenWorkingAllVisible() {
		val text = Val.of("abcde")
		val discloseLeft = Val.of(3)
		val discloseRight = Val.of(3)
		val replacement = Val.of("*")

		val result = FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight, replacement)

		assertThat(
			"blacken function not working as expected",
			result,
			equalTo(Val.of("abcde"))
		)
	}

	@Test
	def void blackenReplacementDefault() {
		val text = Val.of("abcde")
		val discloseLeft = Val.of(1)
		val discloseRight = Val.of(1)

		val result = FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight)

		assertThat(
			"blacken function - default value for replacement not working as expected",
			result,
			equalTo(Val.of("aXXXe"))
		)
	}

	@Test
	def void blackenDiscloseRightDefault() {
		val text = Val.of("abcde")
		val discloseLeft = Val.of(2)

		val result = FilterFunctionLibrary.blacken(text, discloseLeft)

		assertThat(
			"blacken function - default value for disclose left not working as expected",
			result,
			equalTo(Val.of("abXXX"))
		)
	}

	@Test
	def void blackenDiscloseLeftDefault() {
		val text = Val.of("abcde")

		val result = FilterFunctionLibrary.blacken(text)

		assertThat(
			"blacken function - default value for disclose left not working as expected",
			result,
			equalTo(Val.of("XXXXX"))
		)
	}

	@Test
	def void blackenInPolicy() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource": {
					"array": [
						null,
						true
					],
					"key1": "abcde"
				}
			}
		''', AuthorizationSubscription)

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				resource |- {
					@.key1 : filter.blacken(1)
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"array": [
					null,
					true
				],
				"key1": "aXXXX"
			}
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		val authzDecision = INTERPRETER.evaluate(authzSubscription, policyDefinition, PDP_EVALUATION_CONTEXT).
			blockFirst()

		assertThat("builtin function blacken() not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}

	@Test
	def void replace() {
		val result = FilterFunctionLibrary.replace(Val.ofNull(), Val.of(1))
		assertThat(
			"replace function not working as expected",
			result,
			equalTo(Val.of(1))
		)
	}

	@Test
	def void replaceInPolicy() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource": {
					"array": [
						null,
						true
					],
					"key1": "abcde"
				}
			}
		''', AuthorizationSubscription)

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				resource |- {
					@.array[1] : filter.replace("***"),
					@.key1 : filter.replace(null)
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"array": [
					null,
					"***"
				],
				"key1": null
			}
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		val authzDecision = INTERPRETER.evaluate(authzSubscription, policyDefinition, PDP_EVALUATION_CONTEXT).
			blockFirst()

		assertThat("builtin function replace() not working as expected", authzDecision, equalTo(expectedAuthzDecision))
	}
}
