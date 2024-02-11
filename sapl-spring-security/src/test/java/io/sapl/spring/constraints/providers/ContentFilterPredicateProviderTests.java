/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ContentFilterPredicateProviderTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    private static Stream<Arguments> provideTestCases() {
        // @formatter:off
		return Stream.of(
				// when_constraintTypeNonTextual_then_notResponsible
			    Arguments.of("""
                    {
                         "type" : 123
                    }
                    """, Boolean.FALSE),
				// when_constraintWrongType_then_notResponsible
			    Arguments.of("""
					{
						"type" : "unrelatedType"
					}
					""", Boolean.FALSE),
				// when_constraintTypeCorrect_then_isResponsible
			    Arguments.of("""
					{
						"type" : "jsonContentFilterPredicate"
					}
					""", Boolean.TRUE)
			);
		// @formatter:on
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    void validateResponsibility(String constraint, boolean expectedResponsibility) throws JsonProcessingException {
        var sut            = new ContentFilterPredicateProvider(MAPPER);
        var jsonConstraint = MAPPER.readTree(constraint);
        assertThat(sut.isResponsible(jsonConstraint), is(expectedResponsibility));
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
