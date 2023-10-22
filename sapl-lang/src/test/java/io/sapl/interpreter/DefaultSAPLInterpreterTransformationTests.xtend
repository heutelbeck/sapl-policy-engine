/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.api.pdp.AuthorizationDecision
import io.sapl.api.pdp.AuthorizationSubscription
import io.sapl.api.pdp.Decision
import io.sapl.functions.FilterFunctionLibrary
import io.sapl.interpreter.functions.AnnotationFunctionContext
import io.sapl.interpreter.pip.AnnotationAttributeContext
import java.time.Clock
import java.util.Collections
import java.util.HashMap
import java.util.Map
import java.util.Optional
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat

class DefaultSAPLInterpreterTransformationTests {

	static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	static final AnnotationAttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();
	static final AnnotationFunctionContext FUNCTION_CTX = new AnnotationFunctionContext();
	static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<String, JsonNode>());
	
	@BeforeEach
	def void setUp() {
		FUNCTION_CTX.loadLibrary(new SimpleFunctionLibrary());
		FUNCTION_CTX.loadLibrary(new FilterFunctionLibrary());
		FUNCTION_CTX.loadLibrary(new SimpleFilterFunctionLibrary(Clock.systemUTC()));
	}

	@Test
	def void simpleTransformationWithComment() {
		assertThat("simple transformation with comment not working as expected", INTERPRETER.evaluate(
			new AuthorizationSubscription(null, null, null, null),
			'''
				policy "test" 
					permit 
					transform
						"teststring"		// This is a dummy comment
						/* another comment */
			''',
			ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES
		).blockFirst(), equalTo(new AuthorizationDecision(Decision.PERMIT, Optional.of(MAPPER.readValue('''
			"teststring"
		''', JsonNode)), Optional.empty(), Optional.empty())));
	}

	@Test
	def void simpleFiltering() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource":"teststring"
			}
		''', AuthorizationSubscription);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				resource |- filter.blacken
		''';

		val expectedResource = MAPPER.readValue('''
			"XXXXXXXXXX"
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		assertThat("simple filtering not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

	@Test
	def void simpleArrayCondition() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource":[1,2,3,4,5]
			}
		''', AuthorizationSubscription);

		val policyDefinition = '''
			policy "test" 
			permit
			transform
				resource[?(@>2 || @<2)]
		''';

		val expectedResource = MAPPER.readValue('''
			[1,3,4,5]
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		assertThat("simple filtering not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

	@Test
	def void conditionTransformation() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						{
							"key1":1,
							"key2":2
						},
						{
							"key1":3,
							"key2":4
						},
						{
							"key1":5,
							"key2":6
						}
					]
				}
			}
		''', AuthorizationSubscription);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				{
					"array": resource.array[?(@.key1 > 2)]
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"array":[
					{
						"key1":3,
						"key2":4
					},
					{
						"key1":5,
						"key2":6
					}
				]
			}
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		assertThat("transformation with condition not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

	@Test
	def void conditionSubTemplateFiltering() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						{
							"key1":1,
							"key2":2
						},
						{
							"key1":3,
							"key2":4
						},
						{
							"key1":5,
							"key2":6
						}
					]
				}
			}
		''', AuthorizationSubscription);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				{
					"array": resource.array[?(@.key1 > 2)] :: {
						"key20": @.key2
					}
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"array":[
					{
						"key20":4
					},
					{
						"key20":6
					}
				]
			}
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		assertThat("transformation with condition, sub-template and simple filtering not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

	@Test
	def void conditionFilteringRules() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						{
							"key1":1,
							"key2":"2"
						},
						{
							"key1":3,
							"key2":"4"
						},
						{
							"key1":5,
							"key2":"6"
						}
					]
				}
			}
		''', AuthorizationSubscription);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				{
					"array": resource.array[?(@.key1 > 2)] |- {
						@.key2 : filter.blacken
					}
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"array":[
					{
						"key1":3,
						"key2":"X"
					},
					{
						"key1":5,
						"key2":"X"
					}
				]
			}
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		assertThat("transformation with several filtering rules not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

	@Test
	def void arrayLast() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						"1",
						"2",
						"3",
						"4",
						"5"
					]
				}
			}
		''', AuthorizationSubscription);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				{
					"last": resource.array[-1]
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"last":"5"
			}
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		assertThat("array slicing (last element) not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

	@Test
	def void arraySlicing1() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						"1",
						"2",
						"3",
						"4",
						"5"
					]
				}
			}
		''', AuthorizationSubscription);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				{
					"array": resource.array[2:]
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"array":[
					"3",
					"4",
					"5"
				]
			}
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		assertThat("array slicing not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

	@Test
	def void arraySlicing2() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						"1",
						"2",
						"3",
						"4",
						"5"
					]
				}
			}
		''', AuthorizationSubscription);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				{
					"array": resource.array[1:-1:2]
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"array":[
					"2",
					"4"
				]
			}
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		assertThat("array slicing not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

	@Test
	def void arrayExpressionMultipleIndices() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						"1",
						"2",
						"3",
						"4",
						"5"
					],
					"a_number":1
				}
			}
		''', AuthorizationSubscription);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				{
					"array": resource.array[2,4]
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"array":[
					"3",
					"5"
				]
			}
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		assertThat("array selection by expression and multiple indices not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

	@Test
	def void arrayExplicitEach() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						"1","2","3","4","5"
					]
				}
			}
		''', AuthorizationSubscription);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				resource |- {
					each @.array : filter.blacken
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"array":[
					"X","X","X","X","X"
				]
			}
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		assertThat("array with explicit each not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

	@Test
	def void arrayMultidimensional() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						{"value":"1"},
						{"value":"2"},
						{"value":"3"}
					]
				}
			}
		''', AuthorizationSubscription);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				resource  |- {
					@.array[1:].value : filter.blacken
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"array":[
					{"value":"1"},
					{"value":"X"},
					{"value":"X"}
				]
			}
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		assertThat("filtering through object arrays not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

	@Test
	def void recursiveDescent() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						{"value":"1"},
						{"value":"2"},
						{"value":"3"}
					]
				}
			}
		''', AuthorizationSubscription);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				resource..value
		''';

		val expectedResource = MAPPER.readValue('''
			["1","2","3"]
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		assertThat("recursive descent operator not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

	@Test
	def void recursiveDescentInFilterRemove() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						{"value":"1"},
						{"value":"2"},
						{"value":"3"}
					],
					"value":"4"
				}
			}
		''', AuthorizationSubscription);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				resource |- {
					@..value : filter.remove
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"array":[
					{},
					{},
					{}
				]
			}
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		assertThat("recursive descent operator in filter (remove) not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

	@Test
	def void filterReplace() {
		val authzSubscription = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						{"name":"John Doe"},
						{"name":"Jane Doe"}
					],
					"value":"4",
					"name":"Tom Doe"
				}
			}
		''', AuthorizationSubscription);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				resource |- {
					@..name : filter.replace("***")
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"array":[
					{"name":"***"},
					{"name":"***"}
				],
				"value":"4",
				"name":"***"
			}
		''', JsonNode);

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())
		assertThat("function replace() applied with recursive descent selector not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(),
			equalTo(expectedAuthzDecision));
	}

}
