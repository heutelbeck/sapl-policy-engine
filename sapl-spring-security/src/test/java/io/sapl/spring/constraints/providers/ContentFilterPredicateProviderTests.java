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
package io.sapl.spring.constraints.providers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ContentFilterPredicateProviderTests {
	private final static ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void when_constraintIsNull_then_notResponsible() {
		var      sut        = new ContentFilterPredicateProvider(MAPPER);
		JsonNode constraint = null;
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintNonObject_then_notResponsible() throws JsonProcessingException {
		var sut        = new ContentFilterPredicateProvider(MAPPER);
		var constraint = MAPPER.readTree("123");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintNoType_then_notResponsible() throws JsonProcessingException {
		var sut        = new ContentFilterPredicateProvider(MAPPER);
		var constraint = MAPPER.readTree("{ }");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintTypeNonTextual_then_notResponsible() throws JsonProcessingException {
		var sut        = new ContentFilterPredicateProvider(MAPPER);
		var constraint = MAPPER.readTree("""
				{
					"type" : 123
				}
				""");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintWrongType_then_notResponsible() throws JsonProcessingException {
		var sut        = new ContentFilterPredicateProvider(MAPPER);
		var constraint = MAPPER.readTree("""
				{
					"type" : "unrelatedType"
				}
				""");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintTypeCorrect_then_isResponsible() throws JsonProcessingException {
		var sut        = new ContentFilterPredicateProvider(MAPPER);
		var constraint = MAPPER.readTree("""
				{
					"type" : "jsonContentFilterPredicate"
				}
				""");
		assertThat(sut.isResponsible(constraint), is(true));
	}
	
	@Test
	void when_predicateNotMatching_then_False() throws JsonProcessingException {
		var sut        = new ContentFilterPredicateProvider(MAPPER);
		var constraint = MAPPER.readTree("""
				{
					"type"    : "filterJsonContent",
					"actions" : [
						{
							"type" : "delete",
							"path" : "$.key1"
						}
					],
					"conditions" : [
						{
							"path" : "$.key1",
							"type" : "==",
							"value" : "another value that does not match"
						}
					]
				}
				""");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("""
				{
					"key1" : "value1",
					"key2" : "value2"
				}
				""");
		assertThat(handler.test(original), is(false));
	}

	@Test
	void when_handlerHandlesNull_handlerReturnsNull() throws JsonProcessingException {
		var    sut        = new ContentFilteringProvider(MAPPER);
		var    constraint = MAPPER.readTree("""
				{
					"type"    : "filterJsonContent",
					"actions" : [
						{
							"type" : "delete",
							"path" : "$.key1"
						}
					]
				}
				""");
		var    handler    = sut.getHandler(constraint);
		Object original   = null;
		Object expected   = null;
		assertThat(handler.apply(original), is(expected));
	}


}
