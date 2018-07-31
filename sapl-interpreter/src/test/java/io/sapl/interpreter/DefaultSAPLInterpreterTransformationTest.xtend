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
package io.sapl.interpreter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.sapl.api.interpreter.PolicyEvaluationException
import io.sapl.api.pdp.Decision
import io.sapl.api.pdp.Request
import io.sapl.api.pdp.Response
import io.sapl.functions.FilterFunctionLibrary
import io.sapl.functions.SelectionFunctionLibrary
import io.sapl.interpreter.DefaultSAPLInterpreter
import io.sapl.interpreter.functions.AnnotationFunctionContext
import io.sapl.interpreter.functions.FunctionContext
import io.sapl.interpreter.pip.AnnotationAttributeContext
import io.sapl.interpreter.pip.AttributeContext
import java.time.Clock
import java.util.Collections
import java.util.HashMap
import java.util.Map
import java.util.Optional
import org.junit.Before
import org.junit.Test

import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.assertThat

class DefaultSAPLInterpreterTransformationTest {

	static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	static final AttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();
	static final FunctionContext FUNCTION_CTX = new AnnotationFunctionContext();
	static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<String, JsonNode>());

	@Before
	def void init() {
		FUNCTION_CTX.loadLibrary(new SimpleFunctionLibrary());
		FUNCTION_CTX.loadLibrary(new FilterFunctionLibrary());
		FUNCTION_CTX.loadLibrary(new SelectionFunctionLibrary())
		FUNCTION_CTX.loadLibrary(new SimpleFilterFunctionLibrary(Clock.systemUTC()));
	}

	@Test
	def void simpleTransformationWithComment() throws PolicyEvaluationException {
		assertThat("simple transformation with comment not working as expected", INTERPRETER.evaluate(
			new Request(null, null, null, null),
			'''
				policy "test" 
					permit 
					transform
						"teststring"		// This is a dummy comment
						/* another comment */
			''',
			ATTRIBUTE_CTX,
			FUNCTION_CTX,
			SYSTEM_VARIABLES
		), equalTo(new Response(Decision.PERMIT, Optional.of(MAPPER.readValue('''
			"teststring"
		''', JsonNode)), Optional.empty(), Optional.empty())));
	}

	@Test
	def void simpleFiltering() throws PolicyEvaluationException {
		val request = MAPPER.readValue('''
			{
				"resource":"teststring"
			}
		''', Request);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				resource |- filter.blacken
				''';

		val expectedResource = MAPPER.readValue('''
			"XXXXXXXXXX"
		''', JsonNode);

		val expectedResponse = new Response(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(), Optional.empty())
		assertThat("simple filtering not working as expected",
			INTERPRETER.evaluate(request, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}

	@Test
	def void simpleArrayCondition() throws PolicyEvaluationException {
		val request = MAPPER.readValue('''
			{
				"resource":[1,2,3,4,5]
			}
		''', Request);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				resource[?(@>2 || @<2)]
				''';

		val expectedResource = MAPPER.readValue('''
			[1,3,4,5]
		''', JsonNode);

		val expectedResponse = new Response(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(), Optional.empty())
		assertThat("simple filtering not working as expected",
			INTERPRETER.evaluate(request, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}
		
	@Test
	def void conditionTransformation() throws PolicyEvaluationException {
		val request = MAPPER.readValue('''
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
		''', Request);

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

		val expectedResponse = new Response(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(), Optional.empty())
		assertThat("transformation with condition not working as expected",
			INTERPRETER.evaluate(request, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}

	@Test
	def void conditionSubtemplateFiltering() {
		val request = MAPPER.readValue('''
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
		''', Request);

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

		val expectedResponse = new Response(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(), Optional.empty())
		assertThat("transformation with condition, subtemplate and simple filtering not working as expected",
			INTERPRETER.evaluate(request, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}

	@Test
	def void conditionFilteringRules() {
		val request = MAPPER.readValue('''
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
		''', Request);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				{
					"array": resource.array[?(@.key1 > 2)] |- {
						each @. key2 : filter.blacken
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

		val expectedResponse = new Response(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(), Optional.empty())
		assertThat("transformation with several filtering rules not working as expected",
			INTERPRETER.evaluate(request, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}

	@Test
	def void arrayLast() {
		val request = MAPPER.readValue('''
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
		''', Request);

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

		val expectedResponse = new Response(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(), Optional.empty())
		assertThat("array slicing (last element) not working as expected",
			INTERPRETER.evaluate(request, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}

	@Test
	def void arraySlicing1() {
		val request = MAPPER.readValue('''
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
		''', Request);

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

		val expectedResponse = new Response(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(), Optional.empty())
		assertThat("array slicing not working as expected",
			INTERPRETER.evaluate(request, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}

	@Test
	def void arraySlicing2() {
		val request = MAPPER.readValue('''
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
		''', Request);

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

		val expectedResponse = new Response(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(), Optional.empty())
		assertThat("array slicing not working as expected",
			INTERPRETER.evaluate(request, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}

	@Test
	def void arrayExpressionMultipleIndices() {
		val request = MAPPER.readValue('''
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
		''', Request);

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

		val expectedResponse = new Response(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(), Optional.empty())
		assertThat("array selection by expression and multiple indices not working as expected",
			INTERPRETER.evaluate(request, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}

	@Test
	def void arrayExplicitEach() {
		val request = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						"1","2","3","4","5"
					]
				}
			}
		''', Request);

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

		val expectedResponse = new Response(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(), Optional.empty())
		assertThat("array with explicit each not working as expected",
			INTERPRETER.evaluate(request, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}

	@Test
	def void arrayMultidimensional() {
		val request = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						{"value":"1"},
						{"value":"2"},
						{"value":"3"}
					]
				}
			}
		''', Request);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				resource  |- {
					each @.array[1:].value : filter.blacken
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

		val expectedResponse = new Response(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(), Optional.empty())
		assertThat("filtering through object arrays not working as expected",
			INTERPRETER.evaluate(request, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}

	@Test
	def void recursiveDescent() {
		val request = MAPPER.readValue('''
			{
				"resource":{
					"array":[
						{"value":"1"},
						{"value":"2"},
						{"value":"3"}
					]
				}
			}
		''', Request);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				resource..value
				''';

		val expectedResource = MAPPER.readValue('''
			["1","2","3"]
		''', JsonNode);

		val expectedResponse = new Response(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(), Optional.empty())
		assertThat("recursive descent operator not working as expected",
			INTERPRETER.evaluate(request, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}

	@Test
	def void recursiveDescentInFilterRemove() {
		val request = MAPPER.readValue('''
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
		''', Request);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				resource |- {
					each @..value : remove
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

		val expectedResponse = new Response(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(), Optional.empty())
		assertThat("recursive descent operator in filter (remove) not working as expected",
			INTERPRETER.evaluate(request, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}

	@Test
	def void filterReplace() {
		val request = MAPPER.readValue('''
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
		''', Request);

		val policyDefinition = '''
			policy "test" 
			permit 
			transform
				resource |- {
					each @..name : filter.replace("***")
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

		val expectedResponse = new Response(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(), Optional.empty())
		assertThat("function replace() applied with recursive descent selector not working as expected",
			INTERPRETER.evaluate(request, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}

}
