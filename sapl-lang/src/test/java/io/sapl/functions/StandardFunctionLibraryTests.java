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

import static io.sapl.hamcrest.Matchers.val;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParseException;

import io.sapl.api.interpreter.Val;

class StandardFunctionLibraryTests {

    private final static String HTML_DOCUMENT = """
            <!DOCTYPE html>
            <html>
            <body>
            <p>First</p>
            <p>Second</p>
            </body>
            </html>
            """;

    private final static String XML_DOCUMENT = """
            <Flower>
                <name>Poppy</name>
                <color>RED</color>
                <petals>9</petals>
            </Flower>
            """;

    @Test
    void xmlToJsonTest() throws Exception {
        var html = StandardFunctionLibrary.xmlToJson(Val.of(HTML_DOCUMENT));
        assertThat(html.get().get("body").get("p").get(0).asText()).isEqualTo("First");
        var xml = StandardFunctionLibrary.xmlToJson(Val.of(XML_DOCUMENT));
        assertThat(xml.get().get("name").asText()).isEqualTo("Poppy");
        assertThatThrownBy(() -> StandardFunctionLibrary.xmlToJson(Val.of("}NOT/><XML")))
                .isInstanceOf(JsonParseException.class);
    }

    @Test
    void lengthOfEmptyIsZero() {
        assertThat(StandardFunctionLibrary.length(Val.ofEmptyArray()), is(val(0)));
        assertThat(StandardFunctionLibrary.length(Val.ofEmptyObject()), is(val(0)));
    }

    @Test
    void lengthOfArrayWithElements() {
        var array = Val.JSON.arrayNode();
        array.add(Val.JSON.booleanNode(false));
        array.add(Val.JSON.booleanNode(false));
        array.add(Val.JSON.booleanNode(false));
        array.add(Val.JSON.booleanNode(false));
        assertThat(StandardFunctionLibrary.length(Val.of(array)), is(val(4)));
    }

    @Test
    void lengthOfObjectWithElements() {
        var object = Val.JSON.objectNode();
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
    void numberToStringBooleanLeftIntact() {
        assertThat(StandardFunctionLibrary.numberToString(Val.TRUE), is(val("true")));
    }

    @Test
    void numberToStringSomeNumberLeftIntact() {
        assertThat(StandardFunctionLibrary.numberToString(Val.of(1.23e-1D)), is(val("0.123")));
    }

    @Test
    void numberToStringNullEmptyString() {
        assertThat(StandardFunctionLibrary.numberToString(Val.NULL), is(val("")));
    }

    @Test
    void numberToStringTextIntact() {
        assertThat(StandardFunctionLibrary.numberToString(Val.of("ABC")), is(val("ABC")));
    }

    @Test
    void when_noError_then_originalValueIsReturned() {
        assertThat(StandardFunctionLibrary.onErrorMap(Val.of("ORIGINAL"), Val.of("REPLACED")), is(val("ORIGINAL")));
    }

    @Test
    void when_error_then_valueIsReplaced() {
        assertThat(StandardFunctionLibrary.onErrorMap(Val.error(), Val.of("REPLACED")), is(val("REPLACED")));
    }

}
