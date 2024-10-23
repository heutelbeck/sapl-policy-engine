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
package io.sapl.functions;

import static io.sapl.assertj.SaplAssertions.assertThatVal;
import static io.sapl.hamcrest.Matchers.val;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.interpreter.Val;

class StandardFunctionLibraryTests {

    private static final String HTML_DOCUMENT = """
            <!DOCTYPE html>
            <html>
            <body>
            <p>First</p>
            <p>Second</p>
            </body>
            </html>
            """;

    private static final String XML_DOCUMENT = """
            <Flower>
                <name>Poppy</name>
                <color>RED</color>
                <petals>9</petals>
            </Flower>
            """;

    @Test
    void xmlToJsonTest() {
        final var html = StandardFunctionLibrary.xmlToVal(Val.of(HTML_DOCUMENT));
        assertThat(html.get().get("body").get("p").get(0).asText()).isEqualTo("First");
        final var xml = StandardFunctionLibrary.xmlToVal(Val.of(XML_DOCUMENT));
        assertThat(xml.get().get("name").asText()).isEqualTo("Poppy");
        assertThatThrownBy(() -> StandardFunctionLibrary.xmlToVal(Val.of("}NOT/><XML")))
                .isInstanceOf(JsonParseException.class);
    }

    @Test
    void lengthOfEmptyIsZero() {
        assertThat(StandardFunctionLibrary.length(Val.ofEmptyArray()), is(val(0)));
        assertThat(StandardFunctionLibrary.length(Val.ofEmptyObject()), is(val(0)));
    }

    @Test
    void lengthOfArrayWithElements() {
        final var array = Val.JSON.arrayNode();
        array.add(Val.JSON.booleanNode(false));
        array.add(Val.JSON.booleanNode(false));
        array.add(Val.JSON.booleanNode(false));
        array.add(Val.JSON.booleanNode(false));
        assertThat(StandardFunctionLibrary.length(Val.of(array)), is(val(4)));
    }

    @Test
    void lengthOfObjectWithElements() {
        final var object = Val.JSON.objectNode();
        object.set("key1", Val.JSON.booleanNode(false));
        object.set("key2", Val.JSON.booleanNode(false));
        object.set("key3", Val.JSON.booleanNode(false));
        object.set("key4", Val.JSON.booleanNode(false));
        object.set("key5", Val.JSON.booleanNode(false));
        assertThat(StandardFunctionLibrary.length(Val.of(object)), is(val(5)));
    }

    @Test
    void lengthOfText() {
        assertThat(StandardFunctionLibrary.length(Val.of("ABC")), is(val(3)));
    }

    @Test
    void numberToStringBooleanLeftIntact() throws JsonProcessingException {
        assertThat(StandardFunctionLibrary.asString(Val.TRUE), is(val("true")));
        assertThat(StandardFunctionLibrary.asString(Val.ofJson("[1,2,3]")), is(val("[1,2,3]")));
    }

    @Test
    void numberToStringSomeNumberLeftIntact() {
        assertThat(StandardFunctionLibrary.asString(Val.of(1.23e-1D)), is(val("0.123")));
    }

    @Test
    void numberToStringNullEmptyString() {
        assertThat(StandardFunctionLibrary.asString(Val.NULL), is(val("null")));
    }

    @Test
    void numberToStringTextIntact() {
        assertThat(StandardFunctionLibrary.asString(Val.of("ABC")), is(val("ABC")));
    }

    @Test
    void when_noError_then_originalValueIsReturned() {
        assertThat(StandardFunctionLibrary.onErrorMap(Val.of("ORIGINAL"), Val.of("REPLACED")), is(val("ORIGINAL")));
    }

    @Test
    void when_error_then_valueIsReplaced() {
        assertThat(StandardFunctionLibrary.onErrorMap(Val.error((String) null), Val.of("REPLACED")),
                is(val("REPLACED")));
    }

    @Test
    void when_concatenateNoParameters_then_returnsEmptyArray() {
        assertThatVal(StandardFunctionLibrary.concatenate()).hasValue().isArray().isEmpty();
    }

