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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Publisher;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ContentFilteringProviderTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Value toValue(String json) throws JacksonException {
        return ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(json));
    }

    @Test
    void when_getSupportedType_then_isObject() {
        final var sut = new ContentFilteringProvider(MAPPER);
        assertThat(sut.getSupportedType()).isEqualTo(Object.class);
    }

    static Stream<Arguments> isResponsibleCases() throws JacksonException {
        return Stream.of(arguments("null constraint", null, false),
                arguments("non-object constraint", toValue("123"), false),
                arguments("no type field", toValue("{ }"), false),
                arguments("non-textual type", toValue("{\"type\": 123}"), false),
                arguments("wrong type value", toValue("{\"type\": \"unrelatedType\"}"), false),
                arguments("correct type", toValue("{\"type\": \"filterJsonContent\"}"), true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("isResponsibleCases")
    void whenCheckingResponsibility_thenReturnsExpectedResult(String description, Value constraint,
            boolean expectedResult) {
        final var sut = new ContentFilteringProvider(MAPPER);
        assertThat(sut.isResponsible(constraint)).isEqualTo(expectedResult);
    }

    @Test
    void when_noActionsSpecified_then_isIdentity() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type" : "filterJsonContent"
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = MAPPER.readTree("""
                {
                	"key1" : "value1"
                }
                """);
        assertThat(handler.apply(original)).isEqualTo(original);
    }

    static Stream<Arguments> actionValidationErrorCases() {
        return Stream.of(
                arguments("no action type", "{\"type\": \"filterJsonContent\", \"actions\": [{\"path\": \"$.key1\"}]}"),
                arguments("no action path", "{\"type\": \"filterJsonContent\", \"actions\": [{\"type\": \"delete\"}]}"),
                arguments("action not an object", "{\"type\": \"filterJsonContent\", \"actions\": [123]}"),
                arguments("actions not an array", "{\"type\": \"filterJsonContent\", \"actions\": 123}"),
                arguments("action path not textual",
                        "{\"type\": \"filterJsonContent\", \"actions\": [{\"type\": \"delete\", \"path\": 123}]}"),
                arguments("action type not textual",
                        "{\"type\": \"filterJsonContent\", \"actions\": [{\"type\": 123, \"path\": \"$.key1\"}]}"),
                arguments("unknown action type",
                        "{\"type\": \"filterJsonContent\", \"actions\": [{\"type\": \"unknown action\", \"path\": \"$.key1\"}]}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actionValidationErrorCases")
    void whenMalformedAction_thenThrowsError(String description, String constraintJson) throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue(constraintJson);
        final var handler    = sut.getHandler(constraint);
        final var original   = MAPPER.readTree("{\"key1\": \"value1\", \"key2\": \"value2\"}");
        assertThrows(AccessConstraintViolationException.class, () -> handler.apply(original));
    }

    static Stream<Arguments> blackenValidationErrorCases() {
        return Stream.of(arguments("non-textual replacement",
                "{\"type\": \"filterJsonContent\", \"actions\": [{\"type\": \"blacken\", \"path\": \"$.key1\", \"replacement\": 123}]}",
                "{\"key1\": \"value1\", \"key2\": \"value2\"}"),
                arguments("targets non-textual node",
                        "{\"type\": \"filterJsonContent\", \"actions\": [{\"type\": \"blacken\", \"path\": \"$.key1\"}]}",
                        "{\"key1\": 123, \"key2\": \"value2\"}"),
                arguments("discloseRight non-integer",
                        "{\"type\": \"filterJsonContent\", \"actions\": [{\"type\": \"blacken\", \"path\": \"$.key1\", \"replacement\": \"X\", \"discloseRight\": null, \"discloseLeft\": 1}]}",
                        "{\"key1\": \"value1\", \"key2\": \"value2\"}"),
                arguments("discloseLeft non-integer",
                        "{\"type\": \"filterJsonContent\", \"actions\": [{\"type\": \"blacken\", \"path\": \"$.key1\", \"replacement\": \"X\", \"discloseRight\": 1, \"discloseLeft\": \"wrongType\"}]}",
                        "{\"key1\": \"value1\", \"key2\": \"value2\"}"));
    }

    @ParameterizedTest(name = "blacken error: {0}")
    @MethodSource("blackenValidationErrorCases")
    void whenBlackenMalformed_thenThrowsError(String description, String constraintJson, String originalJson)
            throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue(constraintJson);
        final var handler    = sut.getHandler(constraint);
        final var original   = MAPPER.readTree(originalJson);
        assertThrows(AccessConstraintViolationException.class, () -> handler.apply(original));
    }

    @Test
    void when_blacken_then_textIsBlackened() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type"          : "blacken",
                			"path"          : "$.key1",
                			"replacement"   : "X",
                			"discloseRight" : 1,
                			"discloseLeft"  : 1
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
        assertThat(((JsonNode) handler.apply(original)).get("key1").asString()).isEqualTo("vXXXX1");
    }

    @Test
    void when_blackenWithDefinedLengthAndNegativeInteger_then_Error() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type"          : "blacken",
                			"path"          : "$.key1",
                			"replacement"   : "X",
                			"discloseRight" : 1,
                			"discloseLeft"  : 1,
                			"length"        : -1
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

        assertThrows(AccessConstraintViolationException.class, () -> handler.apply(original));
    }

    @Test
    void when_blackenWithDefinedLengthAndStringValue_then_Error() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type"          : "blacken",
                			"path"          : "$.key1",
                			"replacement"   : "X",
                			"discloseRight" : 1,
                			"discloseLeft"  : 1,
                			"length"        : "LENGTH"
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

        assertThrows(AccessConstraintViolationException.class, () -> handler.apply(original));
    }

    @Test
    void when_blackenWithDefinedLength_then_textIsBlackened() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type"          : "blacken",
                			"path"          : "$.key1",
                			"replacement"   : "X",
                			"discloseRight" : 1,
                			"discloseLeft"  : 1,
                			"length"        : 3
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
        assertThat(((JsonNode) handler.apply(original)).get("key1").asString()).isEqualTo("vXXX1");
    }

    @Test
    void when_multipleActions_then_allAreExecuted() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type"          : "blacken",
                			"path"          : "$.key1",
                			"replacement"   : "X",
                			"discloseRight" : 1,
                			"discloseLeft"  : 1
                		},
                		{
                			"type"          : "delete",
                			"path"          : "$.key2"
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
        var       result     = (JsonNode) handler.apply(original);
        assertThat(result.get("key1").asString()).isEqualTo("vXXXX1");
        assertThat(result.has("key2")).isFalse();
    }

    @Test
    void when_blackenWithDefaultReplacement_then_textIsBlackened() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type"          : "blacken",
                			"path"          : "$.key1",
                			"discloseRight" : 1,
                			"discloseLeft"  : 1
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
        assertThat(((JsonNode) handler.apply(original)).get("key1").asString()).isEqualTo("v████1");
    }

    @Test
    void when_stringToBlackenIsShorterThanDisclosedRange_then_textDoesNotChange() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type"          : "blacken",
                			"path"          : "$.key1",
                			"discloseRight" : 2,
                			"discloseLeft"  : 5
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
        assertThat(((JsonNode) handler.apply(original)).get("key1").asString()).isEqualTo("value1");
    }

    @Test
    void when_blackenWithNoParameters_then_textIsBlackenedNoCharsDisclosed() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type"          : "blacken",
                			"path"          : "$.key1"
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
        assertThat(((JsonNode) handler.apply(original)).get("key1").asString()).isEqualTo("██████");
    }

    @Test
    void when_deleteActionSpecified_then_dataIsRemovedFromJson() throws JacksonException {
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
        final var original   = MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2"
                }
                """);
        final var expected   = MAPPER.readTree("""
                {
                	"key2" : "value2"
                }
                """);

        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_pathNotExisting_then_AccessDeniedException() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
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
        assertThrows(AccessConstraintViolationException.class, () -> handler.apply(original));
    }

    static Stream<Arguments> malformedConstraintCases() {
        return Stream.of(arguments("condition not object", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : [ 1 ]
                }
                """), arguments("condition no path", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : [ {} ]
                }
                """), arguments("condition >= not a number", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : [{ "path" : "$.key1", "type" : ">=", "value" : "not a number" }]
                }
                """), arguments("condition <= not a number", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : [{ "path" : "$.key1", "type" : "<=", "value" : "not a number" }]
                }
                """), arguments("condition < not a number", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : [{ "path" : "$.key1", "type" : "<", "value" : "not a number" }]
                }
                """), arguments("condition > not a number", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : [{ "path" : "$.key1", "type" : ">", "value" : "not a number" }]
                }
                """), arguments("condition == not number or text", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : [{ "path" : "$.key1", "type" : "==", "value" : [] }]
                }
                """), arguments("condition regex not text", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : [{ "path" : "$.key1", "type" : "=~", "value" : [] }]
                }
                """), arguments("condition type non-textual", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : [{ "path" : "$.key1", "type" : 12, "value" : "abc" }]
                }
                """), arguments("constraint non-object", "123"), arguments("conditions not array", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : 123
                }
                """), arguments("condition type unknown", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : [{ "path" : "$.key1", "type" : "something unknown", "value" : "abc" }]
                }
                """), arguments("condition value missing", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : [{ "path" : "$.key1", "type" : "==" }]
                }
                """), arguments("condition path value missing", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : [{ "type" : "==", "value" : "abc" }]
                }
                """), arguments("condition type missing", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : [{ "path" : "$.key", "value" : "abc" }]
                }
                """), arguments("condition path non-textual", """
                {
                	"type"    : "filterJsonContent",
                	"actions" : [{ "type" : "delete", "path" : "$.key3" }],
                	"conditions" : [{ "type" : "==", "path" : 123, "value" : "abc" }]
                }
                """));
    }

    @ParameterizedTest(name = "malformed constraint: {0}")
    @MethodSource("malformedConstraintCases")
    void whenMalformedConstraint_thenGetHandlerThrowsException(String description, String constraintJson)
            throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue(constraintJson);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_constraintNull_then_AccessConstraintViolationException() {
        final var sut = new ContentFilteringProvider(MAPPER);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(null));
    }

    @Test
    void when_malformedConstraintConditionsEmpty_then_actionAppliedAndConditionAlwaysTrue() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key1"
                		}
                	],
                	"conditions" : [ ]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2"
                }
                """);
        final var expected   = MAPPER.readTree("""
                {
                	"key2" : "value2"
                }
                """);

        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_predicatePathNotExisting_then_AccessConstraintViolationException() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
                			"path" : "$.a",
                			"type" : "=~",
                			"value" : "^.BC$"
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
        assertThrows(AccessConstraintViolationException.class, () -> handler.apply(original));
    }

    @Test
    void when_predicateNotMatching_then_noModification() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
        assertThat(handler.apply(original)).isEqualTo(original);
    }

    @Test
    void when_handlerHandlesNull_handlerReturnsNull() throws JacksonException {
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
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_handlerHandlesOptional_handlerReturnsModifiedOptional() throws JacksonException {
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
        final var original   = Optional.ofNullable(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2"
                }
                """));
        final var expected   = Optional.ofNullable(MAPPER.readTree("""
                {
                	"key2" : "value2"
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_handlerHandlesList_handlerReturnsModifiedListContents() throws JacksonException {
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
        final var original   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2"
                }
                """));
        final var expected   = List.of(MAPPER.readTree("""
                {
                	"key2" : "value2"
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_handlerHandlesListMultipleConditions_handlerReturnsModifiedListContents() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
                			"path"  : "$.key2",
                			"type"  : "==",
                			"value" : "value2"
                		},{
                			"path"  : "$.key3",
                			"type"  : "==",
                			"value" : "value3"
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2",
                	"key3" : "value3"
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2",
                	"key3" : "value3"
                }
                """));
        final var expected   = List.of(MAPPER.readTree("""
                {
                	"key2" : "value2",
                	"key3" : "value3"
                }
                """), MAPPER.readTree("""
                {
                	"key2" : "value2",
                	"key3" : "value3"
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_handlerHandlesListEqNumberDataNotNumber_handlerNoModification() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
                			"path"  : "$.key2",
                			"type"  : "==",
                			"value" : 2
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2",
                	"key3" : "value3"
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2",
                	"key3" : "value3"
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(original);
    }

    @Test
    void when_handlerNumEq_handlerModifiedMatching() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
                			"path"  : "$.key2",
                			"type"  : "==",
                			"value" : 2
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 2,
                	"key3" : "value3"
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 4,
                	"key3" : "value3"
                }
                """));
        final var expected   = List.of(MAPPER.readTree("""
                {
                	"key2" : 2,
                	"key3" : "value3"
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 4,
                	"key3" : "value3"
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_handlerHandlesListMultipleConditionsAndOnlyOneHoldsInverted_noModifications() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
                			"path"  : "$.key2",
                			"type"  : "==",
                			"value" : "other"
                		},{
                			"path"  : "$.key3",
                			"type"  : "==",
                			"value" : "value3"
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2",
                	"key3" : "value3"
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2",
                	"key3" : "value3"
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(original);
    }

    @Test
    void when_handlerHandlesListMultipleConditionsAndOnlyOneHolds_noModifications() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
                			"path"  : "$.key2",
                			"type"  : "==",
                			"value" : "value2"
                		},{
                			"path"  : "$.key3",
                			"type"  : "==",
                			"value" : "other"
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2",
                	"key3" : "value3"
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2",
                	"key3" : "value3"
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(original);
    }

    @Test
    void when_handlerHandlesListNumberAndTextComparisons_makeModifications() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
                			"path"  : "$.key2",
                			"type"  : "==",
                			"value" : 2
                		},{
                			"path"  : "$.key3",
                			"type"  : "!=",
                			"value" : "other"
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 2,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 2,
                	"key3" : 3
                }
                """));
        final var expected   = List.of(MAPPER.readTree("""
                {
                	"key2" : 2,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key2" : 2,
                	"key3" : 3
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_handlerHandlesListNumberAndNeqCondition_makeModificationsAtMatch() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
                			"path"  : "$.key2",
                			"type"  : "!=",
                			"value" : 2
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 2,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 3,
                	"key3" : 3
                }
                """));
        final var expected   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 2,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key2" : 3,
                	"key3" : 3
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_handlerHandlesListNumberAndEqCondition_makeModificationsAtMatch() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
                			"path"  : "$.key2",
                			"type"  : "==",
                			"value" : 2
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 2,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 3,
                	"key3" : 3
                }
                """));
        final var expected   = List.of(MAPPER.readTree("""
                {
                	"key2" : 2,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 3,
                	"key3" : 3
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_handlerHandlesListNumberAndRegexCondition_makeModificationsAtMatch() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
                			"path"  : "$.key2",
                			"type"  : "=~",
                			"value" : "^update.*"
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 2,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "updateSomething",
                	"key3" : 3
                }
                """));
        final var expected   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 2,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key2" : 3,
                	"key2" : "updateSomething",
                	"key3" : 3
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_handlerHandlesGeqCondition_makeModificationsAtMatch() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
                			"path"  : "$.key2",
                			"type"  : ">=",
                			"value" : 3
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 2,
                	"key3" : 1
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "not a number",
                	"key3" : 2
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 3,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 6,
                	"key3" : 4
                }
                """));
        final var expected   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 2,
                	"key3" : 1
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "not a number",
                	"key3" : 2
                }
                """), MAPPER.readTree("""
                {
                	"key2" : 3,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key2" : 6,
                	"key3" : 4
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_handlerHandlesLeqCondition_makeModificationsAtMatch() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
                			"path"  : "$.key2",
                			"type"  : "<=",
                			"value" : 3
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 2,
                	"key3" : 1
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "not a number",
                	"key3" : 2
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 3,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 6,
                	"key3" : 4
                }
                """));
        final var expected   = List.of(MAPPER.readTree("""
                {
                	"key2" : 2,
                	"key3" : 1
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "not a number",
                	"key3" : 2
                }
                """), MAPPER.readTree("""
                {
                	"key2" : 3,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 6,
                	"key3" : 4
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_handlerHandlesLtCondition_makeModificationsAtMatch() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
                			"path"  : "$.key2",
                			"type"  : "<",
                			"value" : 3
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 2,
                	"key3" : 1
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "not a number",
                	"key3" : 2
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 3,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 6,
                	"key3" : 4
                }
                """));
        final var expected   = List.of(MAPPER.readTree("""
                {
                	"key2" : 2,
                	"key3" : 1
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "not a number",
                	"key3" : 2
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 3,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 6,
                	"key3" : 4
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_handlerHandlesGtCondition_makeModificationsAtMatch() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
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
                			"path"  : "$.key2",
                			"type"  : ">",
                			"value" : 3
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 2,
                	"key3" : 1
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "not a number",
                	"key3" : 2
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 3,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 6,
                	"key3" : 4
                }
                """));
        final var expected   = List.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 2,
                	"key3" : 1
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "not a number",
                	"key3" : 2
                }
                """), MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : 3,
                	"key3" : 3
                }
                """), MAPPER.readTree("""
                {
                	"key2" : 6,
                	"key3" : 4
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_handlerHandlesSet_handlerReturnsModifiedSetContents() throws JacksonException {
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
        final var original   = Set.of(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2"
                }
                """));
        final var expected   = Set.of(MAPPER.readTree("""
                {
                	"key2" : "value2"
                }
                """));
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_handlerHandlesArray_handlerReturnsModifiedArrayContents() throws JacksonException {
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
        final var original   = new JsonNode[] { MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2"
                }
                """) };
        final var expected   = new JsonNode[] { MAPPER.readTree("""
                {
                	"key2" : "value2"
                }
                """) };
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_handlerHandlesMono_handlerModifiesMonoEvent() throws JacksonException {
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
        final var original   = Mono.just(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2"
                }
                """));
        final var expected   = MAPPER.readTree("""
                {
                	"key2" : "value2"
                }
                """);
        StepVerifier.create((Publisher<JsonNode>) handler.apply(original)).expectNext(expected).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_handlerHandlesFlux_handlerModifiesFluxEvents() throws JacksonException {
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
        final var original   = Flux.just(MAPPER.readTree("""
                {
                	"key1" : "value1",
                	"key2" : "value2"
                }
                """));
        final var expected   = MAPPER.readTree("""
                {
                	"key2" : "value2"
                }
                """);
        StepVerifier.create((Publisher<JsonNode>) handler.apply(original)).expectNext(expected).verifyComplete();
    }

    @Test
    void when_replaceActionHasNoReplacement_then_Error() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "replace",
                			"path" : "$.key1"
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
        assertThrows(AccessConstraintViolationException.class, () -> handler.apply(original));
    }

    @Test
    void when_replaceActionSpecified_then_dataIsReplaced() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type"         : "replace",
                			"path"         : "$.key1",
                			"replacement"  : {
                				"I"        : "am",
                				"replaced" : "value"
                			}
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
        final var expected   = MAPPER.readTree("""
                {
                	"key1"  : {
                		"I"        : "am",
                		"replaced" : "value"
                	},
                	"key2"  : "value2"
                }
                """);
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Test
    void when_replaceInMap_then_dataIsReplaced() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type"        : "replace",
                			"path"        : "$.key1",
                			"replacement" : "replaced"
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = Map.of("key1", "value1", "key2", "value2");
        final var expected   = Map.of("key1", "replaced", "key2", "value2");
        assertThat(handler.apply(original)).isEqualTo(expected);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Person {
        String name;
        int    age;
    }

    @Test
    void when_replaceInPoJo_then_dataIsReplaced() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type"        : "replace",
                			"path"        : "$.name",
                			"replacement" : "Alice"
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = new Person("Bob", 32);
        final var expected   = new Person("Alice", 32);
        final var actual     = handler.apply(original);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void when_replaceInPoJoAndMarshallingFails_then_Error() throws JacksonException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = toValue("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "replace",
                			"path" : "$.age",
                			"replacement" : "Alice"
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = new Person("Bob", 32);
        assertThrows(RuntimeException.class, () -> handler.apply(original));
    }

}
