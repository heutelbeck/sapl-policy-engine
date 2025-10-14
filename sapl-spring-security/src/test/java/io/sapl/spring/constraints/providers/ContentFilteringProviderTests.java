/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.spotify.hamcrest.jackson.IsJsonMissing.jsonMissing;
import static com.spotify.hamcrest.jackson.IsJsonObject.jsonObject;
import static com.spotify.hamcrest.jackson.IsJsonText.jsonText;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentFilteringProviderTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void when_getSupportedType_then_isObject() {
        final var sut = new ContentFilteringProvider(MAPPER);
        assertThat(sut.getSupportedType(), is(Object.class));
    }

    @Test
    void when_constraintIsNull_then_notResponsible() {
        final var sut        = new ContentFilteringProvider(MAPPER);
        JsonNode  constraint = null;
        assertThat(sut.isResponsible(constraint), is(false));
    }

    @Test
    void when_constraintNonObject_then_notResponsible() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("123");
        assertThat(sut.isResponsible(constraint), is(false));
    }

    @Test
    void when_constraintNoType_then_notResponsible() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("{ }");
        assertThat(sut.isResponsible(constraint), is(false));
    }

    @Test
    void when_constraintTypeNonTextual_then_notResponsible() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type" : 123
                }
                """);
        assertThat(sut.isResponsible(constraint), is(false));
    }

    @Test
    void when_constraintWrongType_then_notResponsible() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type" : "unrelatedType"
                }
                """);
        assertThat(sut.isResponsible(constraint), is(false));
    }

    @Test
    void when_constraintTypeCorrect_then_isResponsible() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type" : "filterJsonContent"
                }
                """);
        assertThat(sut.isResponsible(constraint), is(true));
    }

    @Test
    void when_noActionsSpecified_then_isIdentity() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(original));
    }

    @Test
    void when_noActionType_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [ { "path" : "$.key1"} ]
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
    void when_noActionPath_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                  	"actions" : [ { "type" : "delete" } ]
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
    void when_actionNotAnObject_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type" : "filterJsonContent",
                	"actions" : [ 123 ]
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
    void when_actionsNotAnArray_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : 123
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
    void when_actionPathNotTextual_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [ {
                					"type" : "delete",
                					"path" : 123
                				  } ]
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
    void when_actionTypeNotTextual_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [ {
                					"type" : 123,
                					"path" : "$.key1"
                				  } ]
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
    void when_unknownAction_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [ {
                					"type" : "unknown action",
                					"path" : "$.key1"
                				  } ]
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
    void when_blackenHasNonTextualReplacement_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type"        : "blacken",
                			"path"        : "$.key1",
                			"replacement" : 123
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
    void when_blackenTargetsNonTextualNode_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "blacken",
                			"path" : "$.key1"
                		}
                	]
                }
                """);
        final var handler    = sut.getHandler(constraint);
        final var original   = MAPPER.readTree("""
                {
                	"key1" : 123,
                	"key2" : "value2"
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> handler.apply(original));
    }

    @Test
    void when_blackenDiscloseRightNonInteger_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type"          : "blacken",
                			"path"          : "$.key1",
                			"replacement"   : "X",
                			"discloseRight" : null,
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
        assertThrows(AccessConstraintViolationException.class, () -> handler.apply(original));
    }

    @Test
    void when_blackenDiscloseLeftNonInteger_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type"          : "blacken",
                			"path"          : "$.key1",
                			"replacement"   : "X",
                			"discloseRight" : 1,
                			"discloseLeft"  : "wrongType"
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
    void when_blacken_then_textIsBlackened() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat((JsonNode) handler.apply(original), is(jsonObject().where("key1", is(jsonText("vXXXX1")))));
    }

    @Test
    void when_blackenWithDefinedLengthAndNegativeInteger_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
    void when_blackenWithDefinedLengthAndStringValue_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
    void when_blackenWithDefinedLength_then_textIsBlackened() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat((JsonNode) handler.apply(original), is(jsonObject().where("key1", is(jsonText("vXXX1")))));
    }

    @Test
    void when_multipleActions_then_allAreExecuted() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat((JsonNode) handler.apply(original),
                is(jsonObject().where("key1", is(jsonText("vXXXX1"))).where("key2", is(jsonMissing()))));
    }

    @Test
    void when_blackenWithDefaultReplacement_then_textIsBlackened() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat((JsonNode) handler.apply(original), is(jsonObject().where("key1", is(jsonText("v████1")))));
    }

    @Test
    void when_stringToBlackenIsShorterThanDisclosedRange_then_textDoesNotChange() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat((JsonNode) handler.apply(original), is(jsonObject().where("key1", is(jsonText("value1")))));
    }

    @Test
    void when_blackenWithNoParameters_then_textIsBlackenedNoCharsDisclosed() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat((JsonNode) handler.apply(original), is(jsonObject().where("key1", is(jsonText("██████")))));
    }

    @Test
    void when_deleteActionSpecified_then_dataIsRemovedFromJson() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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

        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_pathNotExisting_then_AccessDeniedException() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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

    @Test
    void when_malformedConditionNotObject_then_AccessConstraintViolationException() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : [ 1 ]
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_malformedConditionNoPath_then_AccessConstraintViolationException() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : [ {} ]
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_malformedConditionGEQNotANumber_then_AccessConstraintViolationException() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : [
                		{
                			"path"  : "$.key1",
                			"type"  : ">=",
                			"value" : "not a number"
                		}
                	]
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_malformedConditionLeqNotANumber_then_AccessConstraintViolationException() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : [
                		{
                			"path"  : "$.key1",
                			"type"  : "<=",
                			"value" : "not a number"
                		}
                	]
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_malformedConditionLtNotANumber_then_AccessConstraintViolationException() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : [
                		{
                			"path"  : "$.key1",
                			"type"  : "<",
                			"value" : "not a number"
                		}
                	]
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_malformedConditionGtNotANumber_then_AccessConstraintViolationException() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : [
                		{
                			"path"  : "$.key1",
                			"type"  : ">",
                			"value" : "not a number"
                		}
                	]
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_malformedConditionEqNotNumberOrText_then_AccessConstraintViolationException()
            throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : [
                		{
                			"path"  : "$.key1",
                			"type"  : "==",
                			"value" : []
                		}
                	]
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_malformedConditionRegexNotText_then_AccessConstraintViolationException() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : [
                		{
                			"path"  : "$.key1",
                			"type"  : "=~",
                			"value" : []
                		}
                	]
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_constraintNull_then_AccessConstraintViolationException() {
        final var sut = new ContentFilteringProvider(MAPPER);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(null));
    }

    @Test
    void when_malformedConditionTypeNonTextual_then_AccessConstraintViolationException()
            throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : [
                		{
                			"path"  : "$.key1",
                			"type"  : 12,
                			"value" : "abc"
                		}
                	]
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_malformedConstraintNonObject_then_AccessConstraintViolationException() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("123");
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_malformedConstraintConditionsNotArray_then_AccessConstraintViolationException()
            throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : 123
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_malformedConstraintConditionsEmpty_then_actionAppliedAndConditionAlwaysTrue()
            throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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

        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_malformedConditionTypeUnknown_then_AccessConstraintViolationException() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : [
                		{
                			"path"  : "$.key1",
                			"type"  : "something unknown",
                			"value" : "abc"
                		}
                	]
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_malformedConditionValueMissing_then_AccessConstraintViolationException() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : [
                		{
                			"path"  : "$.key1",
                			"type"  : "=="
                		}
                	]
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_malformedConditionPathValueMissing_then_AccessConstraintViolationException()
            throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : [
                		{
                			"type"  : "==",
                			"value" : "abc"
                		}
                	]
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_malformedConditionTypeMissing_then_AccessConstraintViolationException() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : [
                		{
                			"path"  : "$.key",
                			"value" : "abc"
                		}
                	]
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_malformedConditionPathNonTextual_then_AccessConstraintViolationException()
            throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
                {
                	"type"    : "filterJsonContent",
                	"actions" : [
                		{
                			"type" : "delete",
                			"path" : "$.key3"
                		}
                	],
                	"conditions" : [
                		{
                			"type"  : "==",
                			"path"  : 123,
                			"value" : "abc"
                		}
                	]
                }
                """);
        assertThrows(AccessConstraintViolationException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_predicatePathNotExisting_then_AccessConstraintViolationException() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
    void when_predicateNotMatching_then_noModification() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(original));
    }

    @Test
    void when_handlerHandlesNull_handlerReturnsNull() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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

    @Test
    void when_handlerHandlesOptional_handlerReturnsModifiedOptional() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_handlerHandlesList_handlerReturnsModifiedListContents() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_handlerHandlesListMultipleConditions_handlerReturnsModifiedListContents() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_handlerHandlesListEqNumberDataNotNumber_handlerNoModification() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(original));
    }

    @Test
    void when_handlerNumEq_handlerModifiedMatching() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_handlerHandlesListMultipleConditionsAndOnlyOneHoldsInverted_noModifications()
            throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(original));
    }

    @Test
    void when_handlerHandlesListMultipleConditionsAndOnlyOneHolds_noModifications() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(original));
    }

    @Test
    void when_handlerHandlesListNumberAndTextComparisons_makeModifications() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_handlerHandlesListNumberAndNeqCondition_makeModificationsAtMatch() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_handlerHandlesListNumberAndEqCondition_makeModificationsAtMatch() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_handlerHandlesListNumberAndRegexCondition_makeModificationsAtMatch() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_handlerHandlesGeqCondition_makeModificationsAtMatch() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_handlerHandlesLeqCondition_makeModificationsAtMatch() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_handlerHandlesLtCondition_makeModificationsAtMatch() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_handlerHandlesGtCondition_makeModificationsAtMatch() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_handlerHandlesSet_handlerReturnsModifiedSetContents() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_handlerHandlesArray_handlerReturnsModifiedArrayContents() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_handlerHandlesMono_handlerModifiesMonoEvent() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
    void when_handlerHandlesFlux_handlerModifiesFluxEvents() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
    void when_replaceActionHasNoReplacement_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
    void when_replaceActionSpecified_then_dataIsReplaced() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Test
    void when_replaceInMap_then_dataIsReplaced() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(handler.apply(original), is(expected));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Person {
        String name;
        int    age;
    }

    @Test
    void when_replaceInPoJo_then_dataIsReplaced() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
        assertThat(actual, is(expected));
    }

    @Test
    void when_replaceInPoJoAndMarshallingFails_then_Error() throws JsonProcessingException {
        final var sut        = new ContentFilteringProvider(MAPPER);
        final var constraint = MAPPER.readTree("""
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
