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
package io.sapl.functions;

import com.fasterxml.jackson.core.JsonParseException;
import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlFunctionLibraryTests {

    private static final String SIMPLE_XML = """
            <Flower>
                <name>Poppy</name>
                <color>RED</color>
                <petals>9</petals>
            </Flower>
            """;

    private static final String HTML_DOCUMENT = """
            <!DOCTYPE html>
            <html>
            <body>
            <p>First</p>
            <p>Second</p>
            </body>
            </html>
            """;

    @Test
    void xmlToValParsesSimpleXml() {
        val result = XmlFunctionLibrary.xmlToVal(Val.of(SIMPLE_XML));
        assertThat(result.isDefined()).isTrue();
    }

    @Test
    void xmlToValParsesHtmlDocument() {
        val result = XmlFunctionLibrary.xmlToVal(Val.of(HTML_DOCUMENT));
        assertThat(result.get().get("body").get("p").get(0).asText()).isEqualTo("First");
        assertThat(result.get().get("body").get("p").get(1).asText()).isEqualTo("Second");
    }

    @Test
    void xmlToValParsesNestedElements() {
        val xml    = """
                <root>
                    <parent>
                        <child>value</child>
                    </parent>
                </root>
                """;
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.get().get("parent").get("child").asText()).isEqualTo("value");
    }

    @Test
    void xmlToValParsesSelfClosingTags() {
        val xml    = "<root><empty/></root>";
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.isDefined()).isTrue();
    }

    @Test
    void xmlToValParsesMultipleChildrenWithSameName() {
        val xml    = """
                <items>
                    <item>First</item>
                    <item>Second</item>
                    <item>Third</item>
                </items>
                """;
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.get().get("item").isArray()).isTrue();
        assertThat(result.get().get("item").size()).isEqualTo(3);
    }

    @Test
    void xmlToValParsesAttributes() {
        val xml    = "<person name=\"Alice\" age=\"30\"/>";
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.isDefined()).isTrue();
    }

    @Test
    void xmlToValParsesEmptyElement() {
        val xml    = "<root></root>";
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.isDefined()).isTrue();
    }

    @Test
    void xmlToValHandlesWhitespace() {
        val xml    = """
                <root>
                    <item>   Content with spaces   </item>
                </root>
                """;
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.get().get("item").asText()).contains("Content with spaces");
    }

    @Test
    void xmlToValHandlesSpecialCharacters() {
        val xml    = "<root><message>Hello &amp; Goodbye</message></root>";
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.get().get("message").asText()).isEqualTo("Hello & Goodbye");
    }

    @Test
    void xmlToValHandlesUnicodeCharacters() {
        val xml    = "<root><message>Hello 世界 🌸</message></root>";
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.get().get("message").asText()).isEqualTo("Hello 世界 🌸");
    }

    @ParameterizedTest
    @ValueSource(strings = { "<invalid", "<root><unclosed>", "}NOT/><XML", "<root></wrong>", "not xml at all" })
    void xmlToValThrowsExceptionForInvalidXml(String invalidXml) {
        assertThatThrownBy(() -> XmlFunctionLibrary.xmlToVal(Val.of(invalidXml)))
                .isInstanceOf(JsonParseException.class);
    }

    @Test
    void valToXmlConvertsObjectToXmlString() {
        val object = Val.JSON.objectNode();
        object.put("name", "Rose");
        object.put("color", "PINK");
        object.put("petals", 5);

        val result = XmlFunctionLibrary.valToXml(Val.of(object));
        assertThat(result.getText()).contains("<name>Rose</name>");
        assertThat(result.getText()).contains("<color>PINK</color>");
        assertThat(result.getText()).contains("<petals>5</petals>");
    }

    @Test
    void valToXmlHandlesNestedObjects() {
        val parent = Val.JSON.objectNode();
        val child  = Val.JSON.objectNode();
        child.put("value", "test");
        parent.set("child", child);

        val result = XmlFunctionLibrary.valToXml(Val.of(parent));
        assertThat(result.getText()).contains("<child>");
        assertThat(result.getText()).contains("<value>test</value>");
    }

    @Test
    void valToXmlReturnsErrorForErrorValue() {
        val error  = Val.error("Test error");
        val result = XmlFunctionLibrary.valToXml(error);
        assertThat(result.isError()).isTrue();
    }

    @Test
    void valToXmlReturnsUndefinedForUndefinedValue() {
        val undefined = Val.UNDEFINED;
        val result    = XmlFunctionLibrary.valToXml(undefined);
        assertThat(result.isUndefined()).isTrue();
    }

    @Test
    void valToXmlHandlesEmptyObject() {
        val result = XmlFunctionLibrary.valToXml(Val.ofEmptyObject());
        assertThat(result.getText()).isNotEmpty();
    }

    @Test
    void valToXmlHandlesPrimitiveValues() {
        val result = XmlFunctionLibrary.valToXml(Val.of("simple text"));
        assertThat(result.getText()).contains("simple text");
    }

    @Test
    void valToXmlHandlesNumbers() {
        val result = XmlFunctionLibrary.valToXml(Val.of(42));
        assertThat(result.getText()).contains("42");
    }

    @Test
    void valToXmlHandlesBooleans() {
        val result = XmlFunctionLibrary.valToXml(Val.TRUE);
        assertThat(result.getText()).contains("true");
    }

}
