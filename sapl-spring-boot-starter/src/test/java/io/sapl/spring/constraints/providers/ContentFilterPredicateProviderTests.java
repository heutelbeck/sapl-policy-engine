/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;

class ContentFilterPredicateProviderTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Value toValue(String json) throws JsonProcessingException {
        return ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(json));
    }

    @Test
    void when_constraintIsNull_then_notResponsible() {
        final var sut = new ContentFilterPredicateProvider(MAPPER);
        assertThat(sut.isResponsible(null), is(false));
    }

    @Test
    void when_constraintNonObject_then_notResponsible() throws JsonProcessingException {
        final var sut        = new ContentFilterPredicateProvider(MAPPER);
        final var constraint = toValue("123");
        assertThat(sut.isResponsible(constraint), is(false));
    }

    @Test
    void when_constraintNoType_then_notResponsible() throws JsonProcessingException {
        final var sut        = new ContentFilterPredicateProvider(MAPPER);
        final var constraint = toValue("{ }");
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
        final var sut             = new ContentFilterPredicateProvider(MAPPER);
        final var valueConstraint = toValue(constraint);
        assertThat(sut.isResponsible(valueConstraint), is(expectedResponsibility));
    }

    @Test
    void when_predicateNotMatching_then_False() throws JsonProcessingException {
        final var sut        = new ContentFilterPredicateProvider(MAPPER);
        final var constraint = toValue("""
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
        final var handler    = sut.getHandler(constraint);
        final var original   = MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2"
                }
                """);
        assertThat(handler.test(original), is(false));
    }

    @Test
    void when_handlerHandlesNull_handlerReturnsNull() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
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
        final var handler    = sut.getHandler(constraint);
        Object    original   = null;
        Object    expected   = null;
        assertThat(handler.apply(original), is(expected));
    }

}