    @Test
    void when_concatenate_then_concatenatesCorrectly() throws JsonProcessingException {
        assertThatVal(StandardFunctionLibrary.concatenate(Val.ofJson("[ 1,2 ]"), Val.ofJson("[ ]"),
                Val.ofJson("[ 3,4 ]"), Val.ofJson("[ 5,6 ]"))).hasValue().isArray()
                .isEqualTo(Val.ofJson("[1,2,3,4,5,6]").getArrayNode());
    }

    @Test
    void when_intersectNoParameters_then_returnsEmptyArray() {
        assertThatVal(StandardFunctionLibrary.intersect()).hasValue().isArray().isEmpty();
    }

    @Test
    void when_intersect_then_returnsIntersection() throws JsonProcessingException {
        final var actual = StandardFunctionLibrary.intersect(Val.ofJson("[ 1,2,3,4 ]"), Val.ofJson("[ 3,4 ]"),
                Val.ofJson("[ 4,1,3 ]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(2);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(Val.of(4).getJsonNode(), Val.of(3).getJsonNode());
    }

    @Test
    void when_intersectWithOneEmptySet_then_returnsEmptyArray() throws JsonProcessingException {
        assertThatVal(StandardFunctionLibrary.intersect(Val.ofJson("[ 1,2,3,4 ]"), Val.ofJson("[ ]"),
                Val.ofJson("[ 3,4 ]"), Val.ofJson("[ 4,1,3 ]"))).hasValue().isArray().isEmpty();
    }

    @Test
    void when_unionNoParameters_then_returnsEmptyArray() {
        assertThatVal(StandardFunctionLibrary.union()).hasValue().isArray().isEmpty();
    }

    @Test
    void when_union_then_returnsUnion() throws JsonProcessingException {
        final var actual = StandardFunctionLibrary.union(Val.ofJson("[ 1,2,3 ]"), Val.ofJson("[ ]"),
                Val.ofJson("[ 3,4 ]"), Val.ofJson("[ 4,1,3 ]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(4);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(Val.of(4).getJsonNode(), Val.of(3).getJsonNode(),
                Val.of(1).getJsonNode(), Val.of(2).getJsonNode());
    }

    @Test
    void when_toSet_then_returnsSet() throws JsonProcessingException {
        final var actual = StandardFunctionLibrary.toSet(Val.ofJson("[ 1,2,3,2,1,1,1,5,8,10,8,10,3 ]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(6);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(Val.of(1).getJsonNode(), Val.of(2).getJsonNode(),
                Val.of(3).getJsonNode(), Val.of(5).getJsonNode(), Val.of(8).getJsonNode(), Val.of(10).getJsonNode());
    }

    @Test
    void when_differenceWithEmptySet_then_returnsOriginalArrayAsSet() throws JsonProcessingException {
        final var actual = StandardFunctionLibrary.difference(Val.ofJson("[ 1,2,3,2,1,1,1,5,8,10,8,10,3 ]"),
                Val.ofJson("[]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(6);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(Val.of(1).getJsonNode(), Val.of(2).getJsonNode(),
                Val.of(3).getJsonNode(), Val.of(5).getJsonNode(), Val.of(8).getJsonNode(), Val.of(10).getJsonNode());
    }

    @Test
    void when_differenceWithNoIntersection_then_returnsOriginalArrayAsSet() throws JsonProcessingException {
        final var actual = StandardFunctionLibrary.difference(Val.ofJson("[ 1,2,3,2,1,1,1,5,8,10,8,10,3 ]"),
                Val.ofJson("[20,22,\"abc\"]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(6);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(Val.of(1).getJsonNode(), Val.of(2).getJsonNode(),
                Val.of(3).getJsonNode(), Val.of(5).getJsonNode(), Val.of(8).getJsonNode(), Val.of(10).getJsonNode());
    }

    @Test
    void when_differenceWithIntersection_then_returnsCorrectDifferecrAsSet() throws JsonProcessingException {
        final var actual = StandardFunctionLibrary.difference(Val.ofJson("[ 1,2,3,2,1,1,1,5,8,10,8,10,3 ]"),
                Val.ofJson("[10,2]"));
        assertThatVal(actual).hasValue().isArray();
        assertThat(actual.getArrayNode()).hasSize(4);
        assertThat(actual.getArrayNode()).containsExactlyInAnyOrder(Val.of(1).getJsonNode(), Val.of(3).getJsonNode(),
                Val.of(5).getJsonNode(), Val.of(8).getJsonNode());
    }
}
